import i18n from 'i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import { initReactI18next } from 'react-i18next'

import en from './locales/en.json'
import kk from './locales/kk.json'
import ru from './locales/ru.json'

export const SUPPORTED_LANGUAGES = ['kk', 'ru', 'en'] as const
export type Language = (typeof SUPPORTED_LANGUAGES)[number]

export const resources = {
  kk: { translation: kk },
  ru: { translation: ru },
  en: { translation: en },
} as const

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'ru',
    supportedLngs: SUPPORTED_LANGUAGES,
    nonExplicitSupportedLngs: true,
    interpolation: { escapeValue: false },
    detection: {
      // The choice is remembered in the browser; after sign-in the
      // user's saved preference from the backend takes priority.
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'cf_lang',
      caches: ['localStorage'],
    },
  })

i18n.on('languageChanged', (lng) => {
  document.documentElement.lang = lng
})

export default i18n
