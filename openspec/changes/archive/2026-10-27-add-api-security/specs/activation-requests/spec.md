# Spec Delta

## MODIFIED Requirements

### Requirement: Responder de forma idempotente a reenvio do mesmo externalId
O sistema MUST manter no máximo uma solicitação por `externalId` **para cada cliente consumidor**. Quando um `externalId` já existente do mesmo cliente chega com o mesmo `customerId` e `productCode`, o sistema SHALL responder `200 OK` com a solicitação existente, sem criar um novo registro. Solicitações de outros clientes nunca são consideradas na comparação.

#### Scenario: Retry legítimo
- **WHEN** existe a solicitação `EXT-12345` para `CUST-10001`/`INTERNET-500`
- **AND** o cliente reenvia exatamente os mesmos dados
- **THEN** o sistema responde `200 OK` com o mesmo `id` da solicitação original
- **AND** continua existindo um único registro com `EXT-12345` para esse cliente

#### Scenario: Outro cliente usa o mesmo externalId
- **WHEN** existe a solicitação `EXT-12345` do cliente A
- **AND** o cliente B envia `EXT-12345` com qualquer conteúdo
- **THEN** o sistema cria uma nova solicitação para B e responde `201`
