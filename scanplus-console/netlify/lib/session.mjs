const encoder = new TextEncoder(), decoder = new TextDecoder();
export const sessionCookie = '__Host-scanplus_session';
export const sessionSeconds = 8 * 60 * 60;
const hexBytes = value => Uint8Array.from(value.match(/../g), pair => parseInt(pair, 16));
const encode = value => btoa(String.fromCharCode(...value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const decode = value => Uint8Array.from(atob(value.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));
export function publicLoginConfigured(getEnv) { return /^[a-f0-9]{64}$/.test(getEnv('SCANPLUS_LOGIN_KEY_HASH') || '') && /^[a-f0-9]{64}$/.test(getEnv('SCANPLUS_SESSION_SECRET') || ''); }
export function verifiedOwner(context, ownerId, now = Date.now()) {
  const expiry = new Date(context?.user?.expiresAt).getTime();
  return Boolean(ownerId && context?.user?.id === ownerId && Number.isFinite(expiry) && expiry > now);
}
async function signingKey(secret) { return crypto.subtle.importKey('raw', hexBytes(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign', 'verify']); }
export async function validAccessKey(value, expectedHash) {
  // The setup tool generates a random 256-bit key. This is deliberately not a
  // user-selected low-entropy password, which would require a slow password KDF.
  if (typeof value !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(value) || !/^[a-f0-9]{64}$/.test(expectedHash || '')) return false;
  const actual = new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(value))), expected = hexBytes(expectedHash);
  let difference = 0; for (let i = 0; i < actual.length; i++) difference |= actual[i] ^ expected[i];
  return difference === 0;
}
export async function issueSession(origin, secret, now = Date.now()) {
  const issued = Math.floor(now / 1000);
  const payload = encode(encoder.encode(JSON.stringify({ v: 1, sub: 'administrator', aud: origin, iat: issued, exp: issued + sessionSeconds, nonce: crypto.randomUUID() })));
  const signature = encode(new Uint8Array(await crypto.subtle.sign('HMAC', await signingKey(secret), encoder.encode(payload))));
  return `${sessionCookie}=${payload}.${signature}; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=${sessionSeconds}`;
}
export const clearSession = () => `${sessionCookie}=; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=0`;
export async function validSession(request, origin, secret, now = Date.now()) {
  if (!/^[a-f0-9]{64}$/.test(secret || '')) return false;
  const value = request.headers.get('Cookie')?.split(';').map(s => s.trim()).find(s => s.startsWith(`${sessionCookie}=`))?.slice(sessionCookie.length + 1);
  if (!value || value.length > 1024 || !/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(value)) return false;
  try {
    const [payload, signature] = value.split('.');
    if (!await crypto.subtle.verify('HMAC', await signingKey(secret), decode(signature), encoder.encode(payload))) return false;
    const claims = JSON.parse(decoder.decode(decode(payload))), seconds = Math.floor(now / 1000);
    return claims.v === 1 && claims.sub === 'administrator' && claims.aud === origin && typeof claims.nonce === 'string' && Number.isInteger(claims.iat) && Number.isInteger(claims.exp) && claims.iat <= seconds && claims.exp > seconds && claims.exp - claims.iat === sessionSeconds;
  } catch { return false; }
}
