# 0004. Controlar as transições de status no domínio, com lock otimista

- Status: Aceito
- Data: 2026-10-27
- Change: `add-activation-requests`

## Contexto
Os estados são `PENDING → PROCESSING → COMPLETED | FAILED`. Com setters livres, qualquer código poderia, por exemplo, levar `COMPLETED` de volta a `PROCESSING`. Além disso, o worker do provedor e a reconciliação podem tentar alterar a mesma linha ao mesmo tempo.

## Decisão
- A entidade **não expõe `setStatus`**. Ela expõe intenções: `startProcessing()`, `complete()`, `fail()` e `returnToPending()`. O motivo da falha e o agendamento da próxima tentativa entram com a integração ao provedor (ADR 0007).
- As transições permitidas ficam no enum `ActivationStatus` (`canTransitionTo`). Uma transição inválida lança `InvalidStatusTransitionException`, o que é testável sem Spring.
- **Falha temporária volta para `PENDING`**, com `attempts++` e `nextAttemptAt`. `PROCESSING` significa apenas "há um envio em andamento agora". Assim o worker só busca `PENDING` vencidos, e uma linha presa em `PROCESSING` sempre indica uma instância que caiu, o que a reconciliação trata.
- **`@Version`** (lock otimista): se duas escritas concorrentes atingem a mesma linha, a segunda falha com `OptimisticLockException` em vez de sobrescrever a primeira.

| De \ Para | PENDING | PROCESSING | COMPLETED | FAILED |
|---|---|---|---|---|
| PENDING | — | ✅ | ❌ | ❌ |
| PROCESSING | ✅ (retry) | — | ✅ | ✅ |
| COMPLETED | ❌ | ❌ | — | ❌ |
| FAILED | ❌ | ❌ | ❌ | — |

## Alternativas consideradas
- **Permanecer em `PROCESSING` com contador**: o worker teria que distinguir "em andamento" de "aguardando retry", misturando dois significados no mesmo estado.
- **Spring State Machine**: desproporcional para 4 estados.
- **Lock pessimista em toda atualização**: seguraria conexões e locks durante a chamada remota.

## Consequências
- Estados terminais são imutáveis por construção.
- No worker, um conflito de versão é tratado como "outra instância já cuidou disso", e não como erro.
