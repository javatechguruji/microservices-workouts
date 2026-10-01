import { useState } from 'react';
import { useOrders } from '../../../shared/orders/OrdersProvider';
import OrderForm from '../../../shared/orders/OrderForm';

export default function CreateOrderPage() {
  const { orders, orderCreated } = useOrders();
  const [customer, setCustomer] = useState('');
  const suggestions = [...new Set(orders.map((order) => order.customerId))].sort();
  return (
    <>
      <a href="#/admin/orders" className="back-link">
        ← Back to orders
      </a>
      <div className="page-heading">
        <div>
          <p className="eyebrow">NEW ORDER</p>
          <h1>Create order for a customer</h1>
          <p className="muted">Choose a customer in your workspace and review their order.</p>
        </div>
      </div>
      <OrderForm
        customer={customer}
        ordersPath="/admin/orders"
        onCreated={(order) => orderCreated(order, '/admin/orders')}
      >
        <label>
          Customer username
          <input
            required
            maxLength={200}
            value={customer}
            onChange={(event) => setCustomer(event.target.value)}
            list="customers"
            autoComplete="off"
          />
        </label>
        <datalist id="customers">
          {suggestions.map((username) => (
            <option key={username} value={username} />
          ))}
        </datalist>
        <p className="hint">
          Enter an existing customer’s username in your workspace. Suggestions come from previous
          orders.
        </p>
      </OrderForm>
    </>
  );
}
