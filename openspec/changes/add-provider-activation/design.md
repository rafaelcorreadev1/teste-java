# Design

## Context

Depois de `add-activation-requests`, as solicitações nascem `PENDING` e nada mais acontece com elas. A entidade já tem métodos de transição e `@Version`. O provedor é externo e o contrato assumido está abaixo. Requisitos nas specs `provider-activation` e `activation-requests`.

**Contrato assumido do provedor**

```
POST /provider/v1/activate
Idempotency-Key: <id da solicitação>
X-Correlation-Id: <correlation id>
X-Api-Key: <credencial>
{ "requestId": "...", "customerId": "...", "productCode": "..." }
```

## Goals / Non-Goals

**Goals:**
- Não ter nenhuma chamada HTTP dentro de uma transação de banco.
- Fazer com que a perda de uma instância nunca perca uma solicitação.
- Ter política de retentativa determinística e testável sem rede.

**Non-Goals:**
- Circuit breaker: o efeito vem do lote limitado + backoff. Fica descrito no README.
- Broker de mensagens e notificação de outros serviços (descritos no README).

## Decisions

### Assíncrono com a tabela como outbox (ver [ADR 0006](../../../docs/adr/0006-processamento-assincrono-tabela-como-outbox.md))

Migration `V2__add_provider_processing.sql`:

```sql
alter table activation_request
  add column attempts              int          not null default 0,
  add column next_attempt_at       timestamptz  not null default now(),
  add column processing_started_at timestamptz,
  add column failure_reason        varchar(64);
create index ix_activation_request_due        on activation_request (next_attempt_at)       where status = 'PENDING';
create index ix_activation_request_processing on activation_request (processing_started_at) where status = 'PROCESSING';
```

`ActivationDispatcher` roda com `@Scheduled(fixedDelayString = "${activation.worker.poll-interval:2s}")` e é habilitado por `activation.worker.enabled`, que fica desligado nos testes para que estes chamem o dispatcher diretamente:

1. `claimBatch()`: `@Transactional`, query nativa `... FOR UPDATE SKIP LOCKED LIMIT :batchSize`. Para cada linha chama `startProcessing(now)`, que faz `attempts++` e `processing_started_at = now`. O commit acontece ao sair do método. O incremento é feito na reivindicação, e não no resultado, para que uma instância que cai em loop também consuma tentativas.
2. Para cada solicitação reivindicada: `providerClient.activate(...)` → `ProviderResult`, **fora de transação**.
3. `applyResult(id, result)`: `@Transactional`, recarrega a linha e chama `switch (result)`:
   - `Success` → `complete()`;
   - `PermanentFailure f` → `fail("PROVIDER_REJECTED_" + f.status())`;
   - `TransientFailure t` → se `attempts >= maxAttempts`, `fail("MAX_ATTEMPTS_EXCEEDED")`; senão, `returnToPending(retryPolicy.nextAttemptAt(attempts, t.retryAfter()))`.

   Um `ObjectOptimisticLockingFailureException` aqui é logado em `warn` e ignorado: outra execução (por exemplo, a reconciliação) já mexeu na linha.

`claimBatch` e `applyResult` ficam em um bean separado (`ActivationStateStore`) do dispatcher. O `@Transactional` funciona por proxy, então uma chamada interna (`this.metodo()`) ignoraria a anotação.

O lote é processado sequencialmente por execução (`batchSize` padrão 10). O paralelismo vem de múltiplas instâncias. Paralelizar dentro da instância com virtual threads é uma evolução simples, mas não necessária.

**Reconciliação**: `@Scheduled` separado. Busca os ids em `PROCESSING` com `processing_started_at` anterior ao lease e libera cada um numa transação própria, chamando `returnToPending` na entidade. Um `UPDATE` em massa contornaria as transições do domínio (ADR 0004), e uma transação por linha faz com que um conflito de `@Version` (worker terminando ao mesmo tempo) afete só aquela linha. Se a tentativa já era a última, a solicitação vai para `FAILED` (`MAX_ATTEMPTS_EXCEEDED`). O `applyResult` ignora um resultado quando a linha não está mais em `PROCESSING`.

### Classificação, retentativa e idempotência (ver [ADR 0007](../../../docs/adr/0007-politica-de-retentativa-e-idempotencia-provedor.md))

- `ProviderResult` é um `sealed interface` com os records `Success`, `PermanentFailure(int status)` e `TransientFailure(String reason, Duration retryAfter)`.
- `ProviderClient` usa `RestClient` com `JdkClientHttpRequestFactory` (`connectTimeout` no `HttpClient`, fixado em HTTP/1.1 para evitar o upgrade h2c, e `readTimeout` na factory) e `exchange(...)`, que entrega a resposta crua e **não lança exceção** por status. O status é classificado em um método puro (`ProviderResponseClassifier`). `ResourceAccessException` (timeout, conexão recusada) vira `TransientFailure`.
- `RetryPolicy.nextAttemptAt(attempts, retryAfter)` é uma função pura com `Clock` e `RandomGenerator` injetados: `min(cap, base·2^(n−1)) · U(0.5,1.0)` ou `max(retryAfter, isso)`, limitado a `cap`.
- A credencial do provedor vem de `activation.provider.api-key` (env `PROVIDER_API_KEY`) e nunca é logada.

### Mock do provedor no compose

O WireMock é montado com `docker/wiremock/mappings/*.json`. O cenário é escolhido pelo `productCode`:

| `productCode` | Resposta |
|---|---|
| contém `REJECT` | 400 |
| contém `ERROR` | 500 |
| contém `SLOW` | 200 com atraso de 8 s (dispara timeout) |
| demais | 200 |

## Risks / Trade-offs

- [O provedor não honra `Idempotency-Key`] → É uma premissa documentada. O risco residual de duplicidade após timeout fica explícito no README.
- [Polling no banco adiciona carga] → Índice parcial pequeno, intervalo configurável e lote limitado. Acima de milhares por minuto, migrar para broker (ADR 0006).
- [Solicitações paradas em `FAILED` por `MAX_ATTEMPTS_EXCEEDED`] → Ficam visíveis por métrica (próxima change). Um reprocessamento manual fica descrito como evolução.

## Migration Plan

O V2 é aditivo, e as colunas têm default (linhas existentes ficam elegíveis imediatamente). Para fazer rollback do comportamento, basta desligar `activation.worker.enabled`.
