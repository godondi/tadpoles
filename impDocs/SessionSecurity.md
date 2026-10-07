# Session Security

## What changed

Implemented a real revocable session flow across login, refresh, logout, and authenticated requests.

The backend now:

- returns both an access token and a refresh token from `POST /auth/login`
- rotates the refresh token on `POST /api/auth/refresh`
- revokes the submitted refresh token on `POST /api/auth/logout`
- lets admins revoke all active refresh tokens for a user through `POST /api/users/{userId}/refresh-tokens/revoke-all`
- rejects authenticated requests whose linked session has already been revoked or expired

## How it works

### Login

Login now creates a refresh-token record first, then issues a signed access JWT that is bound to that refresh-token record through a `refreshTokenId` claim.

That means the access token is no longer just time-limited; it is also tied to a specific stored session record.

### Refresh

Refreshing a session now:

1. validates the submitted refresh token
2. revokes the old refresh token row
3. creates a new refresh token row
4. issues a new signed access JWT bound to the new refresh-token row

This preserves the rotation pattern that was already present, but now the returned access token is a real JWT instead of an opaque placeholder string.

### Logout and admin revocation

Logout still revokes the submitted refresh token, but because access JWTs are now tied to refresh-token rows, that revocation also invalidates the associated signed-in session for future requests.

Admin bulk revocation uses the same model by revoking every active refresh token for the target user.

### Request-time enforcement

Added `AccessTokenSessionFilter` in the security configuration.

For authenticated requests, if the JWT contains a `refreshTokenId` claim, the filter checks the corresponding `refresh_tokens` row and rejects the request when the session has been revoked, expired, or no longer matches the token user.

## Refactor

Introduced `AccessTokenService` so access-JWT generation is shared between login and refresh flows instead of duplicated across services.

## Tests added or updated

- `AuthControllerTest`
- `AuthServiceImplTest`
- `RefreshTokenControllerTest`
- `RefreshTokenServiceImplTest`
- `RefreshTokenMapperTest`
- `ClientControllerTest` session-revocation coverage

## API docs

Updated `docs/api.yaml` so login now returns the shared auth-token payload and the new admin revoke-all session endpoint is documented.
