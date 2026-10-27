# Tasks

## 1. Autenticação e autorização

- [ ] 1.1 Adicionar `oauth2-resource-server` e `spring-security-test`, configurar `issuer-uri` e o validador de `aud`, e verificar que o contexto sobe
- [ ] 1.2 Criar `SecurityConfig` (stateless, escopos por rota, health público) com entry point e access denied em Problem Details, e verificar com `@WebMvcTest`: sem token → 401, escopo errado → 403, escopo certo → 2xx
- [ ] 1.3 Verificar com teste de integração (JWKS via WireMock e chave RSA local) que um token expirado e um com `aud` incorreta resultam em 401

## 2. Isolamento por cliente

- [ ] 2.1 Criar `V3__add_client_ownership.sql` e o campo `clientId` na entidade, e verificar o contexto com `ddl-auto=validate`
- [ ] 2.2 Propagar o `clientId` do token para criação e consulta (`findByIdAndClientId`, `findByClientIdAndExternalId`) e ajustar o tratamento de duplicidade à nova constraint, verificando com os testes de duplicidade e concorrência atualizados
- [ ] 2.3 Verificar com teste de integração: B não vê a solicitação de A (404), e A e B criam o mesmo `externalId` (201 + 201)

## 3. Limites

- [ ] 3.1 Criar `RequestSizeLimitFilter` (16 KB → 413) e verificar com teste de controller

## 4. Documentação e estudo

- [ ] 4.1 Escrever no README a seção "Segurança" (fluxo, validação do JWT, escopos, BOLA, credencial do provedor e rotação, rate limit no gateway, como obter token localmente) e verificar seguindo os passos
- [ ] 4.2 Marcar ADR 0008 como Aceito e verificar o índice
- [ ] 4.3 Atualizar a seção `add-api-security` em `estudo/estudo.html`, regerar o PDF e verificar a seção no PDF
