# Frontend Front Desk Role Integration Prompt

Implement the frontend integration for the new `FRONT_DESK` role, permission-managed walk-in access, admin-managed front-desk staff controls, and the new dedicated front-desk feature-flag audience using the existing frontend architecture and UI patterns.

## Context

- The backend now supports a third primary role: `FRONT_DESK`.
- This is not just a display-only role. It has backend-enforced permissions and a separate feature-flag audience.
- The frontend must integrate all of the following coherently:
  - authenticated session handling for `FRONT_DESK`
  - admin management of front-desk users
  - admin management of front-desk default permissions and user overrides
  - walk-in / POS access for front-desk users based on effective permissions
  - separate feature-flag toggles for `ADMIN`, `FRONT_DESK`, and `CUSTOMER`
- Backend responses still use the wrapper shape:
  - `CustomApiResponse<T>`
  - shape:
    - `description: string | null`
    - `data: T | null`

## Primary Goal

Update the frontend so it fully supports the new backend authorization model without introducing parallel auth or admin-management patterns.

The implementation must:

- treat `FRONT_DESK` as a first-class authenticated role
- use backend-provided `permissions` as the source of truth for front-desk capabilities
- expose admin tools to manage front-desk staff and permissions
- reuse existing route guards, admin layout, API client, query/mutation patterns, tables, forms, and feature-flag management UI conventions already present in the app

## Backend Contracts

### Auth / Session Payload

- `GET /api/v1/auth/me`
  - Access: `ADMIN`, `FRONT_DESK`, `CUSTOMER`
  - Response wrapper: `CustomApiResponse<AuthResponse>`
- Login and OTP verification flows also return the same `AuthResponse` shape when authentication succeeds

`AuthResponse` fields:

- `id: number`
- `email: string`
- `fullName: string`
- `profilePicture: string`
- `role: 'ADMIN' | 'FRONT_DESK' | 'CUSTOMER'`
- `permissions: FrontDeskPermission[]`

Important:

- `permissions` is meaningful for `FRONT_DESK`
- non-front-desk users may receive an empty permission set
- the frontend must not infer front-desk access from role alone when permissions are available

### Front Desk Permission Enum

The backend currently exposes these permission values:

- `WALK_IN_ORDER_CREATE`
- `WALK_IN_ORDER_VIEW`
- `WALK_IN_ORDER_MARK_RECEIPT_PRINTED`
- `CUSTOMER_SEARCH`
- `PRODUCT_VIEW_ADMIN_CATALOG`
- `PRODUCT_VIEW_STOCK_SUMMARY`

Treat these enum values as exact contract values.

### Admin Front Desk Management Endpoints

All endpoints below are admin-only and live under:

- `/api/v1/admin/front-desk`

#### Default Template

- `GET /api/v1/admin/front-desk/template`
  - Response wrapper: `CustomApiResponse<FrontDeskPermissionTemplateResponse>`
- `PUT /api/v1/admin/front-desk/template`
  - Request body: `FrontDeskPermissionTemplateUpdateRequest`
  - Response wrapper: `CustomApiResponse<FrontDeskPermissionTemplateResponse>`

`FrontDeskPermissionTemplateResponse`:

- `permissions: FrontDeskPermission[]`

`FrontDeskPermissionTemplateUpdateRequest`:

- `permissions: FrontDeskPermission[]`

#### Front Desk Users

- `GET /api/v1/admin/front-desk/users`
  - Response wrapper: `CustomApiResponse<List<FrontDeskUserSummaryResponse>>`
- `POST /api/v1/admin/front-desk/users`
  - Request body: `CreateFrontDeskUserRequest`
  - Response wrapper: `CustomApiResponse<FrontDeskUserSummaryResponse>`
- `PATCH /api/v1/admin/front-desk/users/{userId}/assign`
  - Converts an existing user to `FRONT_DESK`
  - Response wrapper: `CustomApiResponse<FrontDeskUserSummaryResponse>`
- `PATCH /api/v1/admin/front-desk/users/{userId}`
  - Request body: `UpdateFrontDeskUserRequest`
  - Response wrapper: `CustomApiResponse<FrontDeskUserSummaryResponse>`

`FrontDeskUserSummaryResponse`:

- `id: number`
- `fullName: string`
- `email: string`
- `isActive: boolean`
- `role: 'FRONT_DESK' | other UserRole values if reused elsewhere`

`CreateFrontDeskUserRequest`:

- `fullName: string`
- `email: string`
- `password: string`
- `isActive: boolean`

Validation expectations:

- `fullName` required
- `email` required and valid email
- `password` required and minimum length 8
- `isActive` required

`UpdateFrontDeskUserRequest`:

- `fullName?: string`
- `email?: string`
- `password?: string`
- `isActive?: boolean`

Validation expectations:

- `email` must be valid if supplied
- `password` must be at least 8 characters if supplied
- blank `fullName` should be treated as invalid on submission

#### User Permission Overrides

- `GET /api/v1/admin/front-desk/users/{userId}/permissions`
  - Response wrapper: `CustomApiResponse<FrontDeskUserPermissionsResponse>`
- `PUT /api/v1/admin/front-desk/users/{userId}/permissions`
  - Request body: `FrontDeskUserPermissionOverrideUpdateRequest`
  - Response wrapper: `CustomApiResponse<FrontDeskUserPermissionsResponse>`

`FrontDeskUserPermissionsResponse`:

- `userId: number`
- `templatePermissions: FrontDeskPermission[]`
- `allowedOverrides: FrontDeskPermission[]`
- `deniedOverrides: FrontDeskPermission[]`
- `effectivePermissions: FrontDeskPermission[]`

`FrontDeskUserPermissionOverrideUpdateRequest`:

- `allowedPermissions: FrontDeskPermission[]`
- `deniedPermissions: FrontDeskPermission[]`

Important backend rule:

- the same permission must not appear in both `allowedPermissions` and `deniedPermissions`

### Walk-In / POS Endpoints

These routes are no longer admin-only at the controller level. They are available to:

- `ADMIN`
- `FRONT_DESK`

But the backend enforces front-desk permissions per action.

Route base:

- `/api/v1/admin/walk-in`

Endpoints:

- `POST /api/v1/admin/walk-in/order`
  - Requires front-desk permission: `WALK_IN_ORDER_CREATE`
  - Request body: `WalkInOrderRequest`
  - Response wrapper: `CustomApiResponse<WalkInOrderResponse>`
- `GET /api/v1/admin/walk-in/customers/search?query=...`
  - Requires front-desk permission: `CUSTOMER_SEARCH`
  - Response wrapper: `CustomApiResponse<List<CustomerSearchResponse>>`
- `GET /api/v1/admin/walk-in/orders`
  - Requires front-desk permission: `WALK_IN_ORDER_VIEW`
  - Supports optional `date` query param and existing pagination conventions
  - Response wrapper: `CustomApiResponse<PageResponse<WalkInOrderResponse>>`
- `GET /api/v1/admin/walk-in/orders/{orderNumber}`
  - Requires front-desk permission: `WALK_IN_ORDER_VIEW`
  - Response wrapper: `CustomApiResponse<WalkInOrderResponse>`
- `PATCH /api/v1/admin/walk-in/orders/{orderNumber}/receipt-printed`
  - Requires front-desk permission: `WALK_IN_ORDER_MARK_RECEIPT_PRINTED`
  - Response wrapper: `CustomApiResponse<Void>`

Important:

- `ADMIN` bypasses these front-desk permission checks
- `FRONT_DESK` does not bypass them
- if the frontend shows a button the user cannot execute, the backend will still reject it

### Feature Flag Endpoints

Admin feature-flag management now has three separate audience booleans.

Route base:

- `/api/v1/admin/feature-flags`

Endpoints:

- `GET /api/v1/admin/feature-flags`
  - Response wrapper: `CustomApiResponse<List<FeatureFlagResponse>>`
- `GET /api/v1/admin/feature-flags/{featureKey}`
  - Response wrapper: `CustomApiResponse<FeatureFlagResponse>`
- `PATCH /api/v1/admin/feature-flags/{featureKey}`
  - Request body: `FeatureFlagUpdateRequest`
  - Response wrapper: `CustomApiResponse<FeatureFlagResponse>`

`FeatureFlagResponse`:

- `featureKey: FeatureFlagKey`
- `description: string`
- `adminEnabled: boolean`
- `frontDeskEnabled: boolean`
- `customerEnabled: boolean`

`FeatureFlagUpdateRequest`:

- `adminEnabled: boolean`
- `frontDeskEnabled: boolean`
- `customerEnabled: boolean`

Important:

- do not continue treating front-desk flags as admin flags
- the admin feature-flag UI must expose a separate front-desk toggle

## Business Rules The UI Must Respect

### Auth and Session

- role-based app behavior must now handle `FRONT_DESK`
- session restoration after refresh must preserve the `permissions` array from `/auth/me`
- frontend should treat backend `permissions` as authoritative for front-desk behavior

### Front Desk User Management

- only admin should access front-desk management screens
- creating a front-desk user should default to role `FRONT_DESK`
- assigning an existing user to front desk should use the dedicated assign endpoint rather than assuming the frontend can patch an arbitrary generic role field

### Permission Editing

- the default front-desk template is a set of enabled permissions
- user-specific overrides are split into:
  - explicit allows
  - explicit denies
- UI must prevent sending the same permission in both arrays
- effective permissions shown in UI should be based on the backend response, not recomputed differently in the frontend

### Walk-In Gating

- showing the walk-in module to front-desk users must be permission-aware
- `WALK_IN_ORDER_CREATE` controls order placement actions
- `CUSTOMER_SEARCH` controls registered customer lookup behavior
- `WALK_IN_ORDER_VIEW` controls list/detail access
- `WALK_IN_ORDER_MARK_RECEIPT_PRINTED` controls receipt-print status mutation
- if a front-desk user lacks a permission, hide or disable the related action and handle forbidden responses gracefully if the user still reaches it

### Feature Flags

- feature-flag editing screens must now render three toggles or controls per flag:
  - admin
  - front desk
  - customer
- existing feature-flag forms must be updated so they always submit all three booleans

## Frontend Requirements

### 1. Session and Auth Store Updates

- update auth/session types to include:
  - `role`
  - `permissions`
- ensure login success, OTP verification success, session restore, and `/auth/me` hydration all keep `permissions`
- add a central utility for permission checks such as:
  - `hasFrontDeskPermission(permission)`
- do not hardcode permission names in multiple unrelated components

### 2. Admin Front Desk Management UI

Add admin-facing routes and screens for:

- front-desk user listing
- front-desk user creation
- front-desk user edit
- default front-desk permission template editing
- per-user front-desk override editing

Reuse the existing admin layout, route registration, data-table patterns, form components, modal patterns, and mutation feedback patterns.

Expected UI areas:

- a staff management entry in the admin navigation, or an existing settings/users area extension
- a template management panel that lists all `FrontDeskPermission` values with enabled/disabled selection
- a user detail or edit panel that shows:
  - template permissions
  - allowed overrides
  - denied overrides
  - effective permissions

### 3. Front Desk Walk-In UX

Update the existing or planned walk-in/POS frontend so it works for front-desk users.

Expected behavior:

- front-desk users should be able to access walk-in pages only when the app is intended to expose them
- within those pages, actions must be gated by `permissions`
- if a front-desk user can view but not mark receipt printed, the list/detail page can still render while the receipt update action is hidden or disabled

### 4. Feature Flag Admin UI

Update the existing feature-flag management interface to support:

- displaying `frontDeskEnabled`
- editing `frontDeskEnabled`
- sending the full payload with:
  - `adminEnabled`
  - `frontDeskEnabled`
  - `customerEnabled`

If the current feature-flag screen is built as a two-column audience editor, extend it instead of creating a second management page.

## Expected Screens and Flows

### A. Front Desk Users List

Show:

- full name
- email
- active state
- role
- actions to edit permissions or edit user info

Actions:

- create front-desk user
- assign existing user to front desk

If there is already a user-management table pattern, extend it rather than creating a disconnected UI.

### B. Default Front Desk Permission Template Screen

Show all `FrontDeskPermission` values:

- `WALK_IN_ORDER_CREATE`
- `WALK_IN_ORDER_VIEW`
- `WALK_IN_ORDER_MARK_RECEIPT_PRINTED`
- `CUSTOMER_SEARCH`
- `PRODUCT_VIEW_ADMIN_CATALOG`
- `PRODUCT_VIEW_STOCK_SUMMARY`

Support:

- loading current template
- editing selected permissions
- saving through `PUT /api/v1/admin/front-desk/template`

### C. Front Desk User Permission Detail/Edit Screen

Show:

- template permissions
- allowed overrides
- denied overrides
- effective permissions

Support:

- editing `allowedPermissions`
- editing `deniedPermissions`
- blocking contradictory UI selections before submit
- saving through `PUT /api/v1/admin/front-desk/users/{userId}/permissions`

### D. Front Desk User Create/Edit Screen

Create screen should support:

- `fullName`
- `email`
- `password`
- `isActive`

Edit screen should support:

- `fullName`
- `email`
- optional password reset/change entry
- `isActive`

### E. Walk-In Access for Front Desk

On walk-in routes:

- permit `FRONT_DESK` navigation if the user has the relevant permissions needed for the page
- productively handle states such as:
  - can view orders but cannot create
  - can create but cannot search registered customers
  - can view details but cannot mark receipt printed

### F. Feature Flags Screen Update

For each feature flag row or detail view:

- show admin toggle
- show front-desk toggle
- show customer toggle

Make it visually clear that front desk is a separate audience.

## Implementation Guidance

- integrate all requests through the existing API client/service layer
- use existing typed response wrappers for `CustomApiResponse<T>`
- reuse existing query and mutation patterns
- use existing auth/session store patterns instead of inventing a new permission store
- prefer extending existing admin settings, user-management, role-management, or feature-flag screens over creating parallel sections
- keep role and permission utilities centralized

Suggested frontend state helpers:

- `isAdmin`
- `isFrontDesk`
- `hasFrontDeskPermission(permission)`
- `canAccessWalkInCreate`
- `canAccessWalkInView`
- `canSearchWalkInCustomers`
- `canMarkWalkInReceiptPrinted`

Use these helpers to simplify route guards and UI gating, but always remember backend remains the final authority.

## Deliverables

- auth/session type updates for `FRONT_DESK` and `permissions`
- admin UI for front-desk template management
- admin UI for front-desk user creation and editing
- admin UI for front-desk user-specific permission overrides
- walk-in/POS UI updates for permission-aware front-desk access
- feature-flag UI updates for separate front-desk audience toggles
- route and navigation updates consistent with the existing app
- loading, success, error, and forbidden-state handling aligned with existing patterns

## Important Constraints

- do not change backend contracts
- do not treat front-desk permissions as frontend-only access control
- do not assume front-desk is equivalent to admin anywhere in the UI
- do not continue using a two-audience feature-flag form
- do not duplicate user-management or feature-flag management architecture if equivalent patterns already exist
- if the frontend already has a generic table, drawer, modal, or role-badge pattern, reuse it

## Backend-Driven UX Notes

- If the backend returns empty `permissions` for a non-front-desk user, do not treat that as an error.
- If a front-desk user hits a disallowed action, expect a forbidden response and surface a clear error state instead of silently failing.
- For walk-in functionality, prefer hiding obviously unavailable actions, but still handle backend rejection defensively.
- For feature flags, default the UI to the exact backend booleans returned rather than inferring missing states.
