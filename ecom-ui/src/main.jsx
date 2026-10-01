import React from 'react';
import { createRoot } from 'react-dom/client';
import { initializeAuth } from './auth';
import App from './App';
import './styles.css';

const root = createRoot(document.getElementById('root'));
root.render(
  <main className="content">
    <h1>E-commerce UI</h1>
    <p role="status">Preparing your workspace…</p>
  </main>
);
initializeAuth()
  .then(() => root.render(<App />))
  .catch(() =>
    root.render(
      <main className="content panel padded">
        <h1>Unable to initialize sign-in</h1>
        <p>
          We could not connect to the sign-in service. Please check your connection and try again.
        </p>
        <button onClick={() => window.location.assign('/')}>Retry</button>
      </main>
    )
  );
