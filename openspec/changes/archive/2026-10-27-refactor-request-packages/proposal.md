# Proposal

## Why

O pacote `request.app` acumulou duas responsabilidades que mudam por motivos diferentes: o caso de uso síncrono que atende a API (criar, consultar, listar) e o processamento assíncrono que envia as solicitações ao provedor (worker, reconciliação, backoff, métricas). Com as duas no mesmo pacote, a visibilidade *package-private* deixa de proteger os detalhes de cada lado, e quem lê o código não distingue de imediato o que roda na requisição do que roda em segundo plano.

## What Changes

- Novo pacote `request.processing` com o processamento assíncrono: `ActivationDispatcher`, `ActivationScheduler`, `ActivationStateStore`, `ClaimedRequest`, `RetryPolicy`, `ActivationWorkerProperties` e `ActivationStatusMetrics`.
- `request.app` fica só com o caso de uso da API: `ActivationRequestService`, `ActivationRequestWriter`, `CreateResult`, `NewActivationRequest` e as exceções de negócio.
- Testes acompanham os pacotes.
- Nova regra ArchUnit: o caso de uso da API não depende do processamento assíncrono.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

Nenhuma. É uma refatoração sem mudança de comportamento observável (`skip_specs: true`).

## Impact

- Apenas `package`/imports. Nenhuma mudança de API, banco ou configuração.
- `config.ActivationWorkerConfig` passa a importar de `request.processing`.
