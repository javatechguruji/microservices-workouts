import { useState, useEffect } from 'react';
import { useCart, Quantity } from '../shopping/CartProvider';
import { useSession } from '../../../shared/auth/SessionProvider';
import { request, money } from '../../../orders';
export default function CartPage() {
  const cart = useCart();
  const { refreshPrices } = cart;
  const [priced, setPriced] = useState(false);
  const { identity } = useSession();
  const key = `ecom:checkout:${identity.subject}`;
  const [address, setAddress] = useState(() => {
      try {
        return JSON.parse(sessionStorage.getItem(key) || '{}').address || '';
      } catch {
        return '';
      }
    }),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    request('/api/products')
      .then((c) => {
        if (active) {
          refreshPrices(c.categories.flatMap((c) => c.products));
          setPriced(true);
        }
      })
      .catch((e) => active && setError(e.message));
    return () => {
      active = false;
    };
  }, [refreshPrices]);
  async function pay(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const profile = await request('/api/customers/me');
      if (!profile.complete)
        throw new Error(
          'Complete your profile with email, phone and date of birth before checkout.'
        );
      const items = cart.items.map(({ sku, quantity }) => ({ sku, quantity }));
      const signature = JSON.stringify({ items, address: address.trim() });
      let saved;
      try {
        saved = JSON.parse(sessionStorage.getItem(key) || '{}');
      } catch {
        saved = {};
      }
      const idempotencyKey =
        saved.signature === signature ? saved.idempotencyKey : crypto.randomUUID();
      sessionStorage.setItem(key, JSON.stringify({ signature, idempotencyKey, address }));
      const order = await request('/api/orders/checkout', 'POST', {
        items,
        address: address.trim(),
        idempotencyKey,
        expectedAmount: cart.total,
      });
      cart.clear();
      sessionStorage.removeItem(key);
      window.location.hash = `/customer/orders/${order.orderId}`;
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
          <p className="eyebrow">ALMOST YOURS</p>
          <h1>Your cart</h1>
          <p className="muted">Review your items and delivery address.</p>
        </div>
        <a href="#/customer/dashboard">Continue shopping →</a>
      </div>
      {error && (
        <div className="alert" role="alert">
          {error} <a href="#/customer/profile">Your profile</a>
        </div>
      )}
      {!cart.items.length ? (
        <div className="panel padded">
          <h2>Your cart is waiting for you</h2>
          <p>Find something you love in the shop.</p>
          <a href="#/customer/dashboard">Explore products →</a>
        </div>
      ) : (
        <div className="form-grid">
          <section className="panel padded">
            {cart.items.map((i) => (
              <div className="cart-row" key={i.sku}>
                <img src={i.image} alt="" />
                <div>
                  <h3>{i.name}</h3>
                  <p>{money(i.price)} each</p>
                  <Quantity
                    disabled={busy}
                    value={i.quantity}
                    onChange={(v) => cart.quantity(i.sku, v)}
                    label={i.name}
                  />
                  <button
                    disabled={busy}
                    className="text-link"
                    onClick={() => cart.quantity(i.sku, 0)}
                  >
                    Remove {i.name}
                  </button>
                </div>
                <strong>{money((Math.round(i.price * 100) * i.quantity) / 100)}</strong>
              </div>
            ))}
          </section>
          <form className="panel padded checkout-summary" onSubmit={pay}>
            <h2>Order summary</h2>
            <dl className="detail-list">
              <div>
                <dt>Items</dt>
                <dd>{cart.items.reduce((n, i) => n + i.quantity, 0)}</dd>
              </div>
              <div>
                <dt>Delivery</dt>
                <dd>Free</dd>
              </div>
              <div className="total">
                <dt>Estimated total</dt>
                <dd>{money(cart.total)}</dd>
              </div>
            </dl>
            <label>
              Delivery address
              <textarea
                required
                minLength={10}
                maxLength={500}
                value={address}
                onChange={(e) => setAddress(e.target.value)}
                disabled={busy}
              />
            </label>
            <p className="hint">
              Prices are checked again at checkout. Pay now creates your order and completes a
              simulated payment. No card details or real money.
            </p>
            <button disabled={busy || !priced}>{busy ? 'Placing your order…' : 'Pay now'}</button>
          </form>
        </div>
      )}
    </>
  );
}
