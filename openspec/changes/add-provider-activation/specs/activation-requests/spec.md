# Spec Delta

## ADDED Requirements

### Requirement: Expor tentativas e motivo de falha
A representação da solicitação SHALL incluir `attempts` (quantidade de envios ao provedor) e `failureReason` (nulo, exceto quando o status é `FAILED`).

#### Scenario: Solicitação que falhou
- **WHEN** o cliente consulta uma solicitação `FAILED` por rejeição do provedor
- **THEN** a resposta inclui `failureReason` `PROVIDER_REJECTED_400` e `attempts` 1

#### Scenario: Solicitação recém-criada
- **WHEN** o cliente cria uma solicitação
- **THEN** a resposta inclui `attempts` 0 e `failureReason` nulo
