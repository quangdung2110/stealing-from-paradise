// Chạy bởi MONGO_INITDB entrypoint
db = db.getSiblingDB('fs_product_prod');
db.createCollection('products');
db.products.createIndex({ "sellerId": 1 });
db.products.createIndex({ "status": 1, "createdAt": -1 });

db = db.getSiblingDB('fs_noti_prod');
db.createCollection('notifications');
db.notifications.createIndex({ "userId": 1, "createdAt": -1 });
db.notifications.createIndex({ "createdAt": 1 }, { expireAfterSeconds: 2592000 }); // TTL 30 ngày
