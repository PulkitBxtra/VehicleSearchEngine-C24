FROM node:24-alpine AS build
WORKDIR /build
# Dependencies first, so a source edit does not re-resolve the tree.
COPY package.json package-lock.json ./
# `npm install`, not `npm ci`, and deliberately so. Tailwind's oxide and
# lightningcss ship platform-specific native binaries as optional dependencies,
# and npm records only the host platform's variants in the lock file. A lock
# generated on macOS therefore has no Linux entries, and `npm ci` — which
# refuses to deviate from the lock — fails inside the image. `npm install`
# resolves the correct binary for the build platform while still honouring the
# locked versions of everything else.
RUN npm install --no-audit --no-fund
COPY . .
# api-types.ts is committed, so the build needs no running backend.
RUN npm run build

# No node runtime in production: Caddy serves the static output, and this stage
# exists only to hand the compiled bundle to a volume.
FROM alpine:3.20 AS dist
WORKDIR /dist
COPY --from=build /build/dist ./
