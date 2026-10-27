# Proposal

## Why

Os itens 10 e 11 do desafio são obrigatórios: responder o cenário de lentidão em produção e entregar um Dockerfile, explicando como a aplicação rodaria em produção. Para que o raciocínio do item 10 não fique só no papel, o serviço precisa expor o que permite separar API, banco e provedor: correlation id ponta a ponta, health checks e métricas por dependência. É também a hora de fechar o README com as seções obrigatórias.

## What Changes

- Correlation id propagado da requisição HTTP até o log do worker e o header enviado ao provedor (`X-Correlation-Id`). Ele é gravado na solicitação para que o worker continue com o mesmo id.
- Actuator: `health` com `liveness` e `readiness` (o readiness considera o banco), `info` e `prometheus`.
- Métricas:
  - latência da API (automática);
  - latência e resultado das chamadas ao provedor;
  - quantidade de solicitações por status;
  - pool de conexões.
- Logs estruturados em JSON no perfil `prod`, sem tokens nem credenciais.
- Dockerfile multi-stage, imagem final JRE não-root, memória da JVM ajustada ao contêiner, configuração por variáveis de ambiente, desligamento gracioso e `HEALTHCHECK`.
- `docker-compose.yml` completo: aplicação + PostgreSQL + mock do provedor, com um comando.
- README final: "Como executar", "Decisões técnicas", "Cenário de análise", "Produção", "Microsserviços e cloud", "Pontos pendentes", "Melhorias futuras", "Estratégia de testes" e "Uso de IA".

## Capabilities

### New Capabilities

- `operability`: sinais operacionais do serviço (saúde, correlação de logs e métricas) que permitem operar e diagnosticar em produção.

### Modified Capabilities

- `provider-activation`: o envio ao provedor passa a carregar o mesmo correlation id da requisição que criou a solicitação.

## Impact

- Dependência `micrometer-registry-prometheus`.
- Migration V5: coluna `correlation_id`.
- Novos arquivos `Dockerfile` e `.dockerignore`. `docker-compose.yml` e `README.md` evoluem.
- ADR 0010.
