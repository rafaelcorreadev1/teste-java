# 0010. Adotar correlation id, Actuator e Micrometer, e empacotar em imagem multi-stage não-root

- Status: Aceito
- Data: 2026-10-27
- Change: `add-operational-readiness`

## Contexto
O item 10 pede raciocínio de diagnóstico em produção e o item 11 pede um Dockerfile. Sem identificador de correlação e sem métricas separadas por dependência (API × banco × provedor), não dá para isolar o gargalo.

## Decisão
- **Correlation id**: um filtro lê `X-Correlation-Id` (ou gera um UUID; valores fora de `[A-Za-z0-9-]{1,64}` são descartados para evitar *log injection*), põe no MDC, devolve na resposta e usa como `traceId` do Problem Details. O valor é **gravado na solicitação**, e o worker o restaura no MDC e o envia ao provedor. Assim, uma única chave liga a requisição HTTP, o processamento assíncrono e a chamada externa.
- **Actuator**: `health` com grupos `liveness` e `readiness` (o readiness inclui o banco), `info` e `prometheus`. O **provedor externo fica fora do readiness**: incluí-lo faria a queda dele tirar todas as instâncias do balanceador, e a API, que não depende dele de forma síncrona, ficaria inteira indisponível. Detalhes de saúde só aparecem para chamadas autenticadas; `/actuator/prometheus` exige token.
- **Micrometer/Prometheus**:
  - `http.server.requests`, com histograma para p95/p99;
  - timer `activation.provider.call` com tags `outcome` (`success`/`permanent_failure`/`transient_failure`) e `detail` (`http_400`, `provider_http_500`, `provider_unreachable`...), com histograma;
  - gauge `activation.requests` por `status`, **recalculado a cada 30 s** em vez de a cada coleta, para que o Prometheus não dispare um `count` no banco a cada scrape;
  - métricas do pool Hikari.
- **Logs JSON** (logging estruturado ECS do Spring Boot 3.4+) no perfil `prod`, com `traceId` e `requestId` do MDC. Tokens, o header `Authorization` e a credencial do provedor nunca são logados.
- **Imagem**:
  - Dockerfile multi-stage (`eclipse-temurin:21-jdk` → `21-jre`), com as camadas do Spring Boot extraídas da menos para a mais volátil;
  - usuário não-root (uid 10001);
  - `-XX:MaxRAMPercentage=75` e `-XX:+ExitOnOutOfMemoryError`;
  - configuração por variáveis de ambiente, perfil `prod` por padrão;
  - `server.shutdown=graceful`, com espera de 30 s para requisições e para o lote agendado em andamento;
  - `HEALTHCHECK` no liveness.
- **Compose** com a aplicação, Postgres, mock do provedor e Keycloak, mais um teste de fumaça HTTP (`scripts/smoke-test.mjs`) contra esse ambiente.

## Alternativas consideradas
- **OpenTelemetry completo (tracing distribuído)**: é o ideal com vários serviços. Aqui fica descrito, e o correlation id cobre o essencial.
- **jlink/distroless**: imagem menor, mas a depuração fica mais difícil. O JRE oficial é um bom equilíbrio.
- **Porta de gerenciamento separada para o Actuator**: exporia `/actuator/prometheus` sem token numa porta interna. Ficaria dependente da configuração de rede do ambiente; exigir token (o Prometheus suporta `oauth2` com client credentials) funciona em qualquer lugar.

## Consequências
- Com `http.server.requests`, `activation.provider.call` e `hikaricp.connections.pending`, os três sintomas do cenário de análise ficam separáveis em um dashboard.
- O runtime enxuto da imagem JRE **não tem todos os módulos do JDK**. O teste de fumaça revelou que `RandomGenerator.getDefault()` (módulo `jdk.random`) derrubava a aplicação na subida, embora toda a suíte passasse com o JDK completo. O código passou a usar `java.util.Random`, e testar a imagem real virou parte da verificação.
