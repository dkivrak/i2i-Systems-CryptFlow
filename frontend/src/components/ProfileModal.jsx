import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, token } from '../api/client';
import { changeAppLanguage } from '../utils/language';

const MIN_PASSWORD_LENGTH = 8;

export default function ProfileModal({ me, onClose, onLogout }) {
  const { t, i18n } = useTranslation();
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [showConfirmDelete, setShowConfirmDelete] = useState(false);

  const lang = i18n.language;

  const formattedDate = me?.createdAt
    ? new Date(me.createdAt).toLocaleDateString(lang === 'tr' ? 'tr-TR' : 'en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      })
    : t('profile.unknown');

  function handleLanguageChange(newLang) {
    changeAppLanguage(newLang);
  }

  async function submitPasswordChange(e) {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!oldPassword || !newPassword || !confirmPassword) {
      setError(t('profile.fillAllFields'));
      return;
    }

    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      setError(t('profile.passwordMinLength', { count: MIN_PASSWORD_LENGTH }));
      return;
    }

    if (newPassword !== confirmPassword) {
      setError(t('profile.passwordsNoMatch'));
      return;
    }

    setBusy(true);
    try {
      await api('/me/change-password', {
        method: 'POST',
        body: JSON.stringify({ oldPassword, newPassword })
      });
      setSuccess(t('profile.passwordUpdated'));
      setOldPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err) {
      setError(err.message || t('profile.passwordUpdateError'));
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteAccount() {
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      await api('/me', { method: 'DELETE' });
      token.clear();
      sessionStorage.clear();
      onClose();
      onLogout();
    } catch (err) {
      setError(err.message || 'Failed to delete account');
      setBusy(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm"
      onMouseDown={e => e.target === e.currentTarget && onClose()}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={t('profile.myProfile')}
        className="card w-full max-w-2xl rounded-3xl p-7 sm:p-8 animate-in space-y-6 max-h-[90vh] overflow-y-auto"
      >
        {/* Title */}
        <div className="flex items-start justify-between border-b border-white/10 pb-4">
          <div>
            <p className="label">{t('profile.accountSettings')}</p>
            <h2 className="mt-1 text-3xl font-black">{t('profile.myProfile')}</h2>
          </div>
          <button
            aria-label="Close"
            onClick={onClose}
            className="text-3xl text-slate-400 hover:text-white transition-colors leading-none"
          >
            ×
          </button>
        </div>

        {/* 2-Column Grid Layout */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 items-start">
          {/* Left Column: Account Details & Settings */}
          <div className="space-y-5">
            {/* User Info */}
            <div className="rounded-2xl bg-[#081522] p-5 space-y-3 border border-white/5">
              <p className="label text-[10px] tracking-wider">{t('profile.userInformation')}</p>
              <div className="flex justify-between items-center text-sm">
                <span className="text-slate-400">{t('profile.emailLabel')}</span>
                <span className="font-bold text-white truncate max-w-[170px]" title={me?.email}>{me?.email}</span>
              </div>
              <div className="flex justify-between items-center text-sm">
                <span className="text-slate-400">{t('profile.registrationDate')}</span>
                <span className="font-bold text-white text-right">{formattedDate}</span>
              </div>
            </div>

            {/* Language Selection */}
            <div className="space-y-2">
              <label htmlFor="language-select" className="text-xs text-slate-400 block font-bold">
                {t('profile.languagePreference')}
              </label>
              <select
                id="language-select"
                value={lang}
                onChange={e => handleLanguageChange(e.target.value)}
                className="input bg-[#091725] text-white border-white/10 w-full text-sm"
              >
                <option value="en">English (EN)</option>
                <option value="tr">Türkçe (TR)</option>
              </select>
            </div>

            {/* Delete Account */}
            <div className="pt-4 border-t border-white/10 space-y-3">
              <p className="label text-[10px] text-rose-400 tracking-wider font-bold">
                {t('profile.dangerZone')}
              </p>
              <p className="text-xs text-slate-500 leading-relaxed">
                {t('profile.deleteAccountDesc')}
              </p>
              {showConfirmDelete ? (
                <div className="space-y-2 rounded-2xl bg-red-500/10 p-4 border border-red-500/20">
                  <p className="text-xs text-red-300 font-bold">
                    {t('profile.areYouSure')}
                  </p>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={handleDeleteAccount}
                      disabled={busy}
                      className="btn bg-red-600 hover:bg-red-700 text-white flex-1 py-1.5 text-xs font-bold transition"
                    >
                      {busy ? t('profile.deleting') : t('profile.yesDelete')}
                    </button>
                    <button
                      type="button"
                      onClick={() => setShowConfirmDelete(false)}
                      disabled={busy}
                      className="btn bg-white/10 hover:bg-white/20 text-white flex-1 py-1.5 text-xs font-bold transition"
                    >
                      {t('profile.cancel')}
                    </button>
                  </div>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => setShowConfirmDelete(true)}
                  className="btn bg-rose-950/40 hover:bg-rose-900/60 border border-rose-500/30 text-rose-400 w-full py-2.5 font-bold transition"
                >
                  {t('profile.deleteAccount')}
                </button>
              )}
            </div>
          </div>

          {/* Right Column: Change Password */}
          <form onSubmit={submitPasswordChange} className="space-y-4">
            <p className="label text-[10px] tracking-wider">{t('profile.updatePasswordTitle')}</p>

            <div className="space-y-1">
              <label htmlFor="old-password" className="text-xs text-slate-400">{t('profile.currentPassword')}</label>
              <input
                id="old-password"
                type="password"
                className="input py-2 text-sm"
                value={oldPassword}
                onChange={e => setOldPassword(e.target.value)}
                placeholder="••••••••"
                autoComplete="current-password"
              />
            </div>

            <div className="space-y-1">
              <label htmlFor="new-password" className="text-xs text-slate-400">{t('profile.newPassword')}</label>
              <input
                id="new-password"
                type="password"
                className="input py-2 text-sm"
                value={newPassword}
                onChange={e => setNewPassword(e.target.value)}
                placeholder={t('profile.atLeastChars')}
                autoComplete="new-password"
              />
            </div>

            <div className="space-y-1">
              <label htmlFor="confirm-password" className="text-xs text-slate-400">{t('profile.confirmNewPassword')}</label>
              <input
                id="confirm-password"
                type="password"
                className="input py-2 text-sm"
                value={confirmPassword}
                onChange={e => setConfirmPassword(e.target.value)}
                placeholder={t('profile.confirmYourPassword')}
                autoComplete="new-password"
              />
            </div>

            {error && <p role="alert" className="rounded-xl bg-red-500/10 p-3 text-xs text-red-300">{error}</p>}
            {success && <p role="alert" className="rounded-xl bg-emerald-500/10 p-3 text-xs text-emerald-300">{success}</p>}

            <button
              type="submit"
              disabled={busy}
              className="btn btn-primary w-full py-2.5 mt-2"
            >
              {busy ? t('profile.updating') : t('profile.updatePassword')}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
