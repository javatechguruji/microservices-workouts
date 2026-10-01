import { createContext, useContext, useEffect, useState } from 'react';
import { auth, redirectUri } from '../../auth';
import { request } from '../../orders';

const SessionContext = createContext(null);
export function SessionProvider({ children }) {
  const [signedIn, setSignedIn] = useState(Boolean(auth.authenticated));
  const [identity, setIdentity] = useState(null);
  const [loading, setLoading] = useState(Boolean(auth.authenticated));
  const [error, setError] = useState('');
  const [version, setVersion] = useState(0);
  useEffect(() => {
    auth.onAuthLogout = () => {
      setSignedIn(false);
      setIdentity(null);
    };
    return () => {
      auth.onAuthLogout = undefined;
    };
  }, []);
  useEffect(() => {
    if (!signedIn) {
      setLoading(false);
      return;
    }
    let active = true;
    setLoading(true);
    setError('');
    request('/api/orders/security/me')
      .then((value) => {
        if (active) setIdentity(value);
      })
      .catch((e) => {
        if (active) {
          setIdentity(null);
          setError(e.message);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [signedIn, version]);
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
  return (
    <SessionContext.Provider
      value={{
        signedIn,
        identity,
        loading,
        error,
        login,
        logout,
        retry: () => setVersion((v) => v + 1),
      }}
    >
      {children}
    </SessionContext.Provider>
  );
}
export function useSession() {
  const value = useContext(SessionContext);
  if (!value) throw new Error('useSession requires SessionProvider');
  return value;
}
