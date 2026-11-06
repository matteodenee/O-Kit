CREATE TABLE user_wishlist_items (
    id UUID PRIMARY KEY,
    user_profile_id UUID NOT NULL,
    product_id BIGINT NOT NULL,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_user_wishlist_profile
        FOREIGN KEY (user_profile_id) REFERENCES user_profiles (id) ON DELETE CASCADE,
    CONSTRAINT chk_user_wishlist_product_id CHECK (product_id > 0),
    CONSTRAINT uq_user_wishlist_profile_product UNIQUE (user_profile_id, product_id)
);

CREATE INDEX idx_user_wishlist_product
    ON user_wishlist_items (product_id);
