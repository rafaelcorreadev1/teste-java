# Design

## Context

A API está aberta, e a unicidade é global por `external_id`. O worker do provedor não passa pela camada HTTP e não é afetado. Decisão completa em [ADR 0008](../../../docs/adr/0008-seguranca-oauth2-resource-server.md). Requisitos nas specs `api-security` e `activation-requests`.

## Goals / Non-Goals

**Goals:**
- Fazer da segurança configuração declarativa (`SecurityFilterChain`) e não código espalhado nos controllers.
- Testar 401, 403 e BOLA sem servidor de autorização real.

**Non-Goals:**
- Servidor de autorização: fica fora, e o compose pode subir um Keycloak opcional.
- Rate limit na aplicação, que é responsabilidade do gateway (descrito).
- mTLS.

## Decisions

### Resource Server
- `SecurityFilterChain`:
  - stateless e sem CSRF (API sem cookie);
  - `POST /api/v1/requests` exige `hasAuthority("SCOPE_activation:write")`;
  - `GET /api/v1/requests/**` exige `hasAuthority("SCOPE_activation:read")`;
  - `/actuator/health/**` é `permitAll`;
  - o restante é `authenticated`.
- O Spring converte a claim `scope`/`scp` em `SCOPE_*` automaticamente. Se o emissor (ex.: Keycloak/Entra) usar outra claim, ajusta-se no `JwtAuthenticationConverter`.
- `JwtDecoder` via `issuer-uri`, que descobre o `jwks_uri` pelo OpenID discovery e faz cache e rotação por `kid`. Os validadores somam o padrão (`iss`, `exp`, `nbf` com skew de 30 s) e um `JwtClaimValidator` de `aud`.
- `AuthenticationEntryPoint` e `AccessDeniedHandler` escrevem `ProblemDetail` (`unauthorized` e `forbidden`), para manter o contrato de erro do ADR 0005.

### Identidade do consumidor
- `ClientIdResolver` extrai `azp`, ou `client_id` como alternativa, do `Jwt`. O controller recebe `@AuthenticationPrincipal Jwt` e passa o `clientId` ao serviço. O domínio não conhece Spring Security.
- O repositório passa a ter `findByIdAndClientId` e `findByClientIdAndExternalId`. Não existe método que busque só por `id` para uso da API, o que torna a falha BOLA difícil de reintroduzir por engano.

### Migration V3

```sql
alter table activation_request add column client_id varchar(128);
update activation_request set client_id = 'legacy' where client_id is null;
alter table activation_request alter column client_id set not null;
alter table activation_request drop constraint uk_activation_request_external_id;
alter table activation_request add constraint uk_activation_request_client_external_id unique (client_id, external_id);
```

O tratamento de duplicidade passa a reconhecer o novo nome de constraint.

### Limite de corpo
`server.tomcat.max-swallow-size` não resolve, porque limita o descarte e não o aceite. Um filtro (`RequestSizeLimitFilter`) rejeita `Content-Length` acima de 16 KB com `413`, e um corpo *chunked* é lido com limite. Os campos já têm `@Size`.

### Testes
- `@WebMvcTest` com `jwt().authorities(new SimpleGrantedAuthority("SCOPE_activation:read")).jwt(j -> j.claim("azp", "sys-a"))`.
- Para exercitar o `JwtDecoder` real (aud/exp), um teste de integração gera um par RSA local e expõe o JWKS pelo WireMock com `issuer-uri` apontando para ele.

## Risks / Trade-offs

- [Mudança incompatível na unicidade] → A unicidade fica mais permissiva (por cliente), então nenhum cliente existente passa a receber erro novo. As linhas antigas recebem `client_id = 'legacy'`.
- [A claim do client id varia por emissor] → É configurável por propriedade (`activation.security.client-id-claim`, padrão `azp`).
- [Retornar 404 em BOLA dificulta o debug do consumidor] → O log do servidor registra `clientId` e `requestId` em `debug` para o suporte.

## Migration Plan

Aplicar o V3 com a aplicação nova. Os consumidores precisam obter credenciais Client Credentials antes do deploy. É possível uma janela com `activation.security.enabled=false` atrás do gateway, mas isso não é recomendado.
