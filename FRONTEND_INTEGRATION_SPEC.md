# Frontend Integration Spec — Multi-Storeroom Inventory Location Tracking

**Audience:** an agent (or developer) integrating this feature into the existing admin/front-desk frontend.
**Backend branch:** `feat/multi-storeroom-inventory`. All endpoints live and covered by the existing auth/session mechanism — nothing new is required for authentication.

---

## 1. Feature summary (what the user can now do)

The shop now tracks **where each product physically sits**: on the shop floor or in one or more store rooms.

1. **Storage locations** — admins create/edit named locations (`SHOP_FLOOR` or `STORE_ROOM`) and assign three "default role" flags (where stock is received, where walk-in sales deduct from, where e-commerce orders ship from).
2. **Stock transfers** — admins and front-desk staff record physical moves of units between locations ("moved 5 of Product X from Store Room 1 to Shop Floor"). Every move is logged with who moved it and when.
3. **Stock-by-location view** — per-product breakdown of quantities at each location, plus a reconciliation flag.
4. **Shop-floor restock alerts** — when the shop floor runs low on a product but store rooms still hold stock, staff receive a real-time notification (existing notification system, new type `SHOP_FLOOR_RESTOCK`).

Sales/receipts/adjustments update location balances **automatically** on the backend. The frontend never posts sale-related location changes — it only manages locations, records transfers, and displays balances/alerts.

### Key domain rules the UI must respect

- **Location balances can be negative.** A negative number means staff physically restocked the floor without recording a transfer. Do NOT clamp, hide, or treat as an error. Render e.g. `−2 (needs transfer record)` with a warning style.
- **Exactly one location holds each default role** (receiving / walk-in source / e-commerce source). The defaults endpoint *moves* a role to a location; you cannot turn a role "off".
- **Transfers require real stock at the source** (server rejects over-quantity with 400). Sales do not have this restriction.
- **Reservations (unpaid e-commerce carts) do not move location balances.** Between checkout and payment, the sum of location balances will exceed global stock by the reserved quantity — this is expected, not drift. The `stock-by-location` endpoint accounts for this in `balancesMatchGlobal`.
- **Transfers are not financial events** — no prices/costs anywhere in this feature. Quantities only.

---

## 2. Conventions (same as the rest of the API)

- Base URL prefix: `/api/v1/admin/inventory`
- Every response is wrapped in the standard envelope:

```json
{ "description": "human-readable message or null", "data": <payload or null> }
```

- Errors use the same envelope with `data: null`:
  - `400` — validation failure / business rule violation (`description` holds the message)
  - `401` — unauthenticated
  - `403` — authenticated but role not allowed
  - `404` — product or location not found
  - `409` — duplicate resource
- Timestamps are ISO-8601 local date-times **without timezone offset**, e.g. `"2026-06-12T14:03:22.123"` (same as all existing endpoints).
- Role requirements per endpoint are listed below. Roles: `ADMIN`, `FRONT_DESK`.

---

## 3. Enums

| Enum | Values | Used in |
|---|---|---|
| `StorageLocationType` | `STORE_ROOM`, `SHOP_FLOOR` | location `type` field |
| `StockTransferType` | `TRANSFER`, `RECEIPT`, `SALE_DEDUCTION`, `ADJUSTMENT` | transfer-history `transferType` field |

`transferType` meaning for display:
- `TRANSFER` — staff-recorded move between two locations (only type the frontend can create)
- `RECEIPT` — stock received into the default-receiving location (automatic)
- `SALE_DEDUCTION` — walk-in or e-commerce sale deducted (automatic)
- `ADJUSTMENT` — inventory adjustment or product conversion (automatic)

---

## 4. Endpoints

### 4.1 Storage locations (ADMIN only)

#### `GET /api/v1/admin/inventory/locations`
Returns all locations (active and inactive), sorted by name.

```json
{
  "description": null,
  "data": [
    {
      "id": 1,
      "name": "Shop Floor",
      "type": "SHOP_FLOOR",
      "active": true,
      "lowStockThreshold": null,
      "isDefaultReceiving": true,
      "isWalkInSaleSource": true,
      "isEcommerceFulfilmentSource": true,
      "createdAt": "2026-06-12T10:00:00",
      "updatedAt": "2026-06-12T10:00:00"
    },
    {
      "id": 2,
      "name": "Store Room 1",
      "type": "STORE_ROOM",
      "active": true,
      "lowStockThreshold": null,
      "isDefaultReceiving": false,
      "isWalkInSaleSource": false,
      "isEcommerceFulfilmentSource": false,
      "createdAt": "2026-06-12T10:00:00",
      "updatedAt": null
    }
  ]
}
```

> The two seed locations above ("Shop Floor", "Store Room 1") exist after migration — the list is never empty in a migrated environment.

#### `GET /api/v1/admin/inventory/locations/{id}`
Single location, same shape. `404` if unknown.

#### `POST /api/v1/admin/inventory/locations`
```json
{
  "name": "Store Room 2",          // required, non-blank, ≤128 chars, unique (case-insensitive)
  "type": "STORE_ROOM",            // required, enum
  "lowStockThreshold": 3,          // optional, ≥0; floor-alert threshold override (see §6)
  "active": true                   // optional, defaults true
}
```
Returns `200` with the created `StorageLocationResponse` (envelope `description: "Storage location created"`).
`400` if name already exists.

#### `PUT /api/v1/admin/inventory/locations/{id}`
Same body as POST; full update of `name`, `type`, `lowStockThreshold`, `active` (omitting `active` leaves it unchanged).
`400` cases: duplicate name; setting `active: false` on a location that holds any default role (message: *"Cannot deactivate a location that is a default receiving or sale source. Reassign the defaults first."*). Surface that message verbatim and guide the user to the defaults UI.

#### `PATCH /api/v1/admin/inventory/locations/{id}/defaults`
Moves one or more default roles **to** this location. Sends only `true` flags; `false`/omitted flags are ignored (roles can't be switched off, only moved).

```json
{
  "isDefaultReceiving": true,
  "isWalkInSaleSource": false,        // ignored
  "isEcommerceFulfilmentSource": null // ignored
}
```
Returns updated `StorageLocationResponse`. `400` if the target location is inactive.
**UI implication:** after a successful PATCH, re-fetch the location list — the previous holder's flag was cleared server-side.

### 4.2 Stock transfers (ADMIN + FRONT_DESK)

#### `POST /api/v1/admin/inventory/transfers`
```json
{
  "productId": 17,        // required
  "fromLocationId": 2,    // required
  "toLocationId": 1,      // required, must differ from fromLocationId
  "quantity": 5,          // required, ≥1
  "note": "Restocking floor before weekend"   // optional
}
```
Response (`description: "Stock transfer recorded"`):
```json
{
  "description": "Stock transfer recorded",
  "data": {
    "id": 42,
    "productId": 17,
    "productName": "Oud Royale 50ml",
    "fromLocationId": 2,
    "fromLocationName": "Store Room 1",
    "toLocationId": 1,
    "toLocationName": "Shop Floor",
    "quantity": 5,
    "transferType": "TRANSFER",
    "movedByName": "Jane Doe",
    "note": "Restocking floor before weekend",
    "createdAt": "2026-06-12T14:03:22.123"
  }
}
```
`400` cases (show `description` verbatim):
- same source/destination — *"Source and destination locations must differ."*
- inactive location — *"Storage location 'X' is inactive."*
- insufficient source stock — *"Insufficient stock at Store Room 1: 3 on hand, 5 requested."*

`404` — unknown product or location.

#### `GET /api/v1/admin/inventory/transfers?productId=&locationId=&page=0&size=20`
All query params optional. `locationId` matches either side (from **or** to). Newest first.
`data` is a standard Spring `Page`:

```json
{
  "description": null,
  "data": {
    "content": [ { ...StockTransferResponse as above... } ],
    "totalElements": 57,
    "totalPages": 3,
    "number": 0,
    "size": 20,
    "first": true,
    "last": false,
    "numberOfElements": 20,
    "empty": false,
    "pageable": { "...": "ignore" },
    "sort": { "...": "ignore" }
  }
}
```
Read `content`, `totalElements`, `totalPages`, `number`, `size`; ignore the rest.

Rows where `fromLocationId` is `null` mean stock entered from outside (receipt); `toLocationId: null` means stock left the building (sale/adjustment-out). `movedByName` is `"System"` for automatic entries (e.g. e-commerce payment webhooks).

### 4.3 Stock by location (ADMIN + FRONT_DESK)

#### `GET /api/v1/admin/inventory/products/{productId}/stock-by-location`
```json
{
  "description": null,
  "data": {
    "productId": 17,
    "productName": "Oud Royale 50ml",
    "globalStockQuantity": 24,
    "outstandingReservedQuantity": 2,
    "locations": [
      { "locationId": 1, "locationName": "Shop Floor", "locationType": "SHOP_FLOOR", "quantityOnHand": 6 },
      { "locationId": 2, "locationName": "Store Room 1", "locationType": "STORE_ROOM", "quantityOnHand": 20 }
    ],
    "balancesMatchGlobal": true
  }
}
```
Semantics:
- `globalStockQuantity` — sellable stock from the cost engine (already excludes reserved units).
- `outstandingReservedQuantity` — units in unpaid e-commerce carts (will either finalize or auto-release within ~1h).
- Invariant: `sum(locations.quantityOnHand) == globalStockQuantity + outstandingReservedQuantity`. `balancesMatchGlobal` is the server-computed check.
- `balancesMatchGlobal: false` ⇒ true drift (manual DB edits, pre-feature history). Show a subtle "ledger out of sync" badge; resolvable by an adjustment or a corrective transfer — not a frontend bug.
- `locations` only contains locations that have ever held this product. Missing location = 0 (display 0 for known locations not in the list if you render a full matrix).
- `quantityOnHand` may be negative — render with warning style, e.g. `−2 (needs transfer record)`.
- `404` if product unknown.

---

## 5. Real-time restock alerts

Existing notification infrastructure — same WebSocket connection, inbox endpoints, unread counts. Only a **new notification `type`** is added.

- STOMP endpoint: `/ws` (unchanged). Broker: `/topic`, `/queue`; user prefix `/user`.
- Existing subscription `/user/queue/notifications` now also delivers:

```json
{
  "recipientId": 311,
  "notificationId": 87,
  "type": "SHOP_FLOOR_RESTOCK",
  "title": "Shop floor restock needed",
  "message": "Oud Royale 50ml is low on the shop floor (0 left). 20 unit(s) available in store rooms.",
  "referenceType": "PRODUCT",
  "referenceId": "17",
  "read": false,
  "readAt": null,
  "deliveredAt": "2026-06-12T14:03:23",
  "createdAt": "2026-06-12T14:03:23"
}
```

Frontend tasks:
1. Add `SHOP_FLOOR_RESTOCK` to the notification-type renderer (icon: box/shelf; tone: warning).
2. On click, navigate to the product's stock-by-location view (`referenceId` = product id as string) with a pre-filled transfer form (from: store room with stock, to: shop floor).
3. **Throttling is server-side**: while an unread `SHOP_FLOOR_RESTOCK` exists for a product, no duplicate is sent. Marking it read re-arms the alert — so the "mark as read" action effectively acknowledges the restock task. No client-side dedup needed.
4. Recipients: all `ADMIN` and `FRONT_DESK` users (persisted to inbox even when offline; delivered via WS when connected).

---

## 6. Threshold model (for the location settings UI)

Floor alert fires when, after any shop-floor decrement: `floorQuantity <= threshold` **and** store rooms hold > 0 of the product.
- `threshold` = the shop-floor location's `lowStockThreshold` if set, else the product's own `lowStockThreshold` (existing product field, default 5), else 0.
- In the location form, label `lowStockThreshold` as: *"Restock alert threshold — alert staff when shop-floor quantity falls to this level (leave empty to use each product's own threshold)"*. Only meaningful on `SHOP_FLOOR` locations; hide or grey out for store rooms.

---

## 7. Suggested UI work (screens & placement)

1. **Locations settings page** (admin settings area):
   - Table: name, type badge, active toggle, threshold, three role chips (Receiving / Walk-in / E-com).
   - Create/edit modal (POST/PUT). Role chips clickable → confirm dialog → PATCH `/defaults` → refresh list.
   - Block deactivation client-side when a role chip is present (server also enforces).
2. **Transfer recording** (accessible to FRONT_DESK — likely the front-desk dashboard plus product pages):
   - Form: product search (existing product picker), from-location, to-location, quantity, note.
   - Show current `quantityOnHand` of the selected source (from stock-by-location call) next to the quantity input; client-side max hint, but rely on server 400 for the real check.
3. **Product inventory page** — add "Stock by location" panel: per-location quantities, reserved-units line, sync badge from `balancesMatchGlobal`, and a "Transfer history" tab backed by `GET /transfers?productId=`.
4. **Transfers log page** (optional): global paged table from `GET /transfers`, filters by product/location; render automatic rows (`RECEIPT`/`SALE_DEDUCTION`/`ADJUSTMENT`, `movedByName: "System"`) muted, staff `TRANSFER` rows prominent.
5. **Notifications**: new type rendering per §5.

Role gating in the frontend router/menu:
- Locations settings: ADMIN only.
- Transfer form + history + stock-by-location: ADMIN and FRONT_DESK.

---

## 8. Integration test checklist (against a running backend)

1. `GET /locations` as ADMIN → seeded "Shop Floor" (all three role flags true) + "Store Room 1". Same call as FRONT_DESK → 403.
2. Create location, duplicate name → 400 with message.
3. PATCH defaults to move `isWalkInSaleSource` to a store room → previous holder's flag cleared in subsequent GET.
4. POST transfer Store Room → Shop Floor (as FRONT_DESK) → 200; over-quantity → 400 with on-hand count in message.
5. `GET /products/{id}/stock-by-location` → balances reflect the transfer; `balancesMatchGlobal: true`.
6. Perform a walk-in sale draining the floor while store room has stock → WS message `type: SHOP_FLOOR_RESTOCK` arrives on `/user/queue/notifications`; second sale produces no duplicate until the notification is marked read.
7. E-commerce checkout (unpaid) → `outstandingReservedQuantity` rises, location quantities unchanged, `balancesMatchGlobal` still true.
