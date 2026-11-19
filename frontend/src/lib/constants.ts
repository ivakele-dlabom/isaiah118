// Where the browser sends API requests.
//
// "/" means "the same server that served this page". In production, nginx serves
// the site AND forwards anything starting with /api/ to the Spring Boot backend,
// so the browser only ever talks to one address (no CORS, no hardcoded host).
// In development, the Vite dev server does the same job with the proxy in
// vite.config.ts.
//
// Keep the trailing slash: callers write `${BACKEND_BASE_URL}api/...`.
export const BACKEND_BASE_URL = "/";
export const FRONTEND_BASE_URL = "http://localhost:5173/"
