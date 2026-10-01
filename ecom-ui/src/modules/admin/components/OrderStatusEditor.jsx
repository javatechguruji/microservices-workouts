import { useEffect, useState } from 'react';
import { request, statuses, label } from '../../../orders';
export default function OrderStatusEditor({ order, onChanged }) {
  const id = order.id;
  const [status, setStatus] = useState(order.status);
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [notice, setNotice] = useState('');
  useEffect(() => setStatus(order.status), [order.status]);
  async function updateStatus(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const updated = await request(`/api/orders/${id}/status`, 'PATCH', { status });
      onChanged(updated);
      setNotice('Order status updated.');
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
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
      )}{' '}
      <section className="panel padded">
        <h2>Manage order</h2>
        <form onSubmit={updateStatus}>
          <label htmlFor="order-status">Order status</label>
          <select id="order-status" value={status} onChange={(e) => setStatus(e.target.value)}>
            {statuses.map((s) => (
              <option key={s} value={s}>
                {label(s)}
              </option>
            ))}
          </select>
          <button disabled={busy || status === order.status}>Update status</button>
        </form>
      </section>
    </>
  );
}
