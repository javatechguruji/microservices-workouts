import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const proxy = Object.fromEntries(
    ['/api', '/payments', '/notifications'].map((path) => [
      path,
      { target: env.GATEWAY_PROXY_TARGET || 'http://localhost:9100', changeOrigin: true },
    ])
  );
  // Fixed origin matches the public Keycloak client; never silently choose another port.
  return {
    plugins: [react()],
    server: { host: 'localhost', port: 5173, strictPort: true, proxy },
    preview: { host: 'localhost', port: 5173, strictPort: true, proxy },
  };
});
