# Spec Delta

## Purpose

Permite que sistemas de origem registrem solicitações de ativação de produtos de clientes e acompanhem o status de cada uma, sem gerar registros duplicados quando reenviam a mesma solicitação.

## ADDED Requirements

### Requirement: Criar solicitação de ativação
O sistema SHALL criar uma solicitação a partir de `externalId`, `customerId` e `productCode`, com status inicial `PENDING`, e responder `201 Created` com o header `Location` apontando para o recurso criado. A resposta MUST conter `id`, `externalId`, `customerId`, `productCode`, `status`, `createdAt` e `updatedAt`, com as datas em UTC no formato ISO-8601.

#### Scenario: Criação com dados válidos
- **WHEN** o cliente envia `POST /api/v1/requests` com `{"externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500"}`
- **THEN** o sistema responde `201 Created`
- **AND** o header `Location` é `/api/v1/requests/{id}`
- **AND** o corpo traz `status` igual a `PENDING`, um `id` UUID e `createdAt` igual a `updatedAt`, terminando em `Z`

### Requirement: Validar dados de entrada
O sistema MUST rejeitar com `400` uma solicitação em que `externalId`, `customerId` ou `productCode` esteja ausente, em branco, com mais de 64 caracteres ou fora do padrão `[A-Za-z0-9_-]+`, informando cada campo inválido. Nenhum registro é criado.

#### Scenario: Campo obrigatório ausente
- **WHEN** o cliente envia a solicitação sem `customerId`
- **THEN** o sistema responde `400` com um erro de validação cuja lista `errors` contém o campo `customerId`
- **AND** nenhum registro é criado

#### Scenario: Campo acima do tamanho máximo
- **WHEN** o cliente envia `productCode` com 65 caracteres
- **THEN** o sistema responde `400` indicando o campo `productCode`

#### Scenario: Campo com caracteres inválidos
- **WHEN** o cliente envia `externalId` igual a `EXT 123;DROP`
- **THEN** o sistema responde `400` indicando o campo `externalId`

### Requirement: Responder de forma idempotente a reenvio do mesmo externalId
O sistema MUST manter no máximo uma solicitação por `externalId`. Quando um `externalId` já existente chega com o mesmo `customerId` e `productCode`, o sistema SHALL responder `200 OK` com a solicitação existente, sem criar um novo registro.

#### Scenario: Retry legítimo
- **WHEN** existe a solicitação `EXT-12345` para `CUST-10001`/`INTERNET-500`
- **AND** o cliente reenvia exatamente os mesmos dados
- **THEN** o sistema responde `200 OK` com o mesmo `id` da solicitação original
- **AND** continua existindo um único registro com `EXT-12345`

### Requirement: Rejeitar externalId reutilizado com conteúdo divergente
Quando um `externalId` já existente chega com `customerId` ou `productCode` diferentes dos registrados, o sistema MUST responder `409 Conflict` e não alterar a solicitação existente.

#### Scenario: Mesmo externalId com produto diferente
- **WHEN** existe a solicitação `EXT-12345` com `productCode` `INTERNET-500`
- **AND** o cliente envia `EXT-12345` com `productCode` `INTERNET-1000`
- **THEN** o sistema responde `409 Conflict` com um erro do tipo `duplicate-request`
- **AND** a solicitação existente continua com `INTERNET-500`

### Requirement: Garantir unicidade sob requisições concorrentes
A unicidade de `externalId` MUST valer mesmo quando requisições idênticas chegam simultaneamente, inclusive em instâncias diferentes do serviço. Exatamente uma recebe `201` e as demais recebem `200` com o mesmo registro.

#### Scenario: Requisições idênticas em paralelo
- **WHEN** 10 requisições idênticas com `externalId` `EXT-777` são enviadas ao mesmo tempo
- **THEN** existe exatamente um registro com `EXT-777`
- **AND** exatamente uma resposta é `201` e as outras nove são `200` com o mesmo `id`

### Requirement: Consultar solicitação por id
O sistema SHALL retornar a solicitação e o status atual em `GET /api/v1/requests/{id}`. Um id inexistente MUST resultar em `404`, e um id que não é UUID válido MUST resultar em `400`.

#### Scenario: Solicitação existente
- **WHEN** o cliente consulta o `id` de uma solicitação existente
- **THEN** o sistema responde `200` com os dados e o `status` atual

#### Scenario: Solicitação inexistente
- **WHEN** o cliente consulta um UUID que não corresponde a nenhuma solicitação
- **THEN** o sistema responde `404` com um erro do tipo `not-found`

#### Scenario: Id malformado
- **WHEN** o cliente consulta `/api/v1/requests/abc`
- **THEN** o sistema responde `400`

### Requirement: Restringir transições de status
O status de uma solicitação SHALL seguir apenas as transições `PENDING→PROCESSING`, `PROCESSING→COMPLETED`, `PROCESSING→FAILED` e `PROCESSING→PENDING` (falha temporária com nova tentativa agendada). `COMPLETED` e `FAILED` são terminais, e toda transição fora dessa lista MUST ser rejeitada. Cada transição válida atualiza `updatedAt`.

#### Scenario: Transição válida
- **WHEN** uma solicitação `PROCESSING` é concluída
- **THEN** o status passa a `COMPLETED` e `updatedAt` é atualizado

#### Scenario: Estado terminal não muda
- **WHEN** há uma tentativa de mover uma solicitação `COMPLETED` para `PROCESSING`
- **THEN** a transição é rejeitada e a solicitação continua `COMPLETED`

#### Scenario: Pular etapa não é permitido
- **WHEN** há uma tentativa de mover uma solicitação `PENDING` diretamente para `COMPLETED`
- **THEN** a transição é rejeitada

### Requirement: Proteger atualização de status contra escrita concorrente
Duas atualizações concorrentes sobre a mesma solicitação MUST NOT sobrescrever uma à outra silenciosamente: a que partiu de uma versão desatualizada é rejeitada e o estado gravado continua consistente.

#### Scenario: Duas atualizações sobre a mesma versão
- **WHEN** dois processos leem a mesma versão de uma solicitação `PROCESSING`
- **AND** um grava `COMPLETED` e, depois, o outro tenta gravar `PENDING`
- **THEN** a segunda gravação é rejeitada por conflito de versão
- **AND** a solicitação permanece `COMPLETED`
