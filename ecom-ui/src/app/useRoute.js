import { useEffect, useState } from 'react';
export function useRoute() {
  const [route, setRoute] = useState(() => window.location.hash.slice(1) || '/');
  useEffect(() => {
    const changed = () => setRoute(window.location.hash.slice(1) || '/');
    window.addEventListener('hashchange', changed);
    return () => window.removeEventListener('hashchange', changed);
  }, []);
  return route;
}
export function Redirect({ to }) {
  useEffect(() => {
    window.location.replace(`#${to}`);
  }, [to]);
  return null;
}
