# Activation Service

Serviço de ativação de produtos de clientes: registra solicitações, permite consultar o status e realiza a ativação em um provedor externo.

**Stack:** Java 21 · Spring Boot 3.5 · Spring MVC · Spring Data JPA/Hibernate · PostgreSQL 16 · Flyway · Maven · JUnit 5 · Testcontainers · WireMock · Docker.

## Como executar

### Pré-requisitos

- JDK 21
- Docker (para o banco local e para os testes de integração com Testcontainers)

### Testes

```bash
./mvnw verify
```

Um único comando compila e roda:
- os testes unitários (`*Test`, Surefire);
- os testes de integração (`*IT`, Failsafe) contra um PostgreSQL 16 real, que o Testcontainers sobe automaticamente.

### Aplicação local

```bash
docker compose up -d --wait   # Postgres (5432) + mock do provedor (8089) + Keycloak de desenvolvimento (8180)
./mvnw spring-boot:run
curl localhost:8080/actuator/health
```

O schema é criado pelo Flyway na subida (`src/main/resources/db/migration`).

Todas as chamadas em `/api/**` exigem um token. O Keycloak do compose já sobe com o realm `activation` (`docker/keycloak/activation-realm.json`) e três clientes:

| Cliente | Segredo | Escopos |
|---|---|---|
| `sistema-a` | `sistema-a-secret` | `activation:read activation:write` |
| `sistema-b` | `sistema-b-secret` | `activation:read activation:write` |
| `sistema-leitura` | `sistema-leitura-secret` | `activation:read` |

```bash
TOKEN=$(curl -s -X POST localhost:8180/realms/activation/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=sistema-a -d client_secret=sistema-a-secret | jq -r .access_token)
```

O mock do provedor (WireMock, mappings em `docker/wiremock/`) escolhe a resposta pelo `productCode`. Isso permite ver cada caminho da integração:

| `productCode` contém | Resposta do mock | Status final esperado |
|---|---|---|
| `REJECT` (ex.: `X-REJECT`) | 400 | `FAILED` (`PROVIDER_REJECTED_400`) |
| `ERROR` (ex.: `X-ERROR`) | 500 | `PENDING` com retentativas, depois `FAILED` (`MAX_ATTEMPTS_EXCEEDED`) |
| `SLOW` (ex.: `X-SLOW`) | 200 após 8 s (acima do timeout de 5 s) | igual ao caso `ERROR` |
| qualquer outro (ex.: `INTERNET-500`) | 200 | `COMPLETED` |

```bash
id=$(curl -s -X POST localhost:8080/api/v1/requests -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"externalId":"EXT-1","customerId":"CUST-1","productCode":"INTERNET-500"}' | jq -r .id)
sleep 3 && curl -s localhost:8080/api/v1/requests/$id -H "Authorization: Bearer $TOKEN"   # status: COMPLETED
```

### Configuração

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/activation` | URL JDBC do PostgreSQL |
| `DB_USER` | `activation` | Usuário do banco |
| `DB_PASSWORD` | `activation` | Senha do banco |
| `SERVER_PORT` | `8080` | Porta HTTP |
| `PROVIDER_BASE_URL` | `http://localhost:8089` | URL base do provedor de ativação |
| `PROVIDER_API_KEY` | `local-dev-key` | Credencial do provedor (em produção, injetada a partir de um cofre de segredos) |
| `PROVIDER_CONNECT_TIMEOUT` | `2s` | Timeout de conexão com o provedor |
| `PROVIDER_READ_TIMEOUT` | `5s` | Timeout de leitura da resposta do provedor |
| `WORKER_ENABLED` | `true` | Liga o envio assíncrono e a reconciliação |
| `WORKER_POLL_INTERVAL` | `2s` | Intervalo entre buscas de solicitações pendentes |
| `WORKER_RECONCILE_INTERVAL` | `1m` | Intervalo da reconciliação de solicitações presas em `PROCESSING` |
| `OAUTH_ISSUER` | `http://localhost:8180/realms/activation` | Emissor esperado no claim `iss` |
| `OAUTH_JWK_SET_URI` | `.../realms/activation/protocol/openid-connect/certs` | Onde buscar as chaves públicas (JWKS) |
| `OAUTH_AUDIENCE` | `activation-service` | Valor exigido no claim `aud` |

## API

### `POST /api/v1/requests`

Escopo exigido: `activation:write`.

```bash
curl -i -X POST localhost:8080/api/v1/requests \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500"}'
```

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/api/v1/requests/0b7f3c1e-5d2a-4c4e-9a51-2f8d7b1e6c90
X-Correlation-Id: 6f1c...

{"id":"0b7f3c1e-...","externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500",
 "status":"PENDING","attempts":0,"failureReason":null,
 "createdAt":"2026-10-27T14:03:11.123Z","updatedAt":"2026-10-27T14:03:11.123Z"}
```

O `201` significa "registrada". A ativação no provedor acontece em segundo plano, e o cliente acompanha o resultado pelo `GET`. `attempts` conta os envios ao provedor, e `failureReason` explica um `FAILED`.

Regras de validação, iguais para `externalId`, `customerId` e `productCode`:
- obrigatório e não vazio;
- no máximo 64 caracteres;
- apenas `[A-Za-z0-9_-]`.

Datas em UTC, ISO-8601, com precisão de milissegundos.

### `GET /api/v1/requests?status=&customerId=&page=&size=`

Escopo exigido: `activation:read`. Lista **apenas as solicitações do cliente do token**, da mais recente para a mais antiga. Os filtros `status` e `customerId` são opcionais e podem ser combinados. `page` começa em 0, e `size` vale 20 por padrão, com máximo de 100.

```json
{ "content": [ { "id": "...", "status": "FAILED", "...": "..." } ],
  "page": 0, "size": 20, "totalElements": 3, "totalPages": 1 }
```

### `GET /api/v1/requests/{id}`

Escopo exigido: `activation:read`. Retorna a solicitação e o status atual. Responde `404` se ela não existir **ou pertencer a outro cliente**, e `400` se o id não for um UUID.

### Erros

Todas as respostas de erro usam [Problem Details (RFC 9457)](https://www.rfc-editor.org/rfc/rfc9457) com `traceId`, que é o mesmo valor do header `X-Correlation-Id` e dos logs:

```json
{
  "type": "https://api.deroyque.com/problems/validation-error",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "Um ou mais campos não passaram na validação.",
  "instance": "/api/v1/requests",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "errors": [{ "field": "customerId", "message": "must not be blank" }]
}
```

| Situação | Status | `type` |
|---|---|---|
| Campo inválido / id malformado | 400 | `validation-error` |
| JSON malformado | 400 | `malformed-request` |
| Token ausente, expirado ou inválido | 401 (com `WWW-Authenticate: Bearer`) | `unauthorized` |
| Token sem o escopo necessário | 403 | `forbidden` |
| Solicitação inexistente ou de outro cliente | 404 | `not-found` |
| Corpo acima de 16 KB | 413 | `payload-too-large` |
| `externalId` repetido com conteúdo diferente | 409 | `duplicate-request` |
| Atualização concorrente detectada | 409 | `concurrent-update` |
| Erro inesperado (detalhes só no log) | 500 | `internal-error` |

## Decisões técnicas

As decisões de arquitetura estão registradas em [`docs/adr/`](docs/adr/README.md). As especificações de comportamento (requisitos e cenários) estão em [`openspec/`](openspec/).

### Duplicidade (item 3) — [ADR 0003](docs/adr/0003-unicidade-externalid-constraint-banco.md)

Verificar se o `externalId` existe e depois inserir **não basta**. Em READ COMMITTED, duas transações simultâneas consultam, nenhuma enxerga a linha ainda não commitada da outra, e as duas inserem. `@Transactional` não fecha essa janela, e `synchronized` não vale entre instâncias.

A garantia vem de uma **constraint `UNIQUE (external_id)` no PostgreSQL**:

1. O insert roda numa transação própria, com `saveAndFlush` para forçar o `INSERT` dentro dela.
2. Se o banco recusar por violação **dessa** constraint (identificada pelo nome), o serviço, fora da transação que falhou, lê o registro vencedor e decide a resposta:

| Cenário | Resposta | Justificativa |
|---|---|---|
| `externalId` novo | `201 Created` + `Location` | Criação. |
| Mesmo `externalId`, mesmo conteúdo | `200 OK` com o registro existente | É um retry legítimo, e a operação é idempotente. Um `409` faria a origem tratar como erro algo que já deu certo. |
| Mesmo `externalId`, conteúdo diferente | `409 Conflict` | É conflito real, que a origem precisa corrigir. O registro original não é alterado. |
| Duas requisições idênticas em paralelo | Uma `201`, a outra `200` | O banco bloqueia o segundo insert até o primeiro commitar, e então ele cai no caso de retry. |

Coberto por `ConcurrentCreationIT`: 10 threads simultâneas contra Postgres real (Testcontainers) resultam em exatamente 1 registro, 1 criação e 9 respostas "existente".

### Segurança (item 7) — [ADR 0008](docs/adr/0008-seguranca-oauth2-resource-server.md)

Implementada com Spring Security como **OAuth 2.0 Resource Server**. O serviço não emite tokens; ele só os valida.

**Fluxo: Client Credentials.** Quem consome a API são outros sistemas, sem usuário nem navegador. Cada sistema tem um `client_id` e um segredo (ou chave privada, com `private_key_jwt`) no servidor de autorização corporativo (Keycloak, Entra ID, Cognito...), troca essas credenciais por um access token de vida curta (5 min no realm local) e o envia como `Authorization: Bearer`.

**O que é validado em cada token:**

| Verificação | Como |
|---|---|
| Assinatura | Chaves públicas buscadas no JWKS do emissor (`OAUTH_JWK_SET_URI`), mantidas em cache e renovadas quando aparece um `kid` novo. Isso permite rotacionar as chaves do emissor sem redeploy. |
| Emissor (`iss`) | Igual a `OAUTH_ISSUER`. |
| Audiência (`aud`) | Contém `activation-service`. Impede reaproveitar aqui um token emitido para outro serviço. |
| Validade (`exp`, `nbf`) | Com tolerância de 30 s para diferença de relógio entre máquinas. |

**Autorização por escopo:** `activation:write` para criar e `activation:read` para consultar. Sem token, a resposta é `401`; com token sem o escopo, `403`. Os probes `/actuator/health/liveness` e `/readiness` são públicos (o orquestrador não tem token) e não expõem detalhes de componentes.

**Isolamento entre consumidores (OWASP API1:2023, BOLA).** Cada solicitação grava o cliente que a criou (claim `azp` do token). As consultas sempre filtram por ele, e a solicitação de outro cliente responde **`404`, não `403`**, para não confirmar que o id existe. Pelo mesmo motivo, a unicidade de `externalId` passou a ser **por cliente** (`UNIQUE (client_id, external_id)`). Com unicidade global, o sistema B enviando um `externalId` do sistema A receberia os dados de A no `200` de retry, ou um `409` revelando que aquele id existe.

**Credencial do provedor externo.** `PROVIDER_API_KEY` vem por variável de ambiente, injetada em produção a partir de um cofre (AWS Secrets Manager, Azure Key Vault, HashiCorp Vault), e nunca é versionada nem logada. A rotação usa duas credenciais válidas em paralelo durante a janela de troca: o cofre publica a nova, as instâncias passam a usá-la (restart gradual ou recarga via Spring Cloud Vault/AWS) e só então a antiga é revogada no provedor.

**Limites.** O corpo da requisição tem no máximo 16 KB (`413` acima disso, inclusive para corpos *chunked*), e cada campo tem tamanho máximo. **O rate limit por cliente fica no API Gateway**, não na aplicação: com N réplicas, um limite em memória por instância não garante nada, e o gateway já conhece o `client_id`.

**Testes sem servidor de autorização:**
- os testes de controller usam o `jwt()` do `spring-security-test`;
- o `SecurityIT` assina tokens com uma chave RSA gerada no teste e publica o JWKS no WireMock, exercitando o decoder real com token expirado, dentro da tolerância de relógio, audiência errada, emissor errado, chave desconhecida, escopo insuficiente e acesso entre clientes.

### Listagem e índices — [ADR 0009](docs/adr/0009-listagem-paginada-e-indices.md)

Cada índice existe por causa de uma consulta concreta (migrations V1–V4):

| Índice | Consulta atendida | Por quê |
|---|---|---|
| `UNIQUE (client_id, external_id)` | Regra de duplicidade e busca do registro vencedor | É a garantia de unicidade sob concorrência, além de índice de busca. |
| `(client_id, status, created_at desc)` | Listagem com filtro de `status` | `client_id` vem primeiro porque toda listagem filtra pelo consumidor; `created_at desc` no fim entrega a ordenação pronta, sem *sort*. |
| `(client_id, customer_id, created_at desc)` | Listagem com filtro de `customerId` | Mesmo raciocínio. |
| `(next_attempt_at) WHERE status = 'PENDING'` | Busca do worker | Índice **parcial**: contém só pendentes, então fica pequeno mesmo com milhões de solicitações concluídas. |
| `(processing_started_at) WHERE status = 'PROCESSING'` | Reconciliação | Parcial pelo mesmo motivo. |

A listagem sem filtro usa o prefixo `client_id` desses índices. Se ela virar a consulta mais frequente, o próximo índice seria `(client_id, created_at desc)`, decisão a tomar com dados reais de `pg_stat_statements`. O `ActivationRequestListingIT` confirma via `EXPLAIN` que os filtros usam os índices compostos.

Os filtros são montados com `Specification`, só com os predicados informados. O padrão `(:status is null or status = :status)` numa query única costuma gerar no Postgres um plano genérico que ignora os índices. A paginação é por *offset*. Para tabelas muito grandes, a evolução é paginação por cursor (*keyset*), porque o offset degrada em páginas profundas.

### Status da solicitação — [ADR 0004](docs/adr/0004-maquina-de-estados-no-dominio.md)

`PENDING → PROCESSING → COMPLETED | FAILED`, com `PROCESSING → PENDING` para falha temporária. As transições são métodos da entidade, que não tem setters, e estados terminais não mudam. `@Version` impede que duas escritas concorrentes se sobrescrevam.

### Integração com o provedor (item 5) — [ADR 0006](docs/adr/0006-processamento-assincrono-tabela-como-outbox.md) e [ADR 0007](docs/adr/0007-politica-de-retentativa-e-idempotencia-provedor.md)

| Retorno do provedor | Novo status | Nova tentativa? | Justificativa |
|---|---|---|---|
| Sucesso (2xx) | `COMPLETED` | Não | Produto ativado. |
| HTTP 400 / 422 | `FAILED` (`PROVIDER_REJECTED_4xx`) | Não | Dados inválidos para o provedor; repetir dá o mesmo resultado. |
| Outros 4xx (401, 403, 404, 409) | `FAILED` (`PROVIDER_REJECTED_4xx`) | Não | Erro de credencial ou contrato, que exige intervenção. Não adianta insistir. |
| HTTP 408 | `PENDING` | Sim, com backoff | Timeout do lado do provedor; é temporário. |
| HTTP 429 / 503 com `Retry-After` | `PENDING` | Sim, não antes do `Retry-After` (limitado a 5 min) | O provedor pediu explicitamente para aguardar. |
| HTTP 500 / 502 / 503 / 504 | `PENDING` | Sim, com backoff | Falha temporária do provedor. |
| Timeout / conexão recusada | `PENDING` | Sim, com backoff e a **mesma** `Idempotency-Key` | Resultado ambíguo, porque o provedor pode ter ativado. Repetir só é seguro por causa da chave de idempotência. |
| 5ª tentativa falha | `FAILED` (`MAX_ATTEMPTS_EXCEEDED`) | Não | Fica visível para análise e reprocessamento manual. |

Backoff: `min(5 min, 2 s × 2^(n−1))` multiplicado por um fator aleatório entre 0,5 e 1,0 (*jitter*). O jitter evita que solicitações que falharam juntas voltem todas ao mesmo tempo e derrubem o provedor de novo.

**Síncrono ou assíncrono?** Assíncrono. O `POST` grava a solicitação como `PENDING` e responde na hora. Se esperasse o provedor, a latência da API passaria a ser a dele (o cenário do item 10 mostra 400 ms → 3 s), e cada requisição seguraria uma thread do Tomcat durante toda a espera.

**Transação × chamada remota.** Nenhuma chamada HTTP acontece dentro de uma transação de banco. O worker trabalha em três passos:
1. reivindica um lote numa transação curta (`SELECT ... FOR UPDATE SKIP LOCKED` → `PROCESSING`);
2. chama o provedor **sem transação aberta**;
3. grava o resultado em outra transação curta.

**Como garanto que a solicitação gravada será enviada, mesmo se a aplicação cair?** A linha `PENDING`, gravada no mesmo commit da criação, **é** a mensagem a enviar: a própria tabela funciona como *transactional outbox*. Não existe janela entre "gravar" e "publicar". Se a instância cair:
- antes de reivindicar, a linha continua `PENDING` e outra instância a pega;
- durante o envio, a linha fica em `PROCESSING`, e a **reconciliação** a devolve para `PENDING` depois de 5 min (*lease*). O reenvio usa a mesma `Idempotency-Key`.

**Várias instâncias.** O `SKIP LOCKED` faz cada instância pular as linhas já travadas por outra, então ninguém envia a mesma solicitação em paralelo. O `ActivationStateStoreIT` prova isso, e o teste falha se a cláusula for removida.

**Resiliência.** Não usei Resilience4j. O retry é persistido (`next_attempt_at`), sobrevive a reinícios e pode ser observado por SQL, enquanto um retry em memória seguraria a thread e perderia o estado numa queda. O lote limitado (10 por ciclo) com backoff já reduz a pressão sobre um provedor fora do ar. Um circuit breaker seria o próximo passo para parar de tentar por completo enquanto o provedor estiver indisponível.

**Premissa.** O provedor precisa respeitar o `Idempotency-Key`. Sem isso, nenhuma estratégia evita 100% de ativação duplicada após um timeout, porque o resultado da primeira chamada é desconhecido.

## Pontos pendentes

- **Log de duplicidade em nível ERROR**: no retry legítimo, o `SqlExceptionHelper` do Hibernate registra `duplicate key` como ERROR antes de o serviço tratar a violação. Em produção, isso polui o log e pode disparar alertas. As opções são rebaixar esse logger específico ou trocar o insert por `INSERT ... ON CONFLICT DO NOTHING RETURNING` (ADR 0003).
- **Reprocessamento manual de `FAILED`**: não há endpoint administrativo para reenfileirar uma solicitação `FAILED` (por exemplo, depois de corrigir a credencial que causou um 401). Hoje, isso exigiria um `UPDATE` direto no banco.
- **Circuit breaker no cliente do provedor**: descrito na seção de integração, mas não implementado.
- **Rate limit por cliente**: delegado ao API Gateway (seção Segurança), não implementado na aplicação.
- **Autenticação do serviço perante o provedor**: hoje é uma API key estática (`X-Api-Key`). Se o provedor suportar, o ideal é o mesmo modelo Client Credentials, ou mTLS.
