export default function SignInPage({ login, error }) {
  return (
    <div className="signin">
      <div className="signin-brand">
        <span className="brand-mark">e.</span>
        <span>
          Ecom<span className="muted"> / workspace</span>
        </span>
      </div>
      <div className="signin-content">
        <p className="eyebrow">EVERY ORDER, ONE PLACE</p>
        <h1>
          A simpler way to
          <br />
          manage your orders.
        </h1>
        <p>
          Track your purchases, review the details and stay up to date. Your workspace is ready when
          you are.
        </p>
        {error && (
          <div className="alert" role="alert">
            {error}
          </div>
        )}
        <button onClick={login}>
          Sign in to your account <span aria-hidden="true">→</span>
        </button>
        <p className="hint">Secure sign-in for customers and administrators.</p>
      </div>
      <div className="signin-art" aria-hidden="true">
        <div className="order-card">
          <span>ORDER OVERVIEW</span>
          <h2>
            From placed
            <br />
            to confirmed.
          </h2>
          <div className="illustration-line" />
          <p>One workspace. A clear view.</p>
          <div className="illustration-dots">
            <i />
            <i />
            <i />
          </div>
        </div>
      </div>
    </div>
  );
}
