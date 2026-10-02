import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// API calls go directly to VITE_GATEWAY_URL; Vite serves only frontend assets.
export default defineConfig({
  plugins: [react()],
  server: { host: 'localhost', port: 5173, strictPort: true },
  preview: { host: 'localhost', port: 5173, strictPort: true },
});
