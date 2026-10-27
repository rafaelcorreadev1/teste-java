# 0006. Processar a ativação de forma assíncrona, usando a própria tabela como outbox

- Status: Proposto
- Data: 2026-10-27
- Change: `add-provider-activation`

## Contexto
O provedor externo pode ficar lento (o cenário de análise mostra 400 ms → 3 s) ou indisponível. Se o `POST /api/v1/requests` esperar o provedor, há três problemas:
- a latência da API passa a ser a do provedor, e as threads do Tomcat ficam presas;
- com a chamada **dentro** da transação, a conexão do pool fica presa durante o I/O remoto e o pool esgota;
- com a chamada **depois** do commit, se a instância cair entre os dois a solicitação fica gravada e nunca é enviada.

## Decisão
- O `POST` grava a solicitação como `PENDING` e responde `201` imediatamente. **Nenhuma chamada HTTP acontece dentro de uma transação.**
- A linha `PENDING` **é** a mensagem da outbox. Como ela é gravada no mesmo commit da solicitação, o envio não tem como se perder.
- Um worker agendado (`@Scheduled`, com intervalo configurável) faz três passos:
  1. **Reivindica** um lote numa transação curta: `SELECT ... WHERE status = 'PENDING' AND next_attempt_at <= now() ORDER BY next_attempt_at LIMIT :n FOR UPDATE SKIP LOCKED`, e muda as linhas para `PROCESSING` com `processing_started_at = now()`. Com `SKIP LOCKED`, várias instâncias dividem o trabalho sem pegar a mesma linha.
  2. Chama o provedor **fora** da transação.
  3. Grava o resultado em outra transação curta (ADR 0007).
- **Reconciliação**: linhas em `PROCESSING` há mais tempo que um *lease* configurável (5 min) voltam para `PENDING`. Isso cobre a instância que caiu no meio do envio, e o reenvio é seguro por causa da chave de idempotência (ADR 0007).

## Alternativas consideradas
- **Síncrono**: o cliente veria o resultado na hora, mas herdaria toda a lentidão do provedor e o risco de perda descrito acima.
- **`@Async` / `@TransactionalEventListener(AFTER_COMMIT)`**: é rápido, mas se a JVM cair após o commit o evento em memória se perde. Ainda exigiria uma varredura de segurança, que na prática é este mesmo design.
- **Tabela outbox separada + broker (Kafka/RabbitMQ)**: é o caminho natural em escala e para notificar outros microsserviços, mas a infraestrutura é desproporcional ao desafio. Fica descrito no README como evolução.

## Consequências
- O cliente acompanha o status via `GET` (*polling*). O `201` significa "aceito e registrado", não "ativado".
- A latência da ativação ganha o intervalo do worker (segundos), o que é aceitável para ativação de produto.
- O banco faz papel de fila. Isso funciona bem até milhares de mensagens por minuto; acima disso, migrar para um broker.
