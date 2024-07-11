<?php
$servername = "localhost";
$username = "username";
$password = "";
$db_name = "database";

// Create connection
$conn = mysqli($servername, $username, $password, $db_name);

// Check connection
if ($conn=TRUE){
echo "Registerd successfully";
} else{ echo "connection failed" 


} 
?>
