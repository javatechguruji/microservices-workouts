import { useEffect, useState } from 'react';
import { request, money, label } from '../../orders';
import { useSession } from '../auth/SessionProvider';
export default function FulfillmentPanel({ order, onChanged, children }) {
  const { identity } = useSession();
  const [data, setData] = useState(null),
    [error, setError] = useState(''),
    [busy, setBusy] = useState(false),
    [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true,
      timer;
    async function load() {
      try {
        const result = await request(`/api/orders/${order.id}/fulfillment`);
        if (!active) return;
        setData(result);
        setError('');
        if (result.status !== order.status) onChanged({ ...order, status: result.status });
        if (['CREATED', 'RESERVED', 'PAID', 'RELEASING'].includes(result.state))
          timer = setTimeout(load, 2000);
      } catch (e) {
        if (active) setError(e.message);
      }
    }
    load();
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [order.id, order.status, version]);
  async function advance(action) {
    setBusy(true);
    setError('');
    try {
      await request(`/api/orders/${order.id}/fulfillment/${action}`, 'POST', {});
      setVersion((v) => v + 1);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  if (!data)
    return (
      <div className="panel padded">
        {error ? (
          <>
            <p role="alert">{error}</p>
            <button onClick={() => setVersion((v) => v + 1)}>Retry</button>
          </>
        ) : (
          'Loading order items…'
        )}
      </div>
    );
  if (!data.items.length) return children;
  return (
    <section className="panel padded fulfillment">
      <h2>Your order items</h2>
      {data.items.map((i) => (
        <div className="order-line" key={i.sku}>
          <span>
            {i.name}
            <small>
              {i.quantity} × {money(i.unit_price)}
            </small>
          </span>
          <strong>{money(i.subtotal)}</strong>
        </div>
      ))}
      <h3>Delivery</h3>
      <p>{data.address}</p>
      <ol className="order-progress">
        {['CREATED', 'RESERVED', 'PAID', 'CONFIRMED', 'SHIPPED', 'DELIVERED'].map((s) => (
          <li className={s === data.state ? 'current' : ''} key={s}>
            {label(s)}
          </li>
        ))}
      </ol>
      <p role="status">
        {data.state === 'FAILED'
          ? 'Order could not be completed. No payment was taken.'
          : `Current step: ${label(data.state)}`}
      </p>
      {data.error && <p className="alert">{data.error}</p>}
      {data.tracking && (
        <p>
          Tracking: <strong>{data.tracking}</strong>
        </p>
      )}
      {error && <p role="alert">{error}</p>}
      {identity.roles.includes('admin') && identity.permissions.includes('orders:update') && (
        <div className="actions">
          {data.state === 'CONFIRMED' && (
            <button disabled={busy} onClick={() => advance('ship')}>
              Ship order
            </button>
          )}
          {data.state === 'SHIPPED' && (
            <button disabled={busy} onClick={() => advance('deliver')}>
              Mark delivered
            </button>
          )}
        </div>
      )}
      <button className="text-link" onClick={() => setVersion((v) => v + 1)}>
        Refresh fulfillment
      </button>
    </section>
  );
}
