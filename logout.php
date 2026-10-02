<?php
require_once __DIR__ . '/config/app.php';
session_unset(); session_destroy();
header('Location: index.php'); exit;
