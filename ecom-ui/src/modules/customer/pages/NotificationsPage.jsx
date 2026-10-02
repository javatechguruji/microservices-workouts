import { useEffect, useState } from 'react';
import { request, date, label } from '../../../orders';
export default function NotificationsPage() {
  const [rows, setRows] = useState([]),
    [error, setError] = useState('');
  useEffect(() => {
    request('/notifications/me')
      .then(setRows)
      .catch((e) => setError(e.message));
  }, []);
  return (
    <>
      <h1>Your updates</h1>
      {error && <p role="alert">{error}</p>}
      <section className="panel padded">
        {rows.length ? (
          rows.map((r, i) => (
            <p key={i}>
              <a href={`#/customer/orders/${r.order_id}`}>Order #{r.order_id}</a> ·{' '}
              {label(r.status)} <small>{date(r.created_at)}</small>
            </p>
          ))
        ) : (
          <p>Order updates will appear here after checkout and shipping.</p>
        )}
      </section>
    </>
  );
}
