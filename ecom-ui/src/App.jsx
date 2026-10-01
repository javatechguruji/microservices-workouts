import AppRouter from './app/AppRouter';
import { SessionProvider } from './shared/auth/SessionProvider';

export default function App() {
  return (
    <SessionProvider>
      <AppRouter />
    </SessionProvider>
  );
}
