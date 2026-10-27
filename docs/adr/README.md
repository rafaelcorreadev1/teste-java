# Architecture Decision Records

Registro das decisões de arquitetura do serviço de ativação, no formato [MADR](https://adr.github.io/madr/) em pt-BR.
Um ADR **Aceito** não é editado. Para mudar a decisão, crie um ADR novo que o substitua (`Substituído por NNNN`).

| ADR | Decisão | Status | Change OpenSpec |
|---|---|---|---|
| [0001](0001-stack-java21-spring-boot3.md) | Java 21 + Spring Boot 3.5 + Maven + PostgreSQL | Proposto | `bootstrap-project` |
| [0002](0002-migracoes-versionadas-flyway.md) | Schema versionado com Flyway | Proposto | `bootstrap-project` |
| [0003](0003-unicidade-externalid-constraint-banco.md) | Unicidade de `externalId` por constraint no banco + semântica de retry | Proposto | `add-activation-requests` |
| [0004](0004-maquina-de-estados-no-dominio.md) | Transições de status no domínio + lock otimista | Proposto | `add-activation-requests` |
| [0005](0005-erros-problem-details.md) | Erros em Problem Details (RFC 9457) com `traceId` | Proposto | `add-activation-requests` |
| [0006](0006-processamento-assincrono-tabela-como-outbox.md) | Processamento assíncrono usando a própria tabela como outbox | Proposto | `add-provider-activation` |
| [0007](0007-politica-de-retentativa-e-idempotencia-provedor.md) | Política de retentativa e chave de idempotência no provedor | Proposto | `add-provider-activation` |
| [0008](0008-seguranca-oauth2-resource-server.md) | OAuth 2.0 Client Credentials + JWT, escopos e isolamento por cliente | Proposto | `add-api-security` |
| [0009](0009-listagem-paginada-e-indices.md) | Listagem paginada e índices | Proposto | `add-request-listing` |
| [0010](0010-observabilidade-e-empacotamento.md) | Observabilidade e empacotamento em contêiner | Proposto | `add-operational-readiness` |

## Modelo

```markdown
# NNNN. Título no imperativo

- Status: Proposto | Aceito | Substituído por NNNN
- Data: AAAA-MM-DD
- Change: `nome-da-change`

## Contexto
Que força/problema obriga a decidir.

## Decisão
O que foi decidido, de forma direta.

## Alternativas consideradas
- Opção — por que não.

## Consequências
Positivas, negativas e o que fica mais difícil.
```
