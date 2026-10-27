# Proposal

## Why

Os sistemas de origem precisam registrar solicitações de ativação de produto e consultar o status delas. Essa é a base obrigatória do desafio (itens 2, 3, 4 e 6): sem ela não há o que integrar, proteger ou listar. O ponto mais sensível é que esses sistemas fazem retry, então a API precisa ser segura contra duplicidade, inclusive sob concorrência.

## What Changes

- `POST /api/v1/requests` cria uma solicitação `PENDING` a partir de `externalId`, `customerId` e `productCode`, e responde `201 Created` com `Location`.
- `GET /api/v1/requests/{id}` retorna a solicitação e o status atual, ou `404`.
- Validação de entrada: obrigatoriedade, tamanho máximo e formato.
- Unicidade de `externalId` garantida pelo banco, com semântica de retry: mesmo conteúdo → `200` com o registro existente; conteúdo divergente → `409`.
- Ciclo de vida `PENDING → PROCESSING → COMPLETED | FAILED` controlado pelo domínio, com estados terminais imutáveis e proteção contra escrita concorrente.
- Formato padronizado de erro (Problem Details, RFC 9457) com `traceId`, sem vazar detalhes internos.

## Capabilities

### New Capabilities

- `activation-requests`: registro, consulta, unicidade por `externalId` e ciclo de vida de status das solicitações de ativação.
- `api-errors`: contrato de erro da API HTTP (formato, códigos e não exposição de detalhes internos).

### Modified Capabilities

Nenhuma.

## Impact

- Novo endpoint REST `/api/v1/requests` e tabela `activation_request` (migration V1).
- Pacotes `request` e `shared.error`.
- ADRs: 0003 (unicidade), 0004 (máquina de estados), 0005 (Problem Details).
