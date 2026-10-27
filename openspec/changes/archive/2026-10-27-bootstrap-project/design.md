# Design

## Context

O repositório está vazio. Na máquina de desenvolvimento há Docker, mas ainda não há JDK nem Maven instalados. O wrapper resolve o Maven, mas o JDK 21 precisa ser instalado (ex.: `winget install EclipseAdoptium.Temurin.21.JDK`).

## Goals / Non-Goals

**Goals:**
- `./mvnw verify` em um clone limpo compila e roda todos os testes, inclusive os de integração com Postgres real.
- `docker compose up` sobe o banco para executar a aplicação localmente.
- A estrutura de pacotes por feature já fica definida.

**Non-Goals:**
- Dockerfile endurecido e observabilidade, que ficam para `add-operational-readiness`.
- Mock do provedor no compose, que fica para `add-provider-activation`.

## Decisions

- **Stack e banco real nos testes**: ver [ADR 0001](../../../docs/adr/0001-stack-java21-spring-boot3.md). Versão fixada em 3.5.16, a última da linha 3.x. O Initializr já não oferece a 3.5, então o `pom.xml` foi escrito à mão e só o Maven Wrapper veio de um projeto gerado.
- **Flyway + `ddl-auto=validate`**: ver [ADR 0002](../../../docs/adr/0002-migracoes-versionadas-flyway.md).
- **Testcontainers com `@ServiceConnection`** (Spring Boot 3.1+): o Spring configura o datasource a partir do container, sem `@DynamicPropertySource` manual. O container é estático e compartilhado entre as classes de teste (*singleton container*) para não subir um Postgres por classe.
- **Surefire para unitários (`*Test`) e Failsafe para integração (`*IT`)**: `verify` roda os dois e deixa clara a separação na estratégia de testes.
- **Pacotes por feature**: `request` (API e domínio da solicitação), `provider` (integração), `shared` (erros, correlação) e `config`. Separar por camada técnica (`controller/`, `service/`...) espalharia cada feature por todo o projeto.

## Risks / Trade-offs

- [Docker obrigatório para os testes de integração] → O README documenta isso, e a CI (ubuntu-latest) já tem Docker.
- [Primeira execução lenta por causa do download de imagens] → Cache do Maven na CI. O tempo da imagem do Postgres é aceito.
