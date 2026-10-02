
<?php
require_once __DIR__ . '/config/database.php';
$db=getDB();
$q=trim($_GET['q']??''); $location=trim($_GET['location']??''); $type=trim($_GET['type']??'');
$where=["e.status='active'"]; $params=[];
if($q!==''){ $where[]="(e.title LIKE ? OR e.description LIKE ? OR e.location LIKE ?)"; $x="%$q%"; array_push($params,$x,$x,$x); }
if($location!==''){ $where[]="e.location LIKE ?"; $params[]="%$location%"; }
if($type!==''){ $where[]="e.event_type=?"; $params[]=$type; }
$sql="SELECT e.*,u.full_name AS owner_name FROM events e LEFT JOIN users u ON u.id=e.owner_id WHERE ".implode(" AND ",$where)." ORDER BY datetime(e.event_date) ASC";
$stmt=$db->prepare($sql);$stmt->execute($params);$events=$stmt->fetchAll(PDO::FETCH_ASSOC);
?>
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Events • MALTWEB</title><link rel="stylesheet" href="assets/css/maltweb.css"></head>
<body><header class="site-header"><div class="container nav"><a class="brand" href="index.php">MALTWEB</a><nav><a href="search.php">Discover</a><a class="active" href="events.php">Events</a><a href="opportunities.php">Opportunities</a><a href="auth/login.php">Login</a></nav></div></header>
<main class="container" style="padding:42px 20px"><div class="section-heading"><div><span class="eyebrow">LOCAL CALENDAR</span><h1>Events around you</h1><p>Discover what is happening and plan your next move.</p></div><a class="btn" href="event/create.php">+ Create event</a></div>
<form class="search-panel" method="get"><input name="q" value="<?=htmlspecialchars($q)?>" placeholder="Search events..."><input name="location" value="<?=htmlspecialchars($location)?>" placeholder="Area / location"><select name="type"><option value="">All types</option><?php foreach(['Church','Community','Education','Business','Entertainment','Sports','Technology','Training','Other'] as $x):?><option value="<?=$x?>" <?=$type===$x?'selected':''?>><?=$x?></option><?php endforeach;?></select><button class="btn" type="submit">Search</button></form>
<div class="card-grid"><?php foreach($events as $e):?><article class="card"><span class="tag"><?=htmlspecialchars($e['event_type']??'Event')?></span><h2><?=htmlspecialchars($e['title'])?></h2><p><?=nl2br(htmlspecialchars($e['description']))?></p><p><strong>When:</strong> <?=htmlspecialchars($e['event_date'])?></p><p><strong>Where:</strong> <?=htmlspecialchars($e['location']?:'Location not specified')?></p><?php if($e['contact_phone']):?><a href="tel:<?=htmlspecialchars($e['contact_phone'])?>">Call organiser</a><?php endif;?></article><?php endforeach;?></div>
<?php if(!$events):?><div class="empty-state"><h2>No events found</h2><p>Try another search or create an event for your community.</p></div><?php endif;?></main></body></html>
