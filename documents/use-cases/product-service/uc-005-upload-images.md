# UC-PRODUCT-005: Upload Images (Seller)

| Attribute | Value |
|-----------|-------|
| **ID** | UC-PRODUCT-005 |
| **Actor** | Seller (JWT SELLER role, product owner) |
| **Priority** | HIGH |
| **Precondition** | Product exists and seller owns it |
| **Postcondition** | Images uploaded to MinIO and registered in product_image |

---

## Main Flow

### Step 1: Get Presigned Upload URL
```
1. Seller calls GET /products/{productId}/presigned-url?file_name=front.jpg&content_type=image/jpeg
2. System validates:
   - Product exists, seller is owner
   - content_type is image/jpeg, image/png, or image/webp
3. System generates MinIO presigned PUT URL:
   - Bucket: products-media
   - Key: products/{seller_id}/{product_id}/{uuid}.{ext}
   - TTL: 15 minutes
4. Returns 200: { presigned_url, object_url, expires_in: 900 }
```

### Step 2: Upload to MinIO
```
1. Seller (or frontend) PUTs the file to presigned_url directly
2. MinIO stores the object
3. Object accessible at object_url (CDN)
```

### Step 3: Register Image in DB
```
1. Seller calls POST /products/{productId}/images
   Body: { url: "<object_url>", variant_id?: null|<variantId>, sort_order?: 0 }

2. System validates:
   - Total images for product <= 10
   - URL is valid MinIO path

3. System inserts product_image row:
   - variant_id = NULL (common image) or specific variant_id

4. sort_order defaults to max existing + 1

5. Returns 201
```

### Delete Image
```
1. Seller calls DELETE /images/{imageId}
2. System validates ownership via product.seller_id
3. System deletes product_image row
   (MinIO object cleanup is deferred/lazy)
4. Returns 200
```

---

## Storage Convention
```
MinIO Bucket: products-media
Key: products/{seller_id}/{product_id}/{uuid}-{type}.{ext}
Derivatives: {uuid}-{type}_thumb.{ext}, {uuid}-{type}_small.{ext}
```

---

## Error Scenarios

| Scenario | Response |
|----------|----------|
| Invalid content_type | 422 |
| Product not found/not owned | 404 |
| Image count exceeds 10 | 422 |
| Image not found/not owned | 404 |

---

## Related Requirements

| Ref ID | Description |
|--------|-------------|
| FR-PRODUCT-012 | Upload product images |
| FR-PRODUCT-013 | Delete product image |
| BR-PRODUCT-006 | Image validation |
| ENTITY-PRODUCT-004 | PRODUCT_IMAGE |
