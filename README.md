# Activation Service

Serviço de ativação de produtos de clientes: registra solicitações, permite consultar o status e realiza a ativação em um provedor externo.

**Stack:** Java 21 · Spring Boot 3.5 · Spring MVC · Spring Data JPA/Hibernate · Spring Security (OAuth 2.0 Resource Server) · PostgreSQL 16 · Flyway · Maven · JUnit 5 · Mockito · Testcontainers · WireMock · ArchUnit · Micrometer/Prometheus · Docker.

## Sumário

- [Como executar](#como-executar)
- [API](#api)
- [Decisões técnicas](#decisões-técnicas): duplicidade, segurança, listagem, status, integração
- [Estratégia de testes](#estratégia-de-testes)
- [Cenário de análise (item 10)](#cenário-de-análise-item-10)
- [Produção](#produção)
- [Microsserviços e cloud](#microsserviços-e-cloud)
- [Pontos pendentes](#pontos-pendentes)
- [Melhorias futuras](#melhorias-futuras)
- [Uso de IA](#uso-de-ia)

Decisões de arquitetura: [`docs/adr/`](docs/adr/README.md). Especificações (requisitos e cenários): [`openspec/specs/`](openspec/specs/).

## Como executar

### 1. Pré-requisitos

| Para... | Você precisa de |
|---|---|
| Subir tudo e usar a API | **Docker** (Docker Desktop no Windows/macOS) com o Docker Compose v2, e `git` |
| Rodar os testes ou a aplicação fora do contêiner | também o **JDK 21** (ex.: [Eclipse Temurin](https://adoptium.net/)). **Não** é preciso instalar o Maven: o projeto traz o wrapper (`mvnw`) |
| Rodar o teste de fumaça (opcional) | **Node.js 18+** |
| Seguir os exemplos em bash do passo 4 | [`jq`](https://jqlang.org/), para extrair o token do JSON. **Não** vem no Git Bash do Windows; o passo 4 mostra uma alternativa sem ele |

As portas **8080** (API), **5432** (Postgres), **8089** (mock do provedor) e **8180** (Keycloak) precisam estar livres.

### 2. Clonar e configurar

```bash
git clone https://github.com/rafaelcorreadev1/teste-java.git
cd teste-java
cp .env.example .env        # Windows (PowerShell): Copy-Item .env.example .env
```

O `.env` é **opcional**: sem ele, tudo usa os mesmos valores padrão de desenvolvimento. Ele existe para você mudar portas, credenciais ou timeouts sem mexer em código. O mesmo arquivo é lido:
- pelo **Docker Compose**, que o carrega automaticamente;
- pela **aplicação**, quando roda fora do contêiner (`spring.config.import: optional:file:.env[.properties]` no `application.yml`).

O `.env` não é versionado; o modelo comentado é o [`.env.example`](.env.example). Em produção, as mesmas variáveis vêm do orquestrador e de um cofre de segredos (ver [Produção](#produção)).

### 3. Subir o ambiente completo (só Docker)

```bash
docker compose up -d --build --wait
```

Na primeira vez, o comando baixa as imagens e compila a aplicação dentro do Docker (alguns minutos). Ao terminar, todos os serviços estão *healthy*:

| Serviço | Endereço | Para quê |
|---|---|---|
| `app` | http://localhost:8080 | A API |
| `postgres` | `localhost:5432` (usuário/senha `activation`) | Banco; o schema é criado pelo Flyway na subida |
| `provider-mock` | http://localhost:8089 | Mock do provedor externo (WireMock) |
| `keycloak` | http://localhost:8180 (admin/admin) | Emissor de tokens de desenvolvimento |

```bash
curl http://localhost:8080/actuator/health/readiness     # {"status":"UP"}
```

### 4. Obter um token e chamar a API

Toda chamada em `/api/**` exige um token OAuth 2.0. O Keycloak do compose já vem com três clientes (fluxo *client credentials*):

| Cliente | Segredo | Escopos |
|---|---|---|
| `sistema-a` | `sistema-a-secret` | `activation:read activation:write` |
| `sistema-b` | `sistema-b-secret` | `activation:read activation:write` |
| `sistema-leitura` | `sistema-leitura-secret` | `activation:read` |

**Linux, macOS ou Git Bash** (usa [`jq`](https://jqlang.org/); sem ele, o token fica vazio e a API responde `401 Bearer token is malformed`):

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/activation/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=sistema-a -d client_secret=sistema-a-secret | jq -r .access_token)

curl -i -X POST http://localhost:8080/api/v1/requests \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"externalId":"EXT-1","customerId":"CUST-1","productCode":"INTERNET-500"}'

curl -s "http://localhost:8080/api/v1/requests?status=COMPLETED" -H "Authorization: Bearer $TOKEN"
```

Sem `jq`, troque só a primeira linha (usa o Node.js para extrair o token):

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/activation/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=sistema-a -d client_secret=sistema-a-secret \
  | node -pe "JSON.parse(require('fs').readFileSync(0)).access_token")
```

**Windows (PowerShell):**

```powershell
$TOKEN = (Invoke-RestMethod -Method Post -Uri http://localhost:8180/realms/activation/protocol/openid-connect/token `
  -Body @{ grant_type='client_credentials'; client_id='sistema-a'; client_secret='sistema-a-secret' }).access_token
$H = @{ Authorization = "Bearer $TOKEN" }

$req = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/requests -Headers $H `
  -ContentType 'application/json' -Body '{"externalId":"EXT-1","customerId":"CUST-1","productCode":"INTERNET-500"}'
Start-Sleep 5
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/requests/$($req.id)" -Headers $H     # PENDING -> COMPLETED
```

A ativação acontece em segundo plano: a solicitação nasce `PENDING`, e o `GET` mostra o status final alguns segundos depois. Logo após subir o ambiente, o primeiro ciclo do worker pode demorar um pouco mais; se ainda estiver `PENDING`, repita o `GET`. O mock do provedor escolhe a resposta pelo `productCode`, o que permite ver cada caminho da integração:

| `productCode` contém | Resposta do mock | Status final esperado |
|---|---|---|
| `REJECT` (ex.: `X-REJECT`) | 400 | `FAILED` (`PROVIDER_REJECTED_400`) |
| `ERROR` (ex.: `X-ERROR`) | 500 | Retentativas com backoff e, após a 5ª, `FAILED` (`MAX_ATTEMPTS_EXCEEDED`), em cerca de 30 s |
| `SLOW` (ex.: `X-SLOW`) | 200 após 8 s (acima do timeout de 5 s) | igual ao caso `ERROR` |
| qualquer outro (ex.: `INTERNET-500`) | 200 | `COMPLETED` |

### 5. Teste de fumaça (opcional)

Com o ambiente no ar, 40 verificações HTTP de ponta a ponta (criação, duplicidade e concorrência, erros, autorização entre clientes, integração com o provedor, listagem e métricas):

```bash
node scripts/smoke-test.mjs        # termina com "40/40 verificações passaram."
```

### 6. Testes automatizados

```bash
./mvnw verify          # Windows (cmd/PowerShell): .\mvnw.cmd verify
```

Um único comando compila e roda:
- os testes unitários e de arquitetura (`*Test`, Surefire);
- os testes de integração (`*IT`, Failsafe) contra um PostgreSQL 16 real e um WireMock, que o Testcontainers sobe sozinho.

Exige o JDK 21 e o **Docker em execução**. Não precisa do `docker compose up`.

Durante a execução aparecem linhas `ERROR ... duplicate key value violates unique constraint`. Elas são **esperadas**: vêm dos testes de duplicidade, que provocam a violação de propósito, e são registradas pelo Hibernate antes de a aplicação tratá-las. O que vale é o `BUILD SUCCESS` no fim.

### 7. Rodar a aplicação fora do contêiner (desenvolvimento)

Útil para depurar na IDE. Sobe só as dependências e roda a aplicação com o Maven Wrapper. Se você fez o passo 3, pare antes o contêiner da aplicação, que ocupa a porta 8080 (senão: `Port 8080 was already in use`):

```bash
docker compose stop app         # só se o passo 3 foi executado
docker compose up -d --wait postgres provider-mock keycloak
./mvnw spring-boot:run           # Windows: .\mvnw.cmd spring-boot:run
```

Os padrões (e o `.env.example`) já apontam para `localhost`, então nada mais precisa ser configurado. Também é possível executar a classe `ActivationApplication` direto pela IDE, a partir da raiz do projeto, para que o `.env` seja encontrado.

### 8. Parar e limpar

```bash
docker compose down        # para e remove os contêineres
docker compose down -v     # também apaga os dados do Postgres
```

### Problemas comuns

| Sintoma | Causa provável e solução |
|---|---|
| `port is already allocated` / `address already in use` | Outra coisa usa 8080/5432/8089/8180. Pare o processo, ou mude `SERVER_PORT` no `.env` (para a API). |
| `./mvnw: Permission denied` | Rode `chmod +x mvnw`, ou use `sh mvnw verify`. |
| Testes falham com `Could not find a valid Docker environment` | O Docker não está rodando. Os testes de integração usam Testcontainers. |
| API responde `401` com token | Token expirado (vale 5 min): gere outro. Ou ele foi pedido por outro endereço que não `localhost:8180`, o que muda o emissor (`iss`). |
| `docker compose up --wait` falha em `app` | Veja o motivo com `docker compose logs app`. Na primeira subida o Keycloak pode demorar; rode o comando de novo. |

### Configuração

Todas as variáveis estão comentadas no [`.env.example`](.env.example). Resumo:

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `activation` | Banco do Postgres (compose e aplicação) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/activation` | URL JDBC quando a aplicação roda fora do contêiner |
| `SERVER_PORT` | `8080` | Porta HTTP da API |
| `PROVIDER_BASE_URL` | `http://localhost:8089` | URL base do provedor de ativação |
| `PROVIDER_API_KEY` | `local-dev-key` | Credencial do provedor (em produção, vinda de um cofre de segredos) |
| `PROVIDER_CONNECT_TIMEOUT` / `PROVIDER_READ_TIMEOUT` | `2s` / `5s` | Timeouts da chamada ao provedor |
| `WORKER_ENABLED` | `true` | Liga o envio assíncrono e a reconciliação |
| `WORKER_POLL_INTERVAL` / `WORKER_RECONCILE_INTERVAL` | `2s` / `1m` | Frequência do envio e da reconciliação |
| `OAUTH_ISSUER` | `http://localhost:8180/realms/activation` | Emissor esperado no claim `iss` |
| `OAUTH_JWK_SET_URI` | `.../realms/activation/protocol/openid-connect/certs` | Onde buscar as chaves públicas (JWKS) |
| `OAUTH_AUDIENCE` | `activation-service` | Valor exigido no claim `aud` |
| `SHUTDOWN_TIMEOUT` | `30s` | Espera máxima no desligamento gracioso |

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

## Estratégia de testes

O critério foi dar confiança para alterar o sistema, e não perseguir cobertura. Cada nível testa o que só ele consegue provar:

| Nível | Ferramentas | O que prova | Exemplos |
|---|---|---|---|
| **Unitário, sem Spring** | JUnit 5, AssertJ, Mockito | Regras puras, rápidas e determinísticas | Matriz completa de transições de status; backoff com jitter fixo e real; classificação de 19 retornos do provedor; `Retry-After` em segundos e data HTTP; ramos do serviço de criação |
| **Cliente HTTP** | WireMock | Contrato com o provedor sem rede real | Headers (`Idempotency-Key`, `X-Correlation-Id`, `X-Api-Key`), 400/500/503, timeout de leitura, conexão recusada; mappings do compose |
| **Fatia web** | `@WebMvcTest`, `spring-security-test` | Contrato HTTP isolado do banco | 201 + `Location`, 200 no retry, cada `type` de erro, `traceId`, 401/403/413, validação de paginação |
| **Integração** | `@SpringBootTest` + Testcontainers (Postgres 16) + WireMock | O que depende do banco **real** | Constraint única sob 10 threads; `FOR UPDATE SKIP LOCKED` (o teste falha se a cláusula for removida); lock otimista; worker ponta a ponta; tokens reais assinados; `EXPLAIN` usando os índices; métricas e readiness |
| **Arquitetura** | ArchUnit | Regras de dependência que o compilador não garante | Domínio sem Spring; controller sem repositório; endpoints nunca devolvem entidades |
| **Fumaça** | `scripts/smoke-test.mjs` + docker compose | O artefato real (imagem) funcionando com as dependências reais | 40 verificações HTTP com tokens do Keycloak |

Decisões que valem destacar:
- **Postgres real, sem H2**: os pontos críticos do desafio (unicidade concorrente, `SKIP LOCKED`, `timestamptz`) só se comportam de verdade no banco de produção.
- **Um único contexto Spring para todos os `*IT`**, com um Postgres e um WireMock compartilhados (registrados via `DynamicPropertyRegistrar`, que não entra na chave do cache de contexto). A suíte de integração roda em cerca de 20 s.
- **Sem `sleep` para esperar backoff**: o worker agendado fica desligado nos testes, que o acionam diretamente, e "avançam o tempo" movendo `next_attempt_at` para o passado.
- **O teste de fumaça encontrou um bug que nenhum outro teste pegaria**: a imagem JRE enxuta não tem o módulo `jdk.random`, e `RandomGenerator.getDefault()` derrubava a aplicação na subida. Por isso vale testar o artefato, e não só o código.

## Cenário de análise (item 10)

> API 300 ms → 2,5 s · CPU 45% · memória 60% · algumas consultas 50 ms → 1,2 s · serviço externo 400 ms → 3 s

**Leitura inicial.** CPU e memória estão normais enquanto a latência subiu oito vezes. As threads estão **esperando** (I/O, pool de conexões, locks), não trabalhando. Não é falta de capacidade de processamento, e escalar instâncias às cegas pode até piorar (mais conexões disputando o mesmo banco).

**O que eu investigaria primeiro.** Quando começou e o que mudou naquele momento: deploy, volume, mudança no provedor, crescimento de alguma tabela. Depois, separaria as três camadas.

**Como identificar se o problema está na aplicação, no banco ou na integração.**

| Camada | Sinal que a isola |
|---|---|
| Integração | `activation_provider_call_seconds` (p95/p99 por `outcome`) e a taxa de `transient_failure`. Se o provedor subiu de 400 ms para 3 s, aparece aqui primeiro. |
| Banco | `pg_stat_statements` (tempo **de execução** no servidor) × tempo da consulta medido na aplicação. Se o banco diz 50 ms e a aplicação mede 1,2 s, o tempo está **antes** da consulta, esperando conexão. |
| Pool | `hikaricp_connections_pending`, `hikaricp_connections_acquire_seconds`, `hikaricp_connections_active` no máximo. |
| Aplicação | `http_server_requests_seconds` por endpoint; *thread dump* (threads paradas em `HikariPool.getConnection` ou em leitura de socket). |
| Requisição individual | O `X-Correlation-Id` liga o log da requisição, do worker e a chamada ao provedor. Seguir uma requisição lenta mostra onde o tempo foi gasto. |

**Hipótese de causa comum.** Numa implementação que chama o provedor **dentro da transação** (ou de forma síncrona segurando a conexão), cada requisição prende uma conexão do pool por cerca de 3 s quando o provedor fica lento. O pool (10 conexões por padrão) esgota, e todas as outras requisições, inclusive GETs simples, esperam na fila do Hikari. Na visão da aplicação isso aparece como "consulta lenta" (50 ms → 1,2 s), embora o banco esteja ocioso, e a API inteira sobe para 2,5 s. Uma única causa raiz (provedor lento combinado com conexão presa durante I/O remoto) explicaria os três sintomas, com CPU e memória normais.

**Como confirmar:** `hikaricp_connections_pending > 0` e `acquire` alto no mesmo período em que o provedor degradou; `pg_stat_activity` mostrando conexões `idle in transaction`; `pg_stat_statements` com tempo de execução baixo; *thread dump* com threads do Tomcat esperando conexão. A hipótese alternativa, investigada em paralelo, é crescimento da tabela sem índice. Nesse caso, o próprio `pg_stat_statements` mostra execução alta, e o `EXPLAIN ANALYZE` mostra *Seq Scan*.

**Melhorias.**
- Tirar a chamada remota da requisição e de qualquer transação. Este serviço já faz isso: o `POST` só grava, e o worker chama o provedor sem transação aberta.
- Timeouts curtos e explícitos (2 s de conexão, 5 s de leitura), para que um provedor lento não segure recursos indefinidamente.
- *Bulkhead* (pool próprio, limitado, para as chamadas ao provedor) e circuit breaker, para isolar a lentidão dele.
- Índices guiados pelas consultas e paginação (ver tabela de índices).
- Alertas em p95/p99 e em `connections.pending`, e não na média, que esconde a cauda.

**Como validar a correção depois de publicada.**
- Comparar p50/p95/p99 da API, `hikaricp_connections_pending` e o tempo de execução no banco com a linha de base, **na mesma janela de tráfego** do dia anterior.
- Publicar por canário ou rollout gradual, comparando a versão nova com a antiga lado a lado antes de completar.
- Reproduzir a causa em ambiente de teste: o mock do compose tem o cenário `SLOW`, que simula o provedor lento. Com ele, dá para provar que a API continua respondendo rápido enquanto o provedor demora.

## Produção

**Artefato.** O `Dockerfile` gera uma imagem multi-stage:
- build com Maven, com cache de dependências;
- runtime `eclipse-temurin:21-jre` com as camadas do Spring Boot, ordenadas para reaproveitar cache;
- usuário não-root (uid 10001);
- `-XX:MaxRAMPercentage=75` (a JVM respeita o limite de memória do contêiner) e `-XX:+ExitOnOutOfMemoryError` (o orquestrador reinicia em vez de manter um processo degradado);
- perfil `prod` com logs JSON (ECS);
- `HEALTHCHECK` no liveness.

A imagem é construída pela CI **depois** dos testes passarem, e não roda os testes no `docker build`, porque o Testcontainers exigiria Docker-in-Docker.

**Execução.** Em produção eu rodaria em um orquestrador (Kubernetes, ECS ou Cloud Run):
- **mínimo de 2 réplicas** atrás de um balanceador. Várias instâncias são seguras: a unicidade está no banco e o worker usa `SKIP LOCKED`;
- **probes**: `liveness` em `/actuator/health/liveness` (reinicia se travar) e `readiness` em `/actuator/health/readiness` (tira do balanceador se o banco cair). O provedor externo fica **fora** do readiness: se ele cair, as instâncias continuam aceitando solicitações, que serão enviadas depois;
- **desligamento gracioso**: `server.shutdown=graceful` com 30 s para concluir requisições e o lote em andamento (verificado com `docker stop` no meio de uma chamada lenta ao provedor). O `terminationGracePeriodSeconds` do orquestrador precisa ser maior que isso;
- **configuração por variáveis de ambiente** (tabela em [Configuração](#configuração)); **segredos** (`DB_PASSWORD`, `PROVIDER_API_KEY`) vindos de um cofre, nunca da imagem;
- **banco gerenciado** (RDS/Cloud SQL/Azure Database for PostgreSQL), com backups e réplica. As migrations do Flyway rodam na subida; com muitas réplicas, eu as moveria para um *job* de pré-deploy, para não disputarem o lock do Flyway;
- **métricas** em `/actuator/prometheus`, que exige token: o Prometheus suporta `oauth2` com client credentials no `scrape_config`. **Logs JSON** com `traceId` e `requestId`, coletados pelo agente da plataforma;
- **API Gateway** na frente, com TLS, rate limit por `client_id` e limites de tamanho.

## Microsserviços e cloud

- **Várias instâncias ao mesmo tempo.** A regra de duplicidade não depende de memória (constraint única no banco), e o envio ao provedor é dividido entre instâncias por `FOR UPDATE SKIP LOCKED`. A reconciliação é segura para rodar em todas as réplicas (cada linha é liberada numa transação própria, com `@Version`). Escalar horizontalmente não exige *leader election* nem lock distribuído.
- **Configurações e segredos.** Variáveis de ambiente para configuração; segredos no gerenciador da nuvem (AWS Secrets Manager, Azure Key Vault, GCP Secret Manager), injetados no pod via integração da plataforma (External Secrets Operator, CSI driver) ou lidos pelo Spring Cloud AWS/Azure/Vault, o que permite rotação sem rebuild.
- **Avisar outros serviços de que a ativação foi concluída.** Publicar um evento `ActivationCompleted` / `ActivationFailed` num tópico (Kafka, SNS/SQS, Pub/Sub). Para não perder eventos, eu usaria o mesmo princípio de outbox já aplicado aqui: gravar o evento numa tabela `outbox_event` na **mesma transação** que muda o status para `COMPLETED`, e um publicador (ou Debezium/CDC) o envia ao broker com entrega *at-least-once*. Os consumidores ficam idempotentes pelo id do evento.
- **Pipeline de CI/CD.**
  1. PR: `./mvnw verify` (unitários, ArchUnit e integração com Testcontainers), já no GitHub Actions.
  2. Merge na `main`: build da imagem, scan de vulnerabilidades (Trivy/Grype), assinatura (cosign) e push para o registry com a tag do commit.
  3. Deploy automático em homologação, com o teste de fumaça rodando contra o ambiente.
  4. Promoção para produção com canário, observando p95, taxa de erro e `activation_provider_call` antes de completar o rollout. Rollback é voltar a imagem anterior; as migrations são sempre aditivas para permitir isso.
- **Tracing distribuído.** Hoje a correlação é feita pelo `X-Correlation-Id`. Com mais serviços, eu adotaria OpenTelemetry (Micrometer Tracing + exporter OTLP), propagando o `traceparent` W3C até o provedor e o broker.

## Pontos pendentes

O que não foi implementado e como eu faria:

- **Processamento sequencial dentro do lote**: o worker envia as solicitações do lote uma a uma. O teste de fumaça mostrou que uma chamada lenta (timeout de 5 s) atrasa as outras do mesmo lote. Como o paralelismo hoje vem de múltiplas instâncias, isso é aceitável, mas o próximo passo seria processar o lote com *virtual threads* e um limite de concorrência (bulkhead).
- **Log de duplicidade em nível ERROR**: no retry legítimo, o `SqlExceptionHelper` do Hibernate registra `duplicate key` como ERROR antes de o serviço tratar a violação. Em produção, isso polui o log e pode disparar alertas. As opções são rebaixar esse logger específico ou trocar o insert por `INSERT ... ON CONFLICT DO NOTHING RETURNING` (ADR 0003).
- **Reprocessamento manual de `FAILED`**: não há endpoint administrativo para reenfileirar uma solicitação `FAILED` (por exemplo, depois de corrigir a credencial que causou um 401). Hoje, isso exigiria um `UPDATE` direto no banco.
- **Circuit breaker no cliente do provedor**: descrito na seção de integração, mas não implementado.
- **Rate limit por cliente**: delegado ao API Gateway (seção Segurança), não implementado na aplicação.
- **Autenticação do serviço perante o provedor**: hoje é uma API key estática (`X-Api-Key`). Se o provedor suportar, o ideal é o mesmo modelo Client Credentials, ou mTLS.
- **Tracing distribuído (OpenTelemetry)**: descrito em [Microsserviços e cloud](#microsserviços-e-cloud); a correlação atual é feita por `X-Correlation-Id`.

## Melhorias futuras

Se a aplicação fosse evoluir para produção:

1. **Spring Boot 4.x**: o suporte open source da linha 3.5 terminou em junho/2026. A migração passaria por OpenRewrite (`UpgradeSpringBoot_4_0`), Jackson 3 e ajuste dos pacotes de teste (ADR 0001).
2. **Eventos de domínio** (`ActivationCompleted`/`ActivationFailed`) via outbox e broker, para os demais serviços reagirem sem consultar esta API por polling.
3. **Webhook ou notificação ao sistema de origem**, como alternativa ao polling do `GET`.
4. **Endpoint administrativo de reprocessamento**, com escopo próprio (`activation:admin`) e trilha de auditoria.
5. **Circuit breaker e bulkhead** no cliente do provedor, com processamento paralelo do lote.
6. **Paginação por cursor** (*keyset*) na listagem, para tabelas grandes.
7. **Particionamento ou arquivamento** de solicitações terminais antigas, mantendo os índices parciais do worker pequenos.
8. **OpenTelemetry**, dashboards (Grafana) e alertas por SLO (p99 da API, idade da solicitação `PENDING` mais antiga, taxa de `MAX_ATTEMPTS_EXCEEDED`).
9. **Testes de contrato** com o provedor (Pact ou Spring Cloud Contract), para detectar quebras no contrato antes da produção.

## Uso de IA

Usei o **Claude Code** (Anthropic) como assistente durante todo o desenvolvimento:

- **Planejamento**: análise do enunciado (obrigatório × diferencial), redação das especificações no formato [OpenSpec](openspec/) e dos [ADRs](docs/adr/), com as decisões discutidas antes da implementação (por exemplo, a escolha do Spring Boot 3.5.16 em vez da 4.1, registrada no ADR 0001).
- **Implementação**: geração do código e dos testes change a change, seguindo as tarefas do OpenSpec, com commits pequenos por intenção. Os comandos do OpenSpec usados nesse fluxo estão em `.claude/`.
- **Verificação**: execução da suíte, do teste de fumaça contra o ambiente em contêineres e conferência dos requisitos do enunciado.

