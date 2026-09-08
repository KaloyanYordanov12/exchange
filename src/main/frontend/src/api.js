// Thin client over the real backend endpoints. Every value shown in the UI comes
// from these calls; nothing is fabricated. API keys live in memory only (this module
// variable) and are never written to storage.

const creds = {
  // Demo identities from the backend's application.properties. Editable at runtime
  // via setTraderKey / setAdminKey; held in memory only.
  traderKey: 'demo-alice-key',
  adminKey: 'demo-admin-key',
};

export function getTraderKey() {
  return creds.traderKey;
}
export function setTraderKey(key) {
  creds.traderKey = key;
}
export function getAdminKey() {
  return creds.adminKey;
}
export function setAdminKey(key) {
  creds.adminKey = key;
}

async function req(path, { method = 'GET', body, key, adminKey } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (key) headers['X-API-Key'] = key;
  if (adminKey) headers['X-Admin-Key'] = adminKey;
  const res = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }
  if (!res.ok) {
    const message = data && data.error ? data.error : `HTTP ${res.status}`;
    const err = new Error(message);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}

export const api = {
  pairs: () => req('/pairs'),
  book: (pair) => req(`/book?pair=${encodeURIComponent(pair)}`),
  candles: (pair, timeframe, from, to) => {
    const params = new URLSearchParams({ pair, timeframe });
    if (from != null) params.set('from', String(from));
    if (to != null) params.set('to', String(to));
    return req(`/candles?${params.toString()}`);
  },
  invariants: (pair) => req(`/invariants?pair=${encodeURIComponent(pair)}`),
  checkInvariants: (pair) => req(`/invariants/check?pair=${encodeURIComponent(pair)}`),
  account: () => req('/accounts/me', { key: creds.traderKey }),
  placeOrder: (order) =>
    req('/orders', { method: 'POST', body: order, key: creds.traderKey }),
  simulatorMetrics: (pair) =>
    req(`/admin/simulator/metrics?pair=${encodeURIComponent(pair)}`, {
      adminKey: creds.adminKey,
    }),
  startSimulator: (config) =>
    req('/admin/simulator/start', {
      method: 'POST',
      body: config,
      adminKey: creds.adminKey,
    }),
  stopSimulator: (pair) =>
    req(`/admin/simulator/stop?pair=${encodeURIComponent(pair)}`, {
      method: 'POST',
      adminKey: creds.adminKey,
    }),
};
