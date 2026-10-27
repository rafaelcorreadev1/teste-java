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
docker compose up -d     # PostgreSQL 16 em localhost:5432
./mvnw spring-boot:run
curl localhost:8080/actuator/health
```

O schema é criado pelo Flyway na subida (`src/main/resources/db/migration`).

### Configuração

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/activation` | URL JDBC do PostgreSQL |
| `DB_USER` | `activation` | Usuário do banco |
| `DB_PASSWORD` | `activation` | Senha do banco |
| `SERVER_PORT` | `8080` | Porta HTTP |

## API

### `POST /api/v1/requests`

```bash
curl -i -X POST localhost:8080/api/v1/requests \
  -H 'Content-Type: application/json' \
  -d '{"externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500"}'
```

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/api/v1/requests/0b7f3c1e-5d2a-4c4e-9a51-2f8d7b1e6c90
X-Correlation-Id: 6f1c...

{"id":"0b7f3c1e-...","externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500",
 "status":"PENDING","createdAt":"2026-10-27T14:03:11.123Z","updatedAt":"2026-10-27T14:03:11.123Z"}
```

Regras de validação, iguais para `externalId`, `customerId` e `productCode`:
- obrigatório e não vazio;
- no máximo 64 caracteres;
- apenas `[A-Za-z0-9_-]`.

Datas em UTC, ISO-8601, com precisão de milissegundos.

### `GET /api/v1/requests/{id}`

Retorna a solicitação e o status atual. Responde `404` se não existir e `400` se o id não for um UUID.

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
| Solicitação inexistente | 404 | `not-found` |
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

### Status da solicitação — [ADR 0004](docs/adr/0004-maquina-de-estados-no-dominio.md)

`PENDING → PROCESSING → COMPLETED | FAILED`, com `PROCESSING → PENDING` para falha temporária. As transições são métodos da entidade, que não tem setters, e estados terminais não mudam. `@Version` impede que duas escritas concorrentes se sobrescrevam.

## Pontos pendentes

- **Log de duplicidade em nível ERROR**: no retry legítimo, o `SqlExceptionHelper` do Hibernate registra `duplicate key` como ERROR antes de o serviço tratar a violação. Em produção, isso polui o log e pode disparar alertas. As opções são rebaixar esse logger específico ou trocar o insert por `INSERT ... ON CONFLICT DO NOTHING RETURNING` (ADR 0003).
