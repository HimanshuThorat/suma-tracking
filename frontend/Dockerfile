# =====================================================================
# Suma Tracking website (React) as a Docker image.
# Stage 1 builds the website with Node. Stage 2 serves it with nginx,
# which also passes /api calls on to the backend container.
# =====================================================================
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
