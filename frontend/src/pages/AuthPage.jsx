import { useEffect, useState, useRef } from 'react';
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
  const [randomSymbols, setRandomSymbols] = useState([]);
  const { market, changes } = useMarketStream();

  useEffect(() => {
    if (randomSymbols.length === 0 && market?.prices) {
      const allSymbols = Object.keys(market.prices);
      if (allSymbols.length > 0) {
        const shuffled = [...allSymbols].sort(() => 0.5 - Math.random());
        setRandomSymbols(shuffled.slice(0, 9));
      }
    }
  }, [market, randomSymbols]);

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
  const COIN_NAMES = {
    BTC: 'Bitcoin',
    ETH: 'Ethereum',
    SOL: 'Solana',
    BNB: 'Binance Coin',
    ADA: 'Cardano',
    XRP: 'Ripple',
    DOGE: 'Dogecoin',
    DOT: 'Polkadot',
    AVAX: 'Avalanche',
    LINK: 'Chainlink'
  };

  const displayCoins = (() => {
    const activeList = randomSymbols.length > 0
      ? randomSymbols
      : ['BTC', 'ETH', 'SOL', 'BNB', 'ADA', 'XRP', 'DOGE', 'DOT', 'AVAX'];

    return activeList.map(symbol => ({
      symbol,
      change: changes?.[symbol] || 0,
      price: market?.prices?.[symbol]
    }));
  })();

  return (
    <main className="grid-lines min-h-screen grid lg:grid-cols-[1.15fr_.85fr]">
      {/* Left Panel */}
      <section className="hidden lg:flex flex-col justify-between p-14 border-r border-white/10">
        <div className="flex items-center gap-2">
          <img src="/logo.png" alt="CryptFlow Logo" className="h-12 w-12 object-contain" />
          <span className="text-xl font-black tracking-tight">CRYPTFLOW</span>
        </div>
        <div className="mt-8 mb-auto">
          <h1 className="max-w-xl text-6xl font-black leading-[.98] tracking-[-.05em] text-slate-100">
            {t('auth.moveWithMarket')}<br />
            <span className="text-gradient">{t('auth.learnWithoutRisk')}</span>
          </h1>
          <p className="mt-4 max-w-lg text-lg leading-8 text-slate-400">
            {t('auth.heroDescription')}
          </p>

          {/* Live Market Tickers */}
          <div className="mt-5 max-w-md rounded-2xl bg-[#081522]/40 p-3 border border-white/5 overflow-hidden h-[300px] relative [mask-image:linear-gradient(to_bottom,transparent,white_10%,white_90%,transparent)]">
            <div className="animate-marquee-vertical flex flex-col gap-4 py-1">
              {displayCoins.map((item, idx) => {
                const symbol = item.symbol;
                const colorClass = [
                  'bg-amber-400/20 text-amber-300 border border-amber-500/20',
                  'bg-indigo-400/20 text-indigo-300 border border-indigo-500/20',
                  'bg-fuchsia-400/20 text-fuchsia-300 border border-fuchsia-500/20'
                ][idx % 3] || 'bg-slate-400/20 text-slate-300 border border-slate-500/20';

                return (
                  <TickerItem
                    key={`${symbol}-1`}
                    symbol={symbol}
                    price={item.price}
                    change={item.change}
                    coinName={COIN_NAMES[symbol] || symbol}
                    colorClass={colorClass}
                  />
                );
              })}
              {displayCoins.map((item, idx) => {
                const symbol = item.symbol;
                const colorClass = [
                  'bg-amber-400/20 text-amber-300 border border-amber-500/20',
                  'bg-indigo-400/20 text-indigo-300 border border-indigo-500/20',
                  'bg-fuchsia-400/20 text-fuchsia-300 border border-fuchsia-500/20'
                ][idx % 3] || 'bg-slate-400/20 text-slate-300 border border-slate-500/20';

                return (
                  <TickerItem
                    key={`${symbol}-2`}
                    symbol={symbol}
                    price={item.price}
                    change={item.change}
                    coinName={COIN_NAMES[symbol] || symbol}
                    colorClass={colorClass}
                  />
                );
              })}
            </div>
          </div>
        </div>
        <p className="text-sm text-slate-600 mt-6">
          {t('auth.disclaimer')}
        </p>
      </section>

      {/* Right Panel */}
      <section className="flex items-center justify-center p-6">
        <div className="card w-full max-w-md rounded-3xl p-7 sm:p-10">
          <div className="mb-8 lg:hidden flex items-center gap-2 font-black text-xl">
            <img src="/logo.png" alt="CryptFlow Logo" className="h-8 w-8 object-contain" />
            <span className="text-gradient">CRYPTFLOW</span>
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

function CoinLogo({ symbol, colorClass }) {
  const [imgError, setImgError] = useState(false);

  if (imgError) {
    return (
      <span className={`grid h-8 w-8 place-items-center rounded-full font-black text-xs shrink-0 ${colorClass}`}>
        {symbol[0]}
      </span>
    );
  }

  return (
    <img
      src={`https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/128/color/${symbol.toLowerCase()}.png`}
      alt={symbol}
      onError={() => setImgError(true)}
      className="h-8 w-8 rounded-full object-contain shrink-0"
      loading="lazy"
    />
  );
}

function TickerItem({ symbol, price, change, coinName, colorClass }) {
  const prevPriceRef = useRef(price);
  const [flashClass, setFlashClass] = useState('');

  useEffect(() => {
    if (price && prevPriceRef.current && price !== prevPriceRef.current) {
      const isUp = Number(price) > Number(prevPriceRef.current);
      setFlashClass(isUp ? 'text-[#10d98e] scale-105 transition-all duration-300' : 'text-[#ff4b6e] scale-105 transition-all duration-300');
      const timer = setTimeout(() => {
        setFlashClass('');
      }, 1000);
      prevPriceRef.current = price;
      return () => clearTimeout(timer);
    } else if (price) {
      prevPriceRef.current = price;
    }
  }, [price]);

  return (
    <div className="flex items-center justify-between text-sm bg-[#0a1424]/60 border border-white/5 rounded-2xl p-4 backdrop-blur-md">
      <div className="flex items-center gap-3">
        <CoinLogo symbol={symbol} colorClass={colorClass} />
        <div className="text-left">
          <span className="font-bold text-white block">{symbol}</span>
          <span className="text-[10px] text-slate-500 block leading-tight">{coinName}</span>
        </div>
      </div>
      <div className="text-right">
        <span className={`font-bold text-white block ${flashClass}`}>
          {price ? money(price) : '...'}
        </span>
        {change !== undefined && (
          <span className={`text-xs font-bold block ${
            change >= 0 ? 'text-[#10d98e]' : 'text-[#ff4b6e]'
          }`}>
            {change >= 0 ? '+' : ''}{change.toFixed(2)}%
          </span>
        )}
      </div>
    </div>
  );
}
