export const MAX_PAYLOAD = 2 * 1024 * 1024;
export function send(ws, packet) {
  if (ws?.readyState !== 1) return false;
  if (ws.bufferedAmount > MAX_PAYLOAD) { ws.close(1013, 'Backpressure'); return false; }
  ws.send(JSON.stringify(packet)); return true;
}
export function assetPath(path) {
  return path === '/' || path === '/index.html' || path === '/classroom.html' || /^\/assets\/[A-Za-z0-9_.-]+$/.test(path);
}
export function socketPath(path) { return path === '/ws' || path === '/classroom/ws'; }

// Render generateValue emits standard Base64, including +, / and = padding.
// The value is an opaque HTTP header credential, not a URL parameter.
export function validateSecret(secret) {
  if (typeof secret !== 'string' || !/^[A-Za-z0-9_+\/=-]{32,128}$/.test(secret))
    throw new Error('CRAFTLINK_RELAY_SECRET must contain 32..128 Base64 or URL-safe characters');
}
