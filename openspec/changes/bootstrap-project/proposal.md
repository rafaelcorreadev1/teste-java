# Proposal

## Why

O repositório ainda não tem código. Antes de qualquer comportamento de negócio, precisamos de um esqueleto que compile, rode os testes com um único comando (`./mvnw verify`), suba o banco real localmente e na CI e versione o schema. Sem essa base, cada change seguinte teria que resolver infraestrutura junto com regra de negócio.

## What Changes

- Projeto Maven (com wrapper) Spring Boot 3.5 / Java 21, pacote base `com.deroyque.activation`.
- Dependências: Web, Validation, Data JPA, PostgreSQL, Flyway, Actuator e Test, além de Testcontainers (PostgreSQL) e WireMock para testes.
- Configuração por variáveis de ambiente (`DB_URL`, `DB_USER`, `DB_PASSWORD`, ...) com perfis `local` e `prod`.
- Flyway habilitado com `ddl-auto=validate`; o primeiro migration (V1) chega na change `add-activation-requests`.
- `docker-compose.yml` com PostgreSQL 16, para rodar a aplicação localmente.
- Classe base de teste de integração com Testcontainers (`@ServiceConnection`), reaproveitada pelas próximas changes.
- Pipeline do GitHub Actions executando `./mvnw verify`.
- `README.md` inicial com a seção "Como executar" e `.gitignore`.

## Capabilities

### New Capabilities

Nenhuma. Esta change é só de infraestrutura e não altera comportamento observável (`skip_specs: true`).

### Modified Capabilities

Nenhuma.

## Impact

- Cria `pom.xml`, `mvnw`, `.mvn/`, `src/`, `docker-compose.yml`, `.github/workflows/ci.yml`, `README.md` e `.gitignore`.
- Pré-requisitos locais: JDK 21 e Docker (para Testcontainers e compose).
- ADRs: 0001 (stack) e 0002 (Flyway).
