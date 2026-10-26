# syntax=docker/dockerfile:1
# Builds the static site, then serves it with nginx, which also proxies the API to the gateway.

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# GraphQL types are generated ahead of time and committed, so the build needs no schema file.
RUN npm run build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build /usr/share/nginx/html
