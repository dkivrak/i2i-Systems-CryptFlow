import { useState } from 'react';
import { api } from '../api/client';
import { getCurrentLanguage, changeAppLanguage } from '../utils/language';

const MIN_PASSWORD_LENGTH = 8;

export default function ProfileModal({ me, onClose }) {
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [lang, setLang] = useState(() => getCurrentLanguage());

  const formattedDate = me?.createdAt
    ? new Date(me.createdAt).toLocaleDateString(lang === 'tr' ? 'tr-TR' : 'en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      })
    : 'Unknown';

  function handleLanguageChange(newLang) {
    setLang(newLang);
    changeAppLanguage(newLang);
  }

  async function submitPasswordChange(e) {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!oldPassword || !newPassword || !confirmPassword) {
      setError('Please fill all fields.');
      return;
    }

    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      setError(`New password must be at least ${MIN_PASSWORD_LENGTH} characters.`);
      return;
    }

    if (newPassword !== confirmPassword) {
      setError('New passwords do not match.');
      return;
    }

    setBusy(true);
    try {
      await api('/me/change-password', {
        method: 'POST',
        body: JSON.stringify({ oldPassword, newPassword })
      });
      setSuccess('Password updated successfully.');
      setOldPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err) {
      setError(err.message || 'An error occurred while updating password.');
    } finally {
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
        aria-label="Profile and Settings"
        className="card w-full max-w-md rounded-3xl p-7 animate-in space-y-5 max-h-[90vh] overflow-y-auto"
      >
        {/* Title */}
        <div className="flex items-start justify-between">
          <div>
            <p className="label">ACCOUNT SETTINGS</p>
            <h2 className="mt-1 text-3xl font-black">My Profile</h2>
          </div>
          <button
            aria-label="Close"
            onClick={onClose}
            className="text-2xl text-slate-400 hover:text-white transition-colors"
          >
            ×
          </button>
        </div>

        {/* User Info */}
        <div className="rounded-2xl bg-[#081522] p-5 space-y-3">
          <p className="label text-[10px]">USER INFORMATION</p>
          <div className="flex justify-between items-center text-sm">
            <span className="text-slate-400">Email:</span>
            <span className="font-bold text-white">{me?.email}</span>
          </div>
          <div className="flex justify-between items-center text-sm">
            <span className="text-slate-400">Registration Date:</span>
            <span className="font-bold text-white">{formattedDate}</span>
          </div>
        </div>

        {/* Language Selection */}
        <div className="space-y-2">
          <label htmlFor="language-select" className="text-sm text-slate-300 font-bold block">
            Language Preference / Dil Tercihi
          </label>
          <select
            id="language-select"
            value={lang}
            onChange={e => handleLanguageChange(e.target.value)}
            className="input bg-[#091725] text-white border-white/10 w-full"
          >
            <option value="en">English (EN)</option>
            <option value="tr">Türkçe (TR)</option>
          </select>
        </div>

        {/* Change Password Form */}
        <form onSubmit={submitPasswordChange} className="space-y-4 pt-2 border-t border-white/10">
          <p className="label text-[10px]">UPDATE PASSWORD</p>

          <div className="space-y-1">
            <label htmlFor="old-password" className="text-xs text-slate-400">Current Password</label>
            <input
              id="old-password"
              type="password"
              className="input py-2"
              value={oldPassword}
              onChange={e => setOldPassword(e.target.value)}
              placeholder="••••••••"
              autoComplete="current-password"
            />
          </div>

          <div className="space-y-1">
            <label htmlFor="new-password" className="text-xs text-slate-400">New Password</label>
            <input
              id="new-password"
              type="password"
              className="input py-2"
              value={newPassword}
              onChange={e => setNewPassword(e.target.value)}
              placeholder="At least 8 characters"
              autoComplete="new-password"
            />
          </div>

          <div className="space-y-1">
            <label htmlFor="confirm-password" className="text-xs text-slate-400">Confirm New Password</label>
            <input
              id="confirm-password"
              type="password"
              className="input py-2"
              value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              placeholder="Confirm your new password"
              autoComplete="new-password"
            />
          </div>

          {error && <p role="alert" className="rounded-xl bg-red-500/10 p-3 text-xs text-red-300">{error}</p>}
          {success && <p role="alert" className="rounded-xl bg-emerald-500/10 p-3 text-xs text-emerald-300">{success}</p>}

          <button
            type="submit"
            disabled={busy}
            className="btn btn-primary w-full py-2.5"
          >
            {busy ? 'Updating...' : 'Update Password'}
          </button>
        </form>
      </div>
    </div>
  );
}
