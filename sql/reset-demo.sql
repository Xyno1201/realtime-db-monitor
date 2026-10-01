-- =====================================================================
-- RESET FOR A CLEAN DEMO (keeps the database, user and triggers from sql/setup.sql).
-- Stop ServerMain and the Admin first. Run as root or alertapp.
--
-- Empties alerts, the broadcast log and the change history, and puts the 20 sample products
-- back exactly as they were. Nothing else is touched.
--
-- Workbench note: this script uses TRUNCATE, which Workbench's "Safe Updates" mode allows.
-- =====================================================================

USE alert_monitor;

-- Products back to the sample data. TRUNCATE does not fire triggers; the INSERTs below do,
-- which is why the change tables are emptied AFTER them.
TRUNCATE TABLE products;
INSERT INTO products (id, name, category, price, stock) VALUES
 ( 1, 'Wireless Mouse',          'Electronics',   799.00,  120),
 ( 2, 'Mechanical Keyboard',     'Electronics',  3499.00,   45),
 ( 3, '27-inch Monitor',         'Electronics', 15999.00,   18),
 ( 4, 'USB-C Charger 65W',       'Electronics',  1899.00,   60),
 ( 5, 'Noise Cancelling Headset','Electronics',  6499.00,   25),
 ( 6, 'Office Chair',            'Furniture',    8999.00,   12),
 ( 7, 'Standing Desk',           'Furniture',   21999.00,    8),
 ( 8, 'Bookshelf',               'Furniture',    4599.00,   15),
 ( 9, 'A4 Paper (500 sheets)',   'Stationery',    349.00,  400),
 (10, 'Gel Pen (pack of 10)',    'Stationery',    199.00,  650),
 (11, 'Spiral Notebook',         'Stationery',     89.00,  900),
 (12, 'Whiteboard Markers',      'Stationery',    249.00,  220),
 (13, 'Coffee Beans 1kg',        'Pantry',        1199.00,   70),
 (14, 'Green Tea (100 bags)',    'Pantry',         499.00,   95),
 (15, 'Water Bottle',            'Pantry',         399.00,  150),
 (16, 'Laptop Stand',            'Accessories',   1599.00,   40),
 (17, 'Webcam 1080p',            'Accessories',   2799.00,   30),
 (18, 'HDMI Cable 2m',           'Accessories',    399.00,  300),
 (19, 'Desk Lamp',               'Accessories',   1299.00,   35),
 (20, 'External SSD 1TB',        'Accessories',   7499.00,   22);

TRUNCATE TABLE data_change_values;
TRUNCATE TABLE data_changes;
TRUNCATE TABLE alerts;
TRUNCATE TABLE alert_broadcast_log;
