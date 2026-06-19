# UC-PRODUCT-001: Browse Catalog

|| Attribute | Value |
|-----------|-------|
| **ID** | UC-PRODUCT-001 |
| **Actor** | Customer (Public) |
| **Priority** | HIGH |
| **Precondition** | None |
| **Postcondition** | Customer views product detail or category information |

---

## Brief

> **Note:** Product listing and filtering has been moved to Search Service (UC-SEARCH-001).
> This use case now covers only: (1) category tree browsing, and (2) product detail view.

---

## Main Flow

### Part 1: Category Tree (unchanged)

```
1. Customer navigates to category sidebar or category page
2. System returns category tree via GET /categories
   - Only is_active=TRUE categories shown
   - Sorted by sort_order ASC
3. Customer selects a category
   - Customer is redirected to Search Service: GET /search/products?category_id={id}
```

### Part 2: Product Detail (unchanged)

```
4. Customer clicks a product card (from Search Service results)
5. System returns product detail via GET /products/{id}
   - Full variant matrix (active + out_of_stock; inactive excluded)
   - Image gallery (common + variant-specific)
   - Attributes table (Tab "Chi tiet")
   - Description HTML (Tab "Mo ta")
   - Price display per selected variant
```

---

## Variant Selection Sub-Flow

```
1. System groups all variants by variant_attributes keys
2. Customer selects attribute values (e.g., color=Den, size=M)
3. System maps combination to specific variant
4. IF variant is active and stock > 0:
   - Show price, enable "Them vao gio hang" and "Mua ngay" buttons
5. IF variant is out_of_stock:
   - Show "Het hang" button (disabled)
6. IF variant is inactive:
   - Option not shown at all
```

---

## Related Endpoints

|| Endpoint | Usage |
|----------|-------|
| GET /categories | Category tree (Part 1) |
| GET /categories/{categoryId} | Single category detail with breadcrumb and children |
| GET /products/{id} | Product detail with variants and images (Part 2) |

> **Note:** `GET /products` (listing with filters) has been moved to Search Service.
> Frontend should call `GET /search/products` for all product listings.

---

## Related Requirements

|| Ref ID | Description |
|--------|-------------|
| FR-PRODUCT-001 | Browse category tree |
| FR-PRODUCT-006 | Product detail view |
| BR-PRODUCT-001 | Category hierarchy constraints |
| ENTITY-PRODUCT-001 | CATEGORY |
| ENTITY-PRODUCT-002 | PRODUCT |
| ENTITY-PRODUCT-003 | PRODUCT_VARIANT |
| ENTITY-PRODUCT-004 | PRODUCT_IMAGE |
| UC-SEARCH-001 | Product listing and search (Search Service) |

---

## Migration Note

| Before | After |
|--------|-------|
| `GET /products` for listings | `GET /search/products` (Search Service) |
| `GET /products?category_id=X` for category browse | `GET /search/products?category_id=X` (Search Service) |
| Filtering in Product Service | Filtering consolidated in Search Service |

---

## Last Updated

2026-05-22: Separated listing (→ Search Service) from category tree and product detail (→ Product Service).
