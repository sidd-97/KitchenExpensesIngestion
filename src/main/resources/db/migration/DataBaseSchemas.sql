-- ─────────────────────────────────────────────
-- TABLE 1: file_metadata  (central audit + idempotency)
-- ─────────────────────────────────────────────
CREATE TABLE file_metadata (
    id                BIGSERIAL    PRIMARY KEY,
    file_name         TEXT         NOT NULL,
    source            TEXT         NOT NULL,       -- SWIGGY | ZOMATO | OFFLINE
    processing_type   TEXT         NOT NULL,       -- ORDER | INVOICE
    file_origin       TEXT         NOT NULL,       -- LOCAL | S3
    idempotency_key   TEXT         NOT NULL UNIQUE, -- SHA-256(source+type+startDate+endDate)
    report_start_date DATE,
    report_end_date   DATE,
    status            TEXT         NOT NULL DEFAULT 'PENDING',
    -- PENDING | PROCESSING | PROCESSED | FAILED | DUPLICATE | REVIEW
    total_rows        INT          NOT NULL DEFAULT 0,
    processed_rows    INT          NOT NULL DEFAULT 0,
    failed_rows       INT          NOT NULL DEFAULT 0,
    error_message     TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at      TIMESTAMPTZ
);
CREATE INDEX idx_fm_source_type   ON file_metadata(source, processing_type);
CREATE INDEX idx_fm_dates         ON file_metadata(report_start_date, report_end_date);
CREATE INDEX idx_fm_status        ON file_metadata(status);
CREATE INDEX idx_fm_idem_key      ON file_metadata(idempotency_key);


-- ─────────────────────────────────────────────
-- TABLE 2: swiggy_orders
-- ─────────────────────────────────────────────
CREATE TABLE swiggy_orders (
    id                               BIGSERIAL    PRIMARY KEY,
    file_metadata_id                 BIGINT       NOT NULL REFERENCES file_metadata(id),
    file_name                        TEXT         NOT NULL,
    source                           TEXT         NOT NULL DEFAULT 'SWIGGY',
    file_origin                      TEXT         NOT NULL,
    -- core order fields
    order_id                         TEXT,
    order_status                     TEXT,
    order_relay_time                 TIMESTAMPTZ,   -- "Order-relay-time(ordered time)"
    order_acceptance_time            TIMESTAMPTZ,   -- "Order-acceptance-time <placed_time>"
    order_delivery_time              TIMESTAMPTZ,
    order_cancellation_time          TIMESTAMPTZ,
    -- financials
    total_bill_amount                NUMERIC(12,2),
    tax_restaurant                   NUMERIC(12,2),
    packing_charge                   NUMERIC(12,2),
    restaurant_trade_discount        NUMERIC(12,2),
    restaurant_coupon_discount_share NUMERIC(12,2),
    restaurant_bear                  NUMERIC(12,2),
    -- item GST
    item_sgst                        NUMERIC(12,2),
    item_cgst                        NUMERIC(12,2),
    item_igst                        NUMERIC(12,2),
    item_gst_inclusive               NUMERIC(12,2),
    -- packaging GST
    packaging_charge_sgst            NUMERIC(12,2),
    packaging_charge_cgst            NUMERIC(12,2),
    packaging_charge_igst            NUMERIC(12,2),
    packaging_gst_inclusive          NUMERIC(12,2),
    -- service charge GST
    service_charge_sgst              NUMERIC(12,2),
    service_charge_cgst              NUMERIC(12,2),
    service_charge_igst              NUMERIC(12,2),
    service_charge_gst_inclusive     NUMERIC(12,2),
    -- order metadata
    food_prepared                    BOOLEAN,
    edited_status                    TEXT,
    item_count                       INT,
    mou_type                         TEXT,
    cancelled_reason                 TEXT,
    cancellation_responsible_entity  TEXT,
    -- items detail stored as raw string (complex composite column)
    items_detail                     TEXT,
    -- quality
    confidence_score                 DOUBLE PRECISION,
    review_flags                     JSONB,
    created_at                       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_so_order_id      ON swiggy_orders(order_id);
CREATE INDEX idx_so_file_meta     ON swiggy_orders(file_metadata_id);
CREATE INDEX idx_so_relay_time    ON swiggy_orders(order_relay_time);
CREATE INDEX idx_so_status        ON swiggy_orders(order_status);


-- ─────────────────────────────────────────────
-- TABLE 3: zomato_orders
-- ─────────────────────────────────────────────
CREATE TABLE zomato_orders (
    id                                   BIGSERIAL    PRIMARY KEY,
    file_metadata_id                     BIGINT       NOT NULL REFERENCES file_metadata(id),
    file_name                            TEXT         NOT NULL,
    source                               TEXT         NOT NULL DEFAULT 'ZOMATO',
    file_origin                          TEXT         NOT NULL,
    -- restaurant
    restaurant_id                        TEXT,
    restaurant_name                      TEXT,
    subzone                              TEXT,
    city                                 TEXT,
    -- order core
    order_id                             TEXT,
    order_placed_at                      TIMESTAMPTZ,   -- primary date field
    order_status                         TEXT,
    delivery_type                        TEXT,
    distance_km                          NUMERIC(8,2),
    -- items
    items_in_order                       TEXT,
    instructions                         TEXT,
    -- financials
    discount_construct                   TEXT,
    bill_subtotal                        NUMERIC(12,2),
    packaging_charges                    NUMERIC(12,2),
    restaurant_discount_promo            NUMERIC(12,2),
    restaurant_discount_others           NUMERIC(12,2),
    gold_discount                        NUMERIC(12,2),
    brand_pack_discount                  NUMERIC(12,2),
    total                                NUMERIC(12,2),
    -- cancellation / penalty
    cancellation_rejection_reason        TEXT,
    restaurant_compensation_cancellation NUMERIC(12,2),
    restaurant_penalty_rejection         NUMERIC(12,2),
    -- operational
    kpt_duration_minutes                 INT,
    rider_wait_time_minutes              INT,
    order_ready_marked                   TEXT,
    -- customer
    rating                               NUMERIC(3,1),
    review                               TEXT,
    customer_complaint_tag               TEXT,
    customer_id                          TEXT,
    customer_phone                       TEXT,
    -- quality
    confidence_score                     DOUBLE PRECISION,
    review_flags                         JSONB,
    created_at                           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_zo_order_id     ON zomato_orders(order_id);
CREATE INDEX idx_zo_file_meta    ON zomato_orders(file_metadata_id);
CREATE INDEX idx_zo_placed_at    ON zomato_orders(order_placed_at);
CREATE INDEX idx_zo_status       ON zomato_orders(order_status);


-- ─────────────────────────────────────────────
-- TABLE 4: zomato_invoices
-- Wide table — all 61 financial columns as proper typed columns.
-- Intentionally deviates from generic invoice table pattern.
-- ─────────────────────────────────────────────
CREATE TABLE zomato_invoices (
    id                               BIGSERIAL    PRIMARY KEY,
    file_metadata_id                 BIGINT       NOT NULL REFERENCES file_metadata(id),
    file_name                        TEXT         NOT NULL,
    source                           TEXT         NOT NULL DEFAULT 'ZOMATO',
    file_origin                      TEXT         NOT NULL,
    -- identity
    sno                              INT,
    order_id                         TEXT,
    order_date                       DATE,          -- primary date field
    week_no                          INT,
    restaurant_name                  TEXT,
    restaurant_id                    TEXT,
    -- order info
    discount_construct               TEXT,
    mode_of_payment                  TEXT,
    order_status                     TEXT,
    cancellation_policy_percent      NUMERIC(6,2),
    cancellation_rejection_reason    TEXT,
    cancelled_rejected_state         TEXT,
    order_type                       TEXT,
    delivery_state_code              TEXT,
    -- revenue
    subtotal                         NUMERIC(12,2),
    packaging_charge                 NUMERIC(12,2),
    delivery_charge_self_logistics   NUMERIC(12,2),
    restaurant_discount_promo        NUMERIC(12,2),
    restaurant_discount_others       NUMERIC(12,2),
    brand_pack_subscription_fee      NUMERIC(12,2),
    delivery_charge_discount_relisting NUMERIC(12,2),
    total_gst_from_customers         NUMERIC(12,2),
    net_order_value                  NUMERIC(12,2),
    -- commissionable values
    commissionable_subtotal          NUMERIC(12,2),
    commissionable_packaging_charge  NUMERIC(12,2),
    commissionable_total_gst         NUMERIC(12,2),
    total_commissionable_value       NUMERIC(12,2),
    -- service fee & commission
    base_service_fee_percent         NUMERIC(6,4),
    base_service_fee                 NUMERIC(12,2),
    actual_order_distance_km         NUMERIC(8,2),
    long_distance_enablement_fee     NUMERIC(12,2),
    discount_long_distance_fee       NUMERIC(12,2),
    discount_service_fee_30_cap      NUMERIC(12,2),
    payment_mechanism_fee            NUMERIC(12,2),
    service_fee_and_payment_mech_fee NUMERIC(12,2),
    taxes_on_service_fee             NUMERIC(12,2),
    -- tax
    applicable_amount_tcs            NUMERIC(12,2),
    applicable_amount_9_5            NUMERIC(12,2),
    tax_collected_at_source          NUMERIC(12,2),
    tcs_igst_amount                  NUMERIC(12,2),
    tds_194o_amount                  NUMERIC(12,2),
    gst_paid_by_zomato_9_5          NUMERIC(12,2),
    gst_to_be_paid_by_restaurant     NUMERIC(12,2),
    government_charges               NUMERIC(12,2),
    -- adjustments
    customer_compensation_recoupment NUMERIC(12,2),
    delivery_charges_recovery        NUMERIC(12,2),
    amount_received_cash             NUMERIC(12,2),
    credit_debit_note_adjustment     NUMERIC(12,2),
    promo_recovery_adjustment        NUMERIC(12,2),
    extra_inventory_ads_deduction    NUMERIC(12,2),
    brand_loyalty_points_redemption  NUMERIC(12,2),
    express_order_fee                NUMERIC(12,2),
    other_order_level_deductions     NUMERIC(12,2),
    -- settlement
    net_deductions                   NUMERIC(12,2),
    net_additions                    NUMERIC(12,2),
    order_level_payout               NUMERIC(12,2),
    settlement_status                TEXT,
    settlement_date                  DATE,
    bank_utr                         TEXT,
    unsettled_amount                 NUMERIC(12,2),
    customer_id                      TEXT,
    -- quality
    confidence_score                 DOUBLE PRECISION,
    review_flags                     JSONB,
    created_at                       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_zi_order_id     ON zomato_invoices(order_id);
CREATE INDEX idx_zi_file_meta    ON zomato_invoices(file_metadata_id);
CREATE INDEX idx_zi_order_date   ON zomato_invoices(order_date);
CREATE INDEX idx_zi_settlement   ON zomato_invoices(settlement_status, settlement_date);
CREATE INDEX idx_zi_payout       ON zomato_invoices(order_level_payout);


-- ─────────────────────────────────────────────
-- TABLE 5: offline_orders  (pluggable — schema TBD)
-- raw_data JSONB absorbs any column structure.
-- When format finalised: add typed columns, update POJO + mapRow() only.
-- ─────────────────────────────────────────────
CREATE TABLE offline_orders (
    id               BIGSERIAL    PRIMARY KEY,
    file_metadata_id BIGINT       NOT NULL REFERENCES file_metadata(id),
    file_name        TEXT         NOT NULL,
    source           TEXT         NOT NULL DEFAULT 'OFFLINE',
    file_origin      TEXT         NOT NULL,
    order_id         TEXT         NOT NULL,   -- OFFLINE_YYYYMMDD_{row_number}
    order_hash_key   TEXT         NOT NULL,   -- SHA-256 of all row values (dedup within file)
    row_number       INT          NOT NULL,
    raw_data         JSONB        NOT NULL,   -- entire row stored as-is
    confidence_score DOUBLE PRECISION,
    review_flags     JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_oo_order_id   ON offline_orders(order_id);
CREATE INDEX idx_oo_file_meta  ON offline_orders(file_metadata_id);
CREATE INDEX idx_oo_hash       ON offline_orders(order_hash_key);