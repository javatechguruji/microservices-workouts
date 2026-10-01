import { useEffect, useState } from 'react';
import { request, money, date } from '../../orders';
import { Status } from './OrdersTable';
import { useOrders } from './OrdersProvider';

export default function OrderDetailsView({ id, ordersPath, children }) {
  const { upsertOrder } = useOrders();
  const [order, setOrder] = useState(null),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(''),
    [reload, setReload] = useState(0);
  function updateOrder(updated) {
    setOrder(updated);
    upsertOrder(updated);
  }
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    request(`/api/orders/${id}`)
      .then((value) => {
        if (active) {
          setOrder(value);
          upsertOrder(value);
        }
      })
      .catch((e) => {
        if (active) {
          setOrder(null);
          setError(e.message);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id, reload, upsertOrder]);
  return (
    <>
      <a href={`#${ordersPath}`} className="back-link">
        ← Back to orders
      </a>
      {loading && !order ? (
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
                    disabled={loading}
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
                </section>
                <aside className="stack">{children({ order, onChanged: updateOrder })}</aside>
              </div>
            </>
          )}
        </>
      )}
    </>
  );
}
