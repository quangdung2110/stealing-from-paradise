# UC-PRODUCT-002: Manage Categories (Admin)

| Attribute | Value |
|-----------|-------|
| **ID** | UC-PRODUCT-002 |
| **Actor** | Admin (JWT ADMIN role) |
| **Priority** | MEDIUM |
| **Precondition** | Admin authenticated with ADMIN role |
| **Postcondition** | Category tree updated |

---

## Main Flows

### Create Category
```
1. Admin calls POST /admin/categories
   Body: { name, slug, parent_id?, description?, image_url?, sort_order? }
2. System validates:
   - name: required, VARCHAR(255)
   - slug: required, UNIQUE (409 if exists)
   - parent_id: if provided, must reference existing category
3. System inserts category row
4. Returns 201 with category data
```

### Update Category
```
1. Admin calls PUT /admin/categories/{categoryId}
2. System validates:
   - Category exists (404 if not)
   - No circular parent reference
   - Slug uniqueness if changed
3. System updates category
4. Emits category.updated Kafka event -> Search Service reindexes facets
5. Returns 200
```

### Delete Category
```
1. Admin calls DELETE /admin/categories/{categoryId}
2. System validates:
   - Category exists (404 if not)
   - Application may check for child categories/products and warn
3. System deletes category (ON DELETE SET NULL on children)
4. Returns 200
```

---

## Error Scenarios

| Scenario | Response |
|----------|----------|
| Slug already exists | 409 Conflict |
| Category not found | 404 Not Found |
| Circular parent reference | 422 Unprocessable Entity |
| Unauthorized (non-ADMIN) | 403 Forbidden |

---

## Related Requirements

| Ref ID | Description |
|--------|-------------|
| FR-PRODUCT-002 | Admin create category |
| FR-PRODUCT-003 | Admin update category |
| BR-PRODUCT-001 | Category hierarchy constraints |
| ENTITY-PRODUCT-001 | CATEGORY |
