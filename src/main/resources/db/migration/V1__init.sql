CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id           VARCHAR(255)             NOT NULL,
    bearer_token VARCHAR(255)             NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_users              PRIMARY KEY (id),
    CONSTRAINT uq_users_bearer_token UNIQUE (bearer_token)
);

CREATE TABLE wallets (
    id             UUID                     NOT NULL DEFAULT gen_random_uuid(),
    user_id        VARCHAR(255)             NOT NULL,
    balance_paise  BIGINT                   NOT NULL DEFAULT 0,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_wallets         PRIMARY KEY (id),
    CONSTRAINT uq_wallets_user_id UNIQUE (user_id),
    CONSTRAINT ck_wallets_balance CHECK (balance_paise >= 0)
);

CREATE TABLE transfers (
    id                UUID                     NOT NULL DEFAULT gen_random_uuid(),
    idempotency_key   VARCHAR(255)             NOT NULL,
    from_wallet_id    UUID                     NOT NULL,
    to_wallet_id      UUID                     NOT NULL,
    amount_paise      BIGINT                   NOT NULL,
    status            VARCHAR(20)              NOT NULL DEFAULT 'PENDING',
    request_hash      VARCHAR(64)              NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_transfers             PRIMARY KEY (id),
    CONSTRAINT uq_transfers_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_transfers_from        FOREIGN KEY (from_wallet_id) REFERENCES wallets(id),
    CONSTRAINT fk_transfers_to          FOREIGN KEY (to_wallet_id)   REFERENCES wallets(id),
    CONSTRAINT ck_transfers_amount      CHECK (amount_paise > 0),
    CONSTRAINT ck_transfers_diff        CHECK (from_wallet_id <> to_wallet_id),
    CONSTRAINT ck_transfers_status      CHECK (status IN ('PENDING','COMPLETED','DECLINED'))
);

CREATE INDEX idx_wallets_user_id       ON wallets(user_id);
CREATE INDEX idx_transfers_idempotency ON transfers(idempotency_key);
CREATE INDEX idx_transfers_from_wallet ON transfers(from_wallet_id);
CREATE INDEX idx_transfers_to_wallet   ON transfers(to_wallet_id);

INSERT INTO users (id, bearer_token) VALUES
    ('user-001', 'token-user-001'),
    ('user-002', 'token-user-002'),
    ('user-003', 'token-user-003'),
    ('user-004', 'token-user-004'),
    ('user-005', 'token-user-005');
