# Activation Service

Serviço de ativação de produtos de clientes: registra solicitações, permite consultar o status e realiza a ativação em um provedor externo.

**Stack:** Java 21 · Spring Boot 3.5 · Spring MVC · Spring Data JPA/Hibernate · PostgreSQL 16 · Flyway · Maven · JUnit 5 · Testcontainers · WireMock · Docker.

## Como executar

### Pré-requisitos

- JDK 21
- Docker (para o banco local e para os testes de integração com Testcontainers)

### Testes

```bash
./mvnw verify
```

Um único comando compila e roda:
- os testes unitários (`*Test`, Surefire);
- os testes de integração (`*IT`, Failsafe) contra um PostgreSQL 16 real, que o Testcontainers sobe automaticamente.

### Aplicação local

```bash
docker compose up -d --wait   # PostgreSQL 16 (localhost:5432) + mock do provedor (localhost:8089)
./mvnw spring-boot:run
curl localhost:8080/actuator/health
```

O schema é criado pelo Flyway na subida (`src/main/resources/db/migration`).

O mock do provedor (WireMock, mappings em `docker/wiremock/`) escolhe a resposta pelo `productCode`. Isso permite ver cada caminho da integração:

| `productCode` contém | Resposta do mock | Status final esperado |
|---|---|---|
| `REJECT` (ex.: `X-REJECT`) | 400 | `FAILED` (`PROVIDER_REJECTED_400`) |
| `ERROR` (ex.: `X-ERROR`) | 500 | `PENDING` com retentativas, depois `FAILED` (`MAX_ATTEMPTS_EXCEEDED`) |
| `SLOW` (ex.: `X-SLOW`) | 200 após 8 s (acima do timeout de 5 s) | igual ao caso `ERROR` |
| qualquer outro (ex.: `INTERNET-500`) | 200 | `COMPLETED` |

```bash
id=$(curl -s -X POST localhost:8080/api/v1/requests -H 'Content-Type: application/json' \
  -d '{"externalId":"EXT-1","customerId":"CUST-1","productCode":"INTERNET-500"}' | jq -r .id)
sleep 3 && curl -s localhost:8080/api/v1/requests/$id   # status: COMPLETED
```

### Configuração

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/activation` | URL JDBC do PostgreSQL |
| `DB_USER` | `activation` | Usuário do banco |
| `DB_PASSWORD` | `activation` | Senha do banco |
| `SERVER_PORT` | `8080` | Porta HTTP |
| `PROVIDER_BASE_URL` | `http://localhost:8089` | URL base do provedor de ativação |
| `PROVIDER_API_KEY` | `local-dev-key` | Credencial do provedor (em produção, injetada a partir de um cofre de segredos) |
| `PROVIDER_CONNECT_TIMEOUT` | `2s` | Timeout de conexão com o provedor |
| `PROVIDER_READ_TIMEOUT` | `5s` | Timeout de leitura da resposta do provedor |
| `WORKER_ENABLED` | `true` | Liga o envio assíncrono e a reconciliação |
| `WORKER_POLL_INTERVAL` | `2s` | Intervalo entre buscas de solicitações pendentes |
| `WORKER_RECONCILE_INTERVAL` | `1m` | Intervalo da reconciliação de solicitações presas em `PROCESSING` |

## API

### `POST /api/v1/requests`

```bash
curl -i -X POST localhost:8080/api/v1/requests \
  -H 'Content-Type: application/json' \
  -d '{"externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500"}'
```

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/api/v1/requests/0b7f3c1e-5d2a-4c4e-9a51-2f8d7b1e6c90
X-Correlation-Id: 6f1c...

{"id":"0b7f3c1e-...","externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500",
 "status":"PENDING","attempts":0,"failureReason":null,
 "createdAt":"2026-10-27T14:03:11.123Z","updatedAt":"2026-10-27T14:03:11.123Z"}
```

O `201` significa "registrada". A ativação no provedor acontece em segundo plano, e o cliente acompanha o resultado pelo `GET`. `attempts` conta os envios ao provedor, e `failureReason` explica um `FAILED`.

Regras de validação, iguais para `externalId`, `customerId` e `productCode`:
- obrigatório e não vazio;
- no máximo 64 caracteres;
- apenas `[A-Za-z0-9_-]`.

Datas em UTC, ISO-8601, com precisão de milissegundos.

### `GET /api/v1/requests/{id}`

Retorna a solicitação e o status atual. Responde `404` se não existir e `400` se o id não for um UUID.

### Erros

Todas as respostas de erro usam [Problem Details (RFC 9457)](https://www.rfc-editor.org/rfc/rfc9457) com `traceId`, que é o mesmo valor do header `X-Correlation-Id` e dos logs:

```json
{
  "type": "https://api.deroyque.com/problems/validation-error",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "Um ou mais campos não passaram na validação.",
  "instance": "/api/v1/requests",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "errors": [{ "field": "customerId", "message": "must not be blank" }]
}
```

| Situação | Status | `type` |
|---|---|---|
| Campo inválido / id malformado | 400 | `validation-error` |
| JSON malformado | 400 | `malformed-request` |
| Solicitação inexistente | 404 | `not-found` |
| `externalId` repetido com conteúdo diferente | 409 | `duplicate-request` |
| Atualização concorrente detectada | 409 | `concurrent-update` |
| Erro inesperado (detalhes só no log) | 500 | `internal-error` |

## Decisões técnicas

As decisões de arquitetura estão registradas em [`docs/adr/`](docs/adr/README.md). As especificações de comportamento (requisitos e cenários) estão em [`openspec/`](openspec/).

### Duplicidade (item 3) — [ADR 0003](docs/adr/0003-unicidade-externalid-constraint-banco.md)

Verificar se o `externalId` existe e depois inserir **não basta**. Em READ COMMITTED, duas transações simultâneas consultam, nenhuma enxerga a linha ainda não commitada da outra, e as duas inserem. `@Transactional` não fecha essa janela, e `synchronized` não vale entre instâncias.

A garantia vem de uma **constraint `UNIQUE (external_id)` no PostgreSQL**:

1. O insert roda numa transação própria, com `saveAndFlush` para forçar o `INSERT` dentro dela.
2. Se o banco recusar por violação **dessa** constraint (identificada pelo nome), o serviço, fora da transação que falhou, lê o registro vencedor e decide a resposta:

| Cenário | Resposta | Justificativa |
|---|---|---|
| `externalId` novo | `201 Created` + `Location` | Criação. |
| Mesmo `externalId`, mesmo conteúdo | `200 OK` com o registro existente | É um retry legítimo, e a operação é idempotente. Um `409` faria a origem tratar como erro algo que já deu certo. |
| Mesmo `externalId`, conteúdo diferente | `409 Conflict` | É conflito real, que a origem precisa corrigir. O registro original não é alterado. |
| Duas requisições idênticas em paralelo | Uma `201`, a outra `200` | O banco bloqueia o segundo insert até o primeiro commitar, e então ele cai no caso de retry. |

Coberto por `ConcurrentCreationIT`: 10 threads simultâneas contra Postgres real (Testcontainers) resultam em exatamente 1 registro, 1 criação e 9 respostas "existente".

### Status da solicitação — [ADR 0004](docs/adr/0004-maquina-de-estados-no-dominio.md)

`PENDING → PROCESSING → COMPLETED | FAILED`, com `PROCESSING → PENDING` para falha temporária. As transições são métodos da entidade, que não tem setters, e estados terminais não mudam. `@Version` impede que duas escritas concorrentes se sobrescrevam.

### Integração com o provedor (item 5) — [ADR 0006](docs/adr/0006-processamento-assincrono-tabela-como-outbox.md) e [ADR 0007](docs/adr/0007-politica-de-retentativa-e-idempotencia-provedor.md)

| Retorno do provedor | Novo status | Nova tentativa? | Justificativa |
|---|---|---|---|
| Sucesso (2xx) | `COMPLETED` | Não | Produto ativado. |
| HTTP 400 / 422 | `FAILED` (`PROVIDER_REJECTED_4xx`) | Não | Dados inválidos para o provedor; repetir dá o mesmo resultado. |
| Outros 4xx (401, 403, 404, 409) | `FAILED` (`PROVIDER_REJECTED_4xx`) | Não | Erro de credencial ou contrato, que exige intervenção. Não adianta insistir. |
| HTTP 408 | `PENDING` | Sim, com backoff | Timeout do lado do provedor; é temporário. |
| HTTP 429 / 503 com `Retry-After` | `PENDING` | Sim, não antes do `Retry-After` (limitado a 5 min) | O provedor pediu explicitamente para aguardar. |
| HTTP 500 / 502 / 503 / 504 | `PENDING` | Sim, com backoff | Falha temporária do provedor. |
| Timeout / conexão recusada | `PENDING` | Sim, com backoff e a **mesma** `Idempotency-Key` | Resultado ambíguo, porque o provedor pode ter ativado. Repetir só é seguro por causa da chave de idempotência. |
| 5ª tentativa falha | `FAILED` (`MAX_ATTEMPTS_EXCEEDED`) | Não | Fica visível para análise e reprocessamento manual. |

Backoff: `min(5 min, 2 s × 2^(n−1))` multiplicado por um fator aleatório entre 0,5 e 1,0 (*jitter*). O jitter evita que solicitações que falharam juntas voltem todas ao mesmo tempo e derrubem o provedor de novo.

**Síncrono ou assíncrono?** Assíncrono. O `POST` grava a solicitação como `PENDING` e responde na hora. Se esperasse o provedor, a latência da API passaria a ser a dele (o cenário do item 10 mostra 400 ms → 3 s), e cada requisição seguraria uma thread do Tomcat durante toda a espera.

**Transação × chamada remota.** Nenhuma chamada HTTP acontece dentro de uma transação de banco. O worker trabalha em três passos:
1. reivindica um lote numa transação curta (`SELECT ... FOR UPDATE SKIP LOCKED` → `PROCESSING`);
2. chama o provedor **sem transação aberta**;
3. grava o resultado em outra transação curta.

**Como garanto que a solicitação gravada será enviada, mesmo se a aplicação cair?** A linha `PENDING`, gravada no mesmo commit da criação, **é** a mensagem a enviar: a própria tabela funciona como *transactional outbox*. Não existe janela entre "gravar" e "publicar". Se a instância cair:
- antes de reivindicar, a linha continua `PENDING` e outra instância a pega;
- durante o envio, a linha fica em `PROCESSING`, e a **reconciliação** a devolve para `PENDING` depois de 5 min (*lease*). O reenvio usa a mesma `Idempotency-Key`.

**Várias instâncias.** O `SKIP LOCKED` faz cada instância pular as linhas já travadas por outra, então ninguém envia a mesma solicitação em paralelo. O `ActivationStateStoreIT` prova isso, e o teste falha se a cláusula for removida.

**Resiliência.** Não usei Resilience4j. O retry é persistido (`next_attempt_at`), sobrevive a reinícios e pode ser observado por SQL, enquanto um retry em memória seguraria a thread e perderia o estado numa queda. O lote limitado (10 por ciclo) com backoff já reduz a pressão sobre um provedor fora do ar. Um circuit breaker seria o próximo passo para parar de tentar por completo enquanto o provedor estiver indisponível.

**Premissa.** O provedor precisa respeitar o `Idempotency-Key`. Sem isso, nenhuma estratégia evita 100% de ativação duplicada após um timeout, porque o resultado da primeira chamada é desconhecido.

## Pontos pendentes

- **Log de duplicidade em nível ERROR**: no retry legítimo, o `SqlExceptionHelper` do Hibernate registra `duplicate key` como ERROR antes de o serviço tratar a violação. Em produção, isso polui o log e pode disparar alertas. As opções são rebaixar esse logger específico ou trocar o insert por `INSERT ... ON CONFLICT DO NOTHING RETURNING` (ADR 0003).
- **Reprocessamento manual de `FAILED`**: não há endpoint administrativo para reenfileirar uma solicitação `FAILED` (por exemplo, depois de corrigir a credencial que causou um 401). Hoje, isso exigiria um `UPDATE` direto no banco.
- **Circuit breaker no cliente do provedor**: descrito na seção de integração, mas não implementado.
