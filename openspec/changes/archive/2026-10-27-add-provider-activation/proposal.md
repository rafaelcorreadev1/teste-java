# Proposal

## Why

Uma solicitação registrada só tem valor quando o produto é ativado no provedor externo (item 5 do desafio). Essa integração é o ponto mais frágil do sistema: o provedor pode ficar lento, recusar dados, cair ou dar timeout sem que se saiba se ativou. Precisamos garantir que **toda** solicitação gravada chegue ao provedor, que falhas temporárias sejam retentadas com critério e que um retry após timeout não ative o produto duas vezes.

## What Changes

- Envio assíncrono: o `POST /api/v1/requests` continua respondendo na hora com `PENDING`, e um processamento em segundo plano envia as solicitações pendentes a `POST /provider/v1/activate`.
- Entrega garantida: a solicitação pendente fica persistida e é reenviada mesmo se a instância cair, e várias instâncias não enviam a mesma solicitação ao mesmo tempo.
- Classificação de cada retorno do provedor:
  - 2xx → `COMPLETED`;
  - 4xx de dados ou contrato → `FAILED`;
  - 5xx, timeout, erro de conexão, 429 e 503 → nova tentativa.
- Retentativa com backoff exponencial e jitter, respeitando `Retry-After`, com limite de tentativas. Ao atingir o limite, `FAILED`.
- Chave de idempotência e correlation id enviados ao provedor, com a mesma chave em todas as tentativas.
- Timeouts de conexão e leitura configuráveis.
- Reconciliação de solicitações presas em `PROCESSING`.
- A consulta passa a expor `attempts` e `failureReason`.
- Mock do provedor (WireMock) no `docker-compose` com cenários de sucesso, 400, 500 e lentidão.

## Capabilities

### New Capabilities

- `provider-activation`: envio das solicitações ao provedor externo, classificação de retornos, retentativa, idempotência e recuperação de envios interrompidos.

### Modified Capabilities

- `activation-requests`: a resposta da solicitação passa a incluir `attempts` e `failureReason`. É um requisito novo dentro da capability, criada na change anterior.

## Impact

- Migration V2: colunas `attempts`, `next_attempt_at`, `processing_started_at` e `failure_reason`, mais índices parciais do worker.
- Novos pacotes `provider` (cliente HTTP, classificação) e `request.app` (processador e reconciliação).
- Novas propriedades `activation.provider.*` e `activation.worker.*`.
- ADRs: 0006 (assíncrono + tabela como outbox) e 0007 (retentativa e idempotência).
