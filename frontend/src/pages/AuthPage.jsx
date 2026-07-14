import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, token } from '../api/client';

export default function AuthPage({ onAuth }) {
  const navigate = useNavigate();
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ email: '', password: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    setNotice('');
    try {
      if (mode === 'register') {
        await api('/auth/register', {
          method: 'POST',
          body: JSON.stringify(form)
        });
        setMode('login');
        setNotice('Account ready. You can log in now.');
      } else {
        const r = await api('/auth/login', {
          method: 'POST',
          body: JSON.stringify(form)
        });
        token.set(r.token);
        onAuth();
        navigate('/dashboard', { replace: true });
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="grid-lines min-h-screen grid lg:grid-cols-[1.15fr_.85fr]">
      {/* Left Panel */}
      <section className="hidden lg:flex flex-col justify-between p-14 border-r border-white/10">
        <div className="flex items-center gap-3">
          <span className="h-3 w-3 rounded-full bg-[#1fc8a4] shadow-[0_0_20px_#1fc8a4]" />
          <span className="text-xl font-black tracking-tight">CRYPTFLOW</span>
        </div>
        <div>
          <p className="label mb-5">PAPER MARKET LAB</p>
          <h1 className="max-w-xl text-6xl font-black leading-[.98] tracking-[-.05em]">
            Move with the market.<br />
            <span className="text-[#1fc8a4]">Learn without the risk.</span>
          </h1>
          <p className="mt-7 max-w-lg text-lg leading-8 text-slate-400">
            Trade BTC, ETH, and SOL virtual assets. Monitor the live stream, backtest your portfolio, and receive context-aware AI insights.
          </p>
        </div>
        <p className="text-sm text-slate-600">
          Educational purposes only — not financial advice.
        </p>
      </section>

      {/* Right Panel */}
      <section className="flex items-center justify-center p-6">
        <div className="card w-full max-w-md rounded-3xl p-7 sm:p-10">
          <div className="mb-8 lg:hidden font-black">
            CRYPT<span className="text-[#1fc8a4]">FLOW</span>
          </div>

          <p className="label">
            {mode === 'login' ? 'WELCOME BACK' : 'START YOUR LAB'}
          </p>
          <h2 className="mt-2 text-3xl font-black">
            {mode === 'login' ? 'Return to the market.' : 'Create your account.'}
          </h2>
          <p className="mt-2 text-slate-400">
            {mode === 'login' ? 'Secure entry into your simulation portfolio.' : 'Your starting balance will be automatically provisioned.'}
          </p>

          <form onSubmit={submit} className="mt-8 space-y-4">
            <label className="block">
              <span className="mb-2 block text-sm text-slate-300">Email</span>
              <input
                className="input"
                type="email"
                required
                value={form.email}
                onChange={e => setForm({ ...form, email: e.target.value })}
              />
            </label>
            <label className="block">
              <span className="mb-2 block text-sm text-slate-300">Password</span>
              <input
                className="input"
                type="password"
                minLength="8"
                required
                value={form.password}
                onChange={e => setForm({ ...form, password: e.target.value })}
              />
            </label>

            {error && (
              <p role="alert" className="rounded-xl bg-red-500/10 p-3 text-sm text-red-300">
                {error}
              </p>
            )}
            {notice && (
              <p className="rounded-xl bg-emerald-500/10 p-3 text-sm text-emerald-300">
                {notice}
              </p>
            )}

            <button
              disabled={busy}
              className="btn btn-primary w-full"
            >
              {busy ? 'Processing...' : mode === 'login' ? 'Log in' : 'Register'}
            </button>
          </form>

          <button
            className="mt-6 w-full text-sm text-slate-400 hover:text-white"
            onClick={() => {
              setMode(mode === 'login' ? 'register' : 'login');
              setError('');
              setNotice('');
            }}
          >
            {mode === 'login' ? "Don't have an account? Register" : 'Already have an account? Log in'}
          </button>
        </div>
      </section>
    </main>
  );
}
