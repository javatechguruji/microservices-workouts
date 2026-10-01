import { useEffect, useState } from 'react';
import { request, money, date, statuses, label } from '../orders';
import { Status } from '../components/OrdersTable';
export default function OrderDetails({ id, identity, admin, onChanged }) {
  const [order, setOrder] = useState(null),
    [error, setError] = useState(''),
    [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false),
    [status, setStatus] = useState(''),
    [notice, setNotice] = useState(''),
    [receipt, setReceipt] = useState(null),
    [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setOrder(null);
    setError('');
    request(`/api/orders/${id}`)
      .then((o) => {
        if (active) {
          setOrder(o);
          setStatus(o.status);
          onChanged(o);
        }
      })
      .catch((e) => {
        if (active) setError(e.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id, reload]);
  async function updateStatus(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const updated = await request(`/api/orders/${id}/status`, 'PATCH', { status });
      setOrder(updated);
      onChanged(updated);
      setNotice('Order status updated.');
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function pay() {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const payment = await request('/payments', 'POST', {
        orderId: order.id,
        amount: order.amount,
      });
      setReceipt(payment);
      setNotice(
        'Payment successful. Order confirmation may take a moment. Refresh the order to check its latest status.'
      );
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <a href="#/orders" className="back-link">
        ← Back to orders
      </a>
      {loading ? (
        <div className="panel padded" role="status">
          Loading order…
        </div>
      ) : (
        <>
          {error && (
            <div className="alert" role="alert">
              {error}
            </div>
          )}
          {notice && (
            <div className="notice" role="status">
              {notice}
            </div>
          )}
          {!order ? (
            <div className="panel padded">
              <h1>Order unavailable</h1>
              <p>Check the order number and that it belongs to your account or workspace.</p>
              <button onClick={() => setReload((n) => n + 1)}>Try again</button>
            </div>
          ) : (
            <>
              <div className="page-heading">
                <div>
                  <p className="eyebrow">ORDER DETAILS</p>
                  <h1>Order #{order.id}</h1>
                  <p className="muted">Placed on {date(order.createdAt)}</p>
                </div>
                <div className="actions">
                  <Status value={order.status} />
                  <button
                    className="secondary"
                    disabled={busy}
                    onClick={() => setReload((n) => n + 1)}
                  >
                    Refresh order
                  </button>
                </div>
              </div>
              <div className="form-grid">
                <section className="panel padded">
                  <h2>Order overview</h2>
                  <dl className="detail-list">
                    <div>
                      <dt>Order number</dt>
                      <dd>#{order.id}</dd>
                    </div>
                    <div>
                      <dt>Customer</dt>
                      <dd>{order.customerId}</dd>
                    </div>
                    <div>
                      <dt>Created</dt>
                      <dd>{date(order.createdAt)}</dd>
                    </div>
                    <div>
                      <dt>Status</dt>
                      <dd>
                        <Status value={order.status} />
                      </dd>
                    </div>
                    <div className="total">
                      <dt>Order total</dt>
                      <dd>{money(order.amount)}</dd>
                    </div>
                  </dl>
                  {receipt && (
                    <div className="receipt" role="status">
                      <h3>Payment received</h3>
                      <p>
                        Receipt #{receipt.id} · {money(receipt.amount)} · {date(receipt.createdAt)}
                      </p>
                    </div>
                  )}
                </section>
                <aside className="stack">
                  {identity.permissions.includes('payments:create') && (
                    <section className="panel padded">
                      <h2>Payment</h2>
                      <p className="muted">
                        {order.status === 'CONFIRMED'
                          ? 'This order is confirmed.'
                          : 'Complete the payment for this order.'}
                      </p>
                      <strong className="payment-total">{money(order.amount)}</strong>
                      <p className="hint">
                        Demo checkout — no card details or real money required.
                      </p>
                      <button
                        disabled={busy || !!receipt || order.status !== 'PENDING'}
                        onClick={pay}
                      >
                        {receipt ? 'Payment received' : 'Pay now'}
                      </button>
                    </section>
                  )}
                  {admin && identity.permissions.includes('orders:update') && (
                    <section className="panel padded">
                      <h2>Manage order</h2>
                      <form onSubmit={updateStatus}>
                        <label htmlFor="order-status">Order status</label>
                        <select
                          id="order-status"
                          value={status}
                          onChange={(e) => setStatus(e.target.value)}
                        >
                          {statuses.map((s) => (
                            <option key={s} value={s}>
                              {label(s)}
                            </option>
                          ))}
                        </select>
                        <button disabled={busy || status === order.status}>Update status</button>
                      </form>
                    </section>
                  )}
                </aside>
              </div>
            </>
          )}
        </>
      )}
    </>
  );
}
