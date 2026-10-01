import { money } from '../../../orders';
import { useOrders } from '../../../shared/orders/OrdersProvider';
export default function CustomersPage() {
  const { orders } = useOrders();
  const customers = Object.values(
    orders.reduce((all, o) => {
      all[o.customerId] ||= { username: o.customerId, count: 0, total: 0, pending: 0 };
      const c = all[o.customerId];
      c.count++;
      c.total += Number(o.amount);
      if (o.status === 'PENDING') c.pending++;
      return all;
    }, Object.create(null))
  ).sort((a, b) => b.total - a.total);
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">CUSTOMER ACTIVITY</p>
          <h1>Customers</h1>
          <p className="muted">
            Customers with orders in your workspace. Order values include all statuses.
          </p>
        </div>
        <a href="#/admin/orders/new" className="button">
          ＋ Create order
        </a>
      </div>
      <section className="panel">
        {customers.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Customer</th>
                  <th>Orders</th>
                  <th>Pending</th>
                  <th className="numeric">Order value</th>
                  <th>
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {customers.map((c) => (
                  <tr key={c.username}>
                    <td>
                      <strong>{c.username}</strong>
                    </td>
                    <td>{c.count}</td>
                    <td>{c.pending}</td>
                    <td className="numeric amount">{money(c.total)}</td>
                    <td>
                      <a href={`#/admin/orders?customer=${encodeURIComponent(c.username)}`}>
                        View orders →
                      </a>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <h2>No customer activity yet</h2>
            <p>Customers appear here after their first order.</p>
          </div>
        )}
      </section>
    </>
  );
}
