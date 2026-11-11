-- Script d'initialisation de la base de données ms-stock
CREATE USER okit_stock WITH PASSWORD 'okit_stock_password';
CREATE DATABASE okit_stock_db OWNER okit_stock;
GRANT ALL PRIVILEGES ON DATABASE okit_stock_db TO okit_stock;
