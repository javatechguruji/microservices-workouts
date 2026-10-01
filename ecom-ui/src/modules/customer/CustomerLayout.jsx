import WorkspaceLayout from '../../shared/layout/WorkspaceLayout';
const navigation = [
  {
    label: 'Dashboard',
    path: '/customer/dashboard',
    icon: '◫',
    matches: (route) => route === '/customer/dashboard',
  },
  {
    label: 'My orders',
    path: '/customer/orders',
    icon: '▤',
    matches: (route) => route.startsWith('/customer/orders') && route !== '/customer/orders/new',
  },
  {
    label: 'Create order',
    path: '/customer/orders/new',
    icon: '＋',
    matches: (route) => route === '/customer/orders/new',
    permission: 'orders:create',
  },
];
export default function CustomerLayout({ route, children }) {
  return (
    <WorkspaceLayout
      route={route}
      homePath="/customer/dashboard"
      navigation={navigation}
      workspaceLabel="Customer workspace"
      sectionLabel="My account"
      roleLabel="Customer"
    >
      {children}
    </WorkspaceLayout>
  );
}
