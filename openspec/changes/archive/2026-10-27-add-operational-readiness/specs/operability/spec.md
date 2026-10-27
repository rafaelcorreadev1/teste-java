# Spec Delta

## Purpose

Expõe sinais operacionais (saúde, correlação de logs e métricas por dependência) que permitem à orquestração gerir o serviço e ao time diagnosticar problemas de produção, distinguindo aplicação, banco e provedor externo.

## ADDED Requirements

### Requirement: Identificador de correlação por requisição
O sistema SHALL aceitar `X-Correlation-Id` na requisição ou gerar um, devolvê-lo no header de resposta e incluí-lo em todo log emitido durante o processamento. O valor MUST ser o mesmo `traceId` das respostas de erro.

#### Scenario: Cliente envia correlation id
- **WHEN** o cliente cria uma solicitação com `X-Correlation-Id: abc-123`
- **THEN** a resposta traz `X-Correlation-Id: abc-123`
- **AND** os logs dessa requisição contêm `abc-123`

#### Scenario: Cliente não envia correlation id
- **WHEN** o cliente faz uma requisição sem `X-Correlation-Id`
- **THEN** a resposta traz um `X-Correlation-Id` gerado e não vazio

#### Scenario: Valor inválido é substituído
- **WHEN** o cliente envia `X-Correlation-Id` com mais de 64 caracteres ou caracteres fora de `[A-Za-z0-9-]`
- **THEN** o sistema ignora o valor e gera um novo

### Requirement: Health checks de liveness e readiness
O sistema SHALL expor liveness e readiness separadamente. O readiness MUST indicar indisponível quando o banco não responde, enquanto o liveness reflete apenas o estado do processo.

#### Scenario: Banco indisponível
- **WHEN** o banco de dados está inacessível
- **THEN** `/actuator/health/readiness` responde `503`
- **AND** `/actuator/health/liveness` responde `200`

### Requirement: Métricas por dependência
O sistema SHALL expor, em formato Prometheus, a latência das requisições da API, a latência e a contagem por resultado das chamadas ao provedor (`success`, `permanent_failure`, `transient_failure`), a quantidade de solicitações por status e a utilização do pool de conexões com o banco.

#### Scenario: Chamada ao provedor registrada
- **WHEN** o provedor responde `500` a um envio
- **THEN** a métrica de chamadas ao provedor com resultado `transient_failure` é incrementada
- **AND** a métrica é visível em `/actuator/prometheus`

### Requirement: Logs sem dados sensíveis
Os logs MUST NOT conter tokens de acesso, o header `Authorization` nem a credencial do provedor.

#### Scenario: Requisição autenticada com erro
- **WHEN** uma requisição autenticada termina em erro `500`
- **THEN** o log do erro não contém o valor do token Bearer

### Requirement: Desligamento gracioso
Ao receber sinal de término, o sistema SHALL parar de aceitar novas requisições, concluir as requisições e o lote de envio em andamento dentro de um prazo configurável (padrão 30 s) e então encerrar.

#### Scenario: Término durante requisição
- **WHEN** o processo recebe SIGTERM durante uma requisição em andamento
- **THEN** a requisição em andamento é concluída com sucesso antes do encerramento
