
CREATE DATABASE my_database;
USE my_database;
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL
);
INSERT INTO users (username, email) VALUES ('john_doe', 'john@example.com');

INSERT INTO users (username, email) VALUES ('jane_smith', 'jane@example.com');