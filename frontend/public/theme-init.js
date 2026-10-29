// Applies the saved theme before the first paint (no flash of the wrong theme).
// Kept as a separate file so the Content-Security-Policy does not need 'unsafe-inline'.
;(function () {
  try {
    var theme = localStorage.getItem('cf_theme') || 'system'
    var dark = theme === 'dark' || (theme === 'system' && matchMedia('(prefers-color-scheme: dark)').matches)
    if (dark) document.documentElement.classList.add('dark')
    var meta = document.querySelector('meta[name="theme-color"]')
    if (meta) meta.content = dark ? '#1a1a1f' : '#f8f7f4'
  } catch (e) {
    /* storage unavailable: system theme via CSS */
  }
})()
