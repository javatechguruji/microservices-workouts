import { useEffect, useState } from 'react';
import { auth } from '../../../auth';
import { request } from '../../../orders';
const categories = ['Electronics', 'Groceries', 'Home', 'Books', 'Fitness'];
export default function ProfilePage() {
  const [form, setForm] = useState({
      email: auth.tokenParsed?.email || '',
      phone: '',
      dob: '',
      preferences: [],
    }),
    [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [saved, setSaved] = useState(false);
  useEffect(() => {
    request('/api/customers/me')
      .then((p) => {
        if (p.complete)
          setForm({ email: p.email, phone: p.phone, dob: p.dob, preferences: p.preferences });
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);
  function field(e) {
    setSaved(false);
    setForm({ ...form, [e.target.name]: e.target.value });
  }
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await request('/api/customers/me', 'POST', form);
      setSaved(true);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">MAKE YOURSELF AT HOME</p>
          <h1>Your profile & interests</h1>
          <p className="muted">Complete your registration or update your shopping preferences.</p>
        </div>
      </div>
      {error && (
        <div className="alert" role="alert">
          {error}
        </div>
      )}
      {saved && (
        <div className="notice" role="status">
          Profile saved. <a href="#/customer/dashboard">Discover your recommendations →</a>
        </div>
      )}
      {loading ? (
        <p>Loading profile…</p>
      ) : (
        <form className="panel padded profile-form" onSubmit={submit}>
          <label>
            Email
            <input
              required
              type="email"
              name="email"
              value={form.email}
              onChange={field}
              maxLength={254}
            />
          </label>
          <label>
            Phone number
            <input
              required
              type="tel"
              name="phone"
              value={form.phone}
              onChange={field}
              pattern="[+0-9 ()\-]{7,25}"
            />
          </label>
          <label>
            Date of birth
            <input
              required
              type="date"
              name="dob"
              value={form.dob}
              onChange={field}
              max={new Date().toISOString().slice(0, 10)}
            />
          </label>
          <fieldset>
            <legend>What do you enjoy shopping for?</legend>
            <div className="interest-options">
              {categories.map((c) => (
                <label key={c}>
                  <input
                    type="checkbox"
                    checked={form.preferences.includes(c)}
                    onChange={(e) => {
                      setSaved(false);
                      setForm({
                        ...form,
                        preferences: e.target.checked
                          ? [...form.preferences, c]
                          : form.preferences.filter((x) => x !== c),
                      });
                    }}
                  />
                  {c}
                </label>
              ))}
            </div>
          </fieldset>
          <p className="hint">
            Your choices come first, followed by purchase history. If neither is available, your age
            provides simple starter suggestions. You can change your choices anytime.
          </p>
          <button disabled={busy}>{busy ? 'Saving…' : 'Save profile'}</button>
        </form>
      )}
    </>
  );
}
