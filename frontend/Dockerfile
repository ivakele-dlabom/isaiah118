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

COPY package.json package-lock.json ./

RUN npm ci

# Now copy the rest of the source (node_modules, dist, .env are excluded by
# .dockerignore).
COPY . .

RUN npm run build



# The official nginx image, "stable" release, alpine variant.
FROM nginx:stable-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy the compiled site out of stage 1 into the folder nginx serves from.
COPY --from=build /app/dist /usr/share/nginx/html

# Documentation only: nginx listens on 80 inside the container. docker-compose
# publishes it to the host with "ports:".
EXPOSE 80

# /healthz is a tiny endpoint defined in nginx.conf.
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1/healthz >/dev/null || exit 1

