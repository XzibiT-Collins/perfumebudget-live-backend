# Frontend Walk-In Order Integration Prompt

Implement a Walk-In Order / POS flow in the existing frontend application using the backend APIs that already exist.

## Context

- The backend feature is already implemented.
- The frontend is already aware of the new walk-in DTOs, so do not redefine backend contracts unless needed for typing alignment.
- This is an admin-only feature for in-store sales.
- The backend documentation for behavior is in `documentation/walk-in-order-implementation.md`.
- The backend route base is `/api/v1/admin/walk-in`.

## Primary Goal

Build the frontend integration for placing, viewing, and managing walk-in orders using the existing app patterns, state management, API layer, design system, and admin navigation already present in the codebase.

## Required Backend Endpoints

- `POST /api/v1/admin/walk-in/order`
  - Creates a walk-in order
  - Request body: `WalkInOrderRequest`
  - Response wrapper: `CustomApiResponse<WalkInOrderResponse>`
- `GET /api/v1/admin/walk-in/customers/search?query=...`
  - Searches registered customers for admin selection
  - Response wrapper: `CustomApiResponse<List<CustomerSearchResponse>>`
- `GET /api/v1/admin/walk-in/orders`
  - Supports optional `date` query param and existing pagination conventions
  - Response wrapper: `CustomApiResponse<PageResponse<WalkInOrderResponse>>`
- `GET /api/v1/admin/walk-in/orders/{orderNumber}`
  - Fetches one walk-in order
  - Response wrapper: `CustomApiResponse<WalkInOrderResponse>`
- `PATCH /api/v1/admin/walk-in/orders/{orderNumber}/receipt-printed`
  - Marks receipt as printed
  - Response wrapper: `CustomApiResponse<Void>`

## Business Rules The UI Must Respect

- This flow must not use the existing cart.
- Walk-in orders are placed directly by admin.
- Customer can be:
  - a registered customer via `registeredUserId`
  - a walk-in customer via `walkInCustomer`
  - anonymous by sending neither
- Payment methods:
  - `CASH`
  - `MOBILE_MONEY`
  - `CARD`
  - `SPLIT`
- Payment validation expectations:
  - `CASH`: `amountPaid >= totalAmount`
  - `MOBILE_MONEY`: `amountPaid == totalAmount`
  - `CARD`: `amountPaid == totalAmount`
  - `SPLIT`: `splitCashAmount + splitMobileAmount == totalAmount` and `amountPaid == combined split amount`
- Split payment should be handled strictly. Do not allow overpayment UX for split in this iteration.
- `changeGiven` is meaningful for `CASH`, but should remain zero for `MOBILE_MONEY`, `CARD`, and current `SPLIT` flow.
- Manual discount is optional.
- Discount input uses:
  - `discountType` with `PERCENTAGE` or `FLAT`
  - `discountValue`
- `discountType` and `discountValue` must be sent together when a discount is applied.
- Receipt printed state is updated separately through the PATCH endpoint.

## Frontend Requirements

- Add an admin POS / Walk-In Order screen.
- Reuse existing admin layout, route guards, API client utilities, and form conventions.
- Do not invent a separate checkout architecture if the codebase already has patterns for data fetching, mutations, tables, filters, and form submission.
- Keep the implementation consistent with the existing frontend architecture.

## Expected UX

### 1. Walk-In Order Creation Screen

- Product selection for line items
- Quantity input per item
- Optional manual discount
- Discount type selector with `PERCENTAGE` and `FLAT`
- Discount value input
- Customer mode selector:
  - registered customer search
  - walk-in customer details
  - anonymous
- Payment method selector
- Conditional payment fields:
  - `amountPaid` for all methods
  - `splitCashAmount` and `splitMobileAmount` only for `SPLIT`
- Show calculated summary:
  - subtotal
  - discount
  - tax
  - total
  - change when applicable
- Submit to `POST /order`
- On success, show returned order details clearly

### 2. Registered Customer Search

- Use `GET /customers/search`
- Debounce search input if the app already uses debounce patterns
- Let admin select a customer and populate `registeredUserId`

### 3. Walk-In Orders List Screen

- Use `GET /orders`
- Support pagination using existing frontend pagination conventions
- Support optional date filter
- Show useful columns:
  - order number
  - customer name
  - processed by
  - payment method
  - status
  - total amount
  - receipt printed
  - created at

### 4. Walk-In Order Details Screen

- Use `GET /orders/{orderNumber}`
- Display:
  - customer info
  - payment method
  - totals
  - amount paid
  - change given
  - receipt printed status
  - ordered items

### 5. Receipt Printed Action

- Add an action on the details page or list row to call:
  - `PATCH /orders/{orderNumber}/receipt-printed`
- After success, refresh local state so `receiptPrinted` updates immediately

## Implementation Guidance

- Integrate through the existing frontend API service layer rather than ad hoc fetch calls.
- Add typed request/response usage only where the frontend needs explicit wiring.
- Follow the app’s existing patterns for:
  - route registration
  - admin navigation/sidebar
  - query/mutation hooks
  - loading states
  - error handling
  - success toasts/messages
  - empty states
- Keep validation aligned with backend rules to reduce failed submissions.
- Avoid duplicating backend calculations if the app already has a shared pricing/totals pattern; otherwise compute a provisional client-side summary for UX, but treat backend response as source of truth.

## Deliverables

- Admin route(s) and UI for walk-in order creation, listing, and detail view
- API integration for all walk-in endpoints
- Form validation and conditional payment input behavior
- Query/mutation wiring with proper loading/error/success states
- Consistent integration with existing frontend components and patterns

## Important

- Do not change backend contracts.
- Do not use the cart flow.
- Do not add unsupported split-overpayment behavior.
- If there is an existing product picker, admin table, or customer-search component pattern in the frontend, reuse it instead of creating parallel patterns.
