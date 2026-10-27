# 0005. Padronizar erros com Problem Details (RFC 9457)

- Status: Aceito
- Data: 2026-10-27
- Change: `add-activation-requests`

## Contexto
A API é consumida por outros sistemas, que precisam de um formato de erro previsível e legível por máquina. Stack traces e detalhes internos não podem vazar.

## Decisão
- Um `@RestControllerAdvice` estende `ResponseEntityExceptionHandler` e devolve `ProblemDetail` (`application/problem+json`).
- Cada resposta traz `type` estável (`https://api.deroyque.com/problems/<slug>`), `title`, `status`, `detail`, `instance` e `traceId`. Erros de validação trazem também `errors[]` com `field` e `message`.

| Situação | Status | type |
|---|---|---|
| Campo obrigatório ausente / formato inválido | 400 | `validation-error` |
| JSON malformado | 400 | `malformed-request` |
| Registro inexistente | 404 | `not-found` |
| `externalId` repetido com conteúdo divergente | 409 | `duplicate-request` |
| Conflito de versão (lock otimista) | 409 | `concurrent-update` |
| Erro inesperado | 500 | `internal-error` (mensagem genérica; stack só no log) |

- O `traceId` é o mesmo correlation id gravado no MDC e nos logs, então o suporte encontra o erro a partir da resposta.
- O **erro de integração com o provedor não chega ao cliente da API**, porque o processamento é assíncrono (ADR 0006). Ele aparece como status `FAILED` com `failureReason`.

## Alternativas consideradas
- **Formato próprio `{code, message}`**: reinventaria um padrão que o Spring 6 já suporta nativamente.

## Consequências
- O contrato de erro fica documentado e coberto por testes, que verificam `type` e `status`.
