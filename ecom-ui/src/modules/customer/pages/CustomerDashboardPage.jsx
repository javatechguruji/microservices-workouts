import OrdersTable from '../../../shared/orders/OrdersTable';
import { money, newest } from '../../../orders';
import { useSession } from '../../../shared/auth/SessionProvider';
import { useOrders } from '../../../shared/orders/OrdersProvider';
export default function CustomerDashboardPage() {
  const { identity } = useSession();
  const { orders } = useOrders();
  const pending = orders.filter((o) => o.status === 'PENDING').length;
  const confirmed = orders.filter((o) => o.status === 'CONFIRMED').length;
  const failed = orders.filter((o) => o.status === 'FAILED').length;
  const total = orders.reduce((sum, o) => sum + Number(o.amount), 0);
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">YOUR ACCOUNT</p>
          <h1>{`Welcome back, ${identity.username}`}</h1>
          <p className="muted">Track your orders and manage everything from one place.</p>
        </div>
        <a href="#/customer/orders/new" className="button">
          ＋ Create order
        </a>
      </div>
      <div className="stats">
        <article>
          <span>My orders</span>
          <strong>{orders.length}</strong>
          <small>Orders placed with us</small>
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
            <a href="#/customer/orders">View all orders →</a>
          </div>
          <OrdersTable orders={newest(orders).slice(0, 5)} ordersPath="/customer/orders" />
        </section>
        <aside className="panel summary-panel">
          <span className="eyebrow">AT A GLANCE</span>
          <h2>Order summary</h2>
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
          </dl>
          <div className="tip">
            <h3>{pending ? 'A little follow-up goes a long way.' : 'You’re all caught up.'}</h3>
            <p>Open an order to review its details or make a payment.</p>
            <a href="#/customer/orders">{pending ? 'Review orders' : 'Browse orders'} →</a>
          </div>
        </aside>
      </div>
    </>
  );
}
