import { useSession } from '../../../shared/auth/SessionProvider';
import OrderDetailsView from '../../../shared/orders/OrderDetailsView';
import OrderPayment from '../../../shared/orders/OrderPayment';
import OrderStatusEditor from '../components/OrderStatusEditor';

export default function OrderDetailsPage({ id }) {
  const { identity } = useSession();
  return (
    <OrderDetailsView id={id} ordersPath="/admin/orders">
      {({ order, onChanged }) => (
        <>
          {identity.permissions.includes('payments:create') && <OrderPayment order={order} />}
          {identity.permissions.includes('orders:update') && (
            <OrderStatusEditor order={order} onChanged={onChanged} />
          )}
        </>
      )}
    </OrderDetailsView>
  );
}
