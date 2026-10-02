import WorkspaceLayout from '../../shared/layout/WorkspaceLayout';
const navigation = [
  {
    label: 'Shop',
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
    label: 'Your cart',
    path: '/customer/cart',
    icon: '＋',
    matches: (route) => route === '/customer/cart',
  },
  {
    label: 'Your profile',
    path: '/customer/profile',
    icon: '○',
    matches: (r) => r === '/customer/profile',
  },
  {
    label: 'Updates',
    path: '/customer/notifications',
    icon: '◇',
    matches: (r) => r === '/customer/notifications',
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
