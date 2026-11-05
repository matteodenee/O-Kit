-- ms-stock : schéma initial (vendeur unique, REST synchrone, sans outbox)

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE stocks (
                        id                  BIGSERIAL PRIMARY KEY,
                        id_variante         BIGINT      NOT NULL UNIQUE,      -- Variante.idVariante (ms-catalogue), sans FK
                        stock_actuel        INTEGER     NOT NULL CHECK (stock_actuel >= 0),
                        seuil_stock_alerte  INTEGER     NOT NULL DEFAULT 10 CHECK (seuil_stock_alerte >= 0),
                        alert_active        BOOLEAN     NOT NULL DEFAULT FALSE,
                        version             BIGINT      NOT NULL DEFAULT 0,
                        created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
                        updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_stocks_updated_at
    BEFORE UPDATE ON stocks
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE stock_movements (
                                 id               BIGSERIAL PRIMARY KEY,
                                 stock_id         BIGINT      NOT NULL REFERENCES stocks(id),
                                 id_variante      BIGINT      NOT NULL,
                                 quantity_change  INTEGER     NOT NULL CHECK (quantity_change <> 0),
                                 stock_after      INTEGER     NOT NULL CHECK (stock_after >= 0),
                                 reason           VARCHAR(30) NOT NULL
                                     CHECK (reason IN ('INITIAL','ORDER_PAID','SELLER_ADJUSTMENT','RESTOCK','ORDER_CANCELLED')),
                                 reference_id     VARCHAR(100),                          -- ex : orderId
                                 created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_movements_variante ON stock_movements (id_variante, created_at DESC);
CREATE INDEX idx_movements_reference ON stock_movements (reference_id);

CREATE TABLE idempotent_requests (
                                     idempotency_key  VARCHAR(100) PRIMARY KEY,              -- orderId ou en-tête Idempotency-Key
                                     operation        VARCHAR(20)  NOT NULL CHECK (operation IN ('DECREMENT','RELEASE')),
                                     response_status  INTEGER      NOT NULL,
                                     response_body    JSONB,
                                     created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_idempotent_created_at ON idempotent_requests (created_at);

CREATE TABLE notifications (
                               id           BIGSERIAL PRIMARY KEY,
                               id_variante  BIGINT      NOT NULL,
                               type         VARCHAR(20) NOT NULL CHECK (type IN ('LOW_STOCK','OUT_OF_STOCK')),
                               message      VARCHAR(500) NOT NULL,
                               stock_actuel INTEGER     NOT NULL,
                               seuil        INTEGER     NOT NULL,
                               is_read      BOOLEAN     NOT NULL DEFAULT FALSE,
                               created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                               read_at      TIMESTAMPTZ
);
CREATE INDEX idx_notifications_unread ON notifications (is_read, created_at DESC);