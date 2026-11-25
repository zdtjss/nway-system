# syntax=docker/dockerfile:1
# 多阶段构建：第一阶段 Node 构建，第二阶段 Nginx 运行

# ====== 第一阶段：Node 构建 ======
FROM node:22-alpine AS builder
WORKDIR /build
# 先复制依赖描述文件，利用层缓存
COPY package*.json ./
RUN npm install
# 复制源码并构建
COPY . .
RUN npm run build

# ====== 第二阶段：Nginx 运行 ======
FROM nginx:1.27-alpine
LABEL maintainer="nway-system-web"
# 时区 Asia/Shanghai
ENV TZ=Asia/Shanghai
# 写入 nginx 配置：前端静态文件 + 后端 API 反向代理
RUN <<'SHELL'
cat > /etc/nginx/conf.d/default.conf <<'NGINX'
server {
    listen       80;
    server_name  _;

    # 安全响应头
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    # gzip 压缩
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript image/svg+xml;
    gzip_min_length 1024;
    gzip_vary on;

    # 健康检查端点
    location /health {
        access_log off;
        return 200 'OK';
        add_header Content-Type text/plain;
    }

    # 前端静态资源（SPA history 模式回退到 index.html）
    location / {
        root   /usr/share/nginx/html;
        index  index.html;
        try_files $uri $uri/ /index.html;
        # 静态资源缓存
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
            expires 30d;
            add_header Cache-Control "public, immutable";
        }
    }

    # 后端 API 反向代理
    location /nway-system/ {
        # CORS 跨域配置（白名单模式）
        set $cors_origin "";
        if ($http_origin ~* "^https?://(localhost(:[0-9]+)?|127\.0\.0\.1(:[0-9]+)?|YOUR_PRODUCTION_DOMAIN)$") {
            set $cors_origin $http_origin;
        }
        add_header Access-Control-Allow-Origin $cors_origin always;
        add_header Access-Control-Allow-Methods "GET, POST, PUT, DELETE, OPTIONS" always;
        add_header Access-Control-Allow-Headers "Authorization, Content-Type, X-Requested-With" always;
        add_header Access-Control-Allow-Credentials "true" always;
        add_header Access-Control-Max-Age 3600 always;
        if ($request_method = OPTIONS) {
            return 204;
        }
        proxy_pass http://nway-system-service:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 60s;
        # 提示词提取等同步模型调用耗时较长，读超时放宽到 5 分钟
        proxy_read_timeout 300s;
    }

    # 兼容 /api/ 前缀访问
    location /api/ {
        # CORS 跨域配置（白名单模式）
        set $cors_origin "";
        if ($http_origin ~* "^https?://(localhost(:[0-9]+)?|127\.0\.0\.1(:[0-9]+)?|YOUR_PRODUCTION_DOMAIN)$") {
            set $cors_origin $http_origin;
        }
        add_header Access-Control-Allow-Origin $cors_origin always;
        add_header Access-Control-Allow-Methods "GET, POST, PUT, DELETE, OPTIONS" always;
        add_header Access-Control-Allow-Headers "Authorization, Content-Type, X-Requested-With" always;
        add_header Access-Control-Allow-Credentials "true" always;
        add_header Access-Control-Max-Age 3600 always;
        if ($request_method = OPTIONS) {
            return 204;
        }
        rewrite ^/api/(.*)$ /nway-system/$1 break;
        proxy_pass http://nway-system-service:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_read_timeout 300s;
    }
}
NGINX
SHELL
# 复制构建产物到 nginx 静态目录
COPY --from=builder /build/dist /usr/share/nginx/html
EXPOSE 80
