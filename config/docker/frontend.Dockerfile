FROM node:22-alpine AS build
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
ENV VITE_API_BASE_URL=/api
RUN npm run build

FROM nginx:stable-alpine
ARG NGINX_CONFIG=config/nginx/population.container.conf
ARG NGINX_LB_STRATEGY=round_robin
COPY ${NGINX_CONFIG} /etc/nginx/conf.d/default.conf
COPY config/nginx/strategies/${NGINX_LB_STRATEGY}.conf /etc/nginx/upstream/population-strategy.conf
COPY --from=build /build/dist/ /usr/share/nginx/html/
EXPOSE 80
