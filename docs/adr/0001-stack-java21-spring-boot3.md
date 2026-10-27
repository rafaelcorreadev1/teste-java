# 0001. Usar Java 21, Spring Boot 3.5.16, Maven e PostgreSQL

- Status: Aceito
- Data: 2026-10-27
- Change: `bootstrap-project`

## Contexto
A vaga pede Java 17/21, Spring Boot, Spring MVC, Spring Data JPA/Hibernate, banco SQL e Maven. O enunciado aceita H2 só para testes locais e valoriza Spring Boot 3.x e build com um único comando.

## Decisão
- **Java 21 (LTS)**: records para DTOs; `sealed` + `switch` com pattern matching para modelar o resultado da chamada ao provedor.
- **Spring Boot 3.5.16** (última da linha 3.x) com Spring MVC (bloqueante): modelo simples, fácil de depurar e suficiente para a carga do desafio. A linha 3.x foi escolhida porque o enunciado cita "versão 3.x" e é a base que a vaga provavelmente usa.
- **Maven com wrapper**: `./mvnw verify` roda build, testes unitários e de integração.
- **PostgreSQL 16** em todos os ambientes, inclusive nos testes (via Testcontainers). **Sem H2.**

## Alternativas consideradas
- **H2 nos testes**: é mais rápido, mas não reproduz a constraint única sob concorrência, o `FOR UPDATE SKIP LOCKED` nem `timestamptz`. Justamente os pontos críticos do desafio passariam no H2 e falhariam no Postgres.
- **WebFlux**: ganho de throughput sem necessidade real, com custo alto de complexidade e de testes.
- **Gradle**: a vaga usa Maven.
- **Spring Boot 4.1** (estável atual): é suportado, mas traz Jackson 3, starters e pacotes de teste reorganizados e Testcontainers 2. Aumentaria o atrito e o distanciamento da stack da vaga sem ganho para o escopo do desafio.

## Consequências
- Os testes de integração exigem Docker na máquina e na CI.
- Usar o mesmo banco em todo lugar elimina a classe de bugs "funciona no H2".
- O suporte open source da linha 3.5 terminou em junho/2026. Em produção, isso exige suporte comercial ou migração para 4.x. A migração fica em "Melhorias futuras" no README: OpenRewrite (`UpgradeSpringBoot_4_0`), troca para Jackson 3 e ajuste dos imports de teste.
