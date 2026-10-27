// Teste de fumaça contra o ambiente do docker compose (app + Postgres + mock do provedor + Keycloak).
// Uso: docker compose up -d --build --wait && node scripts/smoke-test.mjs
const API = process.env.API_URL ?? 'http://localhost:8080';
const KEYCLOAK = process.env.KEYCLOAK_URL ?? 'http://localhost:8180';
const PROVIDER_MOCK = process.env.PROVIDER_MOCK_URL ?? 'http://localhost:8089';
const RUN = Date.now().toString(36);

const results = [];
const check = (name, ok, detail = '') => {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${ok || !detail ? '' : `  -> ${detail}`}`);
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function token(client) {
  const res = await fetch(`${KEYCLOAK}/realms/activation/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'client_credentials', client_id: client, client_secret: `${client}-secret` }),
  });
  return (await res.json()).access_token;
}

async function call(method, path, { token: bearer, body, raw, headers = {} } = {}) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}),
      ...(body !== undefined || raw !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...headers,
    },
    body: raw ?? (body !== undefined ? JSON.stringify(body) : undefined),
  });
  const text = await res.text();
  let json;
  try { json = text ? JSON.parse(text) : undefined; } catch { json = undefined; }
  return { status: res.status, headers: res.headers, json, text };
}

const request = (externalId, productCode, customerId = 'CUST-1') => ({ externalId, customerId, productCode });

async function waitUntil(bearer, id, predicate, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  let last;
  while (Date.now() < deadline) {
    last = (await call('GET', `/api/v1/requests/${id}`, { token: bearer })).json;
    if (predicate(last)) return last;
    await sleep(1000);
  }
  return last;
}

const waitForStatus = (bearer, id, expected, timeoutMs) =>
  waitUntil(bearer, id, (r) => expected.includes(r?.status), timeoutMs);

const isProblem = (r, slug) => r.headers.get('content-type')?.startsWith('application/problem+json')
  && r.json?.type?.endsWith(`/problems/${slug}`) && !!r.json?.traceId;

// ---------------------------------------------------------------------------------------------
console.log(`\n# Saúde e autenticação`);
check('liveness público 200', (await call('GET', '/actuator/health/liveness')).status === 200);
check('readiness público 200', (await call('GET', '/actuator/health/readiness')).status === 200);
const noToken = await call('GET', '/api/v1/requests');
check('sem token → 401 Problem Details + WWW-Authenticate',
  noToken.status === 401 && isProblem(noToken, 'unauthorized') && /Bearer/.test(noToken.headers.get('www-authenticate') ?? ''));
const invalid = await call('GET', '/api/v1/requests', { token: 'abc.def.ghi' });
check('token inválido → 401', invalid.status === 401);

const [a, b, readOnly] = await Promise.all([token('sistema-a'), token('sistema-b'), token('sistema-leitura')]);
check('tokens obtidos no Keycloak', !!a && !!b && !!readOnly);

console.log(`\n# Criação, consulta e duplicidade`);
const ext = `EXT-${RUN}`;
const created = await call('POST', '/api/v1/requests', { token: a, body: request(ext, 'INTERNET-500'), headers: { 'X-Correlation-Id': `smoke-${RUN}` } });
check('POST válido → 201', created.status === 201, created.text);
check('Location aponta para o recurso', created.headers.get('location')?.endsWith(`/api/v1/requests/${created.json?.id}`));
check('X-Correlation-Id devolvido', created.headers.get('x-correlation-id') === `smoke-${RUN}`);
check('nasce PENDING com campos mínimos e datas UTC',
  created.json?.status === 'PENDING' && created.json?.attempts === 0 && created.json?.failureReason === null
  && /Z$/.test(created.json?.createdAt) && created.json?.createdAt === created.json?.updatedAt);
const id = created.json?.id;

const completed = await waitForStatus(a, id, ['COMPLETED', 'FAILED'], 15000);
check('worker ativa no provedor → COMPLETED', completed?.status === 'COMPLETED' && completed?.attempts === 1, JSON.stringify(completed));

const retry = await call('POST', '/api/v1/requests', { token: a, body: request(ext, 'INTERNET-500') });
check('retry com mesmo conteúdo → 200 com o mesmo id', retry.status === 200 && retry.json?.id === id, retry.text);
const conflict = await call('POST', '/api/v1/requests', { token: a, body: request(ext, 'INTERNET-1000') });
check('mesmo externalId com conteúdo diferente → 409', conflict.status === 409 && isProblem(conflict, 'duplicate-request'));

const parallelExt = `PAR-${RUN}`;
const parallel = await Promise.all(Array.from({ length: 10 }, () =>
  call('POST', '/api/v1/requests', { token: a, body: request(parallelExt, 'INTERNET-500') })));
const statuses = parallel.map((r) => r.status).sort();
check('10 POSTs idênticos em paralelo → um 201 e nove 200 com o mesmo id',
  statuses.filter((s) => s === 201).length === 1 && statuses.filter((s) => s === 200).length === 9
  && new Set(parallel.map((r) => r.json?.id)).size === 1, statuses.join(','));

console.log(`\n# Validação e erros`);
const missing = await call('POST', '/api/v1/requests', { token: a, body: { externalId: 'X1' } });
check('campos ausentes → 400 com errors por campo',
  missing.status === 400 && isProblem(missing, 'validation-error')
  && ['customerId', 'productCode'].every((f) => missing.json.errors.some((e) => e.field === f)));
const badChars = await call('POST', '/api/v1/requests', { token: a, body: request('EXT 1;DROP', 'P') });
check('caracteres inválidos → 400', badChars.status === 400 && badChars.json?.errors?.[0]?.field === 'externalId');
const tooLong = await call('POST', '/api/v1/requests', { token: a, body: request('E'.repeat(65), 'P') });
check('campo acima de 64 → 400', tooLong.status === 400);
const malformed = await call('POST', '/api/v1/requests', { token: a, raw: '{"externalId": "x",' });
check('JSON malformado → 400 malformed-request sem detalhes do parser',
  malformed.status === 400 && isProblem(malformed, 'malformed-request') && !/JSON parse error|Jackson/.test(malformed.text));
const big = await call('POST', '/api/v1/requests', { token: a, raw: JSON.stringify({ ...request('BIG', 'P'), pad: 'x'.repeat(20000) }) });
check('corpo de 20 KB → 413', big.status === 413 && isProblem(big, 'payload-too-large'));
const badId = await call('GET', '/api/v1/requests/abc', { token: a });
check('id malformado → 400', badId.status === 400);
const unknown = await call('GET', '/api/v1/requests/00000000-0000-0000-0000-000000000000', { token: a });
check('id inexistente → 404 not-found', unknown.status === 404 && isProblem(unknown, 'not-found'));
check('nenhuma resposta de erro expõe stack trace',
  [missing, malformed, big, badId, unknown, conflict].every((r) => !/at com\.|Exception|org\.springframework/.test(r.text)));

console.log(`\n# Autorização e isolamento entre clientes`);
const crossRead = await call('GET', `/api/v1/requests/${id}`, { token: b });
check('sistema-b não vê solicitação de sistema-a → 404', crossRead.status === 404);
const sameExtOtherClient = await call('POST', '/api/v1/requests', { token: b, body: request(ext, 'TV-HD') });
check('mesmo externalId em outro cliente → 201', sameExtOtherClient.status === 201);
const readOnlyPost = await call('POST', '/api/v1/requests', { token: readOnly, body: request(`RO-${RUN}`, 'P') });
check('cliente só-leitura não cria → 403', readOnlyPost.status === 403 && isProblem(readOnlyPost, 'forbidden'));
check('cliente só-leitura consulta → 200', (await call('GET', '/api/v1/requests', { token: readOnly })).status === 200);

console.log(`\n# Integração com o provedor`);
const rejected = await call('POST', '/api/v1/requests', { token: a, body: request(`REJ-${RUN}`, 'X-REJECT') });
const rejectedFinal = await waitForStatus(a, rejected.json.id, ['FAILED', 'COMPLETED'], 15000);
check('400 do provedor → FAILED PROVIDER_REJECTED_400 sem retry',
  rejectedFinal?.status === 'FAILED' && rejectedFinal?.failureReason === 'PROVIDER_REJECTED_400' && rejectedFinal?.attempts === 1,
  JSON.stringify(rejectedFinal));
const slow = await call('POST', '/api/v1/requests', { token: a, body: request(`SLOW-${RUN}`, 'X-SLOW') });
const erroring = await call('POST', '/api/v1/requests', { token: a, body: request(`ERR-${RUN}`, 'X-ERROR') });
const slowNow = await waitUntil(a, slow.json.id, (r) => r?.attempts >= 2, 30000);
check('timeout de leitura é tratado como falha temporária e repetido',
  slowNow?.attempts >= 2 && slowNow?.failureReason === null && slowNow?.status !== 'COMPLETED', JSON.stringify(slowNow));
const erroringFinal = await waitForStatus(a, erroring.json.id, ['FAILED'], 90000);
check('500 repetido → 5 tentativas com backoff e FAILED MAX_ATTEMPTS_EXCEEDED',
  erroringFinal?.status === 'FAILED' && erroringFinal?.failureReason === 'MAX_ATTEMPTS_EXCEEDED' && erroringFinal?.attempts === 5,
  JSON.stringify(erroringFinal));

const journal = await (await fetch(`${PROVIDER_MOCK}/__admin/requests?limit=500`)).json();
const callsFor = (requestId) => journal.requests.filter((r) => r.request.headers?.['Idempotency-Key'] === requestId);
const errorCalls = callsFor(erroring.json.id);
check('todas as tentativas usaram a mesma Idempotency-Key', errorCalls.length === 5, `${errorCalls.length} chamadas`);
check('correlation id da criação chegou ao provedor',
  callsFor(id).some((r) => r.request.headers?.['X-Correlation-Id'] === `smoke-${RUN}`));
check('credencial do provedor enviada', callsFor(id).every((r) => !!r.request.headers?.['X-Api-Key']));

console.log(`\n# Listagem`);
const failedList = await call('GET', '/api/v1/requests?status=FAILED&size=100', { token: a });
check('filtro por status', failedList.status === 200 && failedList.json.content.length >= 2
  && failedList.json.content.every((r) => r.status === 'FAILED'));
const page = await call('GET', '/api/v1/requests?page=0&size=2', { token: a });
check('paginação com metadados', page.json?.content?.length === 2 && page.json.size === 2 && page.json.totalElements >= 5);
const dates = page.json.content.map((r) => r.createdAt);
check('ordenação do mais recente para o mais antigo', dates[0] >= dates[1]);
check('size acima de 100 → 400', (await call('GET', '/api/v1/requests?size=1000', { token: a })).status === 400);
check('status desconhecido → 400', (await call('GET', '/api/v1/requests?status=DONE', { token: a })).status === 400);
const bList = await call('GET', '/api/v1/requests?size=100', { token: b });
check('listagem do sistema-b não contém dados do sistema-a', bList.json.content.every((r) => r.id !== id));

console.log(`\n# Observabilidade`);
check('prometheus exige token', (await call('GET', '/actuator/prometheus')).status === 401);
const metrics = (await call('GET', '/actuator/prometheus', { token: a })).text;
check('métricas do provedor por resultado',
  /activation_provider_call_seconds_count\{[^}]*outcome="success"/.test(metrics)
  && /outcome="transient_failure"/.test(metrics) && /outcome="permanent_failure"/.test(metrics));
check('métricas de API e pool expostas', /http_server_requests_seconds_bucket/.test(metrics) && /hikaricp_connections_pending/.test(metrics));

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} verificações passaram.`);
process.exit(failed.length ? 1 : 0);
