import OrderList from '../../../shared/orders/OrderList';
import { useOrders } from '../../../shared/orders/OrdersProvider';
export default function AllOrdersPage({ route }) {
  const { orders } = useOrders();
  const customer = new URLSearchParams(route.split('?')[1] || '').get('customer') || '';
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">ORDER MANAGEMENT</p>
          <h1>All orders</h1>
          <p className="muted">View and manage every customer order in your workspace.</p>
        </div>
        <a className="button" href="#/admin/orders/new">
          ＋ Create order
        </a>
      </div>
      <OrderList
        orders={orders}
        ordersPath="/admin/orders"
        showCustomer
        customer={customer}
        key={customer}
      />
    </>
  );
}
