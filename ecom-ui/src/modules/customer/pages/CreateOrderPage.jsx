import { useSession } from '../../../shared/auth/SessionProvider';
import { useOrders } from '../../../shared/orders/OrdersProvider';
import OrderForm from '../../../shared/orders/OrderForm';

export default function CreateOrderPage() {
  const { identity } = useSession();
  const { orderCreated } = useOrders();
  return (
    <>
      <a href="#/customer/orders" className="back-link">
        ← Back to orders
      </a>
      <div className="page-heading">
        <div>
          <p className="eyebrow">NEW ORDER</p>
          <h1>Create an order</h1>
          <p className="muted">Review the details below and place your order.</p>
        </div>
      </div>
      <OrderForm
        customer={identity.username}
        ordersPath="/customer/orders"
        onCreated={(order) => orderCreated(order, '/customer/orders')}
      >
        <label>
          Customer username
          <input value={identity.username} readOnly />
        </label>
        <p className="hint">This order will be placed under your account.</p>
      </OrderForm>
    </>
  );
}
