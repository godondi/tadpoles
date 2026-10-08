# Frontend containerization notes

## What changed
- Added a real frontend container image in `frontend/Dockerfile`.
- Added a `frontend` service to `docker-compose.yml`.
- Added frontend port variables to `.env.template`.

## Container runtime
The Angular frontend is an SSR app, so the container now:
1. Installs dependencies with `npm ci`.
2. Builds the app with `npm run build`.
3. Runs the Node SSR entrypoint at `dist/pole/server/server.mjs`.

The runtime listens on `PORT` inside the container, defaulting to `4000`.

## Compose wiring
The new compose service publishes the frontend using:
- `FRONTEND_HOST_PORT`
- `FRONTEND_CONTAINER_PORT`

The service uses the same `.env` file pattern as the rest of the stack.

## Environment template additions
Added to `.env.template`:
- `FRONTEND_HOST_PORT=8080`
- `FRONTEND_CONTAINER_PORT=4000`

## How to run
1. Copy the template to your local env file if needed:
   ```powershell
   copy .env.template .env
   ```
2. Start the stack:
   ```powershell
   docker compose up --build
   ```
3. Open the frontend at the configured host port, default:
   - `http://localhost:8080`

## Notes
- The frontend now runs as its own container and does not depend on the placeholder `nginx/Dockerfile`.
- No application code changes were required because the frontend already ships with an SSR server entrypoint.

