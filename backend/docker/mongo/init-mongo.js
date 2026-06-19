// MongoDB init script — chạy trên mongosh
// db: flashsale_product
db.categories.createIndex({ slug: 1 }, { unique: true });
db.categories.createIndex({ parent_id: 1, level: 1 });

db.products.createIndex({ seller_id: 1, status: 1 });
db.products.createIndex({ category_id: 1, status: 1 });
db.products.createIndex({ deleted_at: 1 }, { sparse: true });

db.product_variants.createIndex({ product_id: 1 });
db.product_variants.createIndex({ sku_code: 1 }, { unique: true });

db.inventories.createIndex({ sku_code: 1 }, { unique: true });
db.inventories.createIndex({ product_id: 1 });

// db: flashsale_cart
db.carts.createIndex({ user_id: 1 }, { unique: true });
db.cart_items.createIndex({ cart_id: 1 });
db.cart_items.createIndex({ cart_id: 1, sku_code: 1 });

// db: flashsale_notification — TTL 90 ngày
db.notifications.createIndex({ user_id: 1, is_read: 1 });
db.notifications.createIndex(
  { created_at: 1 },
  { expireAfterSeconds: 7776000 }  // 90 ngày TTL
);

