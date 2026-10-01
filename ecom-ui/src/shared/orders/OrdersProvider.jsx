import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { request } from '../../orders';

const OrdersContext = createContext(null);
// The server scopes this shared API to the authenticated owner/tenant and permissions.
export function OrdersProvider({ children }) {
  const [orders, setOrders] = useState([]),
    [loading, setLoading] = useState(true);
  const [error, setError] = useState(''),
    [notice, setNotice] = useState(''),
    [version, setVersion] = useState(0);
  const upsertOrder = useCallback(
    (order) => setOrders((previous) => [order, ...previous.filter((o) => o.id !== order.id)]),
    []
  );
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    request('/api/orders')
      .then((value) => {
        if (active) setOrders(value);
      })
      .catch((e) => {
        if (active) setError(e.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [version]);
  function orderCreated(order, ordersPath) {
    upsertOrder(order);
    setNotice(`Order #${order.id} created successfully.`);
    window.location.hash = `${ordersPath}/${order.id}`;
  }
  return (
    <OrdersContext.Provider
      value={{
        orders,
        loading,
        error,
        notice,
        upsertOrder,
        orderCreated,
        refresh: () => setVersion((v) => v + 1),
        dismissNotice: () => setNotice(''),
      }}
    >
      {children}
    </OrdersContext.Provider>
  );
}
export function useOrders() {
  const value = useContext(OrdersContext);
  if (!value) throw new Error('useOrders requires OrdersProvider');
  return value;
}
