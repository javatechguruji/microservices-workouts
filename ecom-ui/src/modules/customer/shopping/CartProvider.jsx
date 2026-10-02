import { createContext, useContext, useCallback, useEffect, useState } from 'react';
import { useSession } from '../../../shared/auth/SessionProvider';
const Context = createContext(null);
export function CartProvider({ children }) {
  const { identity } = useSession();
  const key = `ecom:cart:${identity.tenant}:${identity.subject}`;
  const [items, setItems] = useState(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(key) || '[]');
      return Array.isArray(saved)
        ? saved.filter(
            (i) =>
              typeof i.sku === 'string' &&
              Number.isInteger(i.quantity) &&
              i.quantity > 0 &&
              i.quantity <= 99
          )
        : [];
    } catch {
      return [];
    }
  });
  useEffect(() => {
    localStorage.setItem(key, JSON.stringify(items));
  }, [items, key]);
  const refreshPrices = useCallback(
    (products) =>
      setItems((previous) =>
        previous.map((item) => ({
          ...item,
          ...products.find((p) => p.sku === item.sku),
          quantity: item.quantity,
        }))
      ),
    []
  );
  function add(product, quantity) {
    setItems((previous) => {
      const found = previous.find((i) => i.sku === product.sku);
      return found
        ? previous.map((i) =>
            i.sku === product.sku
              ? { ...product, quantity: Math.min(99, i.quantity + quantity) }
              : i
          )
        : [...previous, { ...product, quantity }];
    });
  }
  function quantity(sku, value) {
    setItems((previous) =>
      previous
        .map((i) => (i.sku === sku ? { ...i, quantity: Math.min(99, Math.max(0, value)) } : i))
        .filter((i) => i.quantity > 0)
    );
  }
  return (
    <Context.Provider
      value={{
        items,
        add,
        refreshPrices,
        quantity,
        clear: () => setItems([]),
        total:
          items.reduce((sum, i) => sum + Math.round(Number(i.price) * 100) * i.quantity, 0) / 100,
      }}
    >
      {children}
    </Context.Provider>
  );
}
export const useCart = () => useContext(Context);
export function Quantity({ value, onChange, label, disabled = false }) {
  return (
    <div className="quantity">
      <button
        type="button"
        className="secondary"
        disabled={disabled || value <= 1}
        aria-label={`Decrease ${label}`}
        onClick={() => onChange(value - 1)}
      >
        −
      </button>
      <output aria-label={`${label} quantity`}>{value}</output>
      <button
        type="button"
        className="secondary"
        disabled={disabled || value >= 99}
        aria-label={`Increase ${label}`}
        onClick={() => onChange(value + 1)}
      >
        +
      </button>
    </div>
  );
}
