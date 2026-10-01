import CustomerLayout from './CustomerLayout';
import CustomerDashboardPage from './pages/CustomerDashboardPage';
import MyOrdersPage from './pages/MyOrdersPage';
import CreateOrderPage from './pages/CreateOrderPage';
import OrderDetailsPage from './pages/OrderDetailsPage';

import { useSession } from '../../shared/auth/SessionProvider';
import { AccessDenied, NotFound } from '../../shared/layout/RouteStates';

export default function CustomerModule({ route }) {
  const { identity } = useSession();
  const path = route.split('?')[0];
  const detail = path.match(/^\/customer\/orders\/(\d+)$/);
  let page;
  if (path === '/customer/dashboard') page = <CustomerDashboardPage />;
  else if (path === '/customer/orders') page = <MyOrdersPage />;
  else if (path === '/customer/orders/new')
    page = identity.permissions.includes('orders:create') ? (
      <CreateOrderPage />
    ) : (
      <AccessDenied homePath="/customer/dashboard" />
    );
  else if (detail) page = <OrderDetailsPage key={detail[1]} id={detail[1]} />;
  else page = <NotFound homePath="/customer/dashboard" />;
  return <CustomerLayout route={route}>{page}</CustomerLayout>;
}
