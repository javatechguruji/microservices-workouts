import { useState } from 'react';
import { request, money } from '../../orders';
export default function OrderForm({ customer, ordersPath, onCreated, children }) {
  const [amount, setAmount] = useState(''),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  async function submit(e) {
    e.preventDefault();
    setError('');
    if (
      !customer.trim() ||
      !/^\d+(\.\d{1,2})?$/.test(amount) ||
      Number(amount) <= 0 ||
      Number(amount) > 99999999.99
    ) {
      setError(
        'Enter a customer username and an amount between $0.01 and $99,999,999.99, with up to two decimal places.'
      );
      return;
    }
    setBusy(true);
    try {
      const order = await request('/api/orders', 'POST', {
        customerId: customer.trim(),
        amount: Number(amount),
      });
      onCreated(order);
    } catch (e) {
      setError(e.message);
      setBusy(false);
    }
  }
  return (
    <>
      <div className="form-grid">
        <section className="panel padded">
          <h2>Order information</h2>
          <form onSubmit={submit}>
            {error && (
              <div className="alert" role="alert">
                {error}
              </div>
            )}
            {children}
            <label>
              Order amount (USD)
              <input
                required
                type="number"
                min="0.01"
                max="99999999.99"
                step="0.01"
                placeholder="0.00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </label>
            <div className="form-actions">
              <button disabled={busy} type="submit">
                {busy ? 'Creating order…' : 'Place order'}
              </button>
              <a href={`#${ordersPath}`} className="text-link">
                Cancel
              </a>
            </div>
          </form>
        </section>
        <aside className="panel padded order-summary">
          <h2>Order summary</h2>
          <dl className="summary-list">
            <div>
              <dt>Customer</dt>
              <dd>{customer || 'Not selected'}</dd>
            </div>
            <div>
              <dt>Initial status</dt>
              <dd>Pending</dd>
            </div>
            <div className="total">
              <dt>Total</dt>
              <dd>{money(amount)}</dd>
            </div>
          </dl>
          <p className="hint">
            You can review the order and make a payment after it has been created.
          </p>
        </aside>
      </div>
    </>
  );
}
