# 0003. Garantir a unicidade de externalId por constraint no banco

- Status: Aceito
- Data: 2026-10-27
- Change: `add-activation-requests`

## Contexto
Sistemas de origem fazem retry, e duas requisições com o mesmo `externalId` podem chegar quase juntas, inclusive em instâncias diferentes da aplicação.

O padrão "consultar se existe e, se não existir, inserir" (*check-then-act*) tem uma janela de corrida: as duas transações consultam, nenhuma encontra e as duas inserem. Isso acontece mesmo com `@Transactional`, porque em READ COMMITTED uma transação não enxerga a linha que a outra ainda não commitou.

## Decisão
1. **Constraint `UNIQUE`** no PostgreSQL. É a única barreira que vale para N instâncias e N threads.
2. O serviço tenta inserir. Se o banco rejeitar por violação **dessa** constraint, a transação de inserção é descartada, o registro vencedor é lido em uma nova leitura e a resposta segue esta semântica:

| Cenário | Resposta | Por quê |
|---|---|---|
| `externalId` novo | `201 Created` + `Location` | Criação normal. |
| Mesmo `externalId`, **mesmo conteúdo** | `200 OK` com o registro existente | É um retry legítimo: a operação é idempotente. Um `409` faria a origem tratar como erro algo que já deu certo. |
| Mesmo `externalId`, **conteúdo diferente** | `409 Conflict` (Problem Details) | É conflito real de dados e a origem precisa corrigir. Sobrescrever perderia a solicitação original. |
| Duas idênticas em paralelo | Uma recebe `201`, a outra `200` | A perdedora cai no caminho do retry legítimo. |

"Mesmo conteúdo" significa `customerId` e `productCode` iguais.

## Alternativas consideradas
- **Check-then-act**: tem a corrida descrita acima. Pode servir como *fast path*, mas nunca como garantia.
- **`synchronized` ou lock em memória**: não funciona com várias instâncias.
- **Lock pessimista / `SERIALIZABLE`**: não há linha para travar antes do insert, e `SERIALIZABLE` aumenta abortos.
- **`INSERT ... ON CONFLICT DO NOTHING`**: elegante e sem exceção, mas foge do JPA e prende o código ao dialeto. É uma boa evolução se esse caminho virar gargalo.
- **Lock distribuído (Redis)**: adiciona infraestrutura para algo que o banco já resolve.

## Consequências
- A violação de integridade precisa ser tratada **fora** da transação que falhou, porque depois dela a transação fica marcada para rollback.
- É necessário um teste concorrente contra Postgres real (Testcontainers) para provar a garantia.
