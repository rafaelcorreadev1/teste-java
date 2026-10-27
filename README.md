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

## Decisões técnicas

As decisões de arquitetura estão registradas em [`docs/adr/`](docs/adr/README.md). As especificações de comportamento estão em [`openspec/`](openspec/).
