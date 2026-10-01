import { useEffect, useState } from 'react';
import { auth, redirectUri } from './auth';
import { request } from './orders';
import Dashboard from './pages/Dashboard';
import OrderList from './pages/OrderList';
import CreateOrder from './pages/CreateOrder';
import OrderDetails from './pages/OrderDetails';
import Customers from './pages/Customers';

function useRoute() {
  const [hash, setHash] = useState(location.hash || '#/');
  useEffect(() => {
    const change = () => setHash(location.hash || '#/');
    window.addEventListener('hashchange', change);
    return () => window.removeEventListener('hashchange', change);
  }, []);
  return hash.slice(1);
}
export default function App() {
  const route = useRoute();
  const [signedIn, setSignedIn] = useState(Boolean(auth.authenticated)),
    [identity, setIdentity] = useState(null),
    [orders, setOrders] = useState([]),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(''),
    [notice, setNotice] = useState(''),
    [version, setVersion] = useState(0);
  useEffect(() => {
    auth.onAuthLogout = () => {
      setSignedIn(false);
      setIdentity(null);
      setOrders([]);
    };
    return () => {
      auth.onAuthLogout = undefined;
    };
  }, []);
  useEffect(() => {
    let active = true;
    if (!signedIn) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    Promise.all([request('/api/orders/security/me'), request('/api/orders')])
      .then(([caller, list]) => {
        if (active) {
          setIdentity(caller);
          setOrders(list);
        }
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
  }, [signedIn, version]);
  const admin = identity?.roles.includes('admin') || false;
  const canCreate = identity?.permissions.includes('orders:create');
  function changed(order) {
    if (order) setOrders((previous) => [order, ...previous.filter((o) => o.id !== order.id)]);
    else setVersion((v) => v + 1);
  }
  function created(order) {
    changed(order);
    setNotice(`Order #${order.id} created successfully.`);
    location.hash = `/orders/${order.id}`;
  }
  async function login() {
    try {
      await auth.login({ redirectUri });
    } catch (e) {
      setError(e.message);
    }
  }
  async function logout() {
    try {
      await auth.logout({ redirectUri });
    } catch (e) {
      setError(e.message);
    }
  }
  if (!signedIn)
    return (
      <div className="signin">
        <div className="signin-brand">
          <span className="brand-mark">e.</span>
          <span>
            Ecom<span className="muted"> / workspace</span>
          </span>
        </div>
        <div className="signin-content">
          <p className="eyebrow">EVERY ORDER, ONE PLACE</p>
          <h1>
            A simpler way to
            <br />
            manage your orders.
          </h1>
          <p>
            Track your purchases, review the details and stay up to date. Your workspace is ready
            when you are.
          </p>
          {error && (
            <div className="alert" role="alert">
              {error}
            </div>
          )}
          <button onClick={login}>
            Sign in to your account <span aria-hidden="true">→</span>
          </button>
          <p className="hint">Secure sign-in for customers and administrators.</p>
        </div>
        <div className="signin-art" aria-hidden="true">
          <div className="order-card">
            <span>ORDER OVERVIEW</span>
            <h2>
              From placed
              <br />
              to confirmed.
            </h2>
            <div className="illustration-line" />
            <p>One workspace. A clear view.</p>
            <div className="illustration-dots">
              <i />
              <i />
              <i />
            </div>
          </div>
        </div>
      </div>
    );
  let page;
  const detail = route.match(/^\/orders\/(\d+)$/),
    customer = new URLSearchParams(route.split('?')[1] || '').get('customer') || '';
  if (loading)
    page = (
      <div className="panel padded" role="status">
        Loading your workspace…
      </div>
    );
  else if (!identity)
    page = (
      <div className="panel padded">
        <h1>We couldn’t load your workspace</h1>
        <p>Please check that the application services are running and try again.</p>
        <button onClick={() => setVersion((v) => v + 1)}>Try again</button>
      </div>
    );
  else if (route === '/') page = <Dashboard identity={identity} orders={orders} admin={admin} />;
  else if (route === '/orders/new')
    page = canCreate ? (
      <CreateOrder identity={identity} admin={admin} orders={orders} onCreated={created} />
    ) : (
      <div className="panel padded">
        <h1>Access restricted</h1>
        <p>Your account cannot create orders.</p>
      </div>
    );
  else if (detail)
    page = (
      <OrderDetails
        key={detail[1]}
        id={detail[1]}
        identity={identity}
        admin={admin}
        onChanged={changed}
      />
    );
  else if (route.split('?')[0] === '/orders')
    page = <OrderList key={customer} orders={orders} admin={admin} customer={customer} />;
  else if (route === '/customers')
    page = admin ? (
      <Customers orders={orders} />
    ) : (
      <div className="panel padded">
        <h1>Access restricted</h1>
        <p>Customer management is available to administrators.</p>
        <a href="#/">Return to dashboard</a>
      </div>
    );
  else
    page = (
      <div className="panel padded">
        <h1>Page not found</h1>
        <a href="#/">Return to dashboard</a>
      </div>
    );
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#/">
          <span className="brand-mark">e.</span>
          <span>
            Ecom<small>ORDER WORKSPACE</small>
          </span>
        </a>
        <span className="nav-caption">WORKSPACE</span>
        <nav aria-label="Main navigation">
          <a className={route === '/' ? 'active' : ''} href="#/">
            <span aria-hidden="true">◫</span>
            <span>Dashboard</span>
          </a>
          <a
            className={route.startsWith('/orders') && route !== '/orders/new' ? 'active' : ''}
            href="#/orders"
          >
            <span aria-hidden="true">▤</span>
            <span>{admin ? 'All orders' : 'My orders'}</span>
          </a>
          {canCreate && (
            <a className={route === '/orders/new' ? 'active' : ''} href="#/orders/new">
              <span aria-hidden="true">＋</span>
              <span>Create order</span>
            </a>
          )}
          {admin && (
            <a className={route === '/customers' ? 'active' : ''} href="#/customers">
              <span aria-hidden="true">♧</span>
              <span>Customers</span>
            </a>
          )}
        </nav>
        <div className="sidebar-bottom">
          <span className="workspace-dot" /> Workspace: {identity?.tenant || '…'}
          <p>{admin ? 'Administrator workspace' : 'Customer workspace'}</p>
        </div>
      </aside>
      <div className="main-column">
        <header className="topbar">
          <span className="breadcrumb">
            Workspace <span>/</span> {admin ? 'Administration' : 'My account'}
          </span>
          <div className="account">
            <span className="avatar">{identity?.username?.slice(0, 2).toUpperCase() || '…'}</span>
            <div>
              <strong>{identity?.username || 'Loading…'}</strong>
              <small>{admin ? 'Administrator' : 'Customer'}</small>
            </div>
            <button className="secondary" onClick={logout}>
              Sign out
            </button>
          </div>
        </header>
        <main className="content">
          {error && (
            <div className="alert" role="alert">
              {error}
              <button className="secondary" onClick={() => setVersion((v) => v + 1)}>
                Retry
              </button>
            </div>
          )}
          {notice && (
            <div className="notice" role="status">
              {notice}
              <button
                className="dismiss"
                aria-label="Dismiss notification"
                onClick={() => setNotice('')}
              >
                ×
              </button>
            </div>
          )}
          {page}
        </main>
        <footer>
          ECOM WORKSPACE <span>Order management, made simple.</span>
          <button className="text-link" onClick={() => setVersion((v) => v + 1)}>
            Refresh workspace
          </button>
        </footer>
      </div>
    </div>
  );
}
