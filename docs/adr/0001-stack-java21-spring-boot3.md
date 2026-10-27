# 0001. Usar Java 21, Spring Boot 3.5, Maven e PostgreSQL

- Status: Proposto
- Data: 2026-10-27
- Change: `bootstrap-project`

## Contexto
A vaga pede Java 17/21, Spring Boot, Spring MVC, Spring Data JPA/Hibernate, banco SQL e Maven. O enunciado aceita H2 só para testes locais e valoriza Spring Boot 3.x e build com um único comando.

## Decisão
- **Java 21 (LTS)**: records para DTOs; `sealed` + `switch` com pattern matching para modelar o resultado da chamada ao provedor.
- **Spring Boot 3.5** com Spring MVC (bloqueante): modelo simples, fácil de depurar e suficiente para a carga do desafio.
- **Maven com wrapper**: `./mvnw verify` roda build, testes unitários e de integração.
- **PostgreSQL 16** em todos os ambientes, inclusive nos testes (via Testcontainers). **Sem H2.**

## Alternativas consideradas
- **H2 nos testes**: é mais rápido, mas não reproduz a constraint única sob concorrência, o `FOR UPDATE SKIP LOCKED` nem `timestamptz`. Justamente os pontos críticos do desafio passariam no H2 e falhariam no Postgres.
- **WebFlux**: ganho de throughput sem necessidade real, com custo alto de complexidade e de testes.
- **Gradle**: a vaga usa Maven.

## Consequências
- Os testes de integração exigem Docker na máquina e na CI.
- Usar o mesmo banco em todo lugar elimina a classe de bugs "funciona no H2".
