# 0010. Adotar correlation id, Actuator e Micrometer, e empacotar em imagem multi-stage não-root

- Status: Proposto
- Data: 2026-10-27
- Change: `add-operational-readiness`

## Contexto
O item 10 pede raciocínio de diagnóstico em produção e o item 11 pede um Dockerfile. Sem identificador de correlação e sem métricas separadas por dependência (API × banco × provedor), não dá para isolar o gargalo.

## Decisão
- **Correlation id**: um filtro lê `X-Correlation-Id` (ou gera um UUID), põe no MDC, devolve na resposta e usa como `traceId` do Problem Details. O worker põe o id da solicitação no MDC e propaga o correlation id ao provedor.
- **Actuator**: `health` com grupos `liveness` e `readiness` (o readiness inclui o banco), `info` e `prometheus`.
- **Micrometer/Prometheus**:
  - `http.server.requests`, automático;
  - timer `activation.provider.call` com tags `outcome` e `http_status`;
  - gauge `activation.requests` por `status`;
  - métricas do pool Hikari.
- **Logs JSON** (logging estruturado nativo do Spring Boot 3.4+) no perfil `prod`. Tokens e credenciais nunca são logados.
- **Imagem**:
  - Dockerfile multi-stage (`eclipse-temurin:21-jdk` → `21-jre`);
  - usuário não-root;
  - `-XX:MaxRAMPercentage=75`;
  - configuração por variáveis de ambiente;
  - `server.shutdown=graceful`;
  - `HEALTHCHECK`.

## Alternativas consideradas
- **OpenTelemetry completo (tracing distribuído)**: é o ideal com vários serviços. Aqui fica descrito, e o correlation id cobre o essencial.
- **jlink/distroless**: imagem menor, mas a depuração fica mais difícil. O JRE oficial é um bom equilíbrio.

## Consequências
- Com `http.server.requests`, `activation.provider.call` e `hikaricp.connections.pending`, os três sintomas do cenário de análise ficam separáveis em um dashboard.
