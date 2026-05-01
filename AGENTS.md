# AGENTS.md

## Repo layout
- `backend/` is the Spring Boot (Java 17) API; context path is `/api` and default port is 8080 (see `backend/src/main/resources/application.yml`).
- `frontend/` is the Vite app; primary UI is Vue, but Vite also enables the React plugin and `.jsx` components live under `frontend/src/components/`.
- `database/init.sql` is the canonical MySQL schema/init script (the backend README still mentions `database/schema.sql`, which does not exist).
- Root `package.json` has no scripts; run frontend/backend commands from their own directories.

## Local dev commands
- Backend (from `backend/`): `mvn clean install` then `mvn spring-boot:run`.
- Frontend (from `frontend/`): `npm install` then `npm run dev` (Vite on `http://localhost:5173/`).
- DB init: execute `database/init.sql` in MySQL (it creates `secure_file_manager`).

## Runtime configuration gotchas
- Frontend API base comes from `VITE_API_BASE_URL` or defaults to `protocol//hostname:8080/api` in `frontend/src/api/index.js`.
- `secure-file.storage-root` in `backend/src/main/resources/application.yml` must be an existing, writable absolute path; encrypted files live there.
- `backend/src/main/resources/application.yml` contains demo secrets (DB/mail/JWT/AI keys); treat as local-only and avoid logging or reusing them.

## Preview / kkFileView
- Optional document preview uses kkFileView; config lives under `secure-file.viewer.*` in `backend/src/main/resources/application.yml` and can be overridden via `SECURE_FILE_VIEWER_*` env vars (details in `docs/本地部署指南.md`).
- When kkFileView runs in Docker/remote, `source-base-url` must be reachable from that container or previews will fail.

## Chat WebSocket
- Realtime chat endpoint is `ws://<host>:8080/api/chat/ws?token=<JWT>` (token is required query param).
