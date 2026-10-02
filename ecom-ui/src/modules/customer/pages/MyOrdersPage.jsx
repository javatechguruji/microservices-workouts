import OrderList from '../../../shared/orders/OrderList';
import { useOrders } from '../../../shared/orders/OrdersProvider';
export default function MyOrdersPage() {
  const { orders } = useOrders();

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">ORDER MANAGEMENT</p>
          <h1>My orders</h1>
          <p className="muted">Find an order, check its status, and see the details.</p>
        </div>
        <a className="button" href="#/customer/dashboard">
          ＋ Shop products
        </a>
      </div>
      <OrderList orders={orders} ordersPath="/customer/orders" />
    </>
  );
}
