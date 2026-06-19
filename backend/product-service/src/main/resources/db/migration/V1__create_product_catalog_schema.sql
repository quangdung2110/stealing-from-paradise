-- =====================================================
-- Flyway Migration: V1__create_product_catalog_schema
-- Description: Creates the product catalog schema including
-- categories, products, product variants, product images,
-- stock reservations, carts, and cart items.
-- =====================================================

-- -----------------------------------------------------
-- Table: categories
-- Description: Product categories with hierarchical support
-- -----------------------------------------------------
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    image_url VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL
);

CREATE INDEX idx_categories_parent_id ON categories(parent_id);
CREATE INDEX idx_categories_slug ON categories(slug);

COMMENT ON TABLE categories IS 'Product categories with hierarchical (parent-child) structure for organizing the catalog';
COMMENT ON COLUMN categories.parent_id IS 'Self-referencing FK to parent category (NULL for root categories)';
COMMENT ON COLUMN categories.slug IS 'URL-friendly unique identifier for the category';
COMMENT ON COLUMN categories.sort_order IS 'Display order within parent category';
COMMENT ON COLUMN categories.is_active IS 'Whether the category is visible in the storefront';
COMMENT ON COLUMN categories.deleted_at IS 'Soft delete timestamp (NULL means not deleted)';

-- -----------------------------------------------------
-- Table: products
-- Description: Core product catalog entries with approval workflow
-- -----------------------------------------------------
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL,
    seller_id BIGINT,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    attributes JSONB DEFAULT '{}',
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    reject_reason TEXT,
    reviewed_at TIMESTAMP,
    reviewed_by BIGINT,
    reject_count INT NOT NULL DEFAULT 0,
    submitted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT
);

CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_products_seller_id ON products(seller_id);
CREATE INDEX idx_products_status ON products(status);
CREATE INDEX idx_products_slug ON products(slug);

COMMENT ON TABLE products IS 'Core product entries with multi-stage approval workflow';
COMMENT ON COLUMN products.category_id IS 'FK to the primary category this product belongs to';
COMMENT ON COLUMN products.seller_id IS 'Soft reference to the seller (user service), NULL for admin-created products';
COMMENT ON COLUMN products.attributes IS 'Flexible key-value specifications stored as JSONB';
COMMENT ON COLUMN products.status IS 'DRAFT|PENDING|APPROVED|REJECTED|ACTIVE|OUT_OF_STOCK|INACTIVE';
COMMENT ON COLUMN products.reject_reason IS 'Reason provided when status is REJECTED';
COMMENT ON COLUMN products.reviewed_at IS 'Timestamp when product was reviewed by admin';
COMMENT ON COLUMN products.reviewed_by IS 'Admin user ID who reviewed the product';
COMMENT ON COLUMN products.reject_count IS 'Number of times product has been rejected';
COMMENT ON COLUMN products.submitted_at IS 'Timestamp when product was submitted for review';
COMMENT ON COLUMN products.deleted_at IS 'Soft delete timestamp (NULL means not deleted)';

-- Partial index for efficient PENDING queue queries
CREATE INDEX idx_products_pending ON products(status, created_at) WHERE status = 'PENDING';

COMMENT ON INDEX idx_products_pending IS 'Partial index for efficient review queue queries';

-- -----------------------------------------------------
-- Table: product_variants
-- Description: Product variations (size, color, etc.) with pricing and stock
-- -----------------------------------------------------
CREATE TABLE product_variants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,
    variant_code VARCHAR(50) NOT NULL UNIQUE,
    variant_name VARCHAR(255) NOT NULL,
    variant_attributes JSONB DEFAULT '{}',
    price DECIMAL(18,2) NOT NULL CHECK (price > 0),
    original_price DECIMAL(18,2),
    stock_quantity INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 1,
    image_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_variants_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

CREATE INDEX idx_variants_product_id ON product_variants(product_id);
CREATE INDEX idx_variants_status ON product_variants(status);
CREATE INDEX idx_variants_price ON product_variants(price);
CREATE INDEX idx_variants_code ON product_variants(variant_code);

COMMENT ON TABLE product_variants IS 'Product variations (e.g., size/color combinations) with individual pricing and stock';
COMMENT ON COLUMN product_variants.variant_code IS 'Unique SKU code for the variant (alphanumeric with dashes)';
COMMENT ON COLUMN product_variants.variant_name IS 'Display name for the variant (e.g., "Blue / Large")';
COMMENT ON COLUMN product_variants.variant_attributes IS 'JSONB storing variant-specific attributes like color, size';
COMMENT ON COLUMN product_variants.price IS 'Current selling price (must be greater than 0)';
COMMENT ON COLUMN product_variants.original_price IS 'Original/crossed-out price before discount';
COMMENT ON COLUMN product_variants.stock_quantity IS 'Available inventory for this variant';
COMMENT ON COLUMN product_variants.status IS 'ACTIVE|OUT_OF_STOCK|INACTIVE';
COMMENT ON COLUMN product_variants.version IS 'Optimistic locking version for concurrent updates';
COMMENT ON COLUMN product_variants.deleted_at IS 'Soft delete timestamp (NULL means not deleted)';

-- -----------------------------------------------------
-- Table: product_images
-- Description: Product and variant images with ordering
-- -----------------------------------------------------
CREATE TABLE product_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,
    variant_id UUID,
    url VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_images_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_images_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE
);

CREATE INDEX idx_images_product_id ON product_images(product_id);
CREATE INDEX idx_images_variant_id ON product_images(variant_id);

COMMENT ON TABLE product_images IS 'Product and variant-level images with sort ordering';
COMMENT ON COLUMN product_images.variant_id IS 'NULL for product-level images, set for variant-specific images';
COMMENT ON COLUMN product_images.sort_order IS 'Display order (0 = primary/thumbnail image)';
COMMENT ON COLUMN product_images.url IS 'Full URL to the image';

-- -----------------------------------------------------
-- Table: stock_reservations
-- Description: Temporary stock reservations during checkout
-- -----------------------------------------------------
CREATE TABLE stock_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variant_id UUID NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservations_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE RESTRICT
);

CREATE INDEX idx_reservations_variant_id ON stock_reservations(variant_id);
CREATE INDEX idx_reservations_session_id ON stock_reservations(session_id);
CREATE INDEX idx_reservations_status ON stock_reservations(status);
CREATE INDEX idx_reservations_expires ON stock_reservations(expires_at);

COMMENT ON TABLE stock_reservations IS 'Temporary inventory reservations during checkout sessions';
COMMENT ON COLUMN stock_reservations.session_id IS 'Checkout session identifier for grouping reservations';
COMMENT ON COLUMN stock_reservations.quantity IS 'Number of units reserved (must be positive)';
COMMENT ON COLUMN stock_reservations.status IS 'PENDING|CONFIRMED|RELEASED';
COMMENT ON COLUMN stock_reservations.expires_at IS 'Reservation expiry time (typically 15-30 minutes)';

-- Index for finding expired reservations
CREATE INDEX idx_reservations_expired ON stock_reservations(status, expires_at) WHERE status = 'PENDING';

-- -----------------------------------------------------
-- Table: carts
-- Description: Shopping carts per customer
-- -----------------------------------------------------
CREATE TABLE carts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id BIGINT NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX uk_carts_customer_id ON carts(customer_id) WHERE deleted_at IS NULL;

COMMENT ON TABLE carts IS 'Shopping carts, one per customer';
COMMENT ON COLUMN carts.customer_id IS 'Customer identifier (UUID) - unique constraint enforced';
COMMENT ON COLUMN carts.status IS 'ACTIVE|CONVERTED (converted = checked out)';
COMMENT ON COLUMN carts.deleted_at IS 'Soft delete timestamp (NULL means not deleted)';

-- -----------------------------------------------------
-- Table: cart_items
-- Description: Individual items in a shopping cart
-- -----------------------------------------------------
CREATE TABLE cart_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    quantity INT NOT NULL CHECK (quantity >= 1 AND quantity <= 1000),
    price_snapshot DECIMAL(18,2) NOT NULL,
    variant_name_snapshot VARCHAR(255) NOT NULL,
    variant_image_snapshot VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uk_cart_items_cart_variant ON cart_items(cart_id, variant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_cart_items_variant_id ON cart_items(variant_id);

COMMENT ON TABLE cart_items IS 'Individual items added to a shopping cart';
COMMENT ON COLUMN cart_items.variant_id IS 'Product variant being added to cart';
COMMENT ON COLUMN cart_items.quantity IS 'Number of units (1-1000)';
COMMENT ON COLUMN cart_items.price_snapshot IS 'Price captured at time of adding to cart (for order history)';
COMMENT ON COLUMN cart_items.variant_name_snapshot IS 'Variant name captured at add time';
COMMENT ON COLUMN cart_items.variant_image_snapshot IS 'Variant image URL captured at add time';
COMMENT ON COLUMN cart_items.deleted_at IS 'Soft delete timestamp (NULL means not deleted)';

-- =====================================================
-- End of migration: V1__create_product_catalog_schema
-- =====================================================
