<?php
require_once __DIR__ . '/config/database.php';
$categories = db()->query('SELECT * FROM categories ORDER BY name')->fetchAll();
?>
<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Categories — MALTWEB</title><link rel="stylesheet" href="assets/css/maltweb.css"></head>
<body><header class="site-header"><a class="brand" href="index.php"><span class="brand-mark">M</span><span>MALT<span>WEB</span></span></a><nav class="desktop-nav"><a href="index.php">Home</a><a href="search.php">Search</a><a class="nav-login" href="auth/login.php">Login</a></nav></header>
<main class="section results-page"><span class="kicker">EXPLORE</span><h1>All categories</h1><div class="category-grid"><?php foreach($categories as $c): ?><a class="category-card" href="search.php?category=<?=urlencode($c['slug'])?>"><span class="category-icon"><?=htmlspecialchars($c['icon'])?></span><span><strong><?=htmlspecialchars($c['name'])?></strong><small><?=htmlspecialchars($c['description'])?></small></span><b>↗</b></a><?php endforeach; ?></div></main></body></html>
