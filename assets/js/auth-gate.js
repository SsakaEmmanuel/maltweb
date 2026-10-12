/* MALTWEB v31: require a Supabase session on all pages except the public homepage and auth pages. */
(function () {
  const publicPages = new Set(['index.html', 'login.html', 'register.html']);
  const page = location.pathname.split('/').pop() || 'index.html';
  if (publicPages.has(page)) return;

  const requested = page + location.search + location.hash;
  const safe = /^[a-zA-Z0-9._-]+\.html(?:\?[a-zA-Z0-9_=&%+.,-]*)?(?:#[-a-zA-Z0-9_]*)?$/.test(requested);
  const next = safe ? requested : 'discover.html';
  const loginUrl = 'login.html?next=' + encodeURIComponent(next);
  let redirected = false;

  async function verifySession() {
    if (!window.maltSupabase || redirected) return;
    try {
      const { data, error } = await window.maltSupabase.auth.getSession();
      if (error || !data || !data.session) {
        redirected = true;
        location.replace(loginUrl);
      }
    } catch (_) {
      redirected = true;
      location.replace(loginUrl);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', verifySession, { once: true });
  } else {
    verifySession();
  }
})();
