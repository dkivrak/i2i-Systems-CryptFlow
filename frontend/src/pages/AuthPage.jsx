import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, token } from '../api/client';
import { useMarketStream } from '../hooks/useMarketStream';
import { money } from '../utils/format';

export default function AuthPage({ onAuth }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const { market, changes } = useMarketStream();

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
        sessionStorage.setItem('cryptflow_firstName', form.firstName);
        sessionStorage.setItem('cryptflow_lastName', form.lastName);
        setMode('login');
        setNotice(t('auth.accountReady'));
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
        <div className="mt-8 mb-auto">
          <h1 className="max-w-xl text-6xl font-black leading-[.98] tracking-[-.05em]">
            {t('auth.moveWithMarket')}<br />
            <span className="text-[#1fc8a4]">{t('auth.learnWithoutRisk')}</span>
          </h1>
          <p className="mt-7 max-w-lg text-lg leading-8 text-slate-400">
            {t('auth.heroDescription')}
          </p>

          {/* Live Market Tickers */}
          <div className="mt-10 max-w-md rounded-2xl bg-[#081522]/60 p-5 border border-white/5 space-y-4">
            {['BTC', 'ETH', 'SOL'].map((symbol, idx) => {
              const price = market?.prices?.[symbol];
              const change = changes?.[symbol];
              const coinName = { BTC: 'Bitcoin', ETH: 'Ethereum', SOL: 'Solana' }[symbol];
              const colorClass = ['bg-amber-300 text-amber-950', 'bg-indigo-300 text-indigo-950', 'bg-fuchsia-300 text-fuchsia-950'][idx];

              return (
                <div key={symbol} className="flex items-center justify-between text-sm">
                  <div className="flex items-center gap-3">
                    <span className={`grid h-8 w-8 place-items-center rounded-full font-black text-xs ${colorClass}`}>
                      {symbol[0]}
                    </span>
                    <div>
                      <span className="font-bold text-white block">{symbol}</span>
                      <span className="text-[10px] text-slate-500 block">{coinName}</span>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="font-bold text-white block">
                      {price ? money(price) : '...'}
                    </span>
                    {change !== undefined && (
                      <span className={`text-xs font-bold block ${
                        change >= 0 ? 'text-emerald-400' : 'text-rose-400'
                      }`}>
                        {change >= 0 ? '+' : ''}{change.toFixed(2)}%
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
        <p className="text-sm text-slate-600 mt-6">
          {t('auth.disclaimer')}
        </p>
      </section>

      {/* Right Panel */}
      <section className="flex items-center justify-center p-6">
        <div className="card w-full max-w-md rounded-3xl p-7 sm:p-10">
          <div className="mb-8 lg:hidden font-black">
            CRYPT<span className="text-[#1fc8a4]">FLOW</span>
          </div>

          <p className="label">
            {mode === 'login' ? t('auth.welcomeBack') : t('auth.startYourLab')}
          </p>
          <h2 className="mt-2 text-3xl font-black">
            {mode === 'login' ? t('auth.returnToMarket') : t('auth.createAccount')}
          </h2>
          <p className="mt-2 text-slate-400">
            {mode === 'login' ? t('auth.secureEntry') : t('auth.autoProvisioned')}
          </p>

          <form onSubmit={submit} className="mt-8 space-y-4">
            {mode === 'register' && (
              <>
                <label className="block">
                  <span className="mb-2 block text-sm text-slate-300">{t('auth.firstName')}</span>
                  <input
                    className="input"
                    type="text"
                    required
                    value={form.firstName}
                    onChange={e => setForm({ ...form, firstName: e.target.value })}
                  />
                </label>
                <label className="block">
                  <span className="mb-2 block text-sm text-slate-300">{t('auth.lastName')}</span>
                  <input
                    className="input"
                    type="text"
                    required
                    value={form.lastName}
                    onChange={e => setForm({ ...form, lastName: e.target.value })}
                  />
                </label>
              </>
            )}
            <label className="block">
              <span className="mb-2 block text-sm text-slate-300">{t('auth.email')}</span>
              <input
                className="input"
                type="email"
                required
                value={form.email}
                onChange={e => setForm({ ...form, email: e.target.value })}
              />
            </label>
            <label className="block">
              <span className="mb-2 block text-sm text-slate-300">{t('auth.password')}</span>
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
              {busy ? t('auth.processing') : mode === 'login' ? t('auth.login') : t('auth.register')}
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
            {mode === 'login' ? t('auth.noAccountRegister') : t('auth.haveAccountLogin')}
          </button>
        </div>
      </section>
    </main>
  );
}
