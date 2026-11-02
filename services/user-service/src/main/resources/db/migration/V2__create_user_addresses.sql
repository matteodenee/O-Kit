CREATE TABLE user_addresses (
    id UUID PRIMARY KEY,
    user_profile_id UUID NOT NULL,
    label VARCHAR(80),
    recipient_first_name VARCHAR(100) NOT NULL,
    recipient_last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(32) NOT NULL,
    address_line_1 VARCHAR(200) NOT NULL,
    address_line_2 VARCHAR(200),
    city VARCHAR(100) NOT NULL,
    region VARCHAR(100),
    postal_code VARCHAR(32),
    country_code VARCHAR(2) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_addresses_profile
        FOREIGN KEY (user_profile_id) REFERENCES user_profiles (id) ON DELETE CASCADE,
    CONSTRAINT chk_user_addresses_country_code
        CHECK (country_code ~ '^[A-Z]{2}$')
);

CREATE INDEX idx_user_addresses_active_profile
    ON user_addresses (user_profile_id, created_at)
    WHERE archived_at IS NULL;

CREATE UNIQUE INDEX uq_user_addresses_active_default
    ON user_addresses (user_profile_id)
    WHERE is_default = TRUE AND archived_at IS NULL;
