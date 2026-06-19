#!/bin/sh
# Entrypoint for nginx reverse-proxy — generates config from env vars
set -e

: ${NGINX_PORT:=80}
: ${GATEWAY_HOST:=fs-gateway}
: ${GATEWAY_PORT:=8080}
: ${CUSTOMER_HOST:=fs-customer-fe}
: ${CUSTOMER_PORT:=3000}
: ${SELLER_HOST:=fs-seller-fe}
: ${SELLER_PORT:=3001}
: ${ADMIN_HOST:=fs-admin-fe}
: ${ADMIN_PORT:=3002}

# Generate nginx config with env values substituted
# Using sed to replace __PLACEHOLDER__ tokens AFTER writing the template
sed \
    -e "s|__NGINX_PORT__|${NGINX_PORT}|g" \
    -e "s|__GATEWAY_HOST__|${GATEWAY_HOST}|g" \
    -e "s|__GATEWAY_PORT__|${GATEWAY_PORT}|g" \
    -e "s|__CUSTOMER_HOST__|${CUSTOMER_HOST}|g" \
    -e "s|__CUSTOMER_PORT__|${CUSTOMER_PORT}|g" \
    -e "s|__SELLER_HOST__|${SELLER_HOST}|g" \
    -e "s|__SELLER_PORT__|${SELLER_PORT}|g" \
    -e "s|__ADMIN_HOST__|${ADMIN_HOST}|g" \
    -e "s|__ADMIN_PORT__|${ADMIN_PORT}|g" \
    > /etc/nginx/conf.d/default.conf <<'ENDNGINX'
upstream gateway {
    server __GATEWAY_HOST__:__GATEWAY_PORT__;
}

upstream customer-app {
    server __CUSTOMER_HOST__:__CUSTOMER_PORT__;
}

upstream seller-app {
    server __SELLER_HOST__:__SELLER_PORT__;
}

upstream admin-app {
    server __ADMIN_HOST__:__ADMIN_PORT__;
}

# Rate limiting zones (http context)
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;
limit_req_zone $binary_remote_addr zone=frontend_limit:10m rate=20r/s;

server {
    listen __NGINX_PORT__;
    server_name _;

    real_ip_header X-Real-IP;
    real_ip_recursive on;

    # ── API routing ─────────────────────────────────────────
    # VITE_API_URL=/api/v1 → browser sends /api/v1/auth/register.
    # nginx forwards the full path unchanged to gateway (no trailing / on proxy_pass).
    # Gateway RouteConfig matches /api/v1/** and applies stripPrefix(1):
    #   /api/v1/auth/register → stripPrefix(1) → /v1/auth/register
    #   → identity-service @RequestMapping("/v1/auth") matches ✓
    location /api/ {
        limit_req zone=api_limit burst=20 nodelay;

        proxy_pass http://gateway;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_pass_request_body on;
        proxy_redirect off;
        proxy_buffering off;
        proxy_connect_timeout 10s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    location /seller/ {
        limit_req zone=frontend_limit burst=40 nodelay;

        proxy_pass http://seller-app/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_redirect off;
    }

    location /admin/ {
        limit_req zone=frontend_limit burst=40 nodelay;

        proxy_pass http://admin-app/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_redirect off;
    }

    location / {
        limit_req zone=frontend_limit burst=40 nodelay;

        proxy_pass http://customer-app/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_redirect off;
    }

    location /health {
        access_log off;
        default_type text/plain;
        return 200 "healthy\n";
    }

    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "no-referrer-when-downgrade" always;

    error_page 500 502 503 504 @error;
    location @error {
        default_type text/plain;
        return 503 "Service temporarily unavailable\n";
    }
}
ENDNGINX

exec nginx -g 'daemon off;'
