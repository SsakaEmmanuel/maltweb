<?php
require_once __DIR__ . '/config/app.php';
require_once __DIR__ . '/config/database.php';

$categories = [
    ['icon'=>'🎓','name'=>'Education','slug'=>'education','desc'=>'Schools, training and learning opportunities.'],
    ['icon'=>'⛪','name'=>'Faith & Community','slug'=>'faith-community','desc'=>'Churches, mosques and community organizations.'],
    ['icon'=>'🏪','name'=>'Businesses','slug'=>'businesses','desc'=>'Local shops, companies and organizations.'],
    ['icon'=>'🛠️','name'=>'Services','slug'=>'services','desc'=>'Find skilled people and professional services.'],
    ['icon'=>'💼','name'=>'Opportunities','slug'=>'opportunities','desc'=>'Jobs, internships, scholarships and training.'],
    ['icon'=>'📅','name'=>'Events','slug'=>'events','desc'=>'Discover what is happening around you.'],
    ['icon'=>'🎨','name'=>'Creative','slug'=>'creative','desc'=>'Designers, artists, photographers and creators.'],
    ['icon'=>'💻','name'=>'Technology','slug'=>'technology','desc'=>'Developers, IT services and digital solutions.'],
    ['icon'=>'🤝','name'=>'Community','slug'=>'community','desc'=>'Projects, charity and local initiatives.'],
    ['icon'=>'🏷️','name'=>'Deals','slug'=>'deals','desc'=>'Offers, promotions and useful local deals.'],
];
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>MALTWEB — Discover. Connect. Interact.</title>
<link rel="stylesheet" href="assets/css/maltweb.css">
</head>
<body>
<header class="site-header">
  <a class="brand" href="index.php"><span class="brand-mark">M</span><span>MALT<span>WEB</span></span></a>
  <nav class="desktop-nav">
    <a href="#explore">Explore</a><a href="#categories">Categories</a><a href="#opportunities">Opportunities</a>
    <a class="nav-login" href="auth/login.php">Login</a>
  </nav>
  <button class="menu-toggle" aria-label="Open menu">☰</button>
</header>

<main>
<section class="hero">
  <div class="hero-glow"></div>
  <div class="hero-content">
    <div class="eyebrow">DISCOVER • CONNECT • INTERACT</div>
    <h1>Everything around you,<br><em>connected in one place.</em></h1>
    <p class="hero-copy">Find businesses, services, organizations, opportunities and events around your area — without jumping from site to site.</p>
    <form class="search-box" action="search.php" method="get">
      <span>⌕</span>
      <input name="q" type="search" placeholder="What are you looking for?" autocomplete="off">
      <select name="location" aria-label="Location">
        <option value="">Any area</option>
        <option value="Kampala">Kampala</option>
        <option value="Wakiso">Wakiso</option>
        <option value="Mukono">Mukono</option>
        <option value="Luweero">Luweero</option><option value="Mukono">Mukono</option><option value="Other">Other area</option>
      </select>
      <button type="submit">Search</button>
    </form>
    <div class="quick-links">
      <span>Try:</span>
      <a href="search.php?q=graphic+designer">Graphic designer</a>
      <a href="search.php?q=school">School</a>
      <a href="search.php?q=church">Church</a>
      <a href="search.php?q=job">Jobs</a>
    </div>
  </div>
</section>

<section class="section" id="explore">
  <div class="section-heading">
    <div><span class="kicker">EXPLORE</span><h2>What are you looking for?</h2></div>
    <a class="text-link" href="categories.php">View all →</a>
  </div>
  <div class="category-grid" id="categories">
  <?php foreach ($categories as $cat): ?>
    <a class="category-card" href="search.php?category=<?=urlencode($cat['slug'])?>">
      <span class="category-icon"><?=$cat['icon']?></span>
      <span><strong><?=htmlspecialchars($cat['name'])?></strong><small><?=htmlspecialchars($cat['desc'])?></small></span>
      <b>↗</b>
    </a>
  <?php endforeach; ?>
  </div>
</section>

<section class="split-section" id="opportunities">
  <div class="feature-card dark">
    <span class="kicker light">LOCAL DISCOVERY</span>
    <h2>Find what is happening around you.</h2>
    <p>Search by need, category or area. MALTWEB is being built to turn local information into useful connections.</p>
    <a class="button light-button" href="search.php">Start exploring</a>
  </div>
  <div class="feature-card outline">
    <span class="kicker">FOR LOCAL PEOPLE</span>
    <h2>Have something to offer?</h2>
    <p>Create a profile for your business, organization or service and become discoverable on MALTWEB.</p>
    <a class="button dark-button" href="auth/register.php">Create a profile</a>
  </div>
</section>
</main>

<footer>
  <div class="footer-brand"><span class="brand-mark">M</span><strong>MALTWEB</strong></div>
  <p>Discover • Connect • Interact</p>
  <small>Built as a local digital ecosystem.</small>
</footer>
<script src="assets/js/maltweb.js"></script>
</body>
</html>
