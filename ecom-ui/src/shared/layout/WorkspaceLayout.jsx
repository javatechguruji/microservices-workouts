import { useSession } from '../auth/SessionProvider';
import { useOrders } from '../orders/OrdersProvider';

// Visual shell only. Each module supplies its navigation, labels and pages.
export default function WorkspaceLayout({
  route,
  homePath,
  navigation,
  workspaceLabel,
  sectionLabel,
  roleLabel,
  children,
}) {
  const { identity, logout } = useSession();
  const { loading, error, notice, refresh, dismissNotice } = useOrders();
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href={`#${homePath}`}>
          <span className="brand-mark">e.</span>
          <span>
            Ecom<small>ORDER WORKSPACE</small>
          </span>
        </a>
        <span className="nav-caption">WORKSPACE</span>
        <nav aria-label="Main navigation">
          {navigation
            .filter((item) => !item.permission || identity.permissions.includes(item.permission))
            .map((item) => (
              <a
                key={item.path}
                href={`#${item.path}`}
                className={item.matches(route) ? 'active' : ''}
              >
                <span aria-hidden="true">{item.icon}</span>
                <span>{item.label}</span>
              </a>
            ))}
        </nav>
        <div className="sidebar-bottom">
          <span className="workspace-dot" /> Workspace: {identity?.tenant || '…'}
          <p>{workspaceLabel}</p>
        </div>
      </aside>
      <div className="main-column">
        <header className="topbar">
          <span className="breadcrumb">
            Workspace <span>/</span> {sectionLabel}
          </span>
          <div className="account">
            <span className="avatar">{identity?.username?.slice(0, 2).toUpperCase() || '…'}</span>
            <div>
              <strong>{identity?.username || 'Loading…'}</strong>
              <small>{roleLabel}</small>
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
              <button className="secondary" onClick={refresh}>
                Retry
              </button>
            </div>
          )}
          {notice && (
            <div className="notice" role="status">
              {notice}
              <button className="dismiss" aria-label="Dismiss notification" onClick={dismissNotice}>
                ×
              </button>
            </div>
          )}
          {loading ? (
            <div className="panel padded" role="status">
              Loading your workspace…
            </div>
          ) : (
            children
          )}
        </main>
        <footer>
          ECOM WORKSPACE <span>Order management, made simple.</span>
          <button className="text-link" onClick={refresh}>
            Refresh workspace
          </button>
        </footer>
      </div>
    </div>
  );
}
