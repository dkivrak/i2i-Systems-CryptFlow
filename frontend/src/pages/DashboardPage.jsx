import { useCallback, useEffect, useState } from 'react';
import { api, token } from '../api/client';
import { useMarketStream } from '../hooks/useMarketStream';
import TradeModal from '../components/TradeModal';
import ProfileModal from '../components/ProfileModal';
import { money, coin } from '../utils/format';
import { getCurrentLanguage, changeAppLanguage } from '../utils/language';

const SUPPORTED_SYMBOLS = ['BTC', 'ETH', 'SOL'];

export default function DashboardPage({ onLogout }) {
  const { market, status, error: marketError } = useMarketStream();
  
  const [me, setMe] = useState(null);
  const [portfolio, setPortfolio] = useState(null);
  const [trades, setTrades] = useState([]);
  const [tab, setTab] = useState('market');
  const [modal, setModal] = useState(null);
  const [showProfile, setShowProfile] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const currentLang = getCurrentLanguage();

  const setLanguage = changeAppLanguage;

  const refresh = useCallback(async () => {
    try {
      const [m, p, t] = await Promise.all([
        api('/me'),
        api('/portfolio'),
        api('/trades?size=20')
      ]);
      setMe(m);
      setPortfolio(p);
      setTrades(t.content || []);
      setError('');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  async function logout() {
    try {
      await api('/auth/logout', { method: 'POST' });
    } finally {
      token.clear();
      onLogout();
    }
  }

  /* Lock the price and portfolio info when opening the modal */
  function openTradeModal(symbol){
    const lockedPrice = Number(market?.prices?.[symbol] || 0);
    const asset = portfolio?.assets?.find(a => a.symbol === symbol);
    const assetQuantity = Number(asset?.quantity || 0);
    const usdBalance = Number(portfolio?.usdBalance || 0);
    setModal({ symbol, lockedPrice, usdBalance, assetQuantity });
  }

  if (loading) return <div className="min-h-screen grid place-items-center"><div className="h-10 w-10 animate-spin rounded-full border-2 border-[#1fc8a4] border-t-transparent" /></div>;

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-30 border-b border-white/10 bg-[#07111f]/90 backdrop-blur-xl">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-5 py-4">
          <div className="flex items-center gap-3">
            <span className="h-2.5 w-2.5 rounded-full bg-[#1fc8a4] shadow-[0_0_18px_#1fc8a4]" />
            <span className="font-black tracking-tight">CRYPTFLOW</span>
            <span className="hidden sm:inline text-xs text-slate-600">/ MARKET LAB</span>
          </div>
          <div className="flex items-center gap-4 sm:gap-6">
            {/* Dil Seçimi (Bayraklar) */}
            <div className="flex items-center gap-2 border-r border-white/10 pr-4 sm:pr-6">
              <button
                onClick={() => setLanguage('en')}
                className={`transition-all duration-200 hover:scale-110 active:scale-95 ${
                  currentLang === 'en'
                    ? 'ring-2 ring-[#1fc8a4] ring-offset-2 ring-offset-[#07111f] opacity-100'
                    : 'opacity-40 hover:opacity-80'
                } rounded-full`}
                title="English"
              >
                <svg viewBox="0 0 512 512" width="20" height="20" className="rounded-full overflow-hidden block">
                  <rect width="512" height="512" fill="#012169"/>
                  <path d="M0 0l512 512M0 512L512 0" stroke="#fff" strokeWidth="60"/>
                  <path d="M0 0l512 512M0 512L512 0" stroke="#C8102E" strokeWidth="40"/>
                  <path d="M256 0v512M0 256h512" stroke="#fff" strokeWidth="100"/>
                  <path d="M256 0v512M0 256h512" stroke="#C8102E" strokeWidth="60"/>
                </svg>
              </button>
              <div className="w-[1px] h-4 bg-white/10" />
              <button
                onClick={() => setLanguage('tr')}
                className={`transition-all duration-200 hover:scale-110 active:scale-95 ${
                  currentLang === 'tr'
                    ? 'ring-2 ring-[#1fc8a4] ring-offset-2 ring-offset-[#07111f] opacity-100'
                    : 'opacity-40 hover:opacity-80'
                } rounded-full`}
                title="Türkçe"
              >
                <svg viewBox="0 0 512 512" width="20" height="20" className="rounded-full overflow-hidden block">
                  <rect width="512" height="512" fill="#e30a17"/>
                  <circle cx="210" cy="256" r="120" fill="#fff"/>
                  <circle cx="240" cy="256" r="96" fill="#e30a17"/>
                  <polygon points="325,256 360,282 346,240 382,214 336,214 325,172 314,214 268,214 304,240 290,282" fill="#fff"/>
                </svg>
              </button>
            </div>

            <div className="flex items-center gap-2 text-xs">
              <span className={`h-2 w-2 rounded-full ${status === 'live' ? 'bg-emerald-400' : status === 'connecting' ? 'bg-amber-300 animate-pulse' : 'bg-rose-400'}`} />
              <span className="hidden sm:inline text-slate-400">
                {status === 'live' ? 'Live' : status === 'connecting' ? 'Connecting' : 'Disconnected'}
              </span>
            </div>
            <button
              onClick={() => setShowProfile(true)}
              className="text-slate-400 hover:text-[#1fc8a4] transition-colors"
              aria-label="Profile"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
            </button>
            <button onClick={logout} className="text-sm text-slate-400 hover:text-white transition-colors">Logout</button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-5 py-8">
        {/* Welcome Header */}
        <section className="mb-6">
          <p className="label">PORTFOLIO OVERVIEW</p>
          <h1 className="mt-2 text-4xl font-black tracking-[-.04em]">
            Hello, <span className="text-[#1fc8a4]">{me?.email?.split('@')[0]}</span>
          </h1>
          <p className="mt-2 text-slate-400">The virtual market is open. Test your strategy without risk.</p>
        </section>

        {/* 3-Card Summary Panel */}
        <section className="mb-8 grid gap-4 grid-cols-1 sm:grid-cols-3">
          <div className="card rounded-2xl p-5">
            <p className="label text-[10px] tracking-wider">TOTAL EQUITY</p>
            <p className="mt-2 text-2xl font-black text-white">{money(portfolio?.totalValueUsd)}</p>
          </div>
          <div className="card rounded-2xl p-5">
            <p className="label text-[10px] tracking-wider">AVAILABLE CASH</p>
            <p className="mt-2 text-2xl font-black text-[#1fc8a4]">{money(portfolio?.usdBalance)}</p>
          </div>
          <div className="card rounded-2xl p-5">
            <p className="label text-[10px] tracking-wider">PORTFOLIO VALUE</p>
            <p className="mt-2 text-2xl font-black text-white">{money(portfolio?.assetValueUsd)}</p>
          </div>
        </section>

        {(error || marketError) && (
          <p role="alert" className="mb-6 rounded-xl border border-red-400/20 bg-red-500/10 p-4 text-red-300">
            {error || marketError}
          </p>
        )}

        {/* Tab Navigation */}
        <nav className="mb-6 flex gap-1 overflow-x-auto border-b border-white/10" aria-label="Dashboard sections">
          {[
            ['market', 'Market'],
            ['portfolio', 'Portfolio'],
            ['history', 'Transactions']
          ].map(([id, label]) => (
            <button
              key={id}
              onClick={() => setTab(id)}
              className={`whitespace-nowrap border-b-2 px-5 py-3 text-sm font-bold ${
                tab === id ? 'border-[#1fc8a4] text-white' : 'border-transparent text-slate-500'
              }`}
            >
              {label}
            </button>
          ))}
        </nav>

        {/* Tab Content */}
        {tab === 'market' && <MarketPanel market={market} portfolio={portfolio} onTrade={openTradeModal} />}
        {tab === 'portfolio' && <PortfolioPanel data={portfolio} />}
        {tab === 'history' && <HistoryPanel trades={trades} />}
      </main>

      {modal && (
        <TradeModal
          symbol={modal.symbol}
          lockedPrice={modal.lockedPrice}
          usdBalance={modal.usdBalance}
          assetQuantity={modal.assetQuantity}
          onClose={() => setModal(null)}
          onComplete={refresh}
        />
      )}

      {showProfile && (
        <ProfileModal
          me={me}
          onClose={() => setShowProfile(false)}
        />
      )}
    </div>
  );
}

function MarketPanel({ market, portfolio, onTrade }) {
  return (
    <div>
      <div className="grid gap-4 md:grid-cols-3">
        {SUPPORTED_SYMBOLS.map((s, i) => {
          const asset = portfolio?.assets?.find(a => a.symbol === s);
          return (
            <button
              key={s}
              onClick={() => onTrade(s)}
              className="card group rounded-2xl p-6 text-left transition hover:-translate-y-1 hover:border-[#1fc8a4]/50"
            >
              <div className="flex items-center justify-between">
                <span className={`grid h-11 w-11 place-items-center rounded-full font-black ${
                  ['bg-amber-300 text-amber-950', 'bg-indigo-300 text-indigo-950', 'bg-fuchsia-300 text-fuchsia-950'][i]
                }`}>{s[0]}</span>
                <span className="text-xs text-slate-600">TRADE ↗</span>
              </div>
              <p className="mt-6 label">{s} / USD</p>
              <p className="mt-1 text-3xl font-black">{money(market?.prices?.[s])}</p>
              <div className="mt-5 border-t border-white/10 pt-4 text-sm text-slate-400">
                Holding <span className="float-right text-white">{coin(asset?.quantity)} {s}</span>
              </div>
            </button>
          );
        })}
      </div>
      <p className="mt-4 text-xs text-slate-600">
        Last price update: {market?.updatedAt ? new Date(market.updatedAt).toLocaleString('en-US') : 'waiting'}
      </p>
    </div>
  );
}

function PortfolioPanel({ data }) {
  return (
    <div className="card overflow-hidden rounded-2xl">
      <div className="border-b border-white/10 p-6">
        <p className="label">ASSET ALLOCATION</p>
      </div>
      <div className="divide-y divide-white/5">
        {data?.assets && data.assets.length > 0 ? (
          data.assets.map(a => (
            <div key={a.symbol} className="grid grid-cols-3 border-b border-white/5 px-6 py-5 last:border-0 items-center">
              <b className="text-sm">{a.symbol}</b>
              <span className="text-right text-sm text-slate-400">{coin(a.quantity)}</span>
              <span className="text-right text-sm font-bold text-white">{money(a.valueUsd)}</span>
            </div>
          ))
        ) : (
          <p className="p-6 text-center text-sm text-slate-500">No assets found.</p>
        )}
      </div>
    </div>
  );
}

function HistoryPanel({ trades }) {
  return (
    <div className="card overflow-x-auto rounded-2xl">
      <table className="w-full min-w-[700px] text-left">
        <thead className="label border-b border-white/10">
          <tr>
            <th className="p-5">Transaction</th>
            <th>Quantity</th>
            <th>Price</th>
            <th>Total</th>
            <th>Time</th>
          </tr>
        </thead>
        <tbody>
          {trades.length ? (
            trades.map(t => (
              <tr key={t.id} className="border-b border-white/5">
                <td className={`p-5 font-bold ${t.side === 'BUY' ? 'text-emerald-300' : 'text-rose-300'}`}>
                  {t.side} · {t.symbol}
                </td>
                <td>{coin(t.quantity)}</td>
                <td>{money(t.unitPriceUsd)}</td>
                <td>{money(t.totalUsd)}</td>
                <td className="text-slate-500">{new Date(t.executedAt).toLocaleString('en-US')}</td>
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan="5" className="p-10 text-center text-slate-500">No transactions yet.</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}


