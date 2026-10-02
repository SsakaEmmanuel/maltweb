<?php
session_start();
require_once __DIR__ . '/config/database.php';

$message = '';
$error = '';

try {
    $db = getDB();
    $schema = file_get_contents(__DIR__ . '/database/schema.sql');
    $db->exec($schema);

    // Backward-compatible migration for older MALTWEB databases.
    $columns = $db->query("PRAGMA table_info(events)")->fetchAll(PDO::FETCH_ASSOC);
    $eventCols = array_column($columns, 'name');

    $addEventCols = [
        'status' => "ALTER TABLE events ADD COLUMN status TEXT NOT NULL DEFAULT 'pending'",
        'event_type' => "ALTER TABLE events ADD COLUMN event_type TEXT DEFAULT 'Community Event'",
        'contact_phone' => "ALTER TABLE events ADD COLUMN contact_phone TEXT",
        'contact_email' => "ALTER TABLE events ADD COLUMN contact_email TEXT",
        'website' => "ALTER TABLE events ADD COLUMN website TEXT"
    ];
    foreach ($addEventCols as $col => $sql) {
        if (!in_array($col, $eventCols, true)) {
            $db->exec($sql);
        }
    }

    // Seed useful event categories.
    $cats = ['Church','Community','Education','Business','Entertainment','Sports','Technology','Training','Other'];
    $stmt = $db->prepare("INSERT OR IGNORE INTO event_categories(name) VALUES (?)");
    foreach ($cats as $cat) $stmt->execute([$cat]);

    $message = 'MALTWEB database is ready. Events and opportunities have been enabled.';
} catch (Throwable $e) {
    $error = $e->getMessage();
}
?>
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>MALTWEB Setup</title>
<link rel="stylesheet" href="assets/css/maltweb.css">
</head>
<body>
<main class="container" style="padding:60px 20px">
<div class="card">
<h1>MALTWEB Setup</h1>
<?php if ($message): ?><div class="notice success"><?= htmlspecialchars($message) ?></div><?php endif; ?>
<?php if ($error): ?><div class="notice error"><?= htmlspecialchars($error) ?></div><?php endif; ?>
<a class="btn" href="index.php">Open MALTWEB</a>
</div>
</main>
</body>
</html>
