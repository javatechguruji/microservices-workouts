import { useState } from 'react';
import { request, money, date } from '../../orders';
export default function OrderPayment({ order }) {
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [notice, setNotice] = useState(''),
    [receipt, setReceipt] = useState(null);
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
        <h2>Payment</h2>
        <p className="muted">
          {order.status === 'CONFIRMED'
            ? 'This order is confirmed.'
            : 'Complete the payment for this order.'}
        </p>
        <strong className="payment-total">{money(order.amount)}</strong>
        <p className="hint">Demo checkout — no card details or real money required.</p>
        <button disabled={busy || !!receipt || order.status !== 'PENDING'} onClick={pay}>
          {receipt ? 'Payment received' : 'Pay now'}
        </button>
      </section>
      {receipt && (
        <div className="receipt" role="status">
          <h3>Payment received</h3>
          <p>
            Receipt #{receipt.id} · {money(receipt.amount)} · {date(receipt.createdAt)}
          </p>
        </div>
      )}
    </>
  );
}
