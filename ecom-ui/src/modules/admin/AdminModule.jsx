import AdminLayout from './AdminLayout';
import AdminDashboardPage from './pages/AdminDashboardPage';
import AllOrdersPage from './pages/AllOrdersPage';
import CreateOrderPage from './pages/CreateOrderPage';
import OrderDetailsPage from './pages/OrderDetailsPage';
import CustomersPage from './pages/CustomersPage';
import { useSession } from '../../shared/auth/SessionProvider';
import { AccessDenied, NotFound } from '../../shared/layout/RouteStates';

export default function AdminModule({ route }) {
  const { identity } = useSession();
  const path = route.split('?')[0];
  const detail = path.match(/^\/admin\/orders\/(\d+)$/);
  let page;
  if (path === '/admin/dashboard') page = <AdminDashboardPage />;
  else if (path === '/admin/orders') page = <AllOrdersPage route={route} />;
  else if (path === '/admin/orders/new')
    page = identity.permissions.includes('orders:create') ? (
      <CreateOrderPage />
    ) : (
      <AccessDenied homePath="/admin/dashboard" />
    );
  else if (detail) page = <OrderDetailsPage key={detail[1]} id={detail[1]} />;
  else if (path === '/admin/customers') page = <CustomersPage />;
  else page = <NotFound homePath="/admin/dashboard" />;
  return <AdminLayout route={route}>{page}</AdminLayout>;
}
