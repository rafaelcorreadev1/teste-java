# Spec Delta

## ADDED Requirements

### Requirement: Listar solicitações com filtros e paginação
O sistema SHALL listar, em `GET /api/v1/requests`, apenas as solicitações do cliente consumidor autenticado, com filtros opcionais e combináveis por `status` e `customerId`, ordenadas de `createdAt` mais recente para a mais antiga. A resposta MUST conter `content`, `page`, `size`, `totalElements` e `totalPages`.

#### Scenario: Filtro por status
- **WHEN** o cliente tem 3 solicitações `FAILED` e 5 `COMPLETED`
- **AND** chama `GET /api/v1/requests?status=FAILED`
- **THEN** o sistema responde `200` com as 3 solicitações `FAILED` e `totalElements` 3

#### Scenario: Filtros combinados
- **WHEN** o cliente chama `GET /api/v1/requests?status=PENDING&customerId=CUST-1`
- **THEN** só aparecem solicitações `PENDING` do `CUST-1`

#### Scenario: Paginação
- **WHEN** o cliente tem 25 solicitações e chama `GET /api/v1/requests?page=1&size=10`
- **THEN** o sistema responde com 10 itens, `page` 1, `totalElements` 25 e `totalPages` 3

#### Scenario: Não lista solicitações de outro cliente
- **WHEN** o sistema A tem 2 solicitações e o sistema B tem 4
- **AND** A chama `GET /api/v1/requests`
- **THEN** a resposta contém apenas as 2 solicitações de A

### Requirement: Validar parâmetros de listagem
O sistema MUST rejeitar com `400` um `status` fora dos valores conhecidos, `page` negativo ou `size` fora do intervalo 1 a 100. Sem `size`, o padrão é 20.

#### Scenario: Tamanho de página excessivo
- **WHEN** o cliente chama `GET /api/v1/requests?size=1000`
- **THEN** o sistema responde `400` com erro de validação no parâmetro `size`

#### Scenario: Status desconhecido
- **WHEN** o cliente chama `GET /api/v1/requests?status=DONE`
- **THEN** o sistema responde `400`
