import { lazy, Suspense } from 'react';
import { useSession } from '../shared/auth/SessionProvider';
import SignInPage from '../shared/auth/SignInPage';
import { OrdersProvider } from '../shared/orders/OrdersProvider';
import { AccessDenied, Loading, NotFound } from '../shared/layout/RouteStates';
import { useRoute, Redirect } from './useRoute';
import { canEnterModule, canonicalRoute, defaultModule, moduleForRoute } from './routing';

const CustomerModule = lazy(() => import('../modules/customer/CustomerModule'));
const AdminModule = lazy(() => import('../modules/admin/AdminModule'));

export default function AppRouter() {
  const route = useRoute();
  const session = useSession();
  if (!session.signedIn)
    return <SignInPage login={session.login} register={session.register} error={session.error} />;
  if (session.loading) return <Loading />;
  if (!session.identity)
    return (
      <main className="content panel padded">
        <h1>We couldn’t load your workspace</h1>
        <p role="alert">{session.error}</p>
        <button onClick={session.retry}>Try again</button>
        <button className="secondary" onClick={session.logout}>
          Sign out
        </button>
      </main>
    );
  const { roles, subject } = session.identity;
  const defaultArea = defaultModule(roles);
  if (!defaultArea)
    return (
      <main className="content">
        <AccessDenied />
        <button onClick={session.logout}>Sign out</button>
      </main>
    );
  const canonical = canonicalRoute(route, roles);
  if (canonical !== route) return <Redirect to={canonical} />;
  const homePath = `/${defaultArea}/dashboard`;
  const module = moduleForRoute(route);
  if (!module)
    return (
      <main className="content">
        <NotFound homePath={homePath} />
      </main>
    );
  // Guard before mounting/importing a module or its order data provider.
  if (!canEnterModule(roles, module))
    return (
      <main className="content">
        <AccessDenied homePath={homePath} />
      </main>
    );
  return (
    <OrdersProvider key={`${subject}:${module}`}>
      <Suspense fallback={<Loading />}>
        {module === 'admin' ? <AdminModule route={route} /> : <CustomerModule route={route} />}
      </Suspense>
    </OrdersProvider>
  );
}
