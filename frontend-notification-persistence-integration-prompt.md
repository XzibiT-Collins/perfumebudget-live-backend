# Frontend Notification Persistence Integration Prompt

Implement the frontend integration for persisted admin notifications using the existing frontend architecture, API layer, websocket setup, auth/session handling, admin layout, and UI patterns.

## Context

- The backend implementation is already complete.
- The frontend is already aware of the request and response DTO shapes, so do not redefine backend contracts unless needed for local typing alignment.
- Source of truth for backend behavior is `documentation/notification-persistence-internal.md`.
- Admin notification route base is `/api/v1/admin/notifications`.
- Websocket delivery still uses the existing user-specific notifications queue.

## Primary Goal

Update the current frontend notification flow so notifications are no longer frontend-only ephemeral state. The backend is now the source of truth for notification history and read state, while websocket remains the realtime delivery mechanism.

## Required Backend Endpoints

- `GET /api/v1/admin/notifications`
  - Returns `CustomApiResponse<PageResponse<NotificationResponse>>`
  - Use existing pageable conventions already used in the app

- `GET /api/v1/admin/notifications/unread-count`
  - Returns `CustomApiResponse<UnreadNotificationCountResponse>`

- `PATCH /api/v1/admin/notifications/{recipientId}/read`
  - Returns `CustomApiResponse<NotificationResponse>`

- `PATCH /api/v1/admin/notifications/read-all`
  - Returns `204 No Content`

## Required Websocket Payload

The websocket notification payload is now persistence-aware and should be treated as the same state model as the inbox API.

Current websocket fields:

- `recipientId`
- `notificationId`
- `type`
- `title`
- `message`
- `referenceType`
- `referenceId`
- `read`
- `readAt`
- `deliveredAt`
- `createdAt`

Do not keep relying on the old payment-specific websocket payload shape.

## Business Rules The UI Must Respect

- Backend is the source of truth for notification history.
- Backend is the source of truth for read/unread state.
- Frontend local state may be used as a cache, but not as authoritative storage.
- Notifications must survive logout, refresh, storage clear, and reconnect because they now come from the backend.
- Read state is per recipient, so frontend actions must use `recipientId`, not `notificationId`, for mark-read behavior.
- Websocket events should be merged into existing notification state, not appended blindly.

## Frontend Changes Required

### 1. Replace Frontend-Only Notification Persistence

- Remove or downgrade any local-storage-only notification source of truth.
- If local storage is currently used, keep it only as an optional cache layer.
- The UI should hydrate notifications from the backend on admin app load or admin session restore.

### 2. Add Notification Inbox Query Integration

Use:

- `GET /api/v1/admin/notifications`

Build or update the existing notification query/hook/store so it:

- fetches persisted notifications for the current admin
- supports the existing frontend pagination pattern if the notification UI is paginated
- stores notifications by a stable key, preferably `recipientId`
- sorts newest first if the backend response is not already rendered directly

### 3. Add Unread Count Query Integration

Use:

- `GET /api/v1/admin/notifications/unread-count`

Update whatever badge, header icon, sidebar indicator, or notification bell already exists so unread count comes from the backend instead of derived frontend-only state.

### 4. Update Websocket Notification Handling

Keep the current websocket subscription mechanism, but change the notification event handling so incoming messages are merged into persisted state.

Expected behavior:

- subscribe to the existing notifications queue after authenticated admin session is established
- when a websocket message arrives, upsert it into notification state by `recipientId`
- do not duplicate a notification that already exists from the initial inbox fetch
- update unread badge/count consistently after websocket arrival

If the app already has a query cache, mutation cache, or normalized store pattern, extend that instead of building a second notification state system.

### 5. Add Single Notification Read Action

Use:

- `PATCH /api/v1/admin/notifications/{recipientId}/read`

Expected behavior:

- call this when the user opens, clicks, or otherwise meaningfully views a notification according to the current UX pattern
- update local state optimistically if the app already uses that pattern
- reconcile with the returned `NotificationResponse`
- decrement unread count consistently

Do not mark notifications read by `notificationId`.

### 6. Add Mark-All-Read Action

Use:

- `PATCH /api/v1/admin/notifications/read-all`

Expected behavior:

- expose this action if the current notification dropdown, inbox, or panel design supports bulk actions
- after success, update all locally cached unread notifications to read
- refresh unread count or set it to zero in a way consistent with the app's data-fetching pattern

### 7. Update Notification UI Data Shape

Adjust the current notification item rendering to use the new backend-backed fields:

- `title`
- `message`
- `type`
- `referenceType`
- `referenceId`
- `read`
- `createdAt`

If the existing UI previously relied on payment-only fields such as order number, customer email, total amount, or placed-at from websocket payloads, derive the display from the new generic payload instead.

### 8. Navigation And Deep Linking

If the current notification UX supports clicking a notification to navigate somewhere:

- use `referenceType` and `referenceId` to determine the target route
- for the currently implemented backend event, payment success notifications use:
  - `referenceType = ORDER`
  - `referenceId = <orderNumber>`

If the app already has an admin order details route, clicking the notification should navigate there.

### 9. Loading, Empty, And Error States

Follow the existing frontend conventions for:

- loading spinners or skeletons
- empty notification states
- websocket reconnect handling
- API error toasts/messages
- optimistic update rollback if a mark-read call fails

Do not invent a parallel notification UI pattern if the app already has a dropdown, drawer, panel, or inbox screen.

## Existing Frontend Areas Likely Affected

### Admin Header / Notification Bell

Expected changes:

- unread badge must come from backend unread count
- opening the dropdown/panel should show persisted notifications
- realtime websocket events should update the visible list

### Notification Store / Hook / Context

Expected changes:

- hydrate from backend
- merge websocket events by `recipientId`
- expose mark-read and mark-all-read mutations
- stop treating local storage as the only persistence mechanism

### Admin Session Restore

Expected changes:

- when the app restores an authenticated admin session after refresh, fetch notifications again
- do not assume websocket alone can replay missed events

## Expected UX Guidance

- Notifications should remain visible after refresh or logout/login.
- Read state should remain stable across sessions.
- Realtime updates should feel immediate, but the inbox should still be correct even if the user was offline.
- If the current UI has both a compact dropdown and a fuller notifications page, make both read from the same backend-backed state.

## Implementation Guidance

- Reuse the existing frontend API client utilities and auth-aware request layer.
- Reuse the existing websocket client abstraction and authenticated subscription flow.
- Reuse current query/mutation hooks, state containers, or cache invalidation patterns.
- Do not build a second notification architecture next to the existing one.
- Treat backend responses as canonical after all notification mutations.
- Keep the notification state keyed by `recipientId` to avoid duplicates and incorrect read updates.

## Deliverables

- Backend-backed notification inbox integration using `GET /api/v1/admin/notifications`
- Backend-backed unread badge integration using `GET /api/v1/admin/notifications/unread-count`
- Single read action using `PATCH /api/v1/admin/notifications/{recipientId}/read`
- Bulk read action using `PATCH /api/v1/admin/notifications/read-all` if the current UI supports it
- Updated websocket event handling for the new persisted payload shape
- Removal of frontend-only notification persistence as the source of truth
- Notification click behavior aligned with `referenceType` and `referenceId`

## Implementation Checklist

- Identify the current notification source of truth in the frontend and remove any assumption that local storage alone owns notification state.
- Update the shared notification type/interface to match the persisted backend payload:
  - `recipientId`
  - `notificationId`
  - `type`
  - `title`
  - `message`
  - `referenceType`
  - `referenceId`
  - `read`
  - `readAt`
  - `deliveredAt`
  - `createdAt`
- Add API client methods for:
  - `GET /api/v1/admin/notifications`
  - `GET /api/v1/admin/notifications/unread-count`
  - `PATCH /api/v1/admin/notifications/{recipientId}/read`
  - `PATCH /api/v1/admin/notifications/read-all`
- Update the admin notification store/hook/context to hydrate from `GET /api/v1/admin/notifications` on admin session load.
- Key notification state by `recipientId` so initial fetches and websocket events can be merged safely.
- Update websocket message handling to upsert by `recipientId` instead of append-only behavior.
- Replace frontend-derived unread badge logic with backend unread count integration.
- Wire the notification bell, dropdown, drawer, panel, or page to render persisted notifications from backend-backed state.
- Wire single-notification read behavior to `PATCH /api/v1/admin/notifications/{recipientId}/read`.
- Wire mark-all-read behavior to `PATCH /api/v1/admin/notifications/read-all` if the current UI supports bulk actions.
- Ensure notification click/open actions update local state consistently after successful read mutation.
- Update any old payment-specific notification rendering logic to use the new generic fields.
- Map `referenceType = ORDER` and `referenceId = <orderNumber>` to the correct admin order details route if that route exists.
- Make sure admin refresh, logout/login, and websocket reconnect all rehydrate from backend state rather than expecting missed socket replay.
- Preserve existing frontend architecture and reuse existing API, auth, websocket, and query abstractions instead of building parallel notification systems.

## Verification Checklist

- An admin who refreshes the page still sees previously received notifications.
- An admin who logs out and logs back in still sees persisted notifications.
- A notification sent while no admin tab is open appears after the admin next loads the app.
- A notification sent while the admin is online appears in realtime without requiring refresh.
- The same notification does not appear twice when it exists in both initial fetch data and websocket data.
- Unread badge count matches backend unread count after page load.
- Unread badge count increments correctly when a new websocket notification arrives.
- Marking one notification as read updates the correct item and reduces unread count.
- Mark-all-read updates all unread notifications in the visible UI.
- Read state remains correct after refresh.
- Clicking a payment success notification navigates correctly using `referenceType` and `referenceId`, if the app supports deep linking from notifications.
- No remaining frontend code path treats local storage as the authoritative notification store.

## Important

- Do not change backend contracts.
- Do not keep frontend local storage as the source of truth for notifications.
- Do not mark notifications as read by `notificationId`.
- Do not assume websocket reconnect will replay missed events.
- If the frontend already has notification hooks, context, dropdowns, or inbox pages, extend them instead of building a parallel notification subsystem.
