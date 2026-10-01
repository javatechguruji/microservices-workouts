// Routing policy is intentionally small and independent of React so it can be tested.
export function defaultModule(roles = []) {
  if (roles.includes('admin')) return 'admin';
  if (roles.includes('customer')) return 'customer';
  return null;
}
export function moduleForRoute(route) {
  const match = route.match(/^\/(customer|admin)(?:\/|\?|$)/);
  return match?.[1] || null;
}
export function canEnterModule(roles = [], module) {
  return ['customer', 'admin'].includes(module) && roles.includes(module);
}
export function canonicalRoute(route, roles) {
  const module = defaultModule(roles);
  if (!module) return route;
  if (route === '/' || route === '') return `/${module}/dashboard`;
  if (route === '/customer' || route === '/customer/') return '/customer/dashboard';
  if (route === '/admin' || route === '/admin/') return '/admin/dashboard';
  // Preserve earlier bookmarks while all new links use explicit module paths.
  if (/^\/orders(?:\/|\?|$)/.test(route)) return `/${module}${route}`;
  if (/^\/customers(?:\?|$)/.test(route)) return `/admin${route}`;
  return route;
}
