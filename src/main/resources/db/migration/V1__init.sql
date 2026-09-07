-- Append-only audit tables plus a current-balances snapshot. All monetary and
-- size columns are scaled integers (BIGINT) — never floating point.

CREATE TABLE accounts (
    account_id BIGINT PRIMARY KEY,
    cash       BIGINT NOT NULL,
    asset      BIGINT NOT NULL
);

CREATE TABLE orders (
    order_id         BIGINT PRIMARY KEY,
    side             VARCHAR(4) NOT NULL,
    price            BIGINT NOT NULL,
    quantity         BIGINT NOT NULL,
    account_id       BIGINT NOT NULL,
    arrival_sequence BIGINT NOT NULL
);

CREATE TABLE trades (
    trade_sequence    BIGINT PRIMARY KEY,
    buy_order_id      BIGINT NOT NULL,
    sell_order_id     BIGINT NOT NULL,
    price             BIGINT NOT NULL,
    quantity          BIGINT NOT NULL,
    buyer_account_id  BIGINT NOT NULL,
    seller_account_id BIGINT NOT NULL
);
