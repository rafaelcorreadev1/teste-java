# 0002. Versionar o schema com Flyway

- Status: Proposto
- Data: 2026-10-27
- Change: `bootstrap-project`

## Contexto
A unicidade de `externalId` e os índices de consulta são **regras do banco**. Elas precisam estar versionadas e revisáveis, e não ser geradas implicitamente pelo Hibernate.

## Decisão
- Flyway com scripts SQL em `src/main/resources/db/migration` (`V1__create_activation_request.sql`, ...).
- `spring.jpa.hibernate.ddl-auto=validate`: o Hibernate só **confere** se as entidades batem com o schema.

## Alternativas consideradas
- **`ddl-auto=update`**: não remove nem renomeia, não controla nomes de constraints e é perigoso em produção.
- **Liquibase**: equivalente, mas o XML/YAML esconde o SQL. Para um schema pequeno, SQL puro é mais legível.

## Consequências
- Cada mudança de schema vira um arquivo novo. Migrations já aplicados nunca são editados.
- O nome da constraint (`uk_activation_request_external_id`) fica estável, o que permite reconhecê-la no tratamento de erro (ADR 0003).
