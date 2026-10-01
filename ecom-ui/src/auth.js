import Keycloak from 'keycloak-js';

export const auth = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8180',
  realm: import.meta.env.VITE_KEYCLOAK_REALM || 'ecommerce',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'security-demo-ui',
});

export const redirectUri = `${window.location.origin}/`;
// Initialize once, outside React effects. Tokens stay in adapter memory.
export async function initializeAuth() {
  // Preserve only the requested screen across the SSO round trip, never tokens.
  if (window.location.hash.startsWith('#/'))
    sessionStorage.setItem('ecom:returnTo', window.location.hash);
  const authenticated = await auth.init({
    onLoad: 'check-sso',
    pkceMethod: 'S256',
    responseMode: 'query',
    checkLoginIframe: false,
    redirectUri,
  });
  const route = sessionStorage.getItem('ecom:returnTo');
  sessionStorage.removeItem('ecom:returnTo');
  if (authenticated && route?.startsWith('#/')) window.location.hash = route;
  return authenticated;
}

export async function accessToken() {
  if (!auth.authenticated) throw new Error('Sign in with Keycloak first.');
  try {
    await auth.updateToken(30);
    if (!auth.token) throw new Error('No access token');
    return auth.token;
  } catch {
    auth.clearToken();
    throw new Error('Your session has expired. Sign in with Keycloak again.');
  }
}
