# Tasks

## 1. Correlação

- [x] 1.1 Validar o `X-Correlation-Id` no filtro (formato e tamanho) e verificar com teste de controller os três cenários (enviado, ausente, inválido)
- [x] 1.2 Criar `V5__add_correlation_id.sql`, gravar o correlation id na criação e restaurá-lo no MDC do dispatcher com limpeza em `finally`, e verificar com WireMock que o provedor recebe o mesmo `X-Correlation-Id` da criação

## 2. Saúde e métricas

- [x] 2.1 Configurar Actuator (probes, readiness com db, `prometheus`, `show-details=when-authorized`) e verificar com teste de integração: liveness 200, readiness 503 com o banco parado
- [x] 2.2 Criar o timer `activation.provider.call` e o gauge `activation.requests`, e verificar com teste que `/actuator/prometheus` contém as séries após um envio com 500
- [x] 2.3 Configurar o log JSON no perfil `prod` e verificar com teste (`OutputCaptureExtension`) que um 500 autenticado não loga o token

## 3. Contêiner

- [x] 3.1 Criar `Dockerfile` multi-stage não-root e `.dockerignore`, e verificar com `docker build` e `docker run` (usuário não-root via `docker exec id`, `HEALTHCHECK` healthy)
- [x] 3.2 Completar o `docker-compose.yml` (app + postgres + wiremock) e verificar com `docker compose up --build` o fluxo criar → consultar até `COMPLETED`
- [x] 3.3 Configurar o desligamento gracioso e verificar com `docker stop` durante uma requisição lenta que ela conclui

## 4. Documentação e estudo

- [x] 4.1 Completar o README (Cenário de análise com hipótese de causa comum e validação pós-deploy; Produção; Microsserviços e cloud; Estratégia de testes; Pontos pendentes; Melhorias futuras; Uso de IA) e verificar contra a lista de seções obrigatórias do enunciado
- [x] 4.2 Marcar ADR 0010 como Aceito e verificar o índice
- [x] 4.3 Atualizar a seção `add-operational-readiness` em `estudo/estudo.html`, incluindo o roteiro de preparação para a conversa técnica, regerar o PDF e verificar a seção no PDF
