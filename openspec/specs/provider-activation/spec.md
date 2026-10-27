# provider-activation Specification

## Purpose
Garante que toda solicitação de ativação registrada seja enviada ao provedor externo e termine em um estado final coerente com a resposta dele, tolerando lentidão, indisponibilidade e timeouts sem ativação duplicada.

## Requirements

### Requirement: Envio assíncrono ao provedor
A criação da solicitação MUST NOT aguardar o provedor externo. O sistema SHALL enviar ao provedor, em segundo plano, toda solicitação `PENDING` cuja próxima tentativa esteja vencida, movendo-a para `PROCESSING` durante o envio.

#### Scenario: Criação não depende do provedor
- **WHEN** o provedor externo está demorando 10 segundos para responder
- **AND** o cliente cria uma solicitação
- **THEN** a resposta `201` é devolvida sem esperar o provedor, com `status` `PENDING`

#### Scenario: Solicitação pendente é enviada
- **WHEN** existe uma solicitação `PENDING` com tentativa vencida
- **THEN** o sistema envia `POST /provider/v1/activate` com `customerId`, `productCode` e o `id` da solicitação

### Requirement: Entrega garantida após falha da instância
Uma solicitação registrada MUST ser enviada ao provedor mesmo que a instância pare antes ou durante o envio. Uma solicitação em `PROCESSING` há mais tempo que o limite configurado (padrão 5 minutos) SHALL voltar para `PENDING`, para ser reenviada.

#### Scenario: Instância cai durante o envio
- **WHEN** uma solicitação está em `PROCESSING` há mais tempo que o limite configurado
- **THEN** o sistema a devolve para `PENDING`
- **AND** ela é reenviada ao provedor com a mesma chave de idempotência

### Requirement: Envio exclusivo entre instâncias
Duas instâncias ou execuções concorrentes do processamento MUST NOT enviar a mesma solicitação ao mesmo tempo.

#### Scenario: Duas execuções simultâneas
- **WHEN** duas execuções do processamento rodam ao mesmo tempo sobre 20 solicitações `PENDING`
- **THEN** cada solicitação é enviada ao provedor exatamente uma vez

### Requirement: Chave de idempotência e correlação no envio
Toda chamada ao provedor MUST enviar o header `Idempotency-Key` com o `id` da solicitação, igual em todas as tentativas, e o header `X-Correlation-Id`.

#### Scenario: Retentativa após timeout reutiliza a chave
- **WHEN** a primeira chamada de uma solicitação expira por timeout e uma nova tentativa é feita
- **THEN** as duas chamadas recebidas pelo provedor têm o mesmo `Idempotency-Key`

### Requirement: Conclusão por sucesso do provedor
Uma resposta 2xx do provedor SHALL mover a solicitação para `COMPLETED`.

#### Scenario: Provedor responde 200
- **WHEN** o provedor responde `200` ao envio
- **THEN** a solicitação fica `COMPLETED`

### Requirement: Falha definitiva por erro de dados ou contrato
Respostas 4xx do provedor, exceto 408 e 429, SHALL mover a solicitação para `FAILED` sem nova tentativa, registrando um `failureReason` com o status recebido.

#### Scenario: Provedor responde 400
- **WHEN** o provedor responde `400`
- **THEN** a solicitação fica `FAILED` com `failureReason` `PROVIDER_REJECTED_400`
- **AND** nenhuma nova chamada é feita para ela

### Requirement: Nova tentativa em falha temporária
Respostas 5xx, 408, 429, timeout de leitura e erro de conexão SHALL devolver a solicitação para `PENDING`, com `attempts` incrementado e a próxima tentativa agendada por backoff exponencial com jitter, limitado a um teto configurável.

#### Scenario: Provedor responde 500
- **WHEN** o provedor responde `500` na primeira tentativa
- **THEN** a solicitação volta a `PENDING` com `attempts` 1 e a próxima tentativa agendada no futuro

#### Scenario: Timeout de leitura
- **WHEN** o provedor responde depois do timeout de leitura configurado
- **THEN** a solicitação volta a `PENDING` para nova tentativa

### Requirement: Respeitar Retry-After
Quando uma resposta 429 ou 503 traz `Retry-After`, a próxima tentativa SHALL ser agendada para depois desse prazo, limitado ao teto de backoff.

#### Scenario: 503 com Retry-After de 30 segundos
- **WHEN** o provedor responde `503` com `Retry-After: 30`
- **THEN** a próxima tentativa fica agendada para pelo menos 30 segundos depois

### Requirement: Limite de tentativas
Ao falhar temporariamente na última tentativa permitida (padrão 5), a solicitação SHALL ir para `FAILED` com `failureReason` `MAX_ATTEMPTS_EXCEEDED`.

#### Scenario: Esgota as tentativas
- **WHEN** o provedor responde `500` em todas as 5 tentativas
- **THEN** a solicitação fica `FAILED` com `failureReason` `MAX_ATTEMPTS_EXCEEDED`
- **AND** não é mais enviada

### Requirement: Timeouts configuráveis
Os timeouts de conexão e de leitura da chamada ao provedor MUST ser configuráveis, com padrões de 2 segundos para conexão e 5 segundos para leitura.

#### Scenario: Timeout de leitura respeitado
- **WHEN** o timeout de leitura está configurado em 1 segundo e o provedor responde em 3 segundos
- **THEN** a chamada é interrompida em cerca de 1 segundo e tratada como falha temporária
