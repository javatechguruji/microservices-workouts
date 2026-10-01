import { useSession } from '../../../shared/auth/SessionProvider';
import OrderDetailsView from '../../../shared/orders/OrderDetailsView';
import OrderPayment from '../../../shared/orders/OrderPayment';

export default function OrderDetailsPage({ id }) {
  const { identity } = useSession();
  return (
    <OrderDetailsView id={id} ordersPath="/customer/orders">
      {({ order }) =>
        identity.permissions.includes('payments:create') && <OrderPayment order={order} />
      }
    </OrderDetailsView>
  );
}
