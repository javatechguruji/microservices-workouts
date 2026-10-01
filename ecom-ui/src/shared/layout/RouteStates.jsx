export function AccessDenied({ homePath }) {
  return (
    <section className="panel padded">
      <h1>Access restricted</h1>
      <p>Your account does not have access to this module or action.</p>
      {homePath && <a href={`#${homePath}`}>Return to dashboard</a>}
    </section>
  );
}
export function NotFound({ homePath }) {
  return (
    <section className="panel padded">
      <h1>Page not found</h1>
      <a href={`#${homePath}`}>Return to dashboard</a>
    </section>
  );
}
export function Loading() {
  return (
    <main className="content" role="status">
      Loading your workspace…
    </main>
  );
}
