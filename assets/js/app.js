const $ = (selector) => document.querySelector(selector);

function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (char) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[char]));
}

const online = () => !!window.maltSupabase;

async function loadData() {
  if (!online()) return;

  try {
    const [listingsResult, eventsResult, opportunitiesResult] = await Promise.all([
      maltSupabase
        .from('listings')
        .select('*')
        .eq('status', 'active')
        .order('verified', { ascending: false })
        .order('created_at', { ascending: false }),
      maltSupabase
        .from('events')
        .select('*')
        .eq('status', 'active')
        .order('date', { ascending: true }),
      maltSupabase
        .from('opportunities')
        .select('*')
        .eq('status', 'active')
        .order('created_at', { ascending: false })
    ]);

    if (!listingsResult.error) LISTINGS = listingsResult.data || [];
    if (!eventsResult.error) EVENTS = eventsResult.data || [];
    if (!opportunitiesResult.error) OPPORTUNITIES = opportunitiesResult.data || [];

    return {
      listingsError: listingsResult.error,
      eventsError: eventsResult.error,
      opportunitiesError: opportunitiesResult.error
    };
  } catch (error) {
    console.warn('MALTWEB online data error:', error);
    return { connectionError: error };
  }
}

function card(item) {
  return `<article class="card">
    <span class="verify">${item.verified ? '✓ Verified' : 'Local listing'}</span>
    <h3>${esc(item.name)}</h3>
    <small>${esc(item.category)} • ${esc(item.location)}</small>
    <p>${esc(item.description)}</p>
    <a class="button" href="profile.html?id=${encodeURIComponent(item.id)}">View profile</a>
  </article>`;
}

function render() {
  const params = new URLSearchParams(location.search);
  const query = ($('#q')?.value || params.get('q') || '').toLowerCase();
  const locationFilter = ($('#loc')?.value || params.get('location') || '').toLowerCase();
  const category = $('#cat')?.value || '';
  const type = $('#type')?.value || '';

  const results = LISTINGS.filter((item) =>
    (!query || `${item.name} ${item.category} ${item.description}`.toLowerCase().includes(query)) &&
    (!locationFilter || String(item.location || '').toLowerCase().includes(locationFilter)) &&
    (!category || item.category === category) &&
    (!type || item.type === type)
  );

  if ($('#count')) {
    $('#count').textContent = `${results.length} result${results.length === 1 ? '' : 's'} found`;
  }

  if ($('#results')) {
    $('#results').innerHTML = results.map(card).join('') || '<div class="card">No matches found.</div>';
  }
}

function events() {
  const query = ($('#eq')?.value || '').toLowerCase();
  const results = EVENTS.filter((item) =>
    `${item.name} ${item.description} ${item.location}`.toLowerCase().includes(query)
  );

  if ($('#events')) {
    $('#events').innerHTML = results.map((item) => `
      <article class="card">
        <small>${esc(item.date || '')}</small>
        <h3>${esc(item.name)}</h3>
        <p>${esc(item.category || 'Event')} • ${esc(item.location)}</p>
        <p>${esc(item.description)}</p>
      </article>
    `).join('') || '<div class="card">No events found.</div>';
  }
}

function opps() {
  const query = ($('#oq')?.value || '').toLowerCase();
  const results = OPPORTUNITIES.filter((item) =>
    `${item.name} ${item.description} ${item.location}`.toLowerCase().includes(query)
  );

  if ($('#opps')) {
    $('#opps').innerHTML = results.map((item) => `
      <article class="card">
        <span class="verify">${esc(item.type)}</span>
        <h3>${esc(item.name)}</h3>
        <p>${esc(item.location)}</p>
        <p>${esc(item.description)}</p>
      </article>
    `).join('') || '<div class="card">No opportunities found.</div>';
  }
}

async function profile() {
  const id = new URLSearchParams(location.search).get('id');
  const item = LISTINGS.find((entry) => String(entry.id) === String(id)) || LISTINGS[0];
  if (!item || !$('#profile')) return;

  $('#profile').innerHTML = `<div class="card">
    <h1>${esc(item.name)}</h1>
    <span class="verify">${item.verified ? '✓ Verified' : 'Local listing'}</span>
    <p>${esc(item.category)} • ${esc(item.type)} • ${esc(item.location)}</p>
    <h2>About</h2>
    <p>${esc(item.description)}</p>
    <h2>Contact</h2>
    <p>${esc(item.phone || 'Contact details available soon.')}</p>
    ${item.website ? `<p><a href="${esc(item.website)}" target="_blank" rel="noopener">Visit website</a></p>` : ''}
  </div>`;
}

async function login(event) {
  event.preventDefault();
  if (!online()) {
    alert('MALTWEB could not connect to Supabase. Please refresh the page.');
    return;
  }

  const email = $('#email').value.trim();
  const password = $('#password').value;
  const { error } = await maltSupabase.auth.signInWithPassword({ email, password });

  if (error) {
    alert(error.message);
    return;
  }

  location = 'dashboard.html';
}

async function register(event) {
  event.preventDefault();
  if (!online()) {
    alert('MALTWEB could not connect to Supabase. Please refresh the page.');
    return;
  }

  const name = $('#name').value.trim();
  const email = $('#email').value.trim();
  const password = $('#password').value;
  const role = $('#role').value;

  const { data, error } = await maltSupabase.auth.signUp({
    email,
    password,
    options: { data: { full_name: name, role } }
  });

  if (error) {
    alert(error.message);
    return;
  }

  if (data.user) {
    alert('Account created. Check your email if confirmation is enabled in Supabase.');
  }
  location = 'login.html';
}

async function dashboard() {
  if (!online()) return;

  const { data: { user } } = await maltSupabase.auth.getUser();
  if (!user) {
    location = 'login.html';
    return;
  }

  if ($('#hello')) {
    $('#hello').textContent = `Hello, ${user.user_metadata?.full_name || user.email.split('@')[0]} 👋`;
  }

  if ($('#saved')) {
    $('#saved').innerHTML = '<div class="card">Your online account is connected. Saved profiles can be added in the next MALTWEB module.</div>';
  }
}

async function create(event) {
  event.preventDefault();
  if (!online()) {
    alert('MALTWEB could not connect to Supabase. Please refresh the page.');
    return;
  }

  const { data: { user } } = await maltSupabase.auth.getUser();
  if (!user) {
    location = 'login.html';
    return;
  }

  const payload = {
    name: $('#pn').value.trim(),
    category: $('#pc').value,
    location: $('#pl').value.trim(),
    description: $('#pd').value.trim(),
    phone: $('#pp')?.value.trim() || null,
    type: 'Service Provider',
    verified: false,
    status: 'pending',
    owner_id: user.id
  };

  const { error } = await maltSupabase.from('listings').insert(payload);
  if (error) {
    alert(error.message);
    return;
  }

  if ($('#msg')) $('#msg').textContent = '✓ Profile submitted online for admin review.';
  event.target.reset();
}

async function checkConnection() {
  const status = $('#onlineStatus');
  if (!status) return;

  status.textContent = 'Connecting...';

  if (!online()) {
    status.textContent = 'Connection unavailable';
    return;
  }

  try {
    const { error } = await maltSupabase.from('listings').select('id', { count: 'exact', head: true });
    if (error) throw error;
    status.textContent = '✓ Online database connected';
  } catch (error) {
    console.error('MALTWEB Supabase connection error:', error);
    status.textContent = '⚠ Database connection error';
  }
}

async function init() {
  await checkConnection();
  await loadData();

  if ($('#categories')) {
    $('#categories').innerHTML = CATEGORIES.map((category) =>
      `<a class="cat" href="discover.html?category=${encodeURIComponent(category)}"><b>✦</b>${esc(category)}<small>Discover local options</small></a>`
    ).join('');
  }

  if ($('#cat')) {
    CATEGORIES.forEach((category) => {
      $('#cat').insertAdjacentHTML('beforeend', `<option>${esc(category)}</option>`);
    });

    const params = new URLSearchParams(location.search);
    if (params.get('category')) $('#cat').value = params.get('category');
    render();
  }

  if ($('#profile')) profile();
  if ($('#events')) events();
  if ($('#opps')) opps();

  if ($('#pc')) {
    CATEGORIES.forEach((category) => {
      $('#pc').insertAdjacentHTML('beforeend', `<option>${esc(category)}</option>`);
    });
  }

  if ($('#hello')) dashboard();
}

init();
