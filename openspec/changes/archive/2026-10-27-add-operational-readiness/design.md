# Design

## Context

O `CorrelationIdFilter` já existe desde `add-activation-requests`, alimentando o `traceId`. O worker do provedor roda fora da requisição HTTP e hoje não conhece o id original. Decisão completa em [ADR 0010](../../../docs/adr/0010-observabilidade-e-empacotamento.md).

## Goals / Non-Goals

**Goals:**
- Dar um sinal separado para cada sintoma do cenário do item 10 (API, banco, provedor).
- Fazer `docker compose up --build` subir tudo a partir de um clone limpo.

**Non-Goals:**
- Tracing distribuído com OpenTelemetry (descrito no README como evolução).
- Prometheus/Grafana no compose. As métricas ficam expostas, e a coleta é da plataforma.
- Kubernetes/Terraform (comentados no README).

## Decisions

### Correlação até o provedor
- Migration `V5__add_correlation_id.sql` adiciona `correlation_id varchar(64)`. Ele é gravado na criação a partir do MDC.
- O dispatcher, para cada solicitação, faz `MDC.put("traceId", correlationId)` e `MDC.put("requestId", id)` dentro de `try/finally` (limpa no fim, porque a thread do scheduler é reutilizada). O `ProviderClient` lê o header do MDC.
- O filtro valida o header recebido (`[A-Za-z0-9-]{1,64}`) para impedir *log injection*.

### Saúde
- `management.endpoint.health.probes.enabled=true`, `management.endpoint.health.group.readiness.include=readinessState,db`.
- `show-details=when-authorized`, para que a probe anônima só veja o status agregado.
- O provedor externo **não** entra no readiness. Se entrasse, o provedor fora do ar tiraria todas as instâncias do balanceamento, e a API, que nem depende dele de forma síncrona, ficaria inteira indisponível.

### Métricas
- `Timer` `activation.provider.call` (tags `outcome` e `detail`, este com o status ou o motivo da falha) com histograma para p95/p99, registrado no próprio `ProviderClient`.
- `MultiGauge` `activation.requests` por `status`, atualizado por uma consulta `group by status` agendada a cada 30 s, em vez de ser calculado a cada *scrape*, para não pesar no banco.
- Hikari (`hikaricp.connections.active` / `pending`) e `http.server.requests` já vêm automáticos.

### Logs
- `logging.structured.format.console=ecs` no perfil `prod` (Spring Boot 3.4+). Os campos do MDC entram automaticamente.
- Nenhum log de headers. O `X-Api-Key` do provedor só existe no `RestClient` e nunca é logado.

### Imagem e compose

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B package -DskipTests && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s CMD curl -fs http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]
```

- As camadas ficam ordenadas do que menos muda (dependências) para o que mais muda (código), para aproveitar o cache.
- Os testes não rodam no build da imagem porque já rodaram na CI. O Testcontainers dentro do `docker build` exigiria Docker-in-Docker.
- O `HEALTHCHECK` usa `curl`, que a imagem `eclipse-temurin:21-jre` já traz (conferido na implementação). O runtime dessa imagem, porém, não inclui todos os módulos do JDK: `RandomGenerator.getDefault()` (módulo `jdk.random`) falhou na subida e foi trocado por `java.util.Random`.
- `server.shutdown=graceful` e `spring.lifecycle.timeout-per-shutdown-phase=30s`. O scheduler espera o lote corrente com `spring.task.scheduling.shutdown.await-termination=true`.
- No compose, a app usa `depends_on: postgres: condition: service_healthy` e variáveis de ambiente. A segurança local usa o perfil `local`, com um emissor de teste documentado no README.

## Risks / Trade-offs

- [Métrica `activation.requests` atrasada em até 30 s] → É aceitável para dashboard e alerta.
- [O correlation id do worker é o da criação, não o de cada tentativa] → É intencional: permite rastrear a solicitação inteira por um id. Cada tentativa é distinguida pelo `attempts` no log.

## Migration Plan

O V5 é aditivo e permite nulo (linhas antigas ficam sem correlação, e o worker gera um id nesse caso).
