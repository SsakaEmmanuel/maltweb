<?php
require_once __DIR__ . '/config/database.php';

$q = trim($_GET['q'] ?? '');
$location = trim($_GET['location'] ?? '');
$category = trim($_GET['category'] ?? '');
$type = trim($_GET['type'] ?? '');

$categories = db()->query('SELECT * FROM categories ORDER BY name')->fetchAll();

$sql = "SELECT l.*, c.name AS category_name, c.slug AS category_slug, c.icon
        FROM listings l
        LEFT JOIN categories c ON c.id=l.category_id
        WHERE l.status='active'";
$params = [];

if ($q !== '') {
    $sql .= " AND (l.name LIKE :q OR l.description LIKE :q OR l.location LIKE :q OR l.address LIKE :q)";
    $params[':q'] = "%$q%";
}
if ($location !== '') {
    $sql .= " AND (l.location LIKE :location OR l.address LIKE :location)";
    $params[':location'] = "%$location%";
}
if ($category !== '') {
    $sql .= " AND c.slug = :category";
    $params[':category'] = $category;
}
if ($type !== '') {
    $sql .= " AND l.listing_type = :type";
    $params[':type'] = $type;
}

$sql .= " ORDER BY l.verified DESC, l.created_at DESC";
$stmt = db()->prepare($sql);
$stmt->execute($params);
$results = $stmt->fetchAll();
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Explore — MALTWEB</title><link rel="stylesheet" href="assets/css/maltweb.css">
</head>
<body>
<header class="site-header">
<a class="brand" href="index.php"><span class="brand-mark">M</span><span>MALT<span>WEB</span></span></a>
<nav class="desktop-nav"><a href="index.php">Home</a><a href="categories.php">Categories</a><a class="nav-login" href="auth/login.php">Login</a></nav>
<button class="menu-toggle">☰</button>
</header>

<main class="results-page section">
<div class="section-heading">
<div><span class="kicker">DISCOVER</span><h1>Find what you need.</h1></div>
</div>

<form class="advanced-search" method="get">
<div class="search-field wide"><span>⌕</span><input name="q" value="<?=htmlspecialchars($q)?>" placeholder="Business, service, school, church, job..."></div>
<div class="search-field"><span>📍</span><input name="location" value="<?=htmlspecialchars($location)?>" placeholder="Area or location"></div>
<select name="category"><option value="">All categories</option>
<?php foreach($categories as $c): ?><option value="<?=htmlspecialchars($c['slug'])?>" <?=$category===$c['slug']?'selected':''?>><?=htmlspecialchars($c['name'])?></option><?php endforeach; ?>
</select>
<select name="type"><option value="">All types</option>
<?php foreach(['business'=>'Business','organization'=>'Organization','service'=>'Service','project'=>'Project','opportunity'=>'Opportunity'] as $v=>$label): ?><option value="<?=$v?>" <?=$type===$v?'selected':''?>><?=$label?></option><?php endforeach; ?>
<button class="button dark-button">Search</button>
</form>

<div class="search-tools">
<p><strong><?=count($results)?></strong> result(s)<?=($location?' in '.htmlspecialchars($location):'')?></p>
<?php if($q||$location||$category||$type): ?><a href="search.php">Clear filters ×</a><?php endif; ?>
</div>

<div class="results-grid">
<?php if (!$results): ?>
<div class="empty-state"><div class="empty-symbol">⌕</div><h2>We couldn't find that yet.</h2><p>Try a different keyword, area or category. New profiles will appear here as MALTWEB grows.</p><a class="button dark-button" href="listing/create.php">Add a profile</a></div>
<?php else: foreach ($results as $item): ?>
<a class="listing-card" href="listing/view.php?slug=<?=urlencode($item['slug'])?>">
<span class="listing-icon"><?=htmlspecialchars($item['icon'] ?: '•')?></span>
<div class="listing-info"><span class="mini-label"><?=htmlspecialchars($item['category_name'] ?: 'Listing')?> · <?=htmlspecialchars(ucfirst($item['listing_type']))?></span>
<h2><?=htmlspecialchars($item['name'])?> <?php if($item['verified']): ?><span class="tiny-verified">✓</span><?php endif; ?></h2>
<p><?=htmlspecialchars($item['description'] ?: 'No description yet.')?></p>
<small>📍 <?=htmlspecialchars($item['location'] ?: 'Location not provided')?></small></div><b class="result-arrow">↗</b>
</a>
<?php endforeach; endif; ?>
</div>
</main>
<script src="assets/js/maltweb.js"></script>
</body></html>
