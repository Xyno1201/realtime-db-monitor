-- =====================================================================
-- COMPLETE DATABASE SETUP - the ONLY setup script. Run it ONCE per laptop (or again any
-- time you want to start completely fresh).
--
-- HOW: MySQL Workbench -> connect to "Local instance" as ROOT -> File -> Open SQL Script ->
--      this file -> click the lightning bolt (Execute all). The last result tab should show
--      "SETUP OK" with 20 products and 3 triggers.
--      (CLI: mysql -u root -p < sql/setup.sql)
--
-- WHY ROOT: it creates the database, the app's user, and triggers. With MySQL 8's default
-- binary logging, only an administrator may create triggers (otherwise: error 1419).
--
-- WARNING: it DELETES the whole alert_monitor database first (all alerts, changes and
-- products) and builds it again from scratch. Nothing outside alert_monitor is touched.
-- To only reset the demo data, use sql/reset-demo.sql instead.
--
-- alertapp / alertpass are LOCAL THROWAWAY credentials. Never reuse them as a real password.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. DATABASE + the app's user (config/app.properties uses alertapp / alertpass)
-- ---------------------------------------------------------------------
DROP DATABASE IF EXISTS alert_monitor;
CREATE DATABASE alert_monitor
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'alertapp'@'localhost' IDENTIFIED BY 'alertpass';
-- If the user already existed with another password, put it back to the one the app expects.
ALTER USER 'alertapp'@'localhost' IDENTIFIED BY 'alertpass';
GRANT ALL PRIVILEGES ON alert_monitor.* TO 'alertapp'@'localhost';
FLUSH PRIVILEGES;

USE alert_monitor;

-- ---------------------------------------------------------------------
-- 2. CHANGE CAPTURE - filled automatically by the TRIGGERS on products (step 6).
--    One row per INSERT / UPDATE / DELETE on products.
-- ---------------------------------------------------------------------
CREATE TABLE data_changes (
    change_id     INT           NOT NULL AUTO_INCREMENT,
    table_name    VARCHAR(64)   NOT NULL,              -- which table changed ('products')
    row_id        INT           NOT NULL,              -- primary key of the changed row
    change_type   VARCHAR(10)   NOT NULL,              -- INSERT | UPDATE | DELETE
    changed_by    VARCHAR(100)  NOT NULL,              -- MySQL USER() of whoever made the change
    changed_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at  TIMESTAMP     NULL     DEFAULT NULL, -- NULL = the server has not evaluated it yet
    outcome       VARCHAR(255)  NULL     DEFAULT NULL, -- what the rules decided, e.g. "ALERT #12 (HIGH)"
    PRIMARY KEY (change_id),
    INDEX idx_changes_unprocessed (processed_at, change_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- One row per column of the changed row: its value before and after the change.
-- INSERT: old_value is NULL.  DELETE: new_value is NULL.  Values are stored as text.
CREATE TABLE data_change_values (
    change_id   INT           NOT NULL,
    field_name  VARCHAR(64)   NOT NULL,
    old_value   VARCHAR(255)  NULL,
    new_value   VARCHAR(255)  NULL,
    PRIMARY KEY (change_id, field_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3. ALERTS - created by the server when a change matches a rule, then pushed to dashboards.
--    Lifecycle: NEW -> SENT -> RESOLVED.  (Unchanged from v7 section 69.4.)
-- ---------------------------------------------------------------------
CREATE TABLE alerts (
    id             INT          NOT NULL AUTO_INCREMENT,
    type           VARCHAR(30)  NOT NULL,                  -- RECORD_ADDED | RECORD_CHANGED | RECORD_DELETED
    severity       VARCHAR(20)  NOT NULL,                  -- LOW | MEDIUM | HIGH | CRITICAL (enum Severity)
    source         VARCHAR(50)  NOT NULL,                  -- e.g. "products #12"
    message        VARCHAR(255) NOT NULL,                  -- what happened and which rules matched
    status         VARCHAR(20)  NOT NULL DEFAULT 'NEW',    -- NEW | SENT | RESOLVED
    pushed_status  VARCHAR(20)  NULL     DEFAULT NULL,     -- last status broadcast; NULL = never
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- Supports the poller query: WHERE pushed_status IS NULL OR status <> pushed_status
    -- Never "simplify" that predicate: NULL <> 'SENT' is NULL, not TRUE.
    INDEX idx_alerts_status_pushed (status, pushed_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 4. BROADCAST LOG - "where pushed messages go".
--    The server writes one row every time it pushes an alert to the dashboards.
--    Values are COPIED so the history survives Purge (no foreign key on purpose).
-- ---------------------------------------------------------------------
CREATE TABLE alert_broadcast_log (
    log_id            INT          NOT NULL AUTO_INCREMENT,
    alert_id          INT          NOT NULL,
    type              VARCHAR(30)  NOT NULL,
    severity          VARCHAR(20)  NOT NULL,
    source            VARCHAR(50)  NOT NULL,
    message           VARCHAR(255) NOT NULL,
    broadcast_status  VARCHAR(20)  NOT NULL,              -- the status that was pushed
    dashboard_count   INT          NOT NULL,              -- dashboards connected at that moment
    broadcast_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    INDEX idx_broadcast_log_alert (alert_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 5. PRODUCTS - the company data being monitored (stands in for a real product catalogue).
-- ---------------------------------------------------------------------
CREATE TABLE products (
    id        INT            NOT NULL AUTO_INCREMENT,
    name      VARCHAR(100)   NOT NULL,
    category  VARCHAR(50)    NOT NULL,
    price     DECIMAL(10,2)  NOT NULL,
    stock     INT            NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 20 sample products. Inserted BEFORE the triggers exist, so they raise no alerts.
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

-- ---------------------------------------------------------------------
-- 6. TRIGGERS. After every INSERT / UPDATE / DELETE on products - from Workbench, from the
--    Admin's Products tab, from ANY program - MySQL itself records the change in
--    data_changes + data_change_values. The Java server then checks those rows against the
--    product rules R1-R10 (src/dbmonitor/rules/ProductRules.java).
--    Each trigger records: which row, what kind of change, who did it (USER()), and every
--    column's old/new value. DELIMITER lets a trigger body contain ';' - Workbench and the
--    mysql CLI both understand it.
-- ---------------------------------------------------------------------
DELIMITER $$

CREATE TRIGGER trg_products_after_insert
AFTER INSERT ON products
FOR EACH ROW
BEGIN
    DECLARE cid INT;
    INSERT INTO data_changes (table_name, row_id, change_type, changed_by)
        VALUES ('products', NEW.id, 'INSERT', USER());
    SET cid = LAST_INSERT_ID();
    INSERT INTO data_change_values (change_id, field_name, old_value, new_value) VALUES
        (cid, 'name',     NULL, NEW.name),
        (cid, 'category', NULL, NEW.category),
        (cid, 'price',    NULL, NEW.price),
        (cid, 'stock',    NULL, NEW.stock);
END$$

CREATE TRIGGER trg_products_after_update
AFTER UPDATE ON products
FOR EACH ROW
BEGIN
    DECLARE cid INT;
    -- Only record real changes: <=> is MySQL's NULL-safe "equals".
    IF NOT (OLD.name <=> NEW.name AND OLD.category <=> NEW.category
            AND OLD.price <=> NEW.price AND OLD.stock <=> NEW.stock) THEN
        INSERT INTO data_changes (table_name, row_id, change_type, changed_by)
            VALUES ('products', NEW.id, 'UPDATE', USER());
        SET cid = LAST_INSERT_ID();
        INSERT INTO data_change_values (change_id, field_name, old_value, new_value) VALUES
            (cid, 'name',     OLD.name,     NEW.name),
            (cid, 'category', OLD.category, NEW.category),
            (cid, 'price',    OLD.price,    NEW.price),
            (cid, 'stock',    OLD.stock,    NEW.stock);
    END IF;
END$$

CREATE TRIGGER trg_products_after_delete
AFTER DELETE ON products
FOR EACH ROW
BEGIN
    DECLARE cid INT;
    INSERT INTO data_changes (table_name, row_id, change_type, changed_by)
        VALUES ('products', OLD.id, 'DELETE', USER());
    SET cid = LAST_INSERT_ID();
    INSERT INTO data_change_values (change_id, field_name, old_value, new_value) VALUES
        (cid, 'name',     OLD.name,     NULL),
        (cid, 'category', OLD.category, NULL),
        (cid, 'price',    OLD.price,    NULL),
        (cid, 'stock',    OLD.stock,    NULL);
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- 7. CHECK - this result should say SETUP OK, 20 products, 3 triggers, 0 changes.
-- ---------------------------------------------------------------------
SELECT IF((SELECT COUNT(*) FROM products) = 20
          AND (SELECT COUNT(*) FROM information_schema.TRIGGERS
               WHERE TRIGGER_SCHEMA = 'alert_monitor' AND EVENT_OBJECT_TABLE = 'products') = 3,
          'SETUP OK', 'SETUP INCOMPLETE - read the error above') AS result,
       (SELECT COUNT(*) FROM products) AS products,
       (SELECT COUNT(*) FROM information_schema.TRIGGERS
        WHERE TRIGGER_SCHEMA = 'alert_monitor' AND EVENT_OBJECT_TABLE = 'products') AS triggers,
       (SELECT COUNT(*) FROM data_changes) AS changes;
