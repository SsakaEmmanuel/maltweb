<?php
require_once __DIR__ . '/config/database.php';
if (isset($_SESSION['user']) && in_array($_SESSION['user']['role'], ['business','service_provider'], true)) {
    header('Location: business/dashboard.php'); exit;
}
if (isset($_SESSION['user']) && $_SESSION['user']['role'] === 'admin') {
    header('Location: admin/index.php'); exit;
}

if (empty($_SESSION['user_id'])) { header('Location: auth/login.php'); exit; }
$stmt = db()->prepare('SELECT full_name,role FROM users WHERE id=?');
$stmt->execute([$_SESSION['user_id']]);
$user = $stmt->fetch();
?>
<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Dashboard — MALTWEB</title><link rel="stylesheet" href="assets/css/maltweb.css"></head>
<body><header class="site-header"><a class="brand" href="index.php"><span class="brand-mark">M</span><span>MALT<span>WEB</span></span></a><nav class="desktop-nav"><a href="index.php">Explore</a><a href="logout.php">Logout</a></nav></header>
<main class="section dashboard"><span class="kicker">YOUR MALTWEB</span><h1>Welcome, <?=htmlspecialchars($user['full_name'])?>.</h1><p class="hero-copy">Account type: <strong><?=htmlspecialchars($user['role'])?></strong></p><a class="button dark-button" href="listing/create.php">+ Create a profile</a><div class="dashboard-grid"><div class="dash-card"><span>01</span><h2>Your profile</h2><p>Your public profile will appear here.</p></div><div class="dash-card"><span>02</span><h2>Your listings</h2><p>Add and manage businesses, services or organizations.</p></div><div class="dash-card"><span>03</span><h2>Saved items</h2><p>Keep useful places and services for later.</p></div></div><section class="dashboard-actions"><a class="btn" href="event/create.php">+ Create Event</a><a class="btn secondary" href="opportunity/create.php">+ Post Opportunity</a></section></main></body></html>
