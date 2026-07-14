import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import AssistantMarkdown from './AssistantMarkdown'

describe('AssistantMarkdown', () => {
  it('renders common Gemini Markdown formatting', () => {
    const html = renderToStaticMarkup(
      <AssistantMarkdown text={'**BTC** güçlüdür.\n\n- Bir\n- İki'} />,
    )

    expect(html).toContain('<strong class="font-bold text-white">BTC</strong>')
    expect(html).toContain('<ul')
    expect(html).toContain('<li>Bir</li>')
  })

  it('drops raw HTML and does not create links or images', () => {
    const html = renderToStaticMarkup(
      <AssistantMarkdown
        text={'<script>alert(1)</script>\n[zararlı](javascript:alert(2))\n![izleme](https://example.com/x.png)'}
      />,
    )

    expect(html).not.toContain('<script')
    expect(html).not.toContain('javascript:')
    expect(html).not.toContain('<a')
    expect(html).not.toContain('<img')
    expect(html).toContain('zararlı')
  })
})
