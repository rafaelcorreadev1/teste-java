# Tasks

## 1. Ambiente e esqueleto

- [x] 1.1 Garantir JDK 21 instalado e verificar com `java -version`
- [x] 1.2 Gerar o projeto Spring Boot 3.5 / Java 21 / Maven (Web, Validation, Data JPA, PostgreSQL, Flyway, Actuator) com wrapper e verificar que `./mvnw -v` funciona
- [x] 1.3 Adicionar dependências de teste (Testcontainers PostgreSQL, WireMock standalone, AssertJ) e plugin Failsafe, e verificar que `./mvnw verify` resolve tudo
- [x] 1.4 Criar a estrutura de pacotes `com.deroyque.activation.{request,provider,shared,config}` e verificar que compila

## 2. Configuração e banco

- [x] 2.1 Configurar `application.yml` com datasource por variáveis de ambiente, `ddl-auto=validate` e Flyway, e verificar subindo a aplicação contra o compose
- [x] 2.2 Criar `docker-compose.yml` com PostgreSQL 16 (volume e healthcheck) e verificar que `docker compose up -d` deixa o banco *healthy*
- [x] 2.3 Criar a base de teste de integração com container Postgres singleton e `@ServiceConnection`, e verificar com um `ApplicationIT` que o contexto sobe

## 3. CI e repositório

- [x] 3.1 Criar `.github/workflows/ci.yml` (checkout, JDK 21 com cache Maven, `./mvnw verify`) e verificar a sintaxe do YAML
- [x] 3.2 Criar `.gitignore` (target, IDE, `estudo/`) e `README.md` com a seção "Como executar", e verificar seguindo os passos a partir de um clone limpo
- [x] 3.3 `git init` e primeiro commit do esqueleto, verificando com `git log`

## 4. Documentação e estudo

- [x] 4.1 Marcar ADR 0001 e 0002 como Aceito e verificar o índice em `docs/adr/README.md`
- [x] 4.2 Atualizar a seção `bootstrap-project` em `estudo/estudo.html`, regerar `estudo/estudo.pdf` com `estudo/gerar-pdf.sh` e verificar que o PDF abre com a seção atualizada
