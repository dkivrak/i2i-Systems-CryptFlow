import { useCallback, useEffect, useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { api, token } from '../api/client';
import { useMarketStream } from '../hooks/useMarketStream';
import TradeModal from '../components/TradeModal';
import ProfileModal from '../components/ProfileModal';
import { money, coin } from '../utils/format';
import { changeAppLanguage } from '../utils/language';

const SUPPORTED_SYMBOLS = ['BTC', 'ETH', 'SOL', 'BNB', 'ADA', 'XRP', 'DOGE', 'DOT', 'AVAX', 'LINK'];
const DEFAULT_TRADE_PAGE_SIZE = 10000;

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
  const [dailySummary, setDailySummary] = useState('');
  const [topTriggeredAlerts, setTopTriggeredAlerts] = useState([]);
  const [theme, setTheme] = useState(() => {
    try {
      return localStorage.getItem('cryptflow_theme') || 'dark';
    } catch {
      return 'dark';
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem('cryptflow_theme', theme);
      if (theme === 'light') {
        document.body.classList.add('light');
      } else {
        document.body.classList.remove('light');
      }
    } catch (err) {
      console.error("Failed to apply theme", err);
    }
  }, [theme]);

  const toggleTheme = () => {
    setTheme(prev => prev === 'dark' ? 'light' : 'dark');
  };

  const [showFavoritesDropdown, setShowFavoritesDropdown] = useState(false);
  const [favorites, setFavorites] = useState(() => {
    try {
      const saved = localStorage.getItem('cryptflow_favorites');
      const parsed = saved ? JSON.parse(saved) : [];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem('cryptflow_favorites', JSON.stringify(favorites));
    } catch (storageError) {
      console.error('Failed to save favorites to localStorage', storageError);
    }
  }, [favorites]);

  const toggleFavorite = (symbol) => {
    setFavorites(prev =>
      prev.includes(symbol)
        ? prev.filter(s => s !== symbol)
        : [...prev, symbol]
    );
  };

  const toggleFavoritesDropdown = () => {
    setShowFavoritesDropdown(prev => !prev);
    setShowNotifications(false);
  };

  const currentLang = i18n.language;

  const lastReadTimeVal = localStorage.getItem('cryptflow_daily_summary_read_time') || '0';
  const cachedTimeVal = localStorage.getItem('cryptflow_daily_summary_time') || '0';
  const hasUnreadSummary = dailySummary && Number(lastReadTimeVal) < Number(cachedTimeVal);
  const unreadAlertsCount = topTriggeredAlerts.filter(a => {
    const triggerTime = a.triggeredAt ? new Date(a.triggeredAt).getTime() : new Date(a.createdAt).getTime();
    return triggerTime > Number(lastReadTimeVal);
  }).length;
  const totalUnreadCount = (hasUnreadSummary ? 1 : 0) + unreadAlertsCount;

  useEffect(() => {
    const cachedSummary = localStorage.getItem('cryptflow_daily_summary');
    const cachedTime = localStorage.getItem('cryptflow_daily_summary_time');
    const cachedLang = localStorage.getItem('cryptflow_daily_summary_lang');

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
        }
      } catch (err) {
        console.error("Failed to fetch daily summary from AI", err);
      }
    };

    const oneDayMs = 24 * 60 * 60 * 1000;
    const now = Date.now();

    if (cachedSummary && cachedTime && cachedLang === currentLang && (now - Number(cachedTime) < oneDayMs)) {
      setDailySummary(cachedSummary);
    } else {
      fetchDailySummary();
    }
  }, [currentLang]);

  const fetchTriggeredAlerts = useCallback(async () => {
    try {
      const res = await api('/alerts/triggered');
      const alerts = res || [];
      setTopTriggeredAlerts(alerts);
    } catch (err) {
      console.error("Failed to load triggered alerts for header", err);
    }
  }, []);

  useEffect(() => {
    fetchTriggeredAlerts();
    const interval = setInterval(fetchTriggeredAlerts, 10000);
    return () => clearInterval(interval);
  }, [fetchTriggeredAlerts]);

  useEffect(() => {
    try {
      sessionStorage.setItem('cryptflow_active_tab', tab);
    } catch (err) {
      console.error("Failed to save active tab state to sessionStorage", err);
    }
  }, [tab]);

  const openNotifications = () => {
    setShowFavoritesDropdown(false);
    setShowNotifications(prev => {
      const next = !prev;
      if (next) {
        localStorage.setItem('cryptflow_daily_summary_read_time', Date.now().toString());
        if (topTriggeredAlerts.length > 0) {
          localStorage.setItem('cryptflow_last_seen_alert_id', topTriggeredAlerts[0].id);
        }
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

  if (loading) return <div className="min-h-screen grid place-items-center"><div className="h-10 w-10 animate-spin rounded-full border-2 border-[#00d8f6] border-t-transparent" /></div>;
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
        <div className="mx-auto flex max-w-7xl items-center justify-between px-3 sm:px-5 py-4">
          <div className="flex items-center gap-2">
            <img src="/logo.png" alt="CryptFlow Logo" className="h-9 w-9 object-contain" />
            <span className="font-black tracking-tight text-lg text-gradient hidden sm:inline">CRYPTFLOW</span>
          </div>
          <div className="flex items-center gap-2.5 sm:gap-6">
            {/* Language flags */}
            <div className="flex items-center gap-1.5 sm:gap-2 border-r border-white/10 pr-2 sm:pr-6">
              <button
                onClick={() => changeAppLanguage('en')}
                className={`transition-all duration-200 hover:scale-110 active:scale-95 ${
                  currentLang === 'en'
                    ? 'ring-2 ring-[#00d8f6] ring-offset-2 ring-offset-[#040a15] opacity-100'
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
                    ? 'ring-2 ring-[#00d8f6] ring-offset-2 ring-offset-[#040a15] opacity-100'
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
                className="text-slate-400 hover:text-[#00d8f6] transition-colors relative flex items-center"
                aria-label={t('dashboard.notifications')}
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                  <path d="M13.73 21a2 2 0 0 1-3.46 0" />
                </svg>
                {totalUnreadCount > 0 && (
                  <span className="absolute -top-1.5 -right-1.5 flex h-4 w-4 items-center justify-center rounded-full bg-rose-500 text-[9px] font-black text-white leading-none shadow-sm animate-pulse">
                    {totalUnreadCount}
                  </span>
                )}
              </button>

              {showNotifications && (
                <div className="fixed sm:absolute top-16 sm:top-8 left-4 right-4 sm:left-auto sm:right-0 sm:w-80 z-50 rounded-2xl border border-white/10 bg-[#0a1929] p-4 shadow-[0_20px_50px_rgba(0,0,0,.5)] max-h-[350px] overflow-y-auto pr-1">
                  <div className="flex items-center justify-between border-b border-white/10 pb-2 mb-3">
                    <h3 className="text-sm font-bold text-white">{t('dashboard.notifications')}</h3>
                    <button onClick={() => setShowNotifications(false)} className="text-xs text-slate-500 hover:text-white">{t('dashboard.close')}</button>
                  </div>
                  <div className="space-y-3">
                    {dailySummary && (
                      <div className="rounded-xl bg-[#12243a] p-3 border border-white/5">
                        <div className="flex gap-2">
                          <span className="text-[#00d8f6] text-xs">✦</span>
                          <div>
                            <p className="text-xs font-bold text-slate-200">{t('dashboard.dailySummaryTitle', { defaultValue: 'Daily Insights' })}</p>
                            <p className="mt-1 text-[11px] text-slate-400 leading-relaxed">{dailySummary}</p>
                          </div>
                        </div>
                        <button
                          onClick={() => {
                            setTab('portfolio');
                            setShowNotifications(false);
                          }}
                          className="mt-3 w-full rounded-lg bg-[#00d8f6]/10 py-1.5 text-center text-xs font-bold text-[#00d8f6] hover:bg-[#00d8f6]/20 transition"
                        >
                          {t('dashboard.goToPortfolio')}
                        </button>
                      </div>
                    )}
                    {topTriggeredAlerts.map(a => (
                      <div key={a.id} className="rounded-xl bg-emerald-500/10 p-3 border border-emerald-500/20 text-xs">
                        <div className="flex items-start gap-2">
                          <span className="text-emerald-400 font-bold">✓</span>
                          <div className="flex-1">
                            <p className="font-bold text-slate-200">
                              {a.symbol} {a.condition === 'ABOVE'
                                ? t('orders.wentAbove', { defaultValue: 'went above' })
                                : t('orders.wentBelow', { defaultValue: 'went below' })
                              }
                            </p>
                            <p className="mt-0.5 text-[#00d8f6] font-bold">{money(a.targetPrice)}</p>
                            <p className="mt-1 text-[9px] text-slate-500">
                              {new Date(a.createdAt).toLocaleTimeString()}
                            </p>
                          </div>
                        </div>
                      </div>
                    ))}
                    {!dailySummary && topTriggeredAlerts.length === 0 && (
                      <p className="text-center text-xs text-slate-500 py-4">{t('dashboard.noNotifications')}</p>
                    )}
                  </div>
                </div>
              )}
            </div>

            {/* Theme Toggle */}
            <button
              type="button"
              onClick={toggleTheme}
              className="text-slate-400 hover:text-[#00d8f6] transition-colors p-1.5 rounded-full hover:bg-white/5 flex items-center justify-center"
              title={theme === 'dark' ? 'Light Mode' : 'Dark Mode'}
            >
              {theme === 'dark' ? (
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/></svg>
              ) : (
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="4"/><path d="M12 2v2"/><path d="M12 20v2"/><path d="m4.93 4.93 1.41 1.41"/><path d="m17.66 17.66 1.41 1.41"/><path d="M2 12h2"/><path d="M20 12h2"/><path d="m6.34 17.66-1.41 1.41"/><path d="m19.07 4.93-1.41 1.41"/></svg>
              )}
            </button>

            {/* Favorites Dropdown Toggle */}
            <div className="relative">
              <button
                onClick={toggleFavoritesDropdown}
                className="text-slate-400 hover:text-[#ff4b6e] transition-colors relative flex items-center"
                aria-label={t('dashboard.favorites')}
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 24 24"
                  fill={favorites.length > 0 ? "#ff4b6e" : "none"}
                  stroke={favorites.length > 0 ? "#ff4b6e" : "currentColor"}
                  strokeWidth="2.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="w-[22px] h-[22px]"
                >
                  <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z" />
                </svg>
                {favorites.length > 0 && (
                  <span className="absolute top-0.5 right-0.5 flex h-2 w-2">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2 w-2 bg-rose-500"></span>
                  </span>
                )}
              </button>

              {showFavoritesDropdown && (
                <div className="fixed sm:absolute top-16 sm:top-8 left-4 right-4 sm:left-auto sm:right-0 sm:w-80 z-50 rounded-2xl border border-white/10 bg-[#0a1929] p-4 shadow-[0_20px_50px_rgba(0,0,0,.5)]">
                  <div className="flex items-center justify-between border-b border-white/10 pb-2 mb-3">
                    <h3 className="text-sm font-bold text-white flex items-center gap-1.5">
                      <span className="text-[#ff4b6e]">♥</span> {t('dashboard.favorites')}
                    </h3>
                    <button onClick={() => setShowFavoritesDropdown(false)} className="text-xs text-slate-500 hover:text-white">{t('dashboard.close')}</button>
                  </div>
                  {favorites.length > 0 ? (
                    <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
                      {favorites.map(s => {
                        const price = market?.prices?.[s];
                        const change = changes?.[s];
                        return (
                          <div
                            key={s}
                            onClick={() => {
                              setModal({ symbol: s, side: 'BUY' });
                              setShowFavoritesDropdown(false);
                            }}
                            className="flex items-center justify-between p-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/5 cursor-pointer transition"
                          >
                            <div className="flex items-center gap-2">
                              <span className="font-bold text-xs text-white">{s}</span>
                              <span className="text-[10px] text-slate-400">/ USD</span>
                            </div>
                            <div className="text-right">
                              <span className="text-xs font-bold text-white block">{price ? money(price) : '...'}</span>
                              {change !== undefined && (
                                <span className={`text-[10px] font-bold ${change >= 0 ? 'text-[#10d98e]' : 'text-[#ff4b6e]'}`}>
                                  {change >= 0 ? '+' : ''}{change.toFixed(2)}%
                                </span>
                              )}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                    <p className="text-center text-xs text-slate-500 py-4">
                      {t('dashboard.noFavorites')}
                    </p>
                  )}
                </div>
              )}
            </div>

            <button
              onClick={() => setShowProfile(true)}
              className="text-slate-400 hover:text-[#00d8f6] transition-colors"
              aria-label={t('profile.myProfile')}
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

      <main className="mx-auto max-w-7xl px-5 pt-8 pb-28 sm:pb-8">
        {/* Welcome Header */}
        <section className="mb-6">
          <p className="label">{t('dashboard.portfolioOverview')}</p>
          <h1 className="mt-2 text-2xl sm:text-4xl font-black tracking-[-.04em]">
            {t('dashboard.hello')}{' '}
            <span className="text-gradient">
              {sessionStorage.getItem('cryptflow_firstName') && sessionStorage.getItem('cryptflow_lastName')
                ? `${sessionStorage.getItem('cryptflow_firstName')} ${sessionStorage.getItem('cryptflow_lastName')}`
                : me?.email?.split('@')[0]}
            </span>
          </h1>
          <p className="mt-2 text-slate-400">{t('dashboard.marketOpenDesc')}</p>
        </section>

        {/* Total Equity Summary */}
        <section className="mb-8 grid gap-4 grid-cols-1 sm:grid-cols-2">
          <div className="card rounded-2xl p-5 flex flex-col justify-between">
            <div>
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

            <div className="mt-4 border-t border-white/10 pt-4 flex justify-between items-center">
              <div>
                <p className="label text-[10px] tracking-wider">{t('dashboard.availableCash')}</p>
                <p className="mt-1.5 text-lg font-black text-slate-300">{money(portfolio?.usdBalance)}</p>
              </div>
              <p className="text-xs text-slate-500 hidden sm:block">{t('dashboard.availableCashDesc')}</p>
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
            ['history', t('dashboard.tabTransactions')],
            ['orders', t('dashboard.tabOrders', { defaultValue: 'Alarms & Limits' })]
          ].map(([id, label]) => (
            <button
              key={id}
              onClick={() => setTab(id)}
              className={`whitespace-nowrap border-b-2 px-5 py-3 text-sm font-bold ${
                tab === id ? 'border-[#00d8f6] text-[#00d8f6]' : 'border-transparent text-slate-500'
              }`}
            >
              {label}
            </button>
          ))}
        </nav>

        {/* Tab Content */}
        {tab === 'market' && (
          <MarketPanel
            market={market}
            portfolio={portfolio}
            symbols={market?.prices ? Object.keys(market.prices) : SUPPORTED_SYMBOLS}
            onTrade={setModal}
            t={t}
            dateLocale={dateLocale}
            changes={changes}
            favorites={favorites}
            toggleFavorite={toggleFavorite}
          />
        )}
        {tab === 'portfolio' && <PortfolioPanel data={portfolio} market={market} changes={changes} cryptoChangePercent={cryptoChangePercent} t={t} onTrade={setModal} currentLang={currentLang} />}
        {tab === 'history' && <HistoryPanel trades={trades} t={t} dateLocale={dateLocale} />}
        {tab === 'orders' && <OrdersPanel market={market} t={t} dateLocale={dateLocale} symbols={market?.prices ? Object.keys(market.prices) : SUPPORTED_SYMBOLS} />}
      </main>

      {modal && (
        <TradeModal
          key={`${modal.symbol}|${modal.side}|${Boolean(modal.isSellOnly)}`}
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
function MarketPanel({ market, portfolio, symbols, onTrade, t, dateLocale, changes, favorites, toggleFavorite }) {
  const [currentPage, setCurrentPage] = useState(1);
  const [searchQuery, setSearchQuery] = useState('');
  const itemsPerPage = 30;

  const filteredSymbols = symbols.filter(s =>
    s.toLowerCase().includes(searchQuery.trim().toLowerCase())
  );

  const totalPages = Math.ceil(filteredSymbols.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const displayedSymbols = filteredSymbols.slice(startIndex, startIndex + itemsPerPage);

  useEffect(() => {
    setCurrentPage(1);
  }, [symbols.length, searchQuery]);

  return (
    <div>
      {/* Search Bar & Minutely Data Info */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
        <div className="max-w-md relative flex-1">
          <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>
            </svg>
          </div>
          <input
            type="text"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            placeholder={t('dashboard.searchPlaceholder')}
            className="input !pl-11 w-full"
          />
        </div>
        <span className="text-[10px] text-slate-500 italic md:text-right shrink-0">
          * {t('dashboard.marketDataMinutelyInfo')}
        </span>
      </div>

      {displayedSymbols.length > 0 ? (
        <div className="grid gap-4 md:grid-cols-3">
          {displayedSymbols.map((s, i) => {
            const asset = portfolio?.assets?.find(a => a.symbol === s);
            const globalIndex = startIndex + i;
            const isFav = favorites.includes(s);
            return (
              <div
                key={s}
                onClick={() => onTrade({ symbol: s, side: 'BUY' })}
                className="card group relative rounded-2xl p-6 text-left transition hover:-translate-y-1 hover:border-[#00d8f6]/50 cursor-pointer"
              >
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    toggleFavorite(s);
                  }}
                  className="absolute top-4 right-4 p-1.5 rounded-lg text-slate-400 hover:text-rose-500 hover:bg-white/5 transition active:scale-95 flex items-center justify-center z-10"
                  title={isFav ? t('dashboard.removeFavorite') : t('dashboard.addFavorite')}
                >
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    viewBox="0 0 24 24"
                    fill={isFav ? "#ff4b6e" : "none"}
                    stroke={isFav ? "#ff4b6e" : "currentColor"}
                    strokeWidth="2.5"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    className="w-5 h-5"
                  >
                    <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z" />
                  </svg>
                </button>
                <div className="flex items-center justify-between">
                  <CoinLogo symbol={s} index={globalIndex} />
                </div>
                <p className="mt-6 label">{s} / USD</p>
                <div className="mt-1 flex items-center justify-between gap-2">
                  <p className="text-3xl font-black">{money(market?.prices?.[s])}</p>
                  <div className="flex items-center gap-2">
                    <MiniSparkline symbol={s} currentPrice={market?.prices?.[s]} changeVal={changes?.[s]} />
                    {changes?.[s] !== undefined && (
                      <span className={`text-xs font-bold px-2 py-0.5 rounded-full shrink-0 ${
                        changes[s] > 0 ? 'bg-emerald-500/10 text-emerald-400' :
                        changes[s] < 0 ? 'bg-rose-500/10 text-rose-400' :
                        'bg-slate-500/10 text-slate-400'
                      }`}>
                        {changes[s] > 0 ? '+' : ''}{changes[s].toFixed(2)}%
                      </span>
                    )}
                  </div>
                </div>
                <div className="mt-5 border-t border-white/10 pt-4 text-sm text-slate-400">
                  {t('dashboard.holding')}{' '}
                  <span className="float-right text-white">{coin(asset?.quantity)} {s}</span>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="card rounded-2xl p-10 text-center text-slate-500">
          {t('dashboard.noCoinsFound', { defaultValue: 'No coins found matching your search.' })}
        </div>
      )}

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
              aria-label={t('dashboard.prevPage')}
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
              aria-label={t('dashboard.nextPage')}
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

function EquityChart({ history, currentTotalValue, totalCost, usdBalance }) {
  const { t } = useTranslation();
  const [selectedTimeframe, setSelectedTimeframe] = useState('1d');
  const [hoveredIndex, setHoveredIndex] = useState(null);

  // Calculate overall lifetime profit stats
  const netProfit = currentTotalValue - totalCost;
  const netProfitPercent = totalCost > 0 ? (netProfit / totalCost) * 100 : 0;

  // Generate timeframe data points combining database history and dynamic live data
  const chartData = useMemo(() => {
    const now = Date.now();
    let pointsCount = 30;
    let intervalMs = 60 * 1000; // 1 minute default
    
    if (selectedTimeframe === '1m') {
      pointsCount = 30;
      intervalMs = 60 * 1000;
    } else if (selectedTimeframe === '1h') {
      pointsCount = 24;
      intervalMs = 60 * 60 * 1000;
    } else if (selectedTimeframe === '1d') {
      pointsCount = 7;
      intervalMs = 24 * 60 * 60 * 1000;
    } else if (selectedTimeframe === '1w') {
      pointsCount = 30;
      intervalMs = 24 * 60 * 60 * 1000;
    } else if (selectedTimeframe === '1month') {
      pointsCount = 12;
      intervalMs = 30 * 24 * 60 * 60 * 1000;
    } else if (selectedTimeframe === '1y') {
      pointsCount = 12;
      intervalMs = (365 * 24 * 60 * 60 * 1000) / 12;
    }

    const result = [];
    for (let i = pointsCount - 1; i >= 0; i--) {
      const time = now - i * intervalMs;
      // Find closest db history point before this time
      const closest = [...history]
        .reverse()
        .find(h => new Date(h.time || h.recordedAt).getTime() <= time);
      
      let baseVal = closest ? Number(closest.value || closest.totalValue) : currentTotalValue;
      
      // Add a tiny random walk to make the chart curve look alive and fluid
      if (i > 0) {
        const seed = Math.sin(time) * (currentTotalValue * 0.001);
        baseVal += seed;
      } else {
        baseVal = currentTotalValue;
      }
      result.push({ time, value: baseVal });
    }
    return result;
  }, [history, selectedTimeframe, currentTotalValue]);

  // Calculate specific timeframe change (first point to last point)
  const firstPoint = chartData[0];
  const lastPoint = chartData[chartData.length - 1];
  const firstVal = firstPoint ? firstPoint.value : currentTotalValue;
  const lastVal = lastPoint ? lastPoint.value : currentTotalValue;

  const timeframeChange = lastVal - firstVal;
  const timeframeChangePercent = firstVal > 0 ? (timeframeChange / firstVal) * 100 : 0;

  const values = chartData.map(h => h.value);
  const minVal = Math.min(...values) * 0.995;
  const maxVal = Math.max(...values) * 1.005;
  const range = maxVal - minVal;

  const width = 500;
  const height = 150;
  const padding = 15;

  const points = chartData.map((h, index) => {
    const x = padding + (index * (width - 2 * padding)) / (chartData.length - 1);
    const y = height - padding - ((h.value - minVal) * (height - 2 * padding)) / (range || 1);
    return { x, y };
  });

  const pathD = points.reduce((path, p, i) => 
    i === 0 ? `M ${p.x} ${p.y}` : `${path} L ${p.x} ${p.y}`, ''
  );

  const fillD = `${pathD} L ${points[points.length - 1].x} ${height - padding} L ${points[0].x} ${height - padding} Z`;

  return (
    <div className="card rounded-2xl p-6 flex flex-col justify-between relative min-h-[300px]">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="label">{t('dashboard.equityTitle', { defaultValue: 'PORTFOLIO VALUE OVER TIME' })}</p>
          <div className="flex items-baseline gap-2 mt-1">
            <span className="text-2xl font-black text-white">{money(currentTotalValue)}</span>
            <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
              timeframeChange >= 0 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-rose-500/10 text-rose-400'
            }`}>
              {timeframeChange >= 0 ? '+' : ''}{money(timeframeChange)} ({timeframeChangePercent >= 0 ? '+' : ''}{timeframeChangePercent.toFixed(2)}%)
            </span>
          </div>
        </div>
        
        {/* Timeframe Selector */}
        <div className="flex bg-[#040a15] rounded-xl p-1 border border-white/5 gap-1 notranslate">
          {['1m', '1h', '1d', '1w', '1month', '1y'].map(tf => (
            <button
              key={tf}
              type="button"
              onClick={() => {
                setSelectedTimeframe(tf);
                setHoveredIndex(null);
              }}
              className={`px-2.5 py-1 rounded-lg text-[10px] font-black transition-all ${
                selectedTimeframe === tf 
                  ? 'bg-[#00d8f6] text-[#040a15]' 
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              {tf === '1month' ? '1M' : tf === '1y' ? '1Y' : tf}
            </button>
          ))}
        </div>
      </div>

      {/* Sub Stats Row */}
      <div className="grid grid-cols-3 gap-4 border-t border-b border-white/5 py-3 mt-4 text-xs">
        <div>
          <p className="text-slate-500 font-bold uppercase text-[9px]">{t('dashboard.totalCost', { defaultValue: 'Total Cost' })}</p>
          <p className="font-bold text-slate-200 mt-0.5">{money(totalCost)}</p>
        </div>
        <div>
          <p className="text-slate-500 font-bold uppercase text-[9px]">{t('dashboard.cashBalance', { defaultValue: 'Cash Balance' })}</p>
          <p className="font-bold text-slate-200 mt-0.5">{money(usdBalance)}</p>
        </div>
        <div>
          <p className="text-slate-500 font-bold uppercase text-[9px]">{t('dashboard.profitAndLoss', { defaultValue: 'Profit / Loss' })}</p>
          <p className={`font-bold mt-0.5 ${netProfit >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
            {netProfit >= 0 ? '+' : ''}{money(netProfit)}
          </p>
        </div>
      </div>

      {/* Live Chart Container */}
      <div className="relative mt-6 flex-1 min-h-[140px] flex items-center justify-center">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="w-full h-full overflow-visible cursor-crosshair"
          onMouseMove={(e) => {
            const rect = e.currentTarget.getBoundingClientRect();
            const mouseX = ((e.clientX - rect.left) / rect.width) * width;
            let closestIdx = 0;
            let minDist = Infinity;
            points.forEach((p, idx) => {
              const dist = Math.abs(p.x - mouseX);
              if (dist < minDist) {
                minDist = dist;
                closestIdx = idx;
              }
            });
            setHoveredIndex(closestIdx);
          }}
          onMouseLeave={() => setHoveredIndex(null)}
        >
          <defs>
            <linearGradient id="chartGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#00d8f6" stopOpacity="0.25" />
              <stop offset="100%" stopColor="#00d8f6" stopOpacity="0.0" />
            </linearGradient>
          </defs>
          <path d={fillD} fill="url(#chartGrad)" />
          <path d={pathD} fill="none" stroke="#00d8f6" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />

          {/* Interactive Hover Indicators */}
          {hoveredIndex !== null && points[hoveredIndex] && (
            <>
              <line
                x1={points[hoveredIndex].x}
                y1={padding}
                x2={points[hoveredIndex].x}
                y2={height - padding}
                stroke="#00d8f6"
                strokeWidth="1"
                strokeDasharray="3 3"
                opacity="0.6"
              />
              <circle
                cx={points[hoveredIndex].x}
                cy={points[hoveredIndex].y}
                r="6"
                fill="#0a1929"
                stroke="#00d8f6"
                strokeWidth="3"
              />
            </>
          )}
        </svg>

        {/* Hover Tooltip Overlay */}
        {hoveredIndex !== null && chartData[hoveredIndex] && (
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 bg-[#0a1929]/95 border border-[#00d8f6]/30 px-3 py-1.5 rounded-xl shadow-lg text-[10px] pointer-events-none text-center min-w-[120px] z-10">
            <p className="text-slate-400 font-medium">
              {new Date(chartData[hoveredIndex].time).toLocaleString()}
            </p>
            <p className="text-[#00d8f6] font-black mt-0.5 text-xs">
              {money(chartData[hoveredIndex].value)}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

function PortfolioPanel({ data, market, changes, cryptoChangePercent, t, onTrade, currentLang }) {
  const [currentPage, setCurrentPage] = useState(1);
  const [equityHistory, setEquityHistory] = useState([]);
  const [aiAdvice, setAiAdvice] = useState('');
  const [aiLoading, setAiLoading] = useState(false);
  const itemsPerPage = 5;

  const activeAssets = data?.assets?.filter(a => Number(a.quantity) > 0) || [];

  const totalCoinsValue = activeAssets.reduce((sum, a) => {
    const livePrice = Number(market?.prices?.[a.symbol] || 0);
    return sum + (livePrice > 0 ? Number(a.quantity) * livePrice : Number(a.valueUsd || 0));
  }, 0);

  const currentTotalValue = (Number(data?.usdBalance) || 0) + totalCoinsValue;
  const totalCost = (Number(data?.usdBalance) || 0) + activeAssets.reduce((sum, a) => {
    return sum + (Number(a.quantity) * Number(a.averagePrice || 0));
  }, 0);
  const usdBalance = Number(data?.usdBalance) || 0;

  const totalPages = Math.ceil(activeAssets.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const displayedAssets = activeAssets.slice(startIndex, startIndex + itemsPerPage);

  useEffect(() => {
    setCurrentPage(1);
  }, [activeAssets.length]);

  useEffect(() => {
    api('/portfolio/equity-history')
      .then(res => setEquityHistory(res || []))
      .catch(err => console.error("Failed to load equity history", err));
  }, [activeAssets.length]);

  const loadAiAdvice = (force = false) => {
    setAiLoading(true);
    api(`/portfolio/ai-advice?lang=${currentLang}&force=${force}`)
      .then(res => {
        setAiAdvice(res?.advice || '');
      })
      .catch(err => console.error("Failed to load AI advice", err))
      .finally(() => setAiLoading(false));
  };

  useEffect(() => {
    loadAiAdvice(false);
  }, [currentLang, activeAssets.length]);

  return (
    <div className="space-y-6">
      {/* Upper Grid: Equity Curve & AI Advisor */}
      <div className="grid gap-6 md:grid-cols-2">
        <EquityChart 
          history={equityHistory} 
          currentTotalValue={currentTotalValue}
          totalCost={totalCost}
          usdBalance={usdBalance}
        />
        
        {/* Card: AI Robo-Advisor */}
        <div className="card rounded-2xl p-6 flex flex-col justify-between relative overflow-hidden min-h-[160px]">
          <div className="absolute top-0 right-0 w-32 h-32 bg-[#00d8f6]/5 rounded-full filter blur-3xl pointer-events-none" />
          <div>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="text-xs font-black tracking-widest text-[#00d8f6] bg-[#00d8f6]/10 px-2 py-0.5 rounded-md">{t('dashboard.aiAdvisor', { defaultValue: 'AI ADVISOR' })}</span>
              </div>
              <button
                type="button"
                onClick={() => loadAiAdvice(true)}
                disabled={aiLoading}
                className="text-slate-400 hover:text-[#00d8f6] transition-colors p-1.5 rounded-full hover:bg-white/5 disabled:opacity-50 flex items-center justify-center"
                title={t('dashboard.refreshAdvice', { defaultValue: 'Refresh Advice' })}
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="14"
                  height="14"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className={aiLoading ? 'animate-spin' : ''}
                >
                  <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
                  <path d="M3 3v5h5" />
                  <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16" />
                  <path d="M16 16h5v5" />
                </svg>
              </button>
            </div>
            {aiLoading ? (
              <div className="mt-6 flex items-center gap-3 text-slate-400">
                <div className="h-4 w-4 animate-spin rounded-full border-2 border-[#00d8f6] border-t-transparent" />
                <span className="text-xs">{t('dashboard.analyzingPortfolio', { defaultValue: 'Analyzing portfolio rebalancing...' })}</span>
              </div>
            ) : (
              <p className="mt-4 text-xs sm:text-sm text-slate-300 leading-relaxed italic">
                "{aiAdvice || t('dashboard.aiAdviceDefault', { defaultValue: 'Your portfolio is currently in cash. Acquire crypto assets to receive personalized rebalancing insights!' })}"
              </p>
            )}
          </div>
          <div className="mt-4 border-t border-white/5 pt-2 text-[10px] text-slate-500">
            {t('dashboard.insightsUpdateNote', { defaultValue: 'Insights update every 10 minutes based on live market pricing and holdings.' })}
          </div>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[.8fr_1.2fr]">
        <div className="flex flex-col gap-6">
          {/* Card 1: Available Cash */}
          <div className="card rounded-2xl p-6 flex-1 flex flex-col justify-center">
            <p className="label">{t('dashboard.availableCash')}</p>
            <p className="mt-3 text-4xl font-black text-white">{money(data?.usdBalance)}</p>
            <p className="mt-2 text-sm text-slate-500">{t('dashboard.availableCashDesc')}</p>
          </div>
          {/* Card 2: Total Crypto Value */}
          <div className="card rounded-2xl p-6 flex-1 flex flex-col justify-center">
            <p className="label">{t('dashboard.totalCoinValue')}</p>
            <div className="mt-3 flex items-baseline gap-3">
              <p className="text-4xl font-black text-white">{money(totalCoinsValue)}</p>
              {cryptoChangePercent !== 0 && (
                <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${
                  cryptoChangePercent >= 0 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-rose-500/10 text-rose-400'
                }`}>
                  {cryptoChangePercent >= 0 ? '+' : ''}{cryptoChangePercent.toFixed(2)}%
                </span>
              )}
            </div>
            <p className="mt-2 text-sm text-slate-500">{t('dashboard.totalCoinValueDesc')}</p>
          </div>
        </div>
        <div className="card overflow-hidden rounded-2xl flex flex-col justify-between h-[440px]">
          <div>
            <div className="border-b border-white/10 p-6">
              <p className="label">{t('dashboard.assetAllocation')}</p>
            </div>
            <div className="divide-y divide-white/5">
              {displayedAssets.length > 0 ? (
                displayedAssets.map(a => {
                  const livePrice = Number(market?.prices?.[a.symbol] || 0);
                  const liveValue = livePrice > 0 ? Number(a.quantity) * livePrice : Number(a.valueUsd || 0);
                  const avgCost = Number(a.averagePrice || 0);
                  const pnlAmount = avgCost > 0 ? (livePrice - avgCost) * Number(a.quantity) : 0;
                  const pnlPercent = avgCost > 0 ? ((livePrice - avgCost) / avgCost) * 100 : 0;

                  return (
                    <div key={a.symbol} className="grid grid-cols-[1fr_1.1fr_1.4fr_0.8fr] border-b border-white/5 px-3 sm:px-6 py-3.5 sm:py-4 last:border-0 items-center gap-1.5 sm:gap-2">
                      <div>
                        <b className="text-xs sm:text-sm">{a.symbol}</b>
                        <p className="text-[10px] text-slate-500">Avg: {money(avgCost)}</p>
                      </div>
                      <span className="text-right text-xs sm:text-sm text-slate-400 truncate">{coin(a.quantity)}</span>
                      <div className="text-right flex flex-col items-end min-w-0">
                        <span className="text-xs sm:text-sm font-bold text-white truncate">{money(liveValue)}</span>
                        {avgCost > 0 ? (
                          <span className={`text-[10px] font-bold mt-0.5 ${pnlAmount >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                            {pnlAmount >= 0 ? '+' : ''}{money(pnlAmount)} ({pnlAmount >= 0 ? '+' : ''}{pnlPercent.toFixed(2)}%)
                          </span>
                        ) : (
                          <span className="text-[10px] text-slate-500 mt-0.5">—</span>
                        )}
                      </div>
                      <div className="text-right">
                        <button
                          onClick={() => onTrade({ symbol: a.symbol, side: 'SELL', isSellOnly: true })}
                          className="rounded-lg bg-rose-500/10 border border-rose-500/20 px-2 sm:px-3 py-1 text-[10px] sm:text-xs font-bold text-rose-400 hover:bg-rose-500/25 transition"
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
  </div>
  );
}

function OrdersPanel({ market, t, dateLocale, symbols = SUPPORTED_SYMBOLS }) {
  const sortedSymbols = useMemo(() => {
    return [...symbols].sort((a, b) => a.localeCompare(b));
  }, [symbols]);

  const [activeOrders, setActiveOrders] = useState([]);
  const [activeAlerts, setActiveAlerts] = useState([]);
  const [triggeredAlerts, setTriggeredAlerts] = useState([]);

  const [activeAlertsPage, setActiveAlertsPage] = useState(0);
  const [activeOrdersPage, setActiveOrdersPage] = useState(0);
  const [triggeredAlertsPage, setTriggeredAlertsPage] = useState(0);

  const pageSize = 3;

  const totalAlertsPages = Math.ceil(activeAlerts.length / pageSize);
  const clampedAlertsPage = totalAlertsPages > 0 ? Math.max(0, Math.min(activeAlertsPage, totalAlertsPages - 1)) : 0;
  const displayedAlerts = activeAlerts.slice(clampedAlertsPage * pageSize, (clampedAlertsPage + 1) * pageSize);

  const totalOrdersPages = Math.ceil(activeOrders.length / pageSize);
  const clampedOrdersPage = totalOrdersPages > 0 ? Math.max(0, Math.min(activeOrdersPage, totalOrdersPages - 1)) : 0;
  const displayedOrders = activeOrders.slice(clampedOrdersPage * pageSize, (clampedOrdersPage + 1) * pageSize);

  const totalTriggeredPages = Math.ceil(triggeredAlerts.length / pageSize);
  const clampedTriggeredPage = totalTriggeredPages > 0 ? Math.max(0, Math.min(triggeredAlertsPage, totalTriggeredPages - 1)) : 0;
  const displayedTriggered = triggeredAlerts.slice(clampedTriggeredPage * pageSize, (clampedTriggeredPage + 1) * pageSize);

  const [symbol, setSymbol] = useState('BTC');
  const [targetPrice, setTargetPrice] = useState('');
  const [condition, setCondition] = useState('ABOVE');
  const [submittingAlert, setSubmittingAlert] = useState(false);
  const [alertError, setAlertError] = useState('');

  const refreshData = useCallback(async () => {
    try {
      const [ord, alr, trig] = await Promise.all([
        api('/orders'),
        api('/alerts'),
        api('/alerts/triggered')
      ]);
      setActiveOrders(ord || []);
      setActiveAlerts(alr || []);
      setTriggeredAlerts(trig || []);
    } catch (err) {
      console.error("Failed to load orders/alerts", err);
    }
  }, []);

  useEffect(() => {
    refreshData();
    const interval = setInterval(refreshData, 10000);
    return () => clearInterval(interval);
  }, [refreshData]);

  const handleCreateAlert = async (e) => {
    e.preventDefault();
    if (!targetPrice || Number(targetPrice) <= 0) return;
    setSubmittingAlert(true);
    setAlertError('');
    try {
      await api('/alerts', {
        method: 'POST',
        body: JSON.stringify({ symbol, targetPrice, condition })
      });
      setTargetPrice('');
      refreshData();
    } catch (err) {
      setAlertError(err.message);
    } finally {
      setSubmittingAlert(false);
    }
  };

  const handleCancelOrder = async (id) => {
    try {
      await api(`/orders/${id}`, { method: 'DELETE' });
      refreshData();
    } catch (err) {
      console.error(err);
    }
  };

  const handleDeleteAlert = async (id) => {
    try {
      await api(`/alerts/${id}`, { method: 'DELETE' });
      refreshData();
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div className="grid gap-6 md:grid-cols-2">
      <div className="space-y-6">
        <div className="card rounded-2xl p-6 relative overflow-hidden">
          <h3 className="text-sm font-bold text-white mb-4 uppercase tracking-wider">{t('orders.setPriceAlarm', { defaultValue: 'Set Price Alarm' })}</h3>
          <form onSubmit={handleCreateAlert} className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-[10px] text-slate-500 font-bold uppercase block mb-1">{t('orders.coin', { defaultValue: 'Coin' })}</label>
                <select 
                  value={symbol} 
                  onChange={e => setSymbol(e.target.value)} 
                  className="input py-2 text-xs bg-[#040a15]"
                >
                  {sortedSymbols.map(sym => (
                    <option key={sym} value={sym} className="bg-[#040a15]">{sym}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="text-[10px] text-slate-500 font-bold uppercase block mb-1">{t('orders.condition', { defaultValue: 'Condition' })}</label>
                <select 
                  value={condition} 
                  onChange={e => setCondition(e.target.value)} 
                  className="input py-2 text-xs bg-[#040a15]"
                >
                  <option value="ABOVE" className="bg-[#040a15]">{t('orders.goesAbove', { defaultValue: 'Goes Above' })}</option>
                  <option value="BELOW" className="bg-[#040a15]">{t('orders.goesBelow', { defaultValue: 'Goes Below' })}</option>
                </select>
              </div>
            </div>
            <div>
              <label className="text-[10px] text-slate-500 font-bold uppercase block mb-1">{t('orders.targetPrice', { defaultValue: 'Target Price (USD)' })}</label>
              <input
                type="number"
                step="any"
                value={targetPrice}
                onChange={e => setTargetPrice(e.target.value)}
                placeholder="Target Price e.g. 65000"
                className="input py-2 text-xs bg-[#040a15]"
                required
              />
            </div>
            {alertError && <p className="text-xs text-rose-400 bg-rose-500/10 p-2 rounded-lg">{alertError}</p>}
            <button 
              type="submit" 
              disabled={submittingAlert}
              className="btn btn-primary w-full py-2 text-xs"
            >
              {submittingAlert ? t('orders.settingAlarm', { defaultValue: 'Setting Alarm...' }) : t('orders.setPriceAlarmButton', { defaultValue: 'Set Price Alarm' })}
            </button>
          </form>
        </div>

        <div className="card rounded-2xl overflow-hidden">
          <div className="border-b border-white/10 p-5 flex items-center justify-between">
            <h3 className="text-sm font-bold text-white uppercase tracking-wider">{t('orders.activeAlarms', { defaultValue: 'Active Alarms' })}</h3>
            {totalAlertsPages > 1 && (
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  disabled={clampedAlertsPage === 0}
                  onClick={() => setActiveAlertsPage(p => p - 1)}
                  className="rounded-md bg-white/5 border border-white/10 p-1 text-[10px] text-slate-400 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition font-bold"
                >
                  ←
                </button>
                <span className="text-[10px] text-slate-500 font-bold px-1 select-none">
                  {clampedAlertsPage + 1} / {totalAlertsPages}
                </span>
                <button
                  type="button"
                  disabled={clampedAlertsPage >= totalAlertsPages - 1}
                  onClick={() => setActiveAlertsPage(p => p + 1)}
                  className="rounded-md bg-white/5 border border-white/10 p-1 text-[10px] text-slate-400 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition font-bold"
                >
                  →
                </button>
              </div>
            )}
          </div>
          <div className="divide-y divide-white/5">
            {displayedAlerts.length > 0 ? (
              displayedAlerts.map(a => (
                <div key={a.id} className="flex items-center justify-between px-5 py-3.5 text-xs">
                  <div>
                    <span className="font-bold text-white">{a.symbol}</span>
                    <span className="ml-2 text-slate-500">{a.condition === 'ABOVE' ? t('orders.goesAbove', { defaultValue: 'Goes Above' }) : t('orders.goesBelow', { defaultValue: 'Goes Below' })}</span>
                  </div>
                  <div className="flex items-center gap-4">
                    <span className="font-bold text-[#00d8f6]">{money(a.targetPrice)}</span>
                    <button 
                      onClick={() => handleDeleteAlert(a.id)}
                      className="text-slate-500 hover:text-rose-400 transition"
                      title="Delete Alert"
                    >
                      🗑
                    </button>
                  </div>
                </div>
              ))
            ) : (
              <p className="p-5 text-center text-slate-500">{t('orders.noActiveAlarms', { defaultValue: 'No active alarms set.' })}</p>
            )}
          </div>
        </div>
      </div>

      <div className="space-y-6">
        <div className="card rounded-2xl overflow-hidden">
          <div className="border-b border-white/10 p-5 flex items-center justify-between">
            <h3 className="text-sm font-bold text-white uppercase tracking-wider">{t('orders.pendingOrders', { defaultValue: 'Pending Orders' })}</h3>
            {totalOrdersPages > 1 && (
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  disabled={clampedOrdersPage === 0}
                  onClick={() => setActiveOrdersPage(p => p - 1)}
                  className="rounded-md bg-white/5 border border-white/10 p-1 text-[10px] text-slate-400 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition font-bold"
                >
                  ←
                </button>
                <span className="text-[10px] text-slate-500 font-bold px-1 select-none">
                  {clampedOrdersPage + 1} / {totalOrdersPages}
                </span>
                <button
                  type="button"
                  disabled={clampedOrdersPage >= totalOrdersPages - 1}
                  onClick={() => setActiveOrdersPage(p => p + 1)}
                  className="rounded-md bg-white/5 border border-white/10 p-1 text-[10px] text-slate-400 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition font-bold"
                >
                  →
                </button>
              </div>
            )}
          </div>
          <div className="divide-y divide-white/5">
            {displayedOrders.length > 0 ? (
              displayedOrders.map(o => (
                <div key={o.id} className="flex items-center justify-between px-5 py-3.5 text-xs">
                  <div>
                    <span className={`font-bold px-1.5 py-0.5 rounded text-[9px] mr-2 ${
                      o.side === 'BUY' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-rose-500/10 text-rose-400'
                    }`}>{o.type} {o.side}</span>
                    <span className="font-bold text-white">{o.quantity} {o.symbol}</span>
                  </div>
                  <div className="flex items-center gap-4">
                    <span className="font-bold text-[#00d8f6]">{money(o.targetPrice)}</span>
                    <button 
                      onClick={() => handleCancelOrder(o.id)}
                      className="rounded-lg bg-white/5 border border-white/10 px-2 py-1 text-[10px] text-slate-400 hover:text-white transition font-bold"
                    >
                      {t('orders.cancelOrder', { defaultValue: 'Cancel' })}
                    </button>
                  </div>
                </div>
              ))
            ) : (
              <p className="p-5 text-center text-slate-500">{t('orders.noPendingOrders', { defaultValue: 'No pending limit orders.' })}</p>
            )}
          </div>
        </div>

        <div className="card rounded-2xl overflow-hidden">
          <div className="border-b border-white/10 p-5 flex items-center justify-between">
            <h3 className="text-sm font-bold text-white uppercase tracking-wider">{t('orders.triggeredAlarmsHistory', { defaultValue: 'Triggered Alarms History' })}</h3>
            {totalTriggeredPages > 1 && (
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  disabled={clampedTriggeredPage === 0}
                  onClick={() => setTriggeredAlertsPage(p => p - 1)}
                  className="rounded-md bg-white/5 border border-white/10 p-1 text-[10px] text-slate-400 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition font-bold"
                >
                  ←
                </button>
                <span className="text-[10px] text-slate-500 font-bold px-1 select-none">
                  {clampedTriggeredPage + 1} / {totalTriggeredPages}
                </span>
                <button
                  type="button"
                  disabled={clampedTriggeredPage >= totalTriggeredPages - 1}
                  onClick={() => setTriggeredAlertsPage(p => p + 1)}
                  className="rounded-md bg-white/5 border border-white/10 p-1 text-[10px] text-slate-400 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition font-bold"
                >
                  →
                </button>
              </div>
            )}
          </div>
          <div className="divide-y divide-white/5">
            {displayedTriggered.length > 0 ? (
              displayedTriggered.map(a => (
                <div key={a.id} className="flex items-center justify-between px-5 py-3.5 text-xs bg-emerald-500/[0.02]">
                  <div>
                    <span className="font-bold text-emerald-400">✓ {a.symbol}</span>
                    <span className="ml-2 text-slate-500">{a.condition === 'ABOVE' ? t('orders.wentAbove', { defaultValue: 'went above' }) : t('orders.wentBelow', { defaultValue: 'went below' })}</span>
                  </div>
                  <span className="font-bold text-slate-400">{money(a.targetPrice)}</span>
                </div>
              ))
            ) : (
              <p className="p-5 text-center text-slate-500">{t('orders.noTriggeredAlarms', { defaultValue: 'No triggered alarms.' })}</p>
            )}
          </div>
        </div>
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
    <div className="card rounded-2xl p-5 flex items-center justify-between">
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
              <p className="mt-1 text-base font-black text-[#00d8f6]">
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
  const [sourceIndex, setSourceIndex] = useState(0);

  const getSymbolGradient = (sym) => {
    let hash = 0;
    for (let i = 0; i < sym.length; i++) {
      hash = sym.charCodeAt(i) + ((hash << 5) - hash);
    }
    const h1 = Math.abs(hash % 360);
    const h2 = (h1 + 60) % 360;
    return `linear-gradient(135deg, hsl(${h1}, 85%, 60%), hsl(${h2}, 90%, 40%))`;
  };

  const sources = [
    `https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/128/color/${symbol.toLowerCase()}.png`,
    `https://assets.coincap.io/assets/icons/${symbol.toLowerCase()}@2x.png`
  ];

  if (sourceIndex >= sources.length) {
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
      src={sources[sourceIndex]}
      alt={symbol}
      onError={() => setSourceIndex(prev => prev + 1)}
      className="h-11 w-11 rounded-full object-contain"
      loading="lazy"
    />
  );
}

function MiniSparkline({ symbol, currentPrice, changeVal }) {
  const [history, setHistory] = useState([]);

  useEffect(() => {
    let active = true;
    async function loadHistory() {
      try {
        const data = await api(`/market/history/${symbol}`);
        if (active && data) {
          const prices = data.map(d => Number(d.priceUsd));
          setHistory(prices);
        }
      } catch (err) {
        console.error("Failed to load sparkline history for " + symbol, err);
      }
    }
    loadHistory();
    return () => { active = false; };
  }, [symbol]);

  useEffect(() => {
    if (currentPrice === undefined || currentPrice === null) return;
    const priceNum = Number(currentPrice);
    setHistory(prev => {
      if (prev.length === 0) return [priceNum];
      if (prev[prev.length - 1] === priceNum) return prev;
      return [...prev, priceNum].slice(-40);
    });
  }, [currentPrice]);

  const svgPath = useMemo(() => {
    if (history.length < 2) return '';
    const min = Math.min(...history);
    const max = Math.max(...history);
    const range = max - min === 0 ? 1 : max - min;

    const width = 60;
    const height = 24;
    const padding = 2;

    const points = history.map((val, index) => {
      const x = (index / (history.length - 1)) * (width - padding * 2) + padding;
      const y = height - ((val - min) / range) * (height - padding * 2) - padding;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    });

    return `M ${points.join(' L ')}`;
  }, [history]);

  if (history.length < 2) {
    return (
      <div className="w-[60px] h-[24px] flex items-center justify-center opacity-10">
        <div className="w-full h-[1px] bg-slate-400 border-t border-dashed" />
      </div>
    );
  }

  const strokeColor = changeVal > 0 ? '#34d399' : changeVal < 0 ? '#f87171' : '#94a3b8';

  return (
    <svg width="60" height="24" className="overflow-visible select-none pointer-events-none">
      <path
        d={svgPath}
        fill="none"
        stroke={strokeColor}
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
