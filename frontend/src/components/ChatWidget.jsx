import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import ReactMarkdown from 'react-markdown'
import { api } from '../api/client'

export default function ChatWidget() {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [message, setMessage] = useState('')
  const [messages, setMessages] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const chatEndRef = useRef(null)
  const inputRef = useRef(null)

  const suggestions = [
    t('chat.suggestion1'),
    t('chat.suggestion2'),
    t('chat.suggestion3'),
    t('chat.suggestion4')
  ]

  useEffect(() => { chatEndRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages, busy])
  useEffect(() => { if (open) inputRef.current?.focus() }, [open])

  async function send(e) {
    e.preventDefault()
    if (!message.trim()) return
    const userMessage = message
    setMessages(v => [...v, { role: 'user', text: userMessage }])
    setMessage('')
    setBusy(true)
    setError('')
    try {
      const response = await api('/chat/query', { method: 'POST', body: JSON.stringify({ message: userMessage }) })
      setMessages(v => [...v, { role: 'ai', text: response.answer }])
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
      inputRef.current?.focus()
    }
  }

  function handleSuggestion(text) {
    setMessage(text)
    inputRef.current?.focus()
  }

  return <>
    {/* Floating Toggle Button */}
    <button
      onClick={() => setOpen(v => !v)}
      className="fixed bottom-6 right-6 z-[60] grid h-14 w-14 place-items-center rounded-full bg-gradient-to-br from-[#1fc8a4] to-emerald-600 text-xl text-white shadow-[0_8px_30px_rgba(31,200,164,.4)] transition-all hover:scale-110 hover:shadow-[0_8px_40px_rgba(31,200,164,.55)] active:scale-95"
      aria-label={open ? t('chat.closeChat') : t('chat.aiAssistant')}
    >
      {open
        ? <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        : <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3c.53 0 1.04.02 1.55.07C17.07 3.56 20 6.92 20 11c0 4.42-3.58 8-8 8h-.35a7.96 7.96 0 01-4.65-1.5L3 19l1.5-4A7.96 7.96 0 014 11c0-4.08 2.93-7.44 6.45-7.93C10.96 3.02 11.47 3 12 3z"/><path d="M8 11h.01"/><path d="M12 11h.01"/><path d="M16 11h.01"/></svg>}
    </button>

    {/* Chat Panel */}
    {open && <div className="fixed bottom-24 right-6 z-[60] flex w-[380px] max-w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-2xl border border-white/10 bg-[#0a1929] shadow-[0_25px_80px_rgba(0,0,0,.6)] chat-widget-in" style={{ height: '540px', maxHeight: 'calc(100vh - 8rem)' }}>
      {/* Header */}
      <div className="flex items-center gap-3 border-b border-white/10 bg-[#0c1f33] px-5 py-4">
        <div className="grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br from-[#1fc8a4] to-emerald-600 text-sm font-black text-white shadow-lg shadow-[#1fc8a4]/20">✦</div>
        <div className="flex-1">
          <p className="text-sm font-bold text-white">CryptFlow AI</p>
          <p className="text-[11px] text-slate-500">Gemini · Portfolio Assistant</p>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="h-2 w-2 rounded-full bg-emerald-400 shadow-[0_0_6px_#34d399]" />
          <span className="text-[11px] text-slate-500">{t('chat.online')}</span>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {!messages.length && <div className="flex h-full items-center justify-center text-center">
          <div>
            <div className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-gradient-to-br from-[#1fc8a4]/20 to-emerald-900/20 text-2xl text-[#1fc8a4] ring-1 ring-[#1fc8a4]/20">✦</div>
            <h3 className="mt-4 text-base font-bold text-white">{t('chat.askAboutPortfolio')}</h3>
            <p className="mt-1.5 text-xs text-slate-500 max-w-[260px] mx-auto leading-relaxed">{t('chat.geminiContext')}</p>
            <div className="mt-5 flex flex-wrap justify-center gap-1.5">
              {suggestions.map((s, i) => <button key={i} onClick={() => handleSuggestion(s)} className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[11px] text-slate-400 transition hover:border-[#1fc8a4]/40 hover:bg-[#1fc8a4]/10 hover:text-[#1fc8a4]">{s}</button>)}
            </div>
          </div>
        </div>}

        {messages.map((m, i) => <div key={i} className={`flex ${m.role === 'user' ? 'justify-end' : 'justify-start'}`}>
          {m.role === 'ai' && <div className="mr-2 mt-1 grid h-6 w-6 flex-shrink-0 place-items-center rounded-lg bg-[#1fc8a4]/10 text-[10px] text-[#1fc8a4]">✦</div>}
          <div className={`max-w-[82%] rounded-2xl px-3.5 py-2.5 text-[13px] leading-relaxed ${m.role === 'user' ? 'bg-[#1fc8a4] text-[#06140f] rounded-br-md' : 'bg-[#12243a] text-slate-200 rounded-bl-md ring-1 ring-white/5'}`}>
            {m.role === 'user' ? (
              <div className="whitespace-pre-wrap">{m.text}</div>
            ) : (
              <ReactMarkdown
                components={{
                  ul: (props) => <ul className="list-disc pl-4 space-y-1 my-1" {...props} />,
                  ol: (props) => <ol className="list-decimal pl-4 space-y-1 my-1" {...props} />,
                  li: (props) => <li className="mb-0.5" {...props} />,
                  p: (props) => <p className="mb-1.5 last:mb-0" {...props} />,
                  strong: (props) => <strong className="font-bold text-white" {...props} />,
                  a: (props) => <a className="text-[#1fc8a4] hover:underline" target="_blank" rel="noopener noreferrer" {...props} />,
                  code: ({ className, children, ...props }) => {
                    const match = /language-(\w+)/.exec(className || '')
                    return match ? (
                      <pre className="bg-black/30 rounded p-2 my-2 overflow-x-auto font-mono text-[11px] text-slate-300">
                        <code className={className} {...props}>{children}</code>
                      </pre>
                    ) : (
                      <code className="bg-white/10 rounded px-1.5 py-0.5 text-emerald-400 font-mono text-[11px]" {...props}>{children}</code>
                    )
                  }
                }}
              >
                {m.text}
              </ReactMarkdown>
            )}
          </div>
        </div>)}

        {busy && <div className="flex items-start gap-2">
          <div className="grid h-6 w-6 flex-shrink-0 place-items-center rounded-lg bg-[#1fc8a4]/10 text-[10px] text-[#1fc8a4]">✦</div>
          <div className="rounded-2xl rounded-bl-md bg-[#12243a] px-4 py-3 ring-1 ring-white/5">
            <div className="flex gap-1.5">
              <span className="h-1.5 w-1.5 rounded-full bg-[#1fc8a4] animate-bounce" style={{ animationDelay: '0ms' }} />
              <span className="h-1.5 w-1.5 rounded-full bg-[#1fc8a4] animate-bounce" style={{ animationDelay: '150ms' }} />
              <span className="h-1.5 w-1.5 rounded-full bg-[#1fc8a4] animate-bounce" style={{ animationDelay: '300ms' }} />
            </div>
          </div>
        </div>}
        <div ref={chatEndRef} />
      </div>

      {error && <p className="mx-4 mb-2 rounded-lg bg-red-500/10 p-2.5 text-xs text-red-300">{error}</p>}

      {/* Input */}
      <form onSubmit={send} className="flex items-center gap-2 border-t border-white/10 bg-[#0c1f33] px-4 py-3">
        <input ref={inputRef} className="flex-1 rounded-lg border border-white/10 bg-[#091725] px-3 py-2.5 text-sm text-white placeholder-slate-500 outline-none focus:border-[#1fc8a4] focus:ring-1 focus:ring-[#1fc8a4]/30" maxLength="2000" value={message} onChange={e => setMessage(e.target.value)} placeholder={t('chat.askAnything')} />
        <button disabled={busy || !message.trim()} className="grid h-10 w-10 flex-shrink-0 place-items-center rounded-lg bg-[#1fc8a4] text-[#06140f] transition hover:bg-[#4bdfc0] disabled:opacity-40 disabled:cursor-not-allowed">
          {busy
            ? <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
            : <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>}
        </button>
      </form>
    </div>}
  </>
}
