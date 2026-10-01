import OrdersTable from '../components/OrdersTable';
import { money, newest } from '../orders';
export default function Dashboard({ identity, orders, admin }) {
  const pending = orders.filter((o) => o.status === 'PENDING').length;
  const confirmed = orders.filter((o) => o.status === 'CONFIRMED').length;
  const failed = orders.filter((o) => o.status === 'FAILED').length;
  const total = orders.reduce((sum, o) => sum + Number(o.amount), 0);
  const customers = new Set(orders.map((o) => o.customerId)).size;
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">{admin ? 'BUSINESS OVERVIEW' : 'YOUR ACCOUNT'}</p>
          <h1>{admin ? 'Admin dashboard' : `Welcome back, ${identity.username}`}</h1>
          <p className="muted">
            {admin
              ? 'Keep an eye on customer orders and what needs your attention.'
              : 'Track your orders and manage everything from one place.'}
          </p>
        </div>
        <a href="#/orders/new" className="button">
          ＋ Create order
        </a>
      </div>
      <div className="stats">
        <article>
          <span>{admin ? 'All orders' : 'My orders'}</span>
          <strong>{orders.length}</strong>
          <small>{admin ? 'Across customers in your workspace' : 'Orders placed with us'}</small>
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
            <a href="#/orders">View all orders →</a>
          </div>
          <OrdersTable orders={newest(orders).slice(0, 5)} admin={admin} />
        </section>
        <aside className="panel summary-panel">
          <span className="eyebrow">AT A GLANCE</span>
          <h2>{admin ? 'Workspace summary' : 'Order summary'}</h2>
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
            {admin && (
              <div>
                <dt>Customers with orders</dt>
                <dd>{customers}</dd>
              </div>
            )}
          </dl>
          <div className="tip">
            <h3>{pending ? 'A little follow-up goes a long way.' : 'You’re all caught up.'}</h3>
            <p>
              {admin
                ? 'Review pending orders, update their status, or create a new order for a customer.'
                : 'Open an order to review its details or make a payment.'}
            </p>
            <a href="#/orders">{pending ? 'Review orders' : 'Browse orders'} →</a>
          </div>
        </aside>
      </div>
    </>
  );
}
