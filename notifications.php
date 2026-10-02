
<?php
session_start(); require_once __DIR__.'/config/database.php';
if(!isset($_SESSION['user'])){header('Location: auth/login.php');exit;}
$db=getDB();$uid=(int)$_SESSION['user']['id'];
if(isset($_GET['read'])){$db->prepare("UPDATE notifications SET is_read=1 WHERE user_id=?")->execute([$uid]);}
$st=$db->prepare("SELECT * FROM notifications WHERE user_id=? ORDER BY id DESC LIMIT 50");$st->execute([$uid]);$items=$st->fetchAll(PDO::FETCH_ASSOC);
?>
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Notifications • MALTWEB</title><link rel="stylesheet" href="assets/css/maltweb.css"></head>
<body><main class="container" style="padding:40px 20px"><a href="dashboard.php">← Dashboard</a><div class="section-heading"><div><span class="eyebrow">UPDATES</span><h1>Notifications</h1><p>Important activity from your MALTWEB account.</p></div><a class="btn secondary" href="?read=1">Mark all read</a></div>
<div class="notification-list"><?php foreach($items as $n):?><article class="card notification-card <?=$n['is_read']?'':'unread'?>"><h2><?=htmlspecialchars($n['title'])?></h2><p><?=htmlspecialchars($n['body'])?></p><small><?=htmlspecialchars($n['created_at'])?></small><?php if($n['link']):?><br><a href="<?=htmlspecialchars($n['link'])?>">Open</a><?php endif;?></article><?php endforeach;?></div>
<?php if(!$items):?><div class="empty-state"><h2>You're all caught up</h2></div><?php endif;?></main></body></html>
