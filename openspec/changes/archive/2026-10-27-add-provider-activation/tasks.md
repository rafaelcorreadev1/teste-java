# Tasks

## 1. Modelo

- [x] 1.1 Criar `V2__add_provider_processing.sql` e ajustar a entidade (`attempts`, `nextAttemptAt`, `processingStartedAt`, `failureReason`) e as transições (`startProcessing` incrementa tentativas, `returnToPending(nextAttemptAt)`, `fail(reason)`), verificando com os testes unitários de domínio atualizados
- [x] 1.2 Incluir `attempts` e `failureReason` na resposta da API e verificar com o teste de controller

## 2. Cliente do provedor

- [x] 2.1 Criar `ProviderResult` (sealed) e `ProviderResponseClassifier`, e verificar com teste unitário parametrizado (2xx, 400, 401, 404, 408, 422, 429, 500, 503)
- [x] 2.2 Criar `RetryPolicy` com `Clock` e `RandomGenerator` injetados, e verificar com teste unitário o crescimento exponencial, o teto, o jitter dentro da faixa e o `Retry-After`
- [x] 2.3 Criar `ProviderClient` (RestClient, timeouts por propriedade, headers `Idempotency-Key`, `X-Correlation-Id` e `X-Api-Key`) e verificar com WireMock: sucesso, 400, 500, 503 com `Retry-After` e atraso acima do read-timeout → `TransientFailure`

## 3. Processamento

- [x] 3.1 Criar `ActivationStateStore` (`claimBatch` com `FOR UPDATE SKIP LOCKED` e `applyResult`) e verificar com teste de integração que duas reivindicações concorrentes não retornam a mesma linha
- [x] 3.2 Criar `ActivationDispatcher` (`@Scheduled`, condicional por propriedade) e verificar com teste de integração (Postgres + WireMock) sucesso → `COMPLETED`, 400 → `FAILED`, 500 → `PENDING` com `attempts` 1, e 5×500 → `MAX_ATTEMPTS_EXCEEDED`
- [x] 3.3 Verificar com WireMock que o `Idempotency-Key` é o mesmo nas tentativas após timeout
- [x] 3.4 Criar a reconciliação de `PROCESSING` expirado e verificar com teste de integração que a linha volta para `PENDING` e é reenviada

## 4. Ambiente

- [x] 4.1 Adicionar WireMock ao `docker-compose.yml` com os mappings por `productCode`, e verificar com `ProviderMockMappingsTest` (mappings do compose contra o `ProviderClient` real) e com `docker compose up --wait` + chamadas diretas ao mock; o fluxo app → mock fica no README para execução manual

## 5. Documentação e estudo

- [x] 5.1 Documentar no README a tabela "Retorno do provedor × status × nova tentativa × justificativa", síncrono × assíncrono, transação × chamada remota e a premissa de idempotência, e verificar que bate com os testes
- [x] 5.2 Marcar ADR 0006 e 0007 como Aceito e verificar o índice
- [x] 5.3 Atualizar a seção `add-provider-activation` em `estudo/estudo.html`, regerar o PDF e verificar a seção no PDF
