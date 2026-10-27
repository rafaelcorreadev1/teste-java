# Spec Delta

## MODIFIED Requirements

### Requirement: Chave de idempotência e correlação no envio
Toda chamada ao provedor MUST enviar o header `Idempotency-Key` com o `id` da solicitação, igual em todas as tentativas, e o header `X-Correlation-Id` com o mesmo correlation id da requisição HTTP que criou a solicitação.

#### Scenario: Retentativa após timeout reutiliza a chave
- **WHEN** a primeira chamada de uma solicitação expira por timeout e uma nova tentativa é feita
- **THEN** as duas chamadas recebidas pelo provedor têm o mesmo `Idempotency-Key`

#### Scenario: Correlation id da criação chega ao provedor
- **WHEN** a solicitação foi criada com `X-Correlation-Id: abc-123`
- **THEN** a chamada ao provedor para essa solicitação traz `X-Correlation-Id: abc-123`
