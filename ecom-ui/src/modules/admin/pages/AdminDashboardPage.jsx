import OrdersTable from '../../../shared/orders/OrdersTable';
import { money, newest } from '../../../orders';
import { useOrders } from '../../../shared/orders/OrdersProvider';
export default function AdminDashboardPage() {
  const { orders } = useOrders();
  const pending = orders.filter((o) => o.status === 'PENDING').length;
  const confirmed = orders.filter((o) => o.status === 'CONFIRMED').length;
  const failed = orders.filter((o) => o.status === 'FAILED').length;
  const total = orders.reduce((sum, o) => sum + Number(o.amount), 0);
  const customers = new Set(orders.map((o) => o.customerId)).size;
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">BUSINESS OVERVIEW</p>
          <h1>Admin dashboard</h1>
          <p className="muted">Keep an eye on customer orders and what needs your attention.</p>
        </div>
        <a href="#/admin/orders/new" className="button">
          ＋ Create order
        </a>
      </div>
      <div className="stats">
        <article>
          <span>All orders</span>
          <strong>{orders.length}</strong>
          <small>Across customers in your workspace</small>
        </article>
        <article>
          <span>Awaiting confirmation</span>
          <strong>{pending}</strong>
          <small>Pending orders</small>
        </article>
        <article>
          <span>Confirmed orders</span>
          <strong>{confirmed}</strong>
          <small>Ready for the next step</small>
        </article>
        <article>
          <span>Total order value</span>
          <strong>{money(total)}</strong>
          <small>All statuses · USD</small>
        </article>
      </div>
      <div className="dashboard-grid">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <h2>Recent orders</h2>
              <p className="muted">Your latest activity, all in one place.</p>
            </div>
            <a href="#/admin/orders">View all orders →</a>
          </div>
          <OrdersTable
            orders={newest(orders).slice(0, 5)}
            ordersPath="/admin/orders"
            showCustomer
          />
        </section>
        <aside className="panel summary-panel">
          <span className="eyebrow">AT A GLANCE</span>
          <h2>Workspace summary</h2>
          <dl className="summary-list">
            <div>
              <dt>Pending</dt>
              <dd>{pending}</dd>
            </div>
            <div>
              <dt>Confirmed</dt>
              <dd>{confirmed}</dd>
            </div>
            <div>
              <dt>Failed</dt>
              <dd>{failed}</dd>
            </div>
            <div>
              <dt>Customers with orders</dt>
              <dd>{customers}</dd>
            </div>
          </dl>
          <div className="tip">
            <h3>{pending ? 'A little follow-up goes a long way.' : 'You’re all caught up.'}</h3>
            <p>Review pending orders, update their status, or create a new order for a customer.</p>
            <a href="#/admin/orders">{pending ? 'Review orders' : 'Browse orders'} →</a>
          </div>
        </aside>
      </div>
    </>
  );
}
