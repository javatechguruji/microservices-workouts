import { gatewayUrl } from '../../../api';
import { useEffect, useState } from 'react';
import { request, money } from '../../../orders';
import { useCart, Quantity } from '../shopping/CartProvider';
function ProductCard({ product }) {
  const [quantity, setQuantity] = useState(1),
    [added, setAdded] = useState(false);
  const cart = useCart();
  return (
    <article className="product-card">
      <img src={gatewayUrl(product.image)} alt={product.name} loading="lazy" />
      <div className="product-body">
        <span className="eyebrow">{product.category}</span>
        <h3>{product.name}</h3>
        <p className="rating">
          {product.rating
            ? `★ ${product.rating} · ${product.reviews} reviews`
            : 'No ratings available'}
        </p>
        <p className="hint">
          {product.available == null
            ? 'Availability checked at checkout'
            : product.available > 0
              ? `${product.available} available`
              : 'Currently out of stock'}
        </p>
        <div className="price">
          <strong>{money(product.price)}</strong>
          {product.discountPercent > 0 && (
            <>
              <del>{money(product.originalPrice)}</del>
              <span>{product.discountPercent}% off</span>
            </>
          )}
        </div>
        <Quantity value={quantity} onChange={setQuantity} label={product.name} />
        <button
          disabled={
            product.available === 0 || (product.available != null && quantity > product.available)
          }
          onClick={() => {
            cart.add(product, quantity);
            setAdded(true);
          }}
        >
          Add to cart
        </button>
        {added && <small role="status">Added to your cart</small>}
      </div>
    </article>
  );
}
export default function ShopPage() {
  const [catalog, setCatalog] = useState(null),
    [profile, setProfile] = useState(null),
    [error, setError] = useState(''),
    [version, setVersion] = useState(0);
  const { items } = useCart();
  useEffect(() => {
    let active = true;
    setError('');
    Promise.all([request('/api/products'), request('/api/customers/me')])
      .then(([c, p]) => {
        if (active) {
          setCatalog(c);
          setProfile(p);
        }
      })
      .catch((e) => active && setError(e.message));
    return () => {
      active = false;
    };
  }, [version]);
  return (
    <>
      <div className="shop-hero">
        <div>
          <p className="eyebrow">YOUR EVERYDAY FINDS</p>
          <h1>A little more you.</h1>
          <p>Explore your favorites, discover something new.</p>
          <a className="text-link" href="#/customer/profile">
            Manage your interests →
          </a>
        </div>
        <a className="cart-link" href="#/customer/cart">
          Your cart · {items.reduce((n, i) => n + i.quantity, 0)} items →
        </a>
      </div>
      {error && (
        <div className="alert" role="alert">
          {error}
          <button onClick={() => setVersion((v) => v + 1)}>Try again</button>
        </div>
      )}
      {profile && !profile.complete && (
        <div className="notice">
          Welcome! <a href="#/customer/profile">Complete your profile</a> with contact details and
          interests before checkout.
        </div>
      )}
      {!catalog && !error && <p role="status">Finding your favorites…</p>}
      {catalog && (
        <>
          <nav className="category-links" aria-label="Product categories">
            {catalog.categories.map((c) => (
              <a
                key={c.name}
                href={`#category-${c.name}`}
                onClick={(e) => {
                  e.preventDefault();
                  document
                    .getElementById(`category-${c.name}`)
                    ?.scrollIntoView({ behavior: 'smooth' });
                }}
              >
                {c.name}
              </a>
            ))}
          </nav>
          <p className="muted">Recommended first: {catalog.reason.toLowerCase()}.</p>
          {catalog.categories.map((c) => (
            <section className="category-section" id={`category-${c.name}`} key={c.name}>
              <div className="section-heading">
                <h2>{c.name}</h2>
                {c.preferred && <span className="interest-badge">Picked for you</span>}
              </div>
              <div className="product-grid">
                {c.products.map((p) => (
                  <ProductCard key={p.sku} product={p} />
                ))}
              </div>
            </section>
          ))}
        </>
      )}
    </>
  );
}
