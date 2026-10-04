# Iris web remote: the built Quasar app, served by nginx on port 80.
# It calls the API at /api on its own host, and Traefik sends /api to the backend container,
# so this image has no configuration and is identical in every environment.
FROM node:24-alpine AS build
WORKDIR /app
COPY . .
RUN npm ci --no-audit --no-fund && npm run build

FROM nginx:1.30-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/spa /usr/share/nginx/html
