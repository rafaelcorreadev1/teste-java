# Proposal

## Why

A API é consumida por outros sistemas e hoje aceita qualquer chamada. O item 7 do desafio exige ao menos descrever a proteção com Spring Security, JWT e OAuth 2.0. Implementá-la é um diferencial: escopos separados para leitura e escrita, testes de 401/403 e isolamento entre consumidores (OWASP API1 — BOLA). Sem isolamento, um sistema poderia consultar as solicitações de outro só por conhecer o `id`, ou "descobrir" um `externalId` alheio pela regra de duplicidade.

## What Changes

- A API passa a exigir um JWT Bearer emitido pelo servidor de autorização corporativo (fluxo Client Credentials). O serviço valida assinatura (JWKS), `iss`, `aud` e `exp`.
- Escopos: `activation:write` para criar, `activation:read` para consultar. Sem token → `401`; escopo insuficiente → `403`, ambos em Problem Details.
- Cada solicitação passa a pertencer ao cliente consumidor (`client_id` do token). A consulta de solicitação de outro cliente devolve `404`.
- **BREAKING**: a unicidade de `externalId` passa a ser **por cliente consumidor**, e não mais global.
- Corpo da requisição limitado a 16 KB (`413` acima disso).
- Actuator `health` continua público, para as probes. Os demais endpoints de gestão exigem autenticação.
- README: fluxo escolhido, validação do JWT, origem das chaves, guarda e rotação da credencial do provedor, rate limit no gateway.

## Capabilities

### New Capabilities

- `api-security`: autenticação de sistemas consumidores, autorização por escopo, isolamento de dados entre consumidores e limites de requisição.

### Modified Capabilities

- `activation-requests`: a regra de unicidade de `externalId` passa a valer por cliente consumidor.

## Impact

- Dependência `spring-boot-starter-oauth2-resource-server` e `spring-security-test`.
- Migration V3: coluna `client_id`, troca da constraint única para `(client_id, external_id)`.
- Todos os testes de API passam a enviar JWT (`jwt()` do spring-security-test).
- Propriedades `spring.security.oauth2.resourceserver.jwt.issuer-uri` e `activation.security.audience`.
- ADR 0008.
