import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, token } from '../api/client';
import { useMarketStream } from '../hooks/useMarketStream';
import TradeModal from '../components/TradeModal';
import ProfileModal from '../components/ProfileModal';
import { money, coin } from '../utils/format';
import { changeAppLanguage } from '../utils/language';

const SUPPORTED_SYMBOLS = ['BTC', 'ETH', 'SOL'];
const DEFAULT_TRADE_PAGE_SIZE = 20;

export default function DashboardPage({ onLogout }) {
  const { t, i18n } = useTranslation();
  const { market, status, symbolStatuses, error: marketError, changes } = useMarketStream();

  const [me, setMe] = useState(null);
  const [portfolio, setPortfolio] = useState(null);
  const [trades, setTrades] = useState([]);
  const [tab, setTab] = useState(() => {
    try {
      return sessionStorage.getItem('cryptflow_active_tab') || 'market';
    } catch {
      return 'market';
    }
  });
  const [modal, setModal] = useState(null);
  const [showProfile, setShowProfile] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [showNotifications, setShowNotifications] = useState(false);
  const [hasUnreadNotification, setHasUnreadNotification] = useState(false);
  const [dailySummary, setDailySummary] = useState('');

  const currentLang = i18n.language;

  useEffect(() => {
    const cachedSummary = localStorage.getItem('cryptflow_daily_summary');
    const cachedTime = localStorage.getItem('cryptflow_daily_summary_time');
    const cachedLang = localStorage.getItem('cryptflow_daily_summary_lang');
    const lastReadTime = localStorage.getItem('cryptflow_daily_summary_read_time');

    const fetchDailySummary = async () => {
      try {
        const prompt = currentLang === 'tr'
          ? "Lütfen portföyümün genel durumunu ve piyasadaki son trendleri (BTC, ETH, SOL) analiz eden, en fazla 2-3 cümlelik çok kısa, Türkçe, bilgilendirici ve heyecan verici bir günlük özet yaz. (Yanıtın sadece bu özet olsun, yasal uyarı veya disclaimer ekleme)."
          : "Please write a very short daily summary and advice analyzing the general status of my portfolio and the recent trends in the market (BTC, ETH, SOL), in at most 2-3 sentences, in English, informative and exciting. (Your response must only contain this summary, do not add legal warnings or disclaimers).";

        const response = await api('/chat/query', {
          method: 'POST',
          body: JSON.stringify({ message: prompt })
        });
        if (response?.answer) {
          const cleanAnswer = response.answer.replace(/Yasal Uyarı|Disclaimer[\s\S]*$/gi, '').trim();
          localStorage.setItem('cryptflow_daily_summary', cleanAnswer);
          localStorage.setItem('cryptflow_daily_summary_time', Date.now().toString());
          localStorage.setItem('cryptflow_daily_summary_lang', currentLang);
          setDailySummary(cleanAnswer);
          setHasUnreadNotification(true);
        }
      } catch (err) {
        console.error("Failed to fetch daily summary from AI", err);
      }
    };

    const oneDayMs = 24 * 60 * 60 * 1000;
    const now = Date.now();

    if (cachedSummary && cachedTime && cachedLang === currentLang && (now - Number(cachedTime) < oneDayMs)) {
      setDailySummary(cachedSummary);
      if (!lastReadTime || Number(lastReadTime) < Number(cachedTime)) {
        setHasUnreadNotification(true);
      }
    } else {
      fetchDailySummary();
    }
  }, [currentLang]);

  useEffect(() => {
    try {
      sessionStorage.setItem('cryptflow_active_tab', tab);
    } catch (err) {
      console.error("Failed to save active tab state to sessionStorage", err);
    }
  }, [tab]);

  const openNotifications = () => {
    setShowNotifications(prev => {
      const next = !prev;
      if (next) {
        setHasUnreadNotification(false);
        localStorage.setItem('cryptflow_daily_summary_read_time', Date.now().toString());
      }
      return next;
    });
  };

  const refresh = useCallback(async () => {
    try {
      const [m, p, tRes] = await Promise.all([
        api('/me'),
        api('/portfolio'),
        api(`/trades?size=${DEFAULT_TRADE_PAGE_SIZE}`)
      ]);
      setMe(m);
      setPortfolio(p);
      setTrades(tRes.content || []);
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

  if (loading) return <div className="min-h-screen grid place-items-center"><div className="h-10 w-10 animate-spin rounded-full border-2 border-[#1fc8a4] border-t-transparent" /></div>;

  const STREAM_STATUS_VIEW = {
    live: { dot: 'bg-emerald-400', label: t('dashboard.live') },
    connecting: { dot: 'bg-amber-300 animate-pulse', label: t('dashboard.connecting') },
    stale: { dot: 'bg-orange-400', label: t('dashboard.stale') },
    offline: { dot: 'bg-rose-400', label: t('dashboard.offline') }
  };
  const streamView = STREAM_STATUS_VIEW[status] || STREAM_STATUS_VIEW.offline;
  const dateLocale = currentLang === 'tr' ? 'tr-TR' : 'en-US';

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-30 border-b border-white/10 bg-[#07111f]/90 backdrop-blur-xl">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-5 py-4">
          <div className="flex items-center gap-3">
            <span className="h-2.5 w-2.5 rounded-full bg-[#1fc8a4] shadow-[0_0_18px_#1fc8a4]" />
            <span className="font-black tracking-tight">CRYPTFLOW</span>
            <span className="hidden sm:inline text-xs text-slate-600">{t('dashboard.marketLab')}</span>
          </div>
          <div className="flex items-center gap-4 sm:gap-6">
            {/* Language flags */}
            <div className="flex items-center gap-2 border-r border-white/10 pr-4 sm:pr-6">
              <button
                onClick={() => changeAppLanguage('en')}
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
                onClick={() => changeAppLanguage('tr')}
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
              <span className={`h-2 w-2 rounded-full ${streamView.dot}`} />
              <span className="hidden sm:inline text-slate-400">{streamView.label}</span>
            </div>
            <div className="relative">
              <button
                onClick={openNotifications}
                className="text-slate-400 hover:text-[#1fc8a4] transition-colors relative flex items-center"
                aria-label="Notifications"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                  <path d="M13.73 21a2 2 0 0 1-3.46 0" />
                </svg>
                {hasUnreadNotification && (
                  <span className="absolute -top-0.5 -right-0.5 flex h-2 w-2">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2 w-2 bg-amber-500"></span>
                  </span>
                )}
              </button>

              {showNotifications && (
                <div className="absolute right-0 top-8 z-50 w-80 rounded-2xl border border-white/10 bg-[#0a1929] p-4 shadow-[0_20px_50px_rgba(0,0,0,.5)]">
                  <div className="flex items-center justify-between border-b border-white/10 pb-2 mb-3">
                    <h3 className="text-sm font-bold text-white">{t('dashboard.notifications')}</h3>
                    <button onClick={() => setShowNotifications(false)} className="text-xs text-slate-500 hover:text-white">{t('dashboard.close')}</button>
                  </div>
                  {dailySummary ? (
                    <div className="space-y-3">
                      <div className="rounded-xl bg-[#12243a] p-3 border border-white/5">
                        <div className="flex gap-2">
                          <span className="text-[#1fc8a4] text-xs">✦</span>
                          <div>
                            <p className="text-xs font-bold text-slate-200">{t('dashboard.dailySummaryTitle')}</p>
                            <p className="mt-1 text-[11px] text-slate-400 leading-relaxed">{dailySummary}</p>
                          </div>
                        </div>
                        <button
                          onClick={() => {
                            setTab('portfolio');
                            setShowNotifications(false);
                          }}
                          className="mt-3 w-full rounded-lg bg-[#1fc8a4]/10 py-1.5 text-center text-xs font-bold text-[#1fc8a4] hover:bg-[#1fc8a4]/20 transition"
                        >
                          {t('dashboard.goToPortfolio')}
                        </button>
                      </div>
                    </div>
                  ) : (
                    <p className="text-center text-xs text-slate-500 py-4">{t('dashboard.noNotifications')}</p>
                  )}
                </div>
              )}
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
            <button onClick={logout} className="text-sm text-slate-400 hover:text-white transition-colors">{t('dashboard.logout')}</button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-5 py-8">
        {/* Welcome Header */}
        <section className="mb-6">
          <p className="label">{t('dashboard.portfolioOverview')}</p>
          <h1 className="mt-2 text-4xl font-black tracking-[-.04em]">
            {t('dashboard.hello')}{' '}
            <span className="text-[#1fc8a4]">
              {sessionStorage.getItem('cryptflow_firstName') && sessionStorage.getItem('cryptflow_lastName')
                ? `${sessionStorage.getItem('cryptflow_firstName')} ${sessionStorage.getItem('cryptflow_lastName')}`
                : me?.email?.split('@')[0]}
            </span>
          </h1>
          <p className="mt-2 text-slate-400">{t('dashboard.marketOpenDesc')}</p>
        </section>

        {/* Total Equity Summary */}
        <section className="mb-8 grid gap-4 grid-cols-1 sm:grid-cols-3">
          <div className="card rounded-2xl p-5">
            <p className="label text-[10px] tracking-wider">{t('dashboard.totalEquity')}</p>
            <p className="mt-2 text-2xl font-black text-white">{money(portfolio?.totalValueUsd)}</p>
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
            ['market', t('dashboard.tabMarket')],
            ['portfolio', t('dashboard.tabPortfolio')],
            ['history', t('dashboard.tabTransactions')]
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
        {tab === 'market' && <MarketPanel market={market} portfolio={portfolio} onTrade={setModal} t={t} dateLocale={dateLocale} changes={changes} />}
        {tab === 'portfolio' && <PortfolioPanel data={portfolio} t={t} onTrade={setModal} />}
        {tab === 'history' && <HistoryPanel trades={trades} t={t} dateLocale={dateLocale} />}
      </main>

      {modal && (
        <TradeModal
          symbol={modal.symbol}
          side={modal.side}
          livePrice={market?.prices?.[modal.symbol]}
          priceStatus={symbolStatuses?.[modal.symbol]}
          portfolio={portfolio}
          onClose={() => setModal(null)}
          onComplete={refresh}
        />
      )}

      {showProfile && (
        <ProfileModal
          me={me}
          onClose={() => setShowProfile(false)}
          onLogout={onLogout}
        />
      )}
    </div>
  );
}

function MarketPanel({ market, portfolio, onTrade, t, dateLocale, changes }) {
  return (
    <div>
      <div className="grid gap-4 md:grid-cols-3">
        {SUPPORTED_SYMBOLS.map((s, i) => {
          const asset = portfolio?.assets?.find(a => a.symbol === s);
          return (
            <button
              key={s}
              onClick={() => onTrade({ symbol: s, side: 'BUY' })}
              className="card group rounded-2xl p-6 text-left transition hover:-translate-y-1 hover:border-[#1fc8a4]/50"
            >
              <div className="flex items-center justify-between">
                <span className={`grid h-11 w-11 place-items-center rounded-full font-black ${
                  ['bg-amber-300 text-amber-950', 'bg-indigo-300 text-indigo-950', 'bg-fuchsia-300 text-fuchsia-950'][i]
                }`}>{s[0]}</span>
                <span className="text-xs text-slate-600">{t('dashboard.trade')}</span>
              </div>
              <p className="mt-6 label">{s} / USD</p>
              <div className="mt-1 flex items-baseline justify-between">
                <p className="text-3xl font-black">{money(market?.prices?.[s])}</p>
                {changes?.[s] !== undefined && (
                  <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${
                    changes[s] >= 0 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-rose-500/10 text-rose-400'
                  }`}>
                    {changes[s] >= 0 ? '+' : ''}{changes[s].toFixed(2)}%
                  </span>
                )}
              </div>
              <div className="mt-5 border-t border-white/10 pt-4 text-sm text-slate-400">
                {t('dashboard.holding')}{' '}
                <span className="float-right text-white">{coin(asset?.quantity)} {s}</span>
              </div>
            </button>
          );
        })}
      </div>
      <p className="mt-4 text-xs text-slate-600">
        {t('dashboard.lastPriceUpdate')}{' '}
        {market?.updatedAt ? new Date(market.updatedAt).toLocaleString(dateLocale) : t('dashboard.waiting')}
      </p>
    </div>
  );
}

function PortfolioPanel({ data, t, onTrade }) {
  const totalCoinsValue = data?.assets?.reduce((sum, a) => sum + Number(a.valueUsd || 0), 0) || 0;

  return (
    <div className="grid gap-6 lg:grid-cols-[.8fr_1.2fr]">
      <div className="card rounded-2xl p-6 flex flex-col justify-between">
        <div>
          <p className="label">{t('dashboard.availableCash')}</p>
          <p className="mt-3 text-4xl font-black text-white">{money(data?.usdBalance)}</p>
          <p className="mt-2 text-sm text-slate-500">{t('dashboard.availableCashDesc')}</p>
        </div>
        <div className="mt-6 border-t border-white/10 pt-4">
          <p className="label text-[10px] tracking-wider">{t('dashboard.totalCoinValue')}</p>
          <p className="mt-1.5 text-2xl font-black text-white">{money(totalCoinsValue)}</p>
        </div>
      </div>
      <div className="card overflow-hidden rounded-2xl">
        <div className="border-b border-white/10 p-6">
          <p className="label">{t('dashboard.assetAllocation')}</p>
        </div>
        <div className="divide-y divide-white/5">
          {data?.assets && data.assets.length > 0 ? (
            data.assets.map(a => (
              <div key={a.symbol} className="grid grid-cols-[1fr_1.2fr_1.2fr_1fr] border-b border-white/5 px-6 py-4 last:border-0 items-center gap-2">
                <b className="text-sm">{a.symbol}</b>
                <span className="text-right text-sm text-slate-400">{coin(a.quantity)}</span>
                <span className="text-right text-sm font-bold text-white">{money(a.valueUsd)}</span>
                <div className="text-right">
                  {Number(a.quantity) > 0 ? (
                    <button
                      onClick={() => onTrade({ symbol: a.symbol, side: 'SELL' })}
                      className="rounded-lg bg-rose-500/10 border border-rose-500/20 px-3 py-1 text-xs font-bold text-rose-400 hover:bg-rose-500/25 transition"
                    >
                      {t('trade.sell')}
                    </button>
                  ) : (
                    <span className="text-xs text-slate-600">—</span>
                  )}
                </div>
              </div>
            ))
          ) : (
            <p className="p-6 text-center text-sm text-slate-500">{t('dashboard.noAssets')}</p>
          )}
        </div>
      </div>
    </div>
  );
}

function HistoryPanel({ trades, t, dateLocale }) {
  return (
    <div className="card overflow-x-auto rounded-2xl">
      <table className="w-full min-w-[700px] text-left">
        <thead className="label border-b border-white/10">
          <tr>
            <th className="p-5">{t('dashboard.colTransaction')}</th>
            <th>{t('dashboard.colQuantity')}</th>
            <th>{t('dashboard.colPrice')}</th>
            <th>{t('dashboard.colTotal')}</th>
            <th>{t('dashboard.colTime')}</th>
          </tr>
        </thead>
        <tbody>
          {trades.length ? (
            trades.map(t2 => (
              <tr key={t2.id} className="border-b border-white/5">
                <td className={`p-5 font-bold ${t2.side === 'BUY' ? 'text-emerald-300' : 'text-rose-300'}`}>
                  {t2.side} · {t2.symbol}
                </td>
                <td>{coin(t2.quantity)}</td>
                <td>{money(t2.unitPriceUsd)}</td>
                <td>{money(t2.totalUsd)}</td>
                <td className="text-slate-500">{new Date(t2.executedAt).toLocaleString(dateLocale)}</td>
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan="5" className="p-10 text-center text-slate-500">{t('dashboard.noTransactions')}</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}


