# =============================================================================
# FRONTEND IMAGE (React app, served by nginx)
# -----------------------------------------------------------------------------
# Same multi-stage idea as the backend Dockerfile:
#   Stage 1 ("build"): Node.js compiles the React/TypeScript code into plain
#                      static files (HTML, JS, CSS) in /app/dist.
#   Stage 2 (final):   nginx, a fast web server, serves those files. Node and
#                      node_modules (hundreds of MB) are NOT in the final image.
#
# Why nginx at all? A browser needs something to hand it index.html and the JS.
# The same nginx also forwards /api/... requests to the backend container (see
# nginx.conf), so the browser only talks to ONE address.
# =============================================================================


# ----------------------------- STAGE 1: BUILD --------------------------------

# Node 24 matches the version used for local development.
# "alpine" = a tiny Linux distribution, which keeps the image small.
FROM node:24-alpine AS build

WORKDIR /app

# Copy ONLY the dependency lists first, then install. Same caching trick as
# pom.xml in the backend: this slow layer is reused until package.json or the
# lock file change, not on every code edit.
COPY package.json package-lock.json ./

# "npm ci" = clean, exact install from package-lock.json (reproducible, unlike
# "npm install" which may update the lock file). It fails if the two files
# disagree, which is what you want in a build.
RUN npm ci

# Now copy the rest of the source (node_modules, dist, .env are excluded by
# .dockerignore).
COPY . .

# Type-check (tsc) and bundle (vite). Output goes to /app/dist.
# The app calls relative URLs such as /api/books (src/lib/constants.ts), so
# NOTHING about the server's address is baked in: one image works on any domain.
RUN npm run build


# ----------------------------- STAGE 2: SERVE --------------------------------

# The official nginx image, "stable" release, alpine variant.
FROM nginx:stable-alpine

# The official image ships a default site config at this path. We replace it
# with ours (SPA routing, /api proxy, caching, compression).
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy the compiled site out of stage 1 into the folder nginx serves from.
COPY --from=build /app/dist /usr/share/nginx/html

# Documentation only: nginx listens on 80 inside the container. docker-compose
# publishes it to the host with "ports:".
EXPOSE 80

# Docker runs this command periodically to decide if the container is healthy.
# wget comes with alpine (busybox). We use 127.0.0.1 instead of "localhost"
# because "localhost" can resolve to IPv6 (::1), where nginx is not listening.
# /healthz is a tiny endpoint defined in nginx.conf.
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1/healthz >/dev/null || exit 1

# No CMD/ENTRYPOINT here: the nginx base image already defines one that starts
# nginx in the foreground ("nginx -g 'daemon off;'"). Containers stop when their
# main process exits, so nginx must stay in the foreground.
