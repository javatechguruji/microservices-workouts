// Deliberately limited to this origin's application paths: no arbitrary token destinations.
export function validateRequest(path, method, body) {
  if (
    !/^\/(api\/|payments(?:\/|$)|notifications(?:\/|$))/.test(path) ||
    /[\\\s#]/.test(path) ||
    /%2f|%5c|%2e/i.test(path) ||
    path.split(/[/?]/).includes('..')
  ) {
    throw new Error('Use a gateway path starting with /api/, /payments or /notifications.');
  }
  if (!['GET', 'POST', 'PATCH'].includes(method)) throw new Error('Unsupported HTTP method.');
  if (method !== 'GET') JSON.parse(body);
}

export async function gatewayRequest({
  path,
  method = 'GET',
  body = '',
  spoof = false,
  getToken,
  fetcher = fetch,
}) {
  validateRequest(path, method, body);
  const token = await getToken();
  const headers = { Authorization: `Bearer ${token}` };
  if (spoof)
    Object.assign(headers, {
      'X-Auth-Subject': 'forged',
      'X-Auth-Username': 'admin1',
      'X-Auth-Roles': 'admin',
      'X-Auth-Tenant': 'other',
      'X-Auth-Permissions': 'orders:read:any,inventory:read',
    });
  if (method !== 'GET') headers['Content-Type'] = 'application/json';
  const response = await fetcher(path, {
    method,
    headers,
    ...(method !== 'GET' ? { body } : {}),
    redirect: 'error',
    signal: AbortSignal.timeout(15000),
  });
  const text = await response.text();
  let data = text;
  try {
    data = JSON.parse(text);
  } catch {
    /* Preserve text error responses. */
  }
  return { status: response.status, ok: response.ok, path, method, data };
}
