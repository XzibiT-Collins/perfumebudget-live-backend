# Frontend Inventory Management Integration Prompt

Implement the frontend integration for the new inventory-management flow using the existing frontend architecture, API layer, admin layout, and UI patterns.

## Context

- The backend implementation is already complete.
- The frontend is already aware of the request and response DTOs, so do not redefine backend contracts unless needed for local typing alignment.
- Source of truth for backend behavior is `documentation/inventory-management-internal.md`.
- New admin inventory route base is `/api/v1/admin/inventory`.
- Existing product route base remains `/api/v1/product`.
- Existing stock conversion route base remains `/api/v1/admin/stock-conversions`.

## Primary Goal

Update the admin frontend so stock operations are no longer handled through normal product update. Inventory receipt and adjustment must now be handled through the dedicated inventory endpoints, while the existing product-edit experience becomes catalog-only.

## Required Backend Endpoints

### Inventory Management

- `POST /api/v1/admin/inventory/receipts`
  - Request body: `InventoryReceiptRequest`
  - Response wrapper: `CustomApiResponse<InventorySummaryResponse>`

- `POST /api/v1/admin/inventory/adjustments`
  - Request body: `InventoryAdjustmentRequest`
  - Response wrapper: `CustomApiResponse<InventorySummaryResponse>`

- `GET /api/v1/admin/inventory/products/{productId}/summary`
  - Response wrapper: `CustomApiResponse<InventorySummaryResponse>`

- `GET /api/v1/admin/inventory/products/{productId}/history`
  - Response wrapper: `CustomApiResponse<List<InventoryMovementResponse>>`

### Existing Product Endpoint Affected By This Change

- `PUT /api/v1/product/update/{productId}`
  - Still used for catalog edits
  - Must no longer be used to change:
    - `stockQuantity`
    - `costPrice`
    - selling price for products that still have stock

### Existing Stock Conversion Endpoints Still In Use

- `POST /api/v1/admin/stock-conversions/forward`
- `POST /api/v1/admin/stock-conversions/reverse`

No contract change is required on the frontend for stock conversion requests, but the UI should understand that conversion values now come from FIFO inventory layers.

## Business Rules The UI Must Respect

- Product update is now a catalog flow, not an inventory flow.
- Admin must not use the product-edit form to restock products.
- Admin must not use the product-edit form to change cost price.
- Admin must not use the product-edit form to change selling price when the product still has stock.
- New stock with a new cost and selling price must be entered through inventory receipt.
- Inventory depletion uses FIFO internally.
- Active product cost price and selling price are derived from the oldest non-empty inventory layer.
- Inventory adjustment has two modes:
  - `INCREASE`
  - `DECREASE`
- For `INCREASE`, frontend must require:
  - `unitCost`
  - `unitSellingPrice`
- For `DECREASE`, frontend should not require:
  - `unitCost`
  - `unitSellingPrice`
- Stock conversion remains a separate admin flow and should continue to use the current stock conversion endpoints.

## Frontend Changes Required

### 1. Remove Inventory Editing From Product Update UX

- In the existing admin product edit screen, do not present stock update as a normal editable field for save.
- Do not present cost price as a normal editable field for save.
- If the screen currently allows selling price edits, make sure the UI does not encourage using that path for stocked products.
- Preferred UX:
  - show current stock, active cost price, and active selling price as inventory-derived values
  - link or route the admin to dedicated inventory actions instead

### 2. Add Admin Inventory Actions

Add dedicated inventory actions for a product:

- `Receive Stock`
- `Adjust Stock`
- `View Inventory Summary`
- `View Inventory History`

These can live:

- on the admin product details page
- on the admin product list row actions
- or in a dedicated inventory tab/drawer/modal if the existing frontend already has that pattern

Reuse the existing design system and admin interaction patterns.

### 3. Receive Stock UI

Build a form that submits to:

- `POST /api/v1/admin/inventory/receipts`

Fields to collect:

- `productId`
- `quantity`
- `unitCost`
- `unitSellingPrice`
- `receivedAt` optional
- `reference`
- `note` optional

Expected frontend behavior:

- validate quantity is at least `1`
- require `reference`
- require cost and selling price
- on success:
  - refresh product inventory summary
  - refresh product listing/details if those views show stock or price
  - show success feedback

### 4. Adjust Stock UI

Build a form that submits to:

- `POST /api/v1/admin/inventory/adjustments`

Fields:

- `productId`
- `direction`
- `quantity`
- `reason`
- `reference` optional
- `note` optional
- `unitCost` only when `direction === INCREASE`
- `unitSellingPrice` only when `direction === INCREASE`

Expected frontend behavior:

- when direction is `INCREASE`, show and require cost/selling price fields
- when direction is `DECREASE`, hide or disable those fields
- validate quantity is at least `1`
- require `reason`
- on success:
  - refresh summary/history
  - refresh any product stock display
  - show success feedback

### 5. Inventory Summary View

Use:

- `GET /api/v1/admin/inventory/products/{productId}/summary`

Display at minimum:

- product name
- current stock quantity
- active cost price
- active selling price
- visible inventory layers

Layer rows should show:

- `receivedQuantity`
- `remainingQuantity`
- `unitCost`
- `unitSellingPrice`
- `sourceType`
- `sourceReference`
- `receivedAt`

This screen is important because the frontend must now help admins understand why current price/cost may not match the latest stock entry immediately.

### 6. Inventory History View

Use:

- `GET /api/v1/admin/inventory/products/{productId}/history`

Display at minimum:

- movement type
- quantity
- unit cost
- unit selling price
- reference type
- reference id
- note
- created at

This should be easily accessible from the product admin experience.

## Existing Frontend Areas Affected

### Product Edit Screen

Expected changes:

- remove normal stock editing behavior
- remove normal cost-price editing behavior
- avoid using product update for inventory-sensitive changes
- direct admins to inventory actions instead

If the backend still requires those fields in the request payload because of existing DTO shape, the frontend may continue sending the current values unchanged, but it must not treat them as editable inventory controls.

### Product List / Product Details

If these pages show stock, cost, or selling price:

- refresh them after inventory receipt or adjustment
- optionally add quick links to summary/history

### Stock Conversion UI

Keep the current conversion screens wired to:

- `POST /api/v1/admin/stock-conversions/forward`
- `POST /api/v1/admin/stock-conversions/reverse`

But update any UI copy/help text so admins understand:

- conversion cost is now derived from FIFO inventory layers
- mixed-cost source stock can affect conversion value
- product cached price/cost should not be treated as the source of truth for conversion valuation

## Expected UX Guidance

- The admin should clearly understand that:
  - product editing manages catalog metadata
  - inventory actions manage stock and cost changes
- Use labels such as:
  - `Receive Stock`
  - `Stock Adjustment`
  - `Inventory Layers`
  - `Inventory History`
- If the UI supports inline helper text, explain:
  - new stock with a different cost does not overwrite older stock cost
  - the next active cost/selling price may only apply after older stock is exhausted

## Implementation Guidance

- Reuse the existing frontend API client utilities and query/mutation patterns.
- Reuse existing admin routing, drawers, modals, forms, tables, and detail-page patterns.
- Do not invent a second product-edit architecture.
- Keep inventory concerns isolated in dedicated hooks/services/components where the frontend architecture supports that.
- Treat the backend response as source of truth after inventory mutations.
- Refresh dependent product queries after successful inventory operations.

## Deliverables

- Admin inventory receipt UI wired to `/api/v1/admin/inventory/receipts`
- Admin inventory adjustment UI wired to `/api/v1/admin/inventory/adjustments`
- Inventory summary UI wired to `/api/v1/admin/inventory/products/{productId}/summary`
- Inventory history UI wired to `/api/v1/admin/inventory/products/{productId}/history`
- Updated admin product-edit flow so stock/cost are no longer managed there
- Updated stock-conversion UX copy to reflect FIFO-based valuation

## Important

- Do not change backend contracts.
- Do not keep stock editing in the normal product update UX.
- Do not keep cost-price editing in the normal product update UX.
- Do not assume the latest receipt price immediately becomes active for existing older stock.
- If the frontend already has a product details panel or admin action menu pattern, extend that instead of building a parallel inventory area with different UX conventions.
