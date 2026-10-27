# 0008. Proteger a API com OAuth 2.0 (Client Credentials) + JWT, escopos e isolamento por cliente

- Status: Aceito
- Data: 2026-10-27
- Change: `add-api-security`

## Contexto
Os consumidores são **outros sistemas**, não pessoas. O enunciado pede segurança implementada ou descrita, e valoriza escopos distintos, testes de 401/403 e isolamento entre consumidores (OWASP API1:2023 — BOLA).

## Decisão
- **Fluxo Client Credentials**: é o fluxo feito para máquina a máquina, sem usuário nem navegador. Cada sistema consumidor tem seu `client_id` no servidor de autorização (Keycloak, Entra ID, Cognito...).
- O serviço é um **Resource Server** (`spring-boot-starter-oauth2-resource-server`) e valida no JWT:
  - **assinatura**, com as chaves públicas do `jwks_uri` do emissor (cacheadas e rotacionadas por `kid`);
  - **`iss`** igual ao emissor configurado;
  - **`aud`** contendo `activation-service`;
  - **`exp` / `nbf`**, com tolerância de 30 s de diferença de relógio.
  - O `JwtDecoder` é montado explicitamente (`NimbusJwtDecoder.withJwkSetUri` + validadores de timestamp, issuer e audience) em vez de usar a descoberta via `issuer-uri`. Assim, a subida da aplicação não depende do emissor estar no ar, e a regra de audiência fica visível no código.
- **Escopos**:
  - `activation:write` para `POST /api/v1/requests`;
  - `activation:read` para os `GET`.
  - Sem token a resposta é `401`; com escopo insuficiente, `403`.
- **Isolamento (BOLA)**: a solicitação grava o cliente do token: o claim `azp` (padrão OIDC para Client Credentials), com `client_id` e `sub` como alternativas para emissores que não o enviam. Consultas e listagens filtram pelo `client_id` do chamador. Pedir uma solicitação de outro cliente devolve **`404`**, e não `403`, para não revelar que o id existe.
- A unicidade passa a ser **por cliente**: `UNIQUE (client_id, external_id)`. Dois sistemas de origem podem ter numerações que colidem.
- **Credencial do provedor externo**: vem por variável de ambiente injetada de um cofre (AWS Secrets Manager, Azure Key Vault, Vault). Nunca vai para o repositório nem para o log. A rotação mantém duas credenciais válidas durante a janela de troca, e a aplicação relê o valor sem redeploy.
- **Limites**: o corpo da requisição tem tamanho máximo (16 KB) e os campos também. O rate limit por `client_id` fica no API Gateway (descrito, não implementado).

## Alternativas consideradas
- **API key estática**: sem expiração nem escopos, e com rotação difícil.
- **Só mTLS**: autentica o sistema, mas não carrega escopos de forma prática. Pode complementar dentro da malha.
- **Responder `403` em BOLA**: confirma que o recurso existe e permite enumeração.

## Consequências
- Os testes de controller usam `SecurityMockMvcRequestPostProcessors.jwt()`. Um teste de integração assina tokens reais com chave RSA local e publica o JWKS no WireMock, exercitando o decoder de produção. Nenhum dos dois precisa de servidor de autenticação.
- Para chamar a API localmente, o compose sobe um Keycloak de desenvolvimento com o realm `activation` (clientes `sistema-a`, `sistema-b` e `sistema-leitura`, audience `activation-service`).
- Todos os testes e exemplos de API passam a precisar de token, o que é o preço de a segurança estar implementada e não só descrita.
