import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, token } from '../api/client';
import { useMarketStream } from '../hooks/useMarketStream';
import TradeModal from '../components/TradeModal';
import ProfileModal from '../components/ProfileModal';
import { money, coin } from '../utils/format';
import { changeAppLanguage } from '../utils/language';

const SUPPORTED_SYMBOLS = ['BTC', 'ETH', 'SOL', 'BNB', 'ADA', 'XRP', 'DOGE', 'DOT', 'AVAX', 'LINK'];
const DEFAULT_TRADE_PAGE_SIZE = 20;

export default function DashboardPage({ onLogout }) {
  const { t, i18n } = useTranslation();
  const { market, status, symbolStatuses, error: marketError, changes, dailyOpenPrices, basePrices } = useMarketStream();

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
  const liveTotalCryptoValue = portfolio?.assets?.reduce((sum, a) => {
    const livePrice = Number(market?.prices?.[a.symbol] || 0);
    return sum + (livePrice > 0 ? Number(a.quantity) * livePrice : Number(a.valueUsd || 0));
  }, 0) || 0;

  const baseTotalCryptoValue = portfolio?.assets?.reduce((sum, a) => {
    const openPrice = Number(dailyOpenPrices?.[a.symbol] || basePrices?.[a.symbol] || 0);
    return sum + (openPrice > 0 ? Number(a.quantity) * openPrice : Number(a.valueUsd || 0));
  }, 0) || 0;

  const liveTotalEquity = Number(portfolio?.usdBalance || 0) + liveTotalCryptoValue;
  const baseTotalEquity = Number(portfolio?.usdBalance || 0) + baseTotalCryptoValue;

  const cryptoChangePercent = baseTotalCryptoValue > 0 
    ? ((liveTotalCryptoValue - baseTotalCryptoValue) / baseTotalCryptoValue) * 100 
    : 0;

  const equityChangePercent = baseTotalEquity > 0 
    ? ((liveTotalEquity - baseTotalEquity) / baseTotalEquity) * 100 
    : 0;

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
            <div className="mt-2 flex items-baseline gap-3">
              <p className="text-2xl font-black text-white">{money(liveTotalEquity)}</p>
              {equityChangePercent !== 0 && (
                <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${
                  equityChangePercent >= 0 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-rose-500/10 text-rose-400'
                }`}>
                  {equityChangePercent >= 0 ? '+' : ''}{equityChangePercent.toFixed(2)}%
                </span>
              )}
            </div>
          </div>

          <AssetAllocationChart portfolio={portfolio} market={market} t={t} />
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
        {tab === 'market' && <MarketPanel market={market} portfolio={portfolio} symbols={market?.prices ? Object.keys(market.prices) : SUPPORTED_SYMBOLS} onTrade={setModal} t={t} dateLocale={dateLocale} changes={changes} />}
        {tab === 'portfolio' && <PortfolioPanel data={portfolio} market={market} changes={changes} cryptoChangePercent={cryptoChangePercent} t={t} onTrade={setModal} />}
        {tab === 'history' && <HistoryPanel trades={trades} t={t} dateLocale={dateLocale} />}
      </main>

      {modal && (
        <TradeModal
          symbol={modal.symbol}
          side={modal.side}
          isSellOnly={modal.isSellOnly}
          changePercent={changes?.[modal.symbol]}
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

function MarketPanel({ market, portfolio, symbols, onTrade, t, dateLocale, changes }) {
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 30;

  const totalPages = Math.ceil(symbols.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const displayedSymbols = symbols.slice(startIndex, startIndex + itemsPerPage);

  useEffect(() => {
    setCurrentPage(1);
  }, [symbols.length]);

  return (
    <div>
      <div className="grid gap-4 md:grid-cols-3">
        {displayedSymbols.map((s, i) => {
          const asset = portfolio?.assets?.find(a => a.symbol === s);
          const globalIndex = startIndex + i;
          return (
            <button
              key={s}
              onClick={() => onTrade({ symbol: s, side: 'BUY' })}
              className="card group rounded-2xl p-6 text-left transition hover:-translate-y-1 hover:border-[#1fc8a4]/50"
            >
              <div className="flex items-center justify-between">
                <CoinLogo symbol={s} index={globalIndex} />
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

      {totalPages > 1 && (
        <div className="mt-8 flex items-center justify-between border-t border-white/10 pt-6">
          <p className="text-xs text-slate-600">
            {t('dashboard.lastPriceUpdate')}{' '}
            {market?.updatedAt ? new Date(market.updatedAt).toLocaleString(dateLocale) : t('dashboard.waiting')}
          </p>
          <div className="flex items-center gap-4">
            <button
              onClick={() => setCurrentPage(prev => Math.max(prev - 1, 1))}
              disabled={currentPage === 1}
              className="grid h-10 w-10 place-items-center rounded-xl border border-white/10 bg-white/5 font-bold text-white transition hover:bg-white/10 disabled:opacity-30 disabled:pointer-events-none"
              aria-label="Previous Page"
            >
              ←
            </button>
            <span className="text-sm font-semibold text-slate-300">
              {currentPage} / {totalPages}
            </span>
            <button
              onClick={() => setCurrentPage(prev => Math.min(prev + 1, totalPages))}
              disabled={currentPage === totalPages}
              className="grid h-10 w-10 place-items-center rounded-xl border border-white/10 bg-white/5 font-bold text-white transition hover:bg-white/10 disabled:opacity-30 disabled:pointer-events-none"
              aria-label="Next Page"
            >
              →
            </button>
          </div>
        </div>
      )}

      {totalPages <= 1 && (
        <p className="mt-4 text-xs text-slate-600">
          {t('dashboard.lastPriceUpdate')}{' '}
          {market?.updatedAt ? new Date(market.updatedAt).toLocaleString(dateLocale) : t('dashboard.waiting')}
        </p>
      )}
    </div>
  );
}

function PortfolioPanel({ data, market, changes, cryptoChangePercent, t, onTrade }) {
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 5;

  const activeAssets = data?.assets?.filter(a => Number(a.quantity) > 0) || [];

  const totalCoinsValue = activeAssets.reduce((sum, a) => {
    const livePrice = Number(market?.prices?.[a.symbol] || 0);
    return sum + (livePrice > 0 ? Number(a.quantity) * livePrice : Number(a.valueUsd || 0));
  }, 0);

  const totalPages = Math.ceil(activeAssets.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const displayedAssets = activeAssets.slice(startIndex, startIndex + itemsPerPage);

  useEffect(() => {
    setCurrentPage(1);
  }, [activeAssets.length]);

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
          <div className="mt-1.5 flex items-baseline gap-3">
            <p className="text-2xl font-black text-white">{money(totalCoinsValue)}</p>
            {cryptoChangePercent !== 0 && (
              <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${
                cryptoChangePercent >= 0 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-rose-500/10 text-rose-400'
              }`}>
                {cryptoChangePercent >= 0 ? '+' : ''}{cryptoChangePercent.toFixed(2)}%
              </span>
            )}
          </div>
        </div>
      </div>
      <div className="card overflow-hidden rounded-2xl flex flex-col justify-between">
        <div>
          <div className="border-b border-white/10 p-6">
            <p className="label">{t('dashboard.assetAllocation')}</p>
          </div>
          <div className="divide-y divide-white/5">
            {displayedAssets.length > 0 ? (
              displayedAssets.map(a => {
                const livePrice = Number(market?.prices?.[a.symbol] || 0);
                const liveValue = livePrice > 0 ? Number(a.quantity) * livePrice : Number(a.valueUsd || 0);
                return (
                  <div key={a.symbol} className="grid grid-cols-[1fr_1.2fr_1.2fr_1fr] border-b border-white/5 px-6 py-4 last:border-0 items-center gap-2">
                    <b className="text-sm">{a.symbol}</b>
                    <span className="text-right text-sm text-slate-400">{coin(a.quantity)}</span>
                    <div className="text-right flex items-center justify-end gap-2.5">
                      <span className="text-sm font-bold text-white">{money(liveValue)}</span>
                      {changes?.[a.symbol] !== undefined && (
                        <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                          changes[a.symbol] >= 0 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-rose-500/10 text-rose-400'
                        }`}>
                          {changes[a.symbol] >= 0 ? '+' : ''}{changes[a.symbol].toFixed(2)}%
                        </span>
                      )}
                    </div>
                    <div className="text-right">
                      <button
                        onClick={() => onTrade({ symbol: a.symbol, side: 'SELL', isSellOnly: true })}
                        className="rounded-lg bg-rose-500/10 border border-rose-500/20 px-3 py-1 text-xs font-bold text-rose-400 hover:bg-rose-500/25 transition"
                      >
                        {t('trade.sell')}
                      </button>
                    </div>
                  </div>
                );
              })
            ) : (
              <p className="p-6 text-center text-sm text-slate-500">{t('dashboard.noAssets')}</p>
            )}
          </div>
        </div>

        {totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-white/10 p-5 bg-[#071320]/30 mt-auto">
            <span className="text-xs text-slate-500">
              {t('dashboard.pageInfo', {
                start: startIndex + 1,
                end: Math.min(startIndex + itemsPerPage, activeAssets.length),
                total: activeAssets.length
              })}
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                disabled={currentPage === 1}
                onClick={() => setCurrentPage(p => p - 1)}
                className="rounded-lg bg-white/5 border border-white/10 px-3 py-1.5 text-xs text-slate-300 hover:bg-white/10 disabled:opacity-30 disabled:cursor-not-allowed transition font-bold"
              >
                ← {t('dashboard.prevPage')}
              </button>
              <button
                type="button"
                disabled={currentPage === totalPages}
                onClick={() => setCurrentPage(p => p + 1)}
                className="rounded-lg bg-white/5 border border-white/10 px-3 py-1.5 text-xs text-slate-300 hover:bg-white/10 disabled:opacity-30 disabled:cursor-not-allowed transition font-bold"
              >
                {t('dashboard.nextPage')} →
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function HistoryPanel({ trades, t, dateLocale }) {
  const [currentPage, setCurrentPage] = useState(0);
  const pageSize = 5;
  const totalPages = Math.ceil(trades.length / pageSize);

  const startIndex = currentPage * pageSize;
  const endIndex = startIndex + pageSize;
  const pagedTrades = trades.slice(startIndex, endIndex);

  return (
    <div className="card rounded-2xl overflow-hidden">
      <div className="overflow-x-auto">
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
            {pagedTrades.length ? (
              pagedTrades.map(t2 => (
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

      {totalPages > 1 && (
        <div className="flex items-center justify-between border-t border-white/10 p-5 bg-[#071320]/30">
          <span className="text-xs text-slate-500">
            {t('dashboard.pageInfo', {
              start: startIndex + 1,
              end: Math.min(endIndex, trades.length),
              total: trades.length
            })}
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={currentPage === 0}
              onClick={() => setCurrentPage(p => p - 1)}
              className="rounded-lg bg-white/5 border border-white/10 px-3 py-1.5 text-xs text-slate-300 hover:bg-white/10 disabled:opacity-30 disabled:cursor-not-allowed transition font-bold"
            >
              ← {t('dashboard.prevPage')}
            </button>
            <button
              type="button"
              disabled={endIndex >= trades.length}
              onClick={() => setCurrentPage(p => p + 1)}
              className="rounded-lg bg-white/5 border border-white/10 px-3 py-1.5 text-xs text-slate-300 hover:bg-white/10 disabled:opacity-30 disabled:cursor-not-allowed transition font-bold"
            >
              {t('dashboard.nextPage')} →
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function AssetAllocationChart({ portfolio, market, t }) {
  const [hoveredAsset, setHoveredAsset] = useState(null);

  if (!portfolio) {
    return null;
  }

  const cash = Number(portfolio.usdBalance || 0);
  const assets = portfolio.assets || [];

  const items = [
    { symbol: 'USD', value: cash, color: '#10b981', bgClass: 'bg-emerald-500' },
    ...assets.map((a, idx) => {
      const livePrice = Number(market?.prices?.[a.symbol] || 0);
      const liveValue = livePrice > 0 ? Number(a.quantity) * livePrice : Number(a.valueUsd || 0);
      return {
        symbol: a.symbol,
        value: liveValue,
        color: ['#fbbf24', '#6366f1', '#d946ef'][idx % 3] || '#fbbf24',
        bgClass: ['bg-amber-400', 'bg-indigo-500', 'bg-fuchsia-500'][idx % 3] || 'bg-amber-400'
      };
    })
  ].filter(item => item.value > 0);

  const sum = items.reduce((s, i) => s + i.value, 0);
  let cumulativePercent = 0;

  return (
    <div className="card rounded-2xl p-5 flex items-center justify-between col-span-2">
      <div className="flex items-center gap-6">
        {/* SVG Doughnut Chart */}
        <div className="relative h-20 w-20 flex-shrink-0">
          <svg viewBox="0 0 36 36" className="h-full w-full transform -rotate-90 overflow-visible">
            {items.length === 0 ? (
              <circle cx="18" cy="18" r="15.9155" fill="none" stroke="#1e293b" strokeWidth="8" />
            ) : (
              items.map((item, index) => {
                const percent = (item.value / sum) * 100;
                const dashArray = `${percent} ${100 - percent}`;
                const dashOffset = -cumulativePercent;
                cumulativePercent += percent;

                const isHovered = hoveredAsset?.symbol === item.symbol;

                return (
                  <circle
                    key={index}
                    cx="18"
                    cy="18"
                    r="15.9155"
                    fill="none"
                    stroke={item.color}
                    strokeWidth={isHovered ? "10" : "8"}
                    strokeDasharray={dashArray}
                    strokeDashoffset={dashOffset}
                    pointerEvents="stroke"
                    className="transition-all duration-300 cursor-pointer"
                    style={{
                      transform: isHovered ? 'scale(1.06)' : 'scale(1)',
                      transformOrigin: 'center',
                    }}
                    onMouseEnter={() => setHoveredAsset({ symbol: item.symbol, value: item.value, percent, color: item.color, bgClass: item.bgClass })}
                    onMouseLeave={() => setHoveredAsset(null)}
                  />
                );
              })
            )}
          </svg>
        </div>

        {/* Legend list (Interactive Tooltip) */}
        <div className="flex flex-col justify-center h-14 min-w-[150px]">
          {hoveredAsset ? (
            <div className="animate-in fade-in zoom-in-95 duration-150">
              <div className="flex items-center gap-2 text-xs">
                <span className={`h-2.5 w-2.5 rounded-full ${hoveredAsset.bgClass}`} />
                <span className="font-bold text-white text-sm">{hoveredAsset.symbol}</span>
              </div>
              <p className="mt-1 text-base font-black text-[#1fc8a4]">
                {money(hoveredAsset.value)}
              </p>
              <p className="text-[10px] text-slate-500">
                {hoveredAsset.percent.toFixed(1)}% {t('dashboard.allocation')}
              </p>
            </div>
          ) : (
            <div className="text-slate-500 text-xs leading-relaxed animate-in fade-in duration-200">
              <p className="font-bold text-slate-400">{t('dashboard.hoverChart')}</p>
              <p className="text-[10px] mt-0.5">{t('dashboard.hoverDesc')}</p>
            </div>
          )}
        </div>
      </div>
      <p className="label hidden sm:block text-[9px] tracking-wider text-slate-500 mr-2">
        {t('dashboard.assetAllocation')}
      </p>
    </div>
  );
}

function CoinLogo({ symbol }) {
  const [imgError, setImgError] = useState(false);

  const getSymbolGradient = (sym) => {
    let hash = 0;
    for (let i = 0; i < sym.length; i++) {
      hash = sym.charCodeAt(i) + ((hash << 5) - hash);
    }
    const h1 = Math.abs(hash % 360);
    const h2 = (h1 + 60) % 360;
    return `linear-gradient(135deg, hsl(${h1}, 85%, 60%), hsl(${h2}, 90%, 40%))`;
  };

  if (imgError) {
    const displaySymbol = symbol.length > 4 ? symbol.slice(0, 3) : symbol;
    return (
      <span
        style={{ background: getSymbolGradient(symbol) }}
        className="grid h-11 w-11 place-items-center rounded-full font-black text-[10px] text-white shadow-[0_4px_12px_rgba(0,0,0,0.3)] tracking-tight uppercase"
      >
        {displaySymbol}
      </span>
    );
  }

  return (
    <img
      src={`https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/128/color/${symbol.toLowerCase()}.png`}
      alt={symbol}
      onError={() => setImgError(true)}
      className="h-11 w-11 rounded-full object-contain"
      loading="lazy"
    />
  );
}


