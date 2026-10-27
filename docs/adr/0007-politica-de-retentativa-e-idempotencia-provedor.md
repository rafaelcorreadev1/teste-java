# 0007. Definir a política de retentativa e enviar chave de idempotência ao provedor

- Status: Proposto
- Data: 2026-10-27
- Change: `add-provider-activation`

## Contexto
Nem toda falha merece retry. O caso difícil é o **timeout**, que é ambíguo: o provedor pode ter ativado o produto e só a resposta ter se perdido. Repetir às cegas pode ativar duas vezes.

## Decisão
**Chave de idempotência.** Toda chamada envia `Idempotency-Key: <id da solicitação>`. A chave é a mesma em todas as tentativas, então um provedor que a respeite devolve o resultado da primeira execução em vez de ativar de novo. O `X-Correlation-Id` também é propagado.

**Timeouts explícitos e configuráveis.** Conexão de 2 s e leitura de 5 s (`activation.provider.connect-timeout` / `read-timeout`).

**Classificação da resposta**, modelada como `sealed interface ProviderResult permits Success, PermanentFailure, TransientFailure`:

| Retorno do provedor | Novo status | Nova tentativa? | Justificativa |
|---|---|---|---|
| 2xx | `COMPLETED` | Não | Ativado. |
| 400 / 422 | `FAILED` | Não | Dados inválidos para o provedor; repetir dá o mesmo resultado. |
| Outros 4xx (401, 403, 404) | `FAILED` | Não | Erro de configuração ou contrato que exige intervenção. Gera log de erro e métrica. |
| 429 / 503 com `Retry-After` | `PENDING` | Sim, após o `Retry-After` (limitado ao teto) | O provedor pediu para aguardar. |
| 500 / 502 / 503 / 504 | `PENDING` | Sim, com backoff | Falha temporária do lado dele. |
| Timeout / erro de conexão | `PENDING` | Sim, com backoff e a **mesma** chave | Resultado ambíguo. Só é seguro repetir por causa da chave de idempotência. |
| Limite de tentativas atingido | `FAILED` | Não | `failureReason = MAX_ATTEMPTS_EXCEEDED`, visível para reprocessamento manual. |

**Backoff exponencial com jitter.** `atraso = min(teto, base × 2^(tentativa−1)) × aleatório(0,5–1,0)`, com base de 2 s, teto de 5 min e no máximo 5 tentativas. O jitter evita que milhares de solicitações que falharam juntas tentem de novo juntas e derrubem o provedor outra vez (*thundering herd*).

## Alternativas consideradas
- **Spring Retry / Resilience4j Retry em memória**: segura a thread e perde o estado se a instância cair. O retry persistido em `next_attempt_at` sobrevive a reinícios e fica visível no banco.
- **Circuit breaker (Resilience4j)**: útil para parar de chamar um provedor fora do ar. Com retry persistido e lote limitado, o efeito já é parecido. Fica descrito no README como evolução.
- **Consultar o provedor antes de repetir**: o contrato do provedor não oferece consulta de status.

## Consequências
- A solução depende de o provedor honrar `Idempotency-Key`. Sem isso, não há como evitar 100% de duplicidade após timeout, e isso fica documentado como premissa.
- `attempts`, `next_attempt_at` e `failure_reason` passam a fazer parte do modelo.
