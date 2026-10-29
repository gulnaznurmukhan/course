// Applies the saved theme before the first paint (no flash of the wrong theme).
// Kept as a separate file so the Content-Security-Policy does not need 'unsafe-inline'.
;(function () {
  try {
    var theme = localStorage.getItem('cf_theme') || 'system'
    if (theme === 'system') theme = matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
    var root = document.documentElement
    if (theme === 'dark' || theme === 'pink') root.classList.add(theme)
    var meta = document.querySelector('meta[name="theme-color"]')
    if (meta) meta.content = theme === 'dark' ? '#0c0c0e' : theme === 'pink' ? '#fbdde9' : '#edf0f2'
  } catch (e) {
    /* storage unavailable: system theme via CSS */
  }
})()
