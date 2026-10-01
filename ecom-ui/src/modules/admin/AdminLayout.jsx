import WorkspaceLayout from '../../shared/layout/WorkspaceLayout';
const navigation = [
  {
    label: 'Dashboard',
    path: '/admin/dashboard',
    icon: '◫',
    matches: (route) => route === '/admin/dashboard',
  },
  {
    label: 'All orders',
    path: '/admin/orders',
    icon: '▤',
    matches: (route) => route.startsWith('/admin/orders') && route !== '/admin/orders/new',
  },
  {
    label: 'Create order',
    path: '/admin/orders/new',
    icon: '＋',
    matches: (route) => route === '/admin/orders/new',
    permission: 'orders:create',
  },
  {
    label: 'Customers',
    path: '/admin/customers',
    icon: '♧',
    matches: (route) => route === '/admin/customers',
  },
];
export default function AdminLayout({ route, children }) {
  return (
    <WorkspaceLayout
      route={route}
      homePath="/admin/dashboard"
      navigation={navigation}
      workspaceLabel="Administrator workspace"
      sectionLabel="Administration"
      roleLabel="Administrator"
    >
      {children}
    </WorkspaceLayout>
  );
}
