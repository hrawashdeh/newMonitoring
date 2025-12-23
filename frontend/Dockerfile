# Multi-stage build for React frontend

# Stage 1: Build the React application
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files
COPY package.json package-lock.json* ./

# Install dependencies
RUN npm ci

# Copy source code
COPY . .

# Build for production
RUN npm run build

# Stage 2: Serve with NGINX
FROM nginx:1.25-alpine

# Create directories that nginx user can write to
RUN mkdir -p /tmp/client_temp \
             /tmp/proxy_temp_path \
             /tmp/fastcgi_temp \
             /tmp/uwsgi_temp \
             /tmp/scgi_temp \
             /var/log/nginx \
    && chown -R nginx:nginx /tmp \
    && chown -R nginx:nginx /var/log/nginx \
    && chown -R nginx:nginx /usr/share/nginx/html \
    && chmod -R 777 /tmp \
    && chmod -R 755 /var/log/nginx

# Copy custom nginx main config (to use /tmp for pid file)
COPY nginx-main.conf /etc/nginx/nginx.conf

# Remove default server config
RUN rm -rf /etc/nginx/conf.d/default.conf

# Copy custom server config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy built React app from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Expose port 8080 (non-privileged port)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/health || exit 1

# Switch to nginx user
USER nginx

# Start nginx
CMD ["nginx", "-g", "daemon off;"]
