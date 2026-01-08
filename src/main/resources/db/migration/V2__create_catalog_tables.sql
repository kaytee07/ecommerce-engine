-- Phase 4: Catalog Tables
-- Categories table with hierarchical support (parent_id for tree structure)
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    parent_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    display_order INT DEFAULT 0,
    active BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for faster parent lookup (tree traversal)
CREATE INDEX idx_categories_parent_id ON categories(parent_id);
CREATE INDEX idx_categories_slug ON categories(slug);
CREATE INDEX idx_categories_active ON categories(active);

-- Products table with JSONB attributes for flexible product data
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    price DECIMAL(19, 4) NOT NULL CHECK (price >= 0),
    compare_at_price DECIMAL(19, 4) CHECK (compare_at_price >= 0),
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    attributes JSONB DEFAULT '{}',
    sku VARCHAR(100) UNIQUE,
    active BOOLEAN DEFAULT true NOT NULL,
    featured BOOLEAN DEFAULT false NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for product queries
CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_products_slug ON products(slug);
CREATE INDEX idx_products_active ON products(active);
CREATE INDEX idx_products_featured ON products(featured);
CREATE INDEX idx_products_price ON products(price);
CREATE INDEX idx_products_created_at ON products(created_at DESC);

-- Full-text search index on name and description
CREATE INDEX idx_products_search ON products USING GIN (to_tsvector('english', name || ' ' || COALESCE(description, '')));

-- GIN index for JSONB attributes queries
CREATE INDEX idx_products_attributes ON products USING GIN (attributes);

-- Product images table (for multiple images per product)
CREATE TABLE product_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    url VARCHAR(500) NOT NULL,
    alt_text VARCHAR(255),
    display_order INT DEFAULT 0,
    is_primary BOOLEAN DEFAULT false NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_images_product_id ON product_images(product_id);
CREATE INDEX idx_product_images_primary ON product_images(product_id, is_primary);

-- Insert sample categories for testing
INSERT INTO categories (id, name, slug, description, display_order) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Electronics', 'electronics', 'Electronic devices and gadgets', 1),
    ('22222222-2222-2222-2222-222222222222', 'Clothing', 'clothing', 'Apparel and fashion items', 2),
    ('33333333-3333-3333-3333-333333333333', 'Home & Garden', 'home-garden', 'Home improvement and garden supplies', 3);

-- Insert subcategories
INSERT INTO categories (id, name, slug, description, parent_id, display_order) VALUES
    ('11111111-1111-1111-1111-111111111112', 'Laptops', 'laptops', 'Laptop computers', '11111111-1111-1111-1111-111111111111', 1),
    ('11111111-1111-1111-1111-111111111113', 'Smartphones', 'smartphones', 'Mobile phones', '11111111-1111-1111-1111-111111111111', 2),
    ('22222222-2222-2222-2222-222222222223', 'Men', 'mens-clothing', 'Men''s clothing', '22222222-2222-2222-2222-222222222222', 1),
    ('22222222-2222-2222-2222-222222222224', 'Women', 'womens-clothing', 'Women''s clothing', '22222222-2222-2222-2222-222222222222', 2);

-- Insert sample products for testing
INSERT INTO products (id, name, slug, description, price, category_id, attributes, sku, featured) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'MacBook Pro 16"', 'macbook-pro-16', 'Powerful laptop for professionals with M3 chip', 2499.00, '11111111-1111-1111-1111-111111111112', '{"brand": "Apple", "color": "Space Gray", "storage": "512GB", "ram": "16GB"}', 'MBPRO16-001', true),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'iPhone 15 Pro', 'iphone-15-pro', 'Latest iPhone with titanium design', 1199.00, '11111111-1111-1111-1111-111111111113', '{"brand": "Apple", "color": "Natural Titanium", "storage": "256GB"}', 'IPH15PRO-001', true),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'Samsung Galaxy S24', 'samsung-galaxy-s24', 'Premium Android smartphone', 899.00, '11111111-1111-1111-1111-111111111113', '{"brand": "Samsung", "color": "Phantom Black", "storage": "128GB"}', 'SGS24-001', false),
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'Cotton T-Shirt', 'cotton-tshirt-men', 'Comfortable 100% cotton t-shirt', 29.99, '22222222-2222-2222-2222-222222222223', '{"brand": "BasicWear", "material": "Cotton", "sizes": ["S", "M", "L", "XL"]}', 'TS-MEN-001', false),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'Dell XPS 15', 'dell-xps-15', 'Premium Windows laptop with OLED display', 1899.00, '11111111-1111-1111-1111-111111111112', '{"brand": "Dell", "color": "Platinum Silver", "storage": "1TB", "ram": "32GB"}', 'DELLXPS15-001', false);
