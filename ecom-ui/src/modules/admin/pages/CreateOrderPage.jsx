import { useEffect, useState } from 'react';
import { request, money } from '../../../orders';
export default function CreateOrderPage() {
  const [products, setProducts] = useState([]),
    [customer, setCustomer] = useState(''),
    [address, setAddress] = useState(''),
    [quantities, setQuantities] = useState({}),
    [error, setError] = useState(''),
    [busy, setBusy] = useState(false);
  const total =
    products.reduce((sum, p) => sum + Math.round(p.price * 100) * (quantities[p.sku] || 0), 0) /
    100;
  useEffect(() => {
    request('/api/products')
      .then((c) => setProducts(c.categories.flatMap((c) => c.products)))
      .catch((e) => setError(e.message));
  }, []);
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const items = products
        .filter((p) => quantities[p.sku] > 0)
        .map((p) => ({ sku: p.sku, quantity: quantities[p.sku] }));
      if (!items.length) throw new Error('Choose at least one product.');
      const body = { items, address, customerId: customer, expectedAmount: total };
      const signature = JSON.stringify({ items, address, customerId: customer });
      let saved;
      try {
        saved = JSON.parse(sessionStorage.getItem('ecom:admin-checkout') || '{}');
      } catch {
        saved = {};
      }
      const idempotencyKey =
        saved.signature === signature ? saved.idempotencyKey : crypto.randomUUID();
      sessionStorage.setItem('ecom:admin-checkout', JSON.stringify({ signature, idempotencyKey }));
      const result = await request('/api/orders/checkout', 'POST', { ...body, idempotencyKey });
      sessionStorage.removeItem('ecom:admin-checkout');
      window.location.hash = `/admin/orders/${result.orderId}`;
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">ASSISTED SHOPPING</p>
          <h1>Create an order for a customer</h1>
          <p className="muted">The customer must have a completed profile in your tenant.</p>
        </div>
      </div>
      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}
      <form className="panel padded" onSubmit={submit}>
        <div className="form-grid">
          <label>
            Customer username
            <input
              required
              value={customer}
              onChange={(e) => setCustomer(e.target.value)}
              disabled={busy}
            />
          </label>
          <label>
            Delivery address
            <input
              required
              minLength={10}
              maxLength={500}
              value={address}
              onChange={(e) => setAddress(e.target.value)}
              disabled={busy}
            />
          </label>
        </div>
        <div className="assisted-products">
          {products.map((p) => (
            <div className="cart-row" key={p.sku}>
              <img src={p.image} alt="" />
              <div>
                <h3>{p.name}</h3>
                <p>{money(p.price)} each</p>
              </div>
              <label>
                Quantity for {p.name}
                <input
                  type="number"
                  min="0"
                  max="99"
                  value={quantities[p.sku] || 0}
                  onChange={(e) =>
                    setQuantities({ ...quantities, [p.sku]: Number(e.target.value) })
                  }
                  disabled={busy}
                />
              </label>
            </div>
          ))}
        </div>
        <p className="payment-total">Total: {money(total)}</p>
        <p className="hint">Payment is simulated. The server verifies prices and reserves stock.</p>
        <button disabled={busy || total <= 0}>{busy ? 'Creating order…' : 'Pay now'}</button>
      </form>
    </>
  );
}
