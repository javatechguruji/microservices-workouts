import CustomerLayout from './CustomerLayout';
import ShopPage from './pages/ShopPage';
import CartPage from './pages/CartPage';
import ProfilePage from './pages/ProfilePage';
import NotificationsPage from './pages/NotificationsPage';
import { CartProvider } from './shopping/CartProvider';
import MyOrdersPage from './pages/MyOrdersPage';
import OrderDetailsPage from './pages/OrderDetailsPage';

import { useSession } from '../../shared/auth/SessionProvider';
import { AccessDenied, NotFound } from '../../shared/layout/RouteStates';

export default function CustomerModule({ route }) {
  const { identity } = useSession();
  const path = route.split('?')[0];
  const detail = path.match(/^\/customer\/orders\/(\d+)$/);
  let page;
  if (path === '/customer/dashboard') page = <ShopPage />;
  else if (path === '/customer/cart') page = <CartPage />;
  else if (path === '/customer/profile') page = <ProfilePage />;
  else if (path === '/customer/notifications') page = <NotificationsPage />;
  else if (path === '/customer/orders') page = <MyOrdersPage />;
  else if (path === '/customer/orders/new')
    page = identity.permissions.includes('orders:create') ? (
      <ShopPage />
    ) : (
      <AccessDenied homePath="/customer/dashboard" />
    );
  else if (detail) page = <OrderDetailsPage key={detail[1]} id={detail[1]} />;
  else page = <NotFound homePath="/customer/dashboard" />;
  return (
    <CartProvider>
      <CustomerLayout route={route}>{page}</CustomerLayout>
    </CartProvider>
  );
}
