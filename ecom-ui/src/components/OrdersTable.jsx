import { date, label, money } from '../orders';
export function Status({ value }) {
  return <span className={`badge ${String(value).toLowerCase()}`}>{label(value)}</span>;
}
export default function OrdersTable({ orders, admin, empty = 'No orders to show.' }) {
  if (!orders.length)
    return (
      <div className="empty">
        <span className="empty-icon">▤</span>
        <h3>{empty}</h3>
        <p>Your orders will appear here once they are created.</p>
        <a className="button" href="#/orders/new">
          Create order
        </a>
      </div>
    );
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Order</th>
            {admin && <th>Customer</th>}
            <th>Placed on</th>
            <th>Status</th>
            <th className="numeric">Total</th>
            <th>
              <span className="sr-only">Details</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {orders.map((order) => (
            <tr key={order.id}>
              <td>
                <a className="order-link" href={`#/orders/${order.id}`}>
                  #{order.id}
                </a>
              </td>
              {admin && <td>{order.customerId}</td>}
              <td className="muted">{date(order.createdAt)}</td>
              <td>
                <Status value={order.status} />
              </td>
              <td className="numeric amount">{money(order.amount)}</td>
              <td>
                <a aria-label={`View order ${order.id}`} href={`#/orders/${order.id}`}>
                  View details <span aria-hidden="true">→</span>
                </a>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
