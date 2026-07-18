import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

const LANGUAGE_STORAGE_KEY = 'cryptflow_lang'

function getSavedLanguage() {
  try {
    return localStorage.getItem(LANGUAGE_STORAGE_KEY) || 'en'
  } catch {
    return 'en'
  }
}

const resources = {
  en: {
    translation: {
      // --- Auth Page ---
      auth: {
        paperMarketLab: 'PAPER MARKET LAB',
        moveWithMarket: 'Move with the market.',
        learnWithoutRisk: 'Learn without the risk.',
        heroDescription: 'Trade top cryptocurrencies and virtual assets. Monitor the live stream, backtest your portfolio, and receive context-aware AI insights.',
        disclaimer: 'Educational purposes only — not financial advice.',
        welcomeBack: 'WELCOME BACK',
        startYourLab: 'START YOUR LAB',
        returnToMarket: 'Return to the market.',
        createAccount: 'Create your account.',
        secureEntry: 'Secure entry into your simulation portfolio.',
        autoProvisioned: 'Your starting balance will be automatically provisioned.',
        email: 'Email',
        password: 'Password',
        firstName: 'First Name',
        lastName: 'Last Name',
        accountReady: 'Account ready. You can log in now.',
        processing: 'Processing...',
        login: 'Log in',
        register: 'Register',
        noAccountRegister: "Don't have an account? Register",
        haveAccountLogin: 'Already have an account? Log in'
      },

      // --- Dashboard ---
      dashboard: {
        marketLab: '/ MARKET LAB',
        live: 'Live',
        connecting: 'Connecting',
        disconnected: 'Disconnected',
        stale: 'Stale',
        offline: 'Offline',
        logout: 'Logout',
        portfolioOverview: 'PORTFOLIO OVERVIEW',
        hello: 'Hello,',
        marketOpenDesc: 'The virtual market is open. Test your strategy without risk.',
        totalEquity: 'TOTAL EQUITY',
        availableCash: 'AVAILABLE CASH',
        portfolioValue: 'PORTFOLIO VALUE',
        tabMarket: 'Market',
        tabPortfolio: 'Portfolio',
        tabTransactions: 'Transactions',
        tabGemini: 'Gemini',
        trade: 'TRADE ↗',
        holding: 'Holding',
        lastPriceUpdate: 'Last price update:',
        refreshPrices: 'Refresh Prices',
        waiting: 'waiting',
        assetAllocation: 'ASSET ALLOCATION',
        availableCashDesc: 'Available USD for new orders.',
        noAssets: 'No assets found.',
        colTransaction: 'Transaction',
        colQuantity: 'Quantity',
        colPrice: 'Price',
        colTotal: 'Total',
        colTime: 'Time',
        noTransactions: 'No transactions yet.',
        notifications: 'Notifications',
        close: 'Close',
        favorites: 'Favorites',
        noFavorites: 'No favorites added yet.',
        addFavorite: 'Add to favorites',
        removeFavorite: 'Remove from favorites',
        dailySummaryTitle: 'Daily AI Summary',
        goToPortfolio: 'Go to Portfolio',
        noNotifications: 'No new notifications.',
        totalCoinValue: 'Total Crypto Value',
        totalCoinValueDesc: 'Total estimated value of your cryptocurrency holdings.',
        pageInfo: 'Showing: {{start}}-{{end}} / Total: {{total}}',
        prevPage: 'Previous',
        nextPage: 'Next',
        hoverChart: 'Inspect Allocation',
        hoverDesc: 'Hover over segments to view details.',
        allocation: 'allocation',
        searchPlaceholder: 'Search coin... (e.g. BTC, SOL)'
      },

      // --- Chat Widget ---
      chat: {
        suggestion1: 'What is the risk distribution of my portfolio?',
        suggestion2: 'Summarize my recent transactions',
        suggestion3: 'Which asset should I invest in?',
        suggestion4: 'What is my wallet balance?',
        closeChat: 'Close chat',
        aiAssistant: 'AI Assistant',
        online: 'Online',
        aiContextTitle: 'AI CONTEXT',
        usdAndPortfolio: '✓ USD balance and portfolio',
        recent20Trades: '✓ Recent 20 transactions',
        currentPrices: '✓ Current market prices',
        priceTrends: '✓ Recent price trends',
        askAboutPortfolio: 'Ask about your portfolio',
        geminiContext: 'Gemini uses current prices, asset holdings, and transactions as context.',
        chatInputPlaceholder: 'How is the risk distribution of my portfolio?',
        sendButton: 'Send',
        askAnything: 'Ask anything...'
      },

      // --- Trade Modal ---
      trade: {
        newOrder: 'NEW ORDER',
        symbolTransaction: '{{symbol}} Transaction',
        lockedPrice: 'Locked price',
        priceLockCountdown: 'Price locked for 30 seconds. Countdown: {{seconds}}s',
        priceLockExpired: 'The 30-second price lock has expired. Close and reopen the trade to get a new price.',
        priceLockExpiredButton: 'Price Lock Expired',
        buy: 'Buy',
        sell: 'Sell',
        noBalanceWarning: 'You must have USD balance or crypto assets to perform a transaction.',
        coinQuantity: 'Coin quantity',
        estimatedTotal: 'Estimated total',
        available: 'Available:',
        owned: 'Owned:',
        insufficientFunds: 'Insufficient funds. Available: {{amount}}',
        insufficientAsset: 'Insufficient asset balance. Available: {{quantity}} {{symbol}}',
        priceUnavailable: 'Price data is unavailable.',
        priceStale: 'Price data is stale.',
        insufficientUsd: 'Insufficient USD balance for this transaction.',
        insufficientAssetSimple: 'Insufficient asset balance for sale.',
        invalidAmount: 'Quantity must be positive with at most 8 decimal places.',
        minimumOrderValue: 'Total order value must be at least $0.01.',
        livePrice: 'Live Price',
        processingOrder: 'Processing order...',
        executeOrder: 'Execute Order',
        successTitle: 'Transaction Successful!',
        receiptDesc: 'Your transaction has been processed securely.',
        receiptType: 'Transaction Type',
        receiptAsset: 'Asset',
        receiptQuantity: 'Quantity',
        receiptPrice: 'Unit Price',
        receiptTotal: 'Total Amount',
        closeReceipt: 'Done',
        confirmTitle: 'Order Confirmation',
        confirmDesc: 'Please confirm the transaction details below.',
        approveOrder: 'Yes, Approve',
        backToEdit: 'Go Back'
      },

      // --- Profile Modal ---
      profile: {
        accountSettings: 'ACCOUNT SETTINGS',
        myProfile: 'My Profile',
        userInformation: 'USER INFORMATION',
        emailLabel: 'Email:',
        registrationDate: 'Registration Date:',
        languagePreference: 'Language Preference',
        updatePasswordTitle: 'UPDATE PASSWORD',
        currentPassword: 'Current Password',
        newPassword: 'New Password',
        confirmNewPassword: 'Confirm New Password',
        fillAllFields: 'Please fill all fields.',
        passwordMinLength: 'New password must be at least {{count}} characters.',
        passwordsNoMatch: 'New passwords do not match.',
        passwordUpdated: 'Password updated successfully.',
        passwordUpdateError: 'An error occurred while updating password.',
        updating: 'Updating...',
        updatePassword: 'Update Password',
        atLeastChars: 'At least 8 characters',
        confirmYourPassword: 'Confirm your new password',
        unknown: 'Unknown',
        dangerZone: 'DANGER ZONE',
        deleteAccountDesc: 'Permanently deleting your account will remove all transaction history and wallet balances. This action is irreversible.',
        areYouSure: 'Are you sure? This cannot be undone.',
        deleting: 'Deleting...',
        yesDelete: 'Yes, Delete My Account',
        cancel: 'Cancel',
        deleteAccount: 'Permanently Delete Account'
      }
    }
  },

  tr: {
    translation: {
      // --- Auth Page ---
      auth: {
        paperMarketLab: 'KAĞIT PİYASA LABORATUVARI',
        moveWithMarket: 'Piyasa ile hareket et.',
        learnWithoutRisk: 'Risk almadan öğren.',
        heroDescription: 'En popüler kripto para ve sanal varlıklarla işlem yapın. Canlı akışı izleyin, portföyünüzü test edin ve yapay zeka destekli analizler alın.',
        disclaimer: 'Yalnızca eğitim amaçlıdır — finansal tavsiye değildir.',
        welcomeBack: 'HOŞ GELDİNİZ',
        startYourLab: 'LABORATUVARINIZI KURUN',
        returnToMarket: 'Piyasaya geri dön.',
        createAccount: 'Hesabınızı oluşturun.',
        secureEntry: 'Simülasyon portföyünüze güvenli giriş.',
        autoProvisioned: 'Başlangıç bakiyeniz otomatik olarak sağlanacaktır.',
        email: 'E-posta',
        password: 'Şifre',
        firstName: 'Ad',
        lastName: 'Soyad',
        accountReady: 'Hesap hazır. Şimdi giriş yapabilirsiniz.',
        processing: 'İşleniyor...',
        login: 'Giriş Yap',
        register: 'Kayıt Ol',
        noAccountRegister: 'Hesabınız yok mu? Kayıt olun',
        haveAccountLogin: 'Zaten hesabınız var mı? Giriş yapın'
      },

      // --- Dashboard ---
      dashboard: {
        marketLab: '/ PİYASA LAB',
        live: 'Canlı',
        connecting: 'Bağlanıyor',
        disconnected: 'Bağlantı Kesildi',
        stale: 'Güncel Değil',
        offline: 'Çevrimdışı',
        logout: 'Çıkış',
        portfolioOverview: 'PORTFÖY ÖZETİ',
        hello: 'Merhaba,',
        marketOpenDesc: 'Sanal piyasa açık. Stratejinizi risksiz test edin.',
        totalEquity: 'TOPLAM VARLIK',
        availableCash: 'MEVCUT NAKİT',
        portfolioValue: 'PORTFÖY DEĞERİ',
        tabMarket: 'Piyasa',
        tabPortfolio: 'Portföy',
        tabTransactions: 'İşlemler',
        tabGemini: 'Gemini',
        trade: 'İŞLEM ↗',
        holding: 'Miktar',
        lastPriceUpdate: 'Son fiyat güncellemesi:',
        refreshPrices: 'Fiyatları Yenile',
        waiting: 'bekleniyor',
        assetAllocation: 'VARLIK DAĞILIMI',
        availableCashDesc: 'Yeni alım emirleri için kullanılabilir USD.',
        noAssets: 'Varlık bulunamadı.',
        colTransaction: 'İşlem',
        colQuantity: 'Miktar',
        colPrice: 'Fiyat',
        colTotal: 'Toplam',
        colTime: 'Zaman',
        noTransactions: 'Henüz işlem yok.',
        notifications: 'Bildirimler',
        close: 'Kapat',
        favorites: 'Favoriler',
        noFavorites: 'Henüz favori eklenmedi.',
        addFavorite: 'Favorilere ekle',
        removeFavorite: 'Favorilerden çıkar',
        dailySummaryTitle: 'Günlük Yapay Zeka Özeti',
        goToPortfolio: 'Portföye Git',
        noNotifications: 'Yeni bildirim yok.',
        totalCoinValue: 'Sahip Olunan Kripto Değeri',
        totalCoinValueDesc: 'Sahip olduğunuz kripto varlıkların toplam tahmini değeri.',
        pageInfo: 'Gösterilen: {{start}}-{{end}} / Toplam: {{total}}',
        prevPage: 'Geri',
        nextPage: 'İleri',
        hoverChart: 'Dağılımı İncele',
        hoverDesc: 'Grafik dilimlerinin üzerine gelerek detayları görün.',
        allocation: 'oranında',
        searchPlaceholder: 'Coin ara... (örn: BTC, SOL)'
      },

      // --- Chat Widget ---
      chat: {
        suggestion1: 'Portföyümün risk dağılımı nedir?',
        suggestion2: 'Son işlemlerimi özetle',
        suggestion3: 'Hangi varlığa yatırım yapmalıyım?',
        suggestion4: 'Cüzdan bakiyem ne kadar?',
        closeChat: 'Sohbeti kapat',
        aiAssistant: 'Yapay Zeka Asistanı',
        online: 'Çevrimiçi',
        aiContextTitle: 'AI CONTEXT',
        usdAndPortfolio: '✓ USD bakiyesi ve portföy',
        recent20Trades: '✓ Son 20 işlem',
        currentPrices: '✓ Güncel piyasa fiyatları',
        priceTrends: '✓ Son fiyat hareketleri',
        askAboutPortfolio: 'Portföyün hakkında sor',
        geminiContext: 'Gemini güncel fiyatları, varlıklarını ve son işlemlerini bağlam olarak kullanır.',
        chatInputPlaceholder: 'Portföyümdeki risk dağılımı nasıl?',
        sendButton: 'Gönder',
        askAnything: 'Bir şey sorun...'
      },

      // --- Trade Modal ---
      trade: {
        newOrder: 'YENİ EMİR',
        symbolTransaction: '{{symbol}} İşlemi',
        lockedPrice: 'Sabitlenmiş fiyat',
        priceLockCountdown: 'Fiyat 30 saniyeliğine kilitlendi. Geri sayım: {{seconds}} sn',
        priceLockExpired: '30 saniyelik fiyat kilidinin süresi doldu. Yeni fiyat için işlemi kapatıp yeniden açın.',
        priceLockExpiredButton: 'Fiyat Kilidi Süresi Doldu',
        buy: 'Al',
        sell: 'Sat',
        noBalanceWarning: 'İşlem yapabilmek için USD bakiyeniz veya kripto varlıklarınız olmalıdır.',
        coinQuantity: 'Coin miktarı',
        estimatedTotal: 'Tahmini toplam',
        available: 'Mevcut:',
        owned: 'Sahip olunan:',
        insufficientFunds: 'Yetersiz bakiye. Mevcut: {{amount}}',
        insufficientAsset: 'Yetersiz varlık bakiyesi. Mevcut: {{quantity}} {{symbol}}',
        priceUnavailable: 'Fiyat verisi kullanılamıyor.',
        priceStale: 'Fiyat verisi güncel değil.',
        insufficientUsd: 'Bu işlem için yeterli USD bakiyeniz yok.',
        insufficientAssetSimple: 'Satış için yeterli varlık bakiyeniz yok.',
        invalidAmount: 'Miktar en fazla 8 ondalık basamaklı pozitif bir sayı olmalıdır.',
        minimumOrderValue: 'Toplam işlem tutarı en az $0.01 olmalıdır.',
        livePrice: 'Canlı Fiyat',
        processingOrder: 'Emir işleniyor...',
        executeOrder: 'Emri Gerçekleştir',
        successTitle: 'İşlem Başarılı!',
        receiptDesc: 'İşleminiz güvenli bir şekilde gerçekleştirildi.',
        receiptType: 'İşlem Tipi',
        receiptAsset: 'Varlık',
        receiptQuantity: 'Miktar',
        receiptPrice: 'Birim Fiyat',
        receiptTotal: 'Toplam Tutar',
        closeReceipt: 'Tamam',
        confirmTitle: 'İşlem Onayı',
        confirmDesc: 'Lütfen aşağıdaki işlem detaylarını onaylayın.',
        approveOrder: 'Evet, Onayla',
        backToEdit: 'Geri Dön'
      },

      // --- Profile Modal ---
      profile: {
        accountSettings: 'HESAP AYARLARI',
        myProfile: 'Profilim',
        userInformation: 'KULLANICI BİLGİLERİ',
        emailLabel: 'E-posta:',
        registrationDate: 'Kayıt Tarihi:',
        languagePreference: 'Dil Tercihi',
        updatePasswordTitle: 'ŞİFRE GÜNCELLE',
        currentPassword: 'Mevcut Şifre',
        newPassword: 'Yeni Şifre',
        confirmNewPassword: 'Yeni Şifreyi Onayla',
        fillAllFields: 'Lütfen tüm alanları doldurun.',
        passwordMinLength: 'Yeni şifre en az {{count}} karakter olmalıdır.',
        passwordsNoMatch: 'Yeni şifreler eşleşmiyor.',
        passwordUpdated: 'Şifre başarıyla güncellendi.',
        passwordUpdateError: 'Şifre güncellenirken bir hata oluştu.',
        updating: 'Güncelleniyor...',
        updatePassword: 'Şifreyi Güncelle',
        atLeastChars: 'En az 8 karakter',
        confirmYourPassword: 'Yeni şifrenizi onaylayın',
        unknown: 'Bilinmiyor',
        dangerZone: 'TEHLİKELİ BÖLGE',
        deleteAccountDesc: 'Hesabınızı kalıcı olarak sildiğinizde, tüm işlem geçmişiniz ve cüzdan bakiyeniz geri döndürülemeyecek şekilde silinecektir.',
        areYouSure: 'Emin misiniz? Bu işlem geri alınamaz.',
        deleting: 'Siliniyor...',
        yesDelete: 'Evet, Hesabımı Sil',
        cancel: 'Vazgeç',
        deleteAccount: 'Hesabı Kalıcı Olarak Sil'
      }
    }
  }
}

i18n
  .use(initReactI18next)
  .init({
    resources,
    lng: getSavedLanguage(),
    fallbackLng: 'en',
    interpolation: { escapeValue: false },
    react: { useSuspense: false }
  })

i18n.on('languageChanged', (lng) => {
  try {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, lng)
  } catch {
    // localStorage unavailable — silently ignore
  }
})

export default i18n
