import ReactMarkdown from 'react-markdown'

const allowedElements = [
  'p',
  'strong',
  'em',
  'ul',
  'ol',
  'li',
  'code',
  'pre',
  'blockquote',
  'br',
]

const components = {
  p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
  strong: ({ children }) => <strong className="font-bold text-white">{children}</strong>,
  em: ({ children }) => <em className="italic">{children}</em>,
  ul: ({ children }) => <ul className="my-2 list-disc space-y-1 pl-5">{children}</ul>,
  ol: ({ children }) => <ol className="my-2 list-decimal space-y-1 pl-5">{children}</ol>,
  li: ({ children }) => <li>{children}</li>,
  blockquote: ({ children }) => (
    <blockquote className="my-2 border-l-2 border-[#1fc8a4]/60 pl-3 text-slate-400">
      {children}
    </blockquote>
  ),
  pre: ({ children }) => (
    <pre className="my-2 overflow-x-auto rounded-lg bg-black/20 p-3 text-xs">{children}</pre>
  ),
  code: ({ children }) => (
    <code className="rounded bg-black/20 px-1 py-0.5 font-mono text-xs text-emerald-200">
      {children}
    </code>
  ),
}

export default function AssistantMarkdown({ text }) {
  return (
    <ReactMarkdown
      allowedElements={allowedElements}
      components={components}
      skipHtml
      unwrapDisallowed
    >
      {text}
    </ReactMarkdown>
  )
}
