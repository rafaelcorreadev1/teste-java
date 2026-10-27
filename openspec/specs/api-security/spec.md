# api-security Specification

## Purpose
Garante que só sistemas consumidores autenticados e autorizados usem a API, e que cada consumidor enxergue apenas as solicitações que ele próprio criou.

## Requirements

### Requirement: Exigir token de acesso válido
Todo endpoint de `/api/**` MUST exigir um token JWT Bearer válido. O token é válido quando a assinatura confere com as chaves publicadas pelo emissor configurado, `iss` é esse emissor, `aud` contém `activation-service` e `exp` não passou (tolerância de 30 s). Caso contrário, o sistema responde `401` em Problem Details.

#### Scenario: Requisição sem token
- **WHEN** o cliente chama `GET /api/v1/requests/{id}` sem o header `Authorization`
- **THEN** o sistema responde `401` com `Content-Type` `application/problem+json`

#### Scenario: Token expirado
- **WHEN** o cliente envia um token com `exp` no passado
- **THEN** o sistema responde `401`

#### Scenario: Audiência incorreta
- **WHEN** o cliente envia um token válido cuja `aud` não contém `activation-service`
- **THEN** o sistema responde `401`

### Requirement: Autorizar por escopo
Criar solicitações MUST exigir o escopo `activation:write`, e consultar MUST exigir o escopo `activation:read`. Um token válido sem o escopo necessário resulta em `403` em Problem Details.

#### Scenario: Escopo de leitura tentando criar
- **WHEN** um cliente com apenas `activation:read` chama `POST /api/v1/requests`
- **THEN** o sistema responde `403` e nenhuma solicitação é criada

#### Scenario: Escopo de escrita tentando consultar
- **WHEN** um cliente com apenas `activation:write` chama `GET /api/v1/requests/{id}`
- **THEN** o sistema responde `403`

### Requirement: Isolar solicitações por cliente consumidor
Cada solicitação SHALL pertencer ao cliente consumidor identificado no token que a criou. Consultas MUST retornar apenas solicitações do próprio cliente, e a consulta de uma solicitação de outro cliente MUST responder `404`, indistinguível de inexistente.

#### Scenario: Cliente consulta solicitação de outro cliente
- **WHEN** o sistema A cria uma solicitação
- **AND** o sistema B consulta o `id` dela com um token válido e escopo `activation:read`
- **THEN** o sistema responde `404`

#### Scenario: Mesmo externalId em clientes diferentes
- **WHEN** os sistemas A e B criam, cada um, uma solicitação com `externalId` `EXT-1`
- **THEN** as duas solicitações são criadas com `201` e ids distintos

### Requirement: Limitar o tamanho do corpo da requisição
O sistema MUST rejeitar corpos de requisição maiores que 16 KB com `413`, sem processá-los.

#### Scenario: Corpo grande demais
- **WHEN** o cliente envia `POST /api/v1/requests` com corpo de 20 KB
- **THEN** o sistema responde `413`

### Requirement: Manter verificações de saúde acessíveis
Os endpoints de liveness e readiness MUST responder sem autenticação, para uso pela orquestração, sem expor detalhes de componentes a chamadas anônimas.

#### Scenario: Probe sem token
- **WHEN** a orquestração chama `/actuator/health/liveness` sem token
- **THEN** o sistema responde `200` com o status agregado e sem detalhes
