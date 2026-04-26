# Frontend Product Enlistment Integration Prompt

Implement the frontend integration for the new product enlistment model using the existing frontend architecture, admin product management flow, API layer, ecommerce storefront flow, cart flow, and UI patterns.

## Context

- The backend implementation is already complete.
- The frontend is already aware of the existing product request and response DTOs, so only update typing where needed to align with the new backend field.
- This change introduces a new product field:
  - `isEnlisted`
- `isEnlisted` determines whether a product is available for ecommerce.
- A product may still exist in inventory and be sold through walk-in/admin flows even when it is not enlisted for ecommerce.
- Source of truth for this behavior is the current backend implementation.
- Product route base remains `/api/v1/product`.

## Primary Goal

Update the frontend so:

- admins can control whether a product is enlisted for ecommerce
- customer-facing ecommerce surfaces only show products that are both active and enlisted
- frontend product create/update forms align with the backend DTO shape
- customer cart and purchase flows do not assume every active product is ecommerce-visible

## Critical Backend Rule

For ecommerce visibility and purchase eligibility, a product must satisfy both:

- `isActive === true`
- `isEnlisted === true`

This rule now applies to:

- public product listing
- public product search
- public product details page
- add-to-cart eligibility
- checkout eligibility

Do not treat `isActive` alone as enough for ecommerce.

## Backend Contract Changes

### Product Request

`ProductRequest` now includes:

- `isEnlisted`

This means admin create and update forms must send:

- `isActive`
- `isEnlisted`
- `isFeatured`

alongside the rest of the existing product payload.

### Product Responses

The admin-facing product response models now include:

- `isEnlisted`

Specifically:

- `ProductDetails`
- `ProductListing`

Frontend types and UI rendering should align with that.

## Required Backend Endpoints Already In Use

### Product Admin

- `POST /api/v1/product/add-product`
- `PUT /api/v1/product/update/{productId}`
- `GET /api/v1/product/admin/listing`
- `GET /api/v1/product/admin/search`
- `GET /api/v1/product/main/{productId}`

### Public Ecommerce Product Reads

- `GET /api/v1/product/listing`
- `GET /api/v1/product/search`
- `GET /api/v1/product/{slug}`

### Customer Purchase Flow

- existing cart endpoints
- existing checkout flow

No new frontend endpoint is required for this feature. The main work is updating request payloads, UI controls, and frontend assumptions.

## Business Rules The UI Must Respect

- `isEnlisted` is not the same as `isActive`.
- `isActive` controls whether the product is generally active in the system.
- `isEnlisted` controls whether the product is listed for ecommerce.
- A product can be:
  - active and enlisted
  - active and not enlisted
  - inactive and not enlisted
  - inactive and enlisted
- Customer ecommerce screens only care about products that are both active and enlisted.
- Admin screens must still be able to view and manage unenlisted products.
- Walk-in/admin sales use cases still need access to unenlisted products.

## Frontend Changes Required

### 1. Update Product Types

Update the shared frontend product types/interfaces to include:

- `isEnlisted`

At minimum, align:

- product form type
- admin product details type
- admin/public product listing type

Do not leave frontend product types assuming `isActive` is the only visibility flag.

### 2. Update Admin Product Create Form

Add an `isEnlisted` control to the admin product-create UX.

Expected behavior:

- admin can explicitly set whether the product is enlisted for ecommerce
- the control should be clear and not conflated with `isActive`
- use labels that make the distinction obvious, for example:
  - `Active`
  - `Enlisted for ecommerce`

The create request must submit the selected `isEnlisted` value in `ProductRequest`.

### 3. Update Admin Product Edit Form

Add or update the `isEnlisted` control in the existing admin product-edit UX.

Expected behavior:

- admin can toggle ecommerce enlistment independently from active status
- the edit form must persist this field through:
  - `PUT /api/v1/product/update/{productId}`
- if the current admin edit page has product status controls, integrate `isEnlisted` there instead of building a second configuration area

### 4. Update Admin Product List And Details Views

Wherever admins view products, surface enlistment status clearly.

Recommended UI changes:

- add an `Enlisted` column, badge, or status chip in admin product listings
- show `isEnlisted` in admin product details
- visually distinguish:
  - inactive products
  - active but unenlisted products
  - active and enlisted products

This is important so admins understand why a product exists in admin but is missing from ecommerce.

### 5. Update Public Storefront Assumptions

The backend now filters public product listing, search, and details using both `isActive` and `isEnlisted`.

Frontend requirements:

- do not assume all admin-visible products can also appear in public storefront UI
- if the frontend caches product lists or product details aggressively, make sure stale admin data does not leak into public customer views
- align empty states and “not found” handling with the fact that a product slug may now disappear from ecommerce if it is unenlisted

### 6. Update Product Detail Page Error Handling

Because public product detail now requires both flags:

- a previously known slug may now return not found for customers

Frontend should:

- handle this gracefully with the app’s existing 404/not-found pattern
- not treat it as a broken server response

### 7. Update Cart And Purchase UX Assumptions

Even if a product was once visible, it may become unenlisted later.

Frontend should be resilient when:

- adding to cart fails for a product that is no longer ecommerce-eligible
- restoring cart from storage fails for a product that is no longer ecommerce-eligible
- checkout fails because one or more cart items are no longer active or enlisted

Expected behavior:

- show the backend error message using current toast/error conventions
- refresh or reconcile cart state if the app already has a cart refresh pattern
- do not assume failure only means out-of-stock

### 8. Update Any Visibility Labels Or Help Text

If the admin UI already contains status help text or product publish language, update it so the distinction is explicit:

- `Active` means the product is active in the system
- `Enlisted for ecommerce` means the product appears in customer storefront and can be bought online

Avoid wording that suggests activation alone makes a product purchasable online.

## Important Backend Nuance

The backend does not default `isEnlisted` to `true`.

That means:

- the frontend must not assume new products are automatically ecommerce-listed
- the create form should set this intentionally
- if the frontend omits the field, products may end up not enlisted

Make the control explicit in the admin form and make sure the request always includes the chosen value.

## Suggested UX Direction

For admin create/edit forms, use two distinct controls:

- `Active`
- `Enlisted for ecommerce`

Optional helper text:

- `Active controls whether the product is active in the system.`
- `Enlisted for ecommerce controls whether customers can see and buy it online.`

This is preferable to one combined toggle because the backend now models them separately.

## Existing Frontend Areas Likely Affected

### Admin Product Form

Expected changes:

- add `isEnlisted` field
- include `isEnlisted` in payload serialization
- update validation and default form values

### Admin Product Table / Cards / Details

Expected changes:

- display enlistment status
- support filtering or scanning by status if the UI already has status filters

### Public Product Listing / Search / Product Page

Expected changes:

- rely on backend-filtered results
- handle products disappearing from ecommerce when unenlisted

### Cart / Checkout / Cart Restore

Expected changes:

- handle backend rejection for products that are no longer ecommerce-eligible
- surface meaningful error feedback to customers

## Implementation Guidance

- Reuse the existing frontend API client, product form architecture, admin product routes, and cart/store logic.
- Do not invent a separate product-management flow just for enlistment.
- Extend the current product form and product status rendering patterns.
- Treat backend responses as the source of truth after product create/update.
- Keep admin and public product views conceptually separate:
  - admin can see all products
  - customers only see active and enlisted products

## Deliverables

- `isEnlisted` added to frontend product request/response typing
- admin create form updated to capture and submit `isEnlisted`
- admin edit form updated to capture and submit `isEnlisted`
- admin product list/details updated to display enlistment status
- customer-facing storefront aligned with the new backend visibility rule
- cart/checkout UX updated to handle products that are no longer ecommerce-eligible

## Implementation Checklist

- Update frontend product types/interfaces to include `isEnlisted`.
- Update admin create-product form default values and serialization to include `isEnlisted`.
- Update admin edit-product form default values and serialization to include `isEnlisted`.
- Add visible UI controls for both `isActive` and `isEnlisted`.
- Ensure admin product list or detail views display whether a product is enlisted.
- Make sure public storefront code does not assume all active products are ecommerce-visible.
- Make sure product detail page handles backend not-found for unenlisted slugs gracefully.
- Audit cart add-item flows for assumptions that only inactive or out-of-stock products can fail.
- Audit cart restore flows for assumptions that old products remain purchasable.
- Audit checkout flows for assumptions that cart contents remain ecommerce-eligible forever.
- Reuse existing frontend abstractions for API calls, forms, route guards, and toasts instead of building new ones.

## Verification Checklist

- Admin can create a product with `isEnlisted = false`.
- Admin can create a product with `isEnlisted = true`.
- Admin can update an existing product from enlisted to unenlisted.
- Admin can update an existing product from unenlisted to enlisted.
- Admin product listing shows unenlisted products.
- Admin product details show correct enlistment status.
- A product that is active but not enlisted does not appear in customer product listing.
- A product that is active but not enlisted does not appear in customer product search.
- A product that is active but not enlisted does not resolve successfully on the public product detail page.
- A product that is active and enlisted still appears normally for customers.
- Customer add-to-cart handles rejection gracefully when a product is no longer enlisted.
- Customer checkout handles rejection gracefully when a cart item is no longer enlisted.
- Any cart restore/local persistence flow handles backend rejection for unenlisted products without breaking the cart UI.

## Important

- Do not change backend contracts.
- Do not treat `isActive` as equivalent to ecommerce visibility.
- Do not hide unenlisted products from admin product management screens.
- Do not assume `isEnlisted` defaults to `true`.
- If the frontend already has product status chips, toggle groups, or form sections, extend them instead of introducing a parallel status-management UI.
