-- ============================================
-- Invoice Processor - MySQL Schema
-- Run this once to create the database
-- ============================================

CREATE DATABASE IF NOT EXISTS invoice_db;
USE invoice_db;

-- ── File Transaction Tracking ────────────────
CREATE TABLE IF NOT EXISTS file_transaction (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id  VARCHAR(100) NOT NULL UNIQUE,
    input_file_name VARCHAR(255) NOT NULL,
    output_file_name VARCHAR(255),
    status          ENUM('RECEIVED','PROCESSING','SUCCESS','FAILED') NOT NULL DEFAULT 'RECEIVED',
    error_message   TEXT,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ── Extracted Invoice Data ───────────────────
CREATE TABLE IF NOT EXISTS invoice (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id  VARCHAR(100),
    invoice_number  VARCHAR(100),
    vendor_name     VARCHAR(255),
    invoice_date    DATE,
    total_amount    DECIMAL(15,2),
    currency        VARCHAR(10),
    FOREIGN KEY (transaction_id) REFERENCES file_transaction(transaction_id)
);

-- ── Invoice Line Items ───────────────────────
CREATE TABLE IF NOT EXISTS invoice_line_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id      BIGINT NOT NULL,
    description     VARCHAR(500),
    quantity        INT,
    unit_price      DECIMAL(15,2),
    total_price     DECIMAL(15,2),
    FOREIGN KEY (invoice_id) REFERENCES invoice(id)
);
