export function getCurrentLanguage() {
  const match = document.cookie.match(/googtrans=\/en\/([a-z]{2})/)
  return match ? match[1] : 'en'
}

export function changeAppLanguage(lang) {
  document.cookie = 'googtrans=;expires=Thu, 01 Jan 1970 00:00:00 UTC;path=/;'
  document.cookie = `googtrans=/en/${lang};path=/;`
  window.location.reload()
}
