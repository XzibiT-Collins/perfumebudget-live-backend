# Frontend Auth OTP And Feature Flag Integration Prompt

Update the existing frontend auth flow to match the backend OTP and feature-flag changes without breaking current API usage.

## Context

- The backend implementation is already complete.
- The frontend is already aware of the request and response DTO shapes, so do not redefine backend contracts unless needed for local typing alignment.
- This task is about wiring the existing frontend to the correct endpoints and handling the updated auth flow correctly.
- Source of truth for backend behavior is `documentation/auth-otp-feature-flags.md`.
- Auth route base is `/api/v1/auth`.
- Admin feature-flag route base is `/api/v1/admin/feature-flags`.

## Primary Goal

Update the frontend so email/password registration and login continue to work with the new OTP behavior, and add admin UI integration for the general feature-flag endpoints.

## Required Backend Endpoints

### Auth

- `POST /api/v1/auth/register`
  - Existing registration endpoint
  - Behavior: still creates the user and sends a registration OTP
- `POST /api/v1/auth/verify-otp`
  - Registration OTP verification endpoint
  - Request body: existing `OtpVerificationRequest`
  - Response wrapper: `CustomApiResponse<AuthResponse>`
- `POST /api/v1/auth/resend-otp`
  - Registration OTP resend endpoint
  - Request body: existing `EmailRequest`
  - Response wrapper: `CustomApiResponse<?>`
- `POST /api/v1/auth/login`
  - Email/password login endpoint
  - Request body: existing `LoginRequest`
  - Response wrapper: `CustomApiResponse<LoginResponse>`
- `POST /api/v1/auth/verify-login-otp`
  - Login OTP verification endpoint
  - Request body: `LoginOtpVerificationRequest`
  - Response wrapper: `CustomApiResponse<AuthResponse>`
- `POST /api/v1/auth/resend-login-otp`
  - Login OTP resend endpoint
  - Request body: `LoginOtpResendRequest`
  - Response wrapper: `CustomApiResponse<?>`

### Admin Feature Flags

- `GET /api/v1/admin/feature-flags`
  - Response wrapper: `CustomApiResponse<List<FeatureFlagResponse>>`
- `GET /api/v1/admin/feature-flags/{featureKey}`
  - Response wrapper: `CustomApiResponse<FeatureFlagResponse>`
- `PATCH /api/v1/admin/feature-flags/{featureKey}`
  - Request body: `FeatureFlagUpdateRequest`
  - Response wrapper: `CustomApiResponse<FeatureFlagResponse>`

## Critical Auth Behavior Changes The UI Must Support

### 1. Registration Remains OTP-Based

- After a successful call to `POST /register`, the user is not fully activated yet.
- The frontend must continue to send the user to the registration OTP verification screen.
- Registration OTP verification still uses:
  - `POST /verify-otp`
- Registration OTP resend still uses:
  - `POST /resend-otp`

### 2. Login Is Now Conditional

- `POST /login` no longer always means the user is fully signed in.
- The login response is now `LoginResponse` and must be treated as a two-state result.

The frontend must branch on:

- `requiresOtp`

Behavior:

- If `requiresOtp === false`
  - login is complete
  - `auth` is populated
  - continue the existing successful-login flow
- If `requiresOtp === true`
  - login is not complete yet
  - `auth` will be `null`
  - the response includes:
    - `challengeToken`
    - `email`
  - the frontend must transition the user into a login OTP verification step instead of treating login as complete

### 3. Login OTP Verification Is Challenge-Based

- Do not try to verify login OTP with email + otp only.
- The frontend must call:
  - `POST /verify-login-otp`
- The payload must include:
  - `challengeToken`
  - `otp`
- The frontend must preserve the returned `challengeToken` from `/login` long enough to complete verification or resend the OTP.

### 4. Login OTP Resend Is Separate From Registration OTP Resend

- For login OTP resend, call:
  - `POST /resend-login-otp`
- The payload must include:
  - `challengeToken`
- Do not reuse the registration resend flow for login OTP.

## Frontend Requirements

### Auth Flow

- Reuse the existing login, register, and OTP UI patterns where possible.
- Do not create a parallel auth architecture if the app already has:
  - auth pages
  - route guards
  - mutation hooks
  - auth store/session handling
  - toast/error handling
- Keep the implementation aligned with the current frontend architecture.

### Registration Flow

- Keep the current register -> verify OTP flow working.
- Make sure the register success path still routes into OTP verification and does not assume immediate authenticated completion.

### Login Flow

- Update the login submission handler to branch on `LoginResponse.requiresOtp`.
- When `requiresOtp` is `true`:
  - do not route to the authenticated app yet
  - do not assume a fully authenticated session in local UI state
  - navigate to a login OTP verification screen or step
  - preserve `challengeToken` and `email` in the appropriate temporary state container already used by the app for auth transitions
- When `requiresOtp` is `false`:
  - continue the normal post-login flow

### Login OTP Screen Requirements

- Add or adapt an OTP verification screen specifically for login completion.
- It must:
  - accept OTP input
  - call `POST /verify-login-otp`
  - send the stored `challengeToken`
  - allow resend via `POST /resend-login-otp`
- The screen can display the email returned from the login response for user confirmation.
- If the app already has a reusable OTP form component, reuse it and only switch endpoint wiring and payload composition.

### Feature Flag Admin Integration Requirements

- Add or update an admin settings screen for feature flags using the existing admin UI patterns.
- Use the general feature-flag endpoints rather than building a login-OTP-specific settings panel.
- At minimum, support displaying and updating `LOGIN_OTP`.
- The UI should expose separate toggles for:
  - admin users
  - customer users
- `PATCH /api/v1/admin/feature-flags/{featureKey}` should be called with:
  - `adminEnabled`
  - `customerEnabled`

## Expected UX Changes

### 1. Registration

- User submits registration form
- Frontend calls `POST /register`
- On success, frontend routes to registration OTP verification
- OTP verification uses `POST /verify-otp`
- Resend uses `POST /resend-otp`

### 2. Login When Login OTP Is Disabled For That Audience

- User submits email/password
- Frontend calls `POST /login`
- Response has `requiresOtp=false`
- Frontend completes the existing login success flow

### 3. Login When Login OTP Is Enabled For That Audience

- User submits email/password
- Frontend calls `POST /login`
- Response has `requiresOtp=true`
- Frontend routes to login OTP verification step
- Frontend stores `challengeToken`
- Frontend verifies with `POST /verify-login-otp`
- Frontend resends with `POST /resend-login-otp`
- Only after successful verification should the app continue as a completed login

## Implementation Guidance

- Integrate through the existing API service layer and auth state handling rather than ad hoc fetch logic.
- Reuse existing request/response wrappers, form validation patterns, route patterns, and shared OTP UI where possible.
- Do not change backend contracts.
- Do not collapse registration OTP and login OTP into one frontend action if the payloads and endpoint purposes differ.
- Keep temporary login challenge state scoped to the login OTP flow only.
- Handle loading, error, expired-OTP, and resend states using current app conventions.

## Important Backend Details The Frontend Must Respect

- `POST /verify-otp` is for registration OTP only.
- `POST /verify-login-otp` is for login OTP only.
- `POST /resend-otp` is for registration OTP only.
- `POST /resend-login-otp` is for login OTP only.
- `POST /login` may now return a pending-OTP state instead of immediate authenticated completion.
- Login OTP behavior is controlled by backend feature flags, so the frontend must always be able to handle both possible login outcomes.

## Deliverables

- Updated login flow integration for `LoginResponse`
- Login OTP verification and resend wiring using the correct endpoints
- Registration OTP flow preserved against the correct endpoints
- Admin feature-flag UI integration for `LOGIN_OTP`
- Consistent handling using existing frontend architecture, routing, auth state, and UI patterns

## Do Not

- change backend endpoints
- assume login always completes in one request
- verify login OTP with email instead of `challengeToken`
- reuse registration resend logic for login resend
- build a one-off login OTP toggle screen outside the general feature-flag management flow if the app already has a settings/admin pattern
