import i18n from '../i18n'

export function getCurrentLanguage() {
  return i18n.language || 'en'
}

export function changeAppLanguage(lang) {
  i18n.changeLanguage(lang)
}
