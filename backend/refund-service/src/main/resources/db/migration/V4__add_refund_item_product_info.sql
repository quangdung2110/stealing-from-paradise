-- Snapshot tên + ảnh sản phẩm cho từng refund item, để hiển thị ở admin/buyer
-- mà không cần join lại sang order-service. Dữ liệu được order-service gửi kèm
-- trong event REFUND_REQUESTED (snake_case: product_name / image_snapshot).
-- IF NOT EXISTS: an toàn nếu Hibernate ddl-auto đã tạo cột trên máy dev.
ALTER TABLE refund.refund_items ADD COLUMN IF NOT EXISTS product_name VARCHAR(500);
ALTER TABLE refund.refund_items ADD COLUMN IF NOT EXISTS image_snapshot VARCHAR(1000);
