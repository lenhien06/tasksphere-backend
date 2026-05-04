# TaskSphere — Server Migration Guide

Làm theo đúng thứ tự này trên **VPS mới (Ubuntu 22.04)** là server sẽ chạy y hệt hiện tại.

---

## Thông tin cơ bản

| Thành phần | Chi tiết |
|---|---|
| Frontend | Next.js 15, chạy port **3000** |
| Backend | Spring Boot (Java 21), chạy port **8080** |
| Database | MySQL 8.0, exposed port **3308** |
| Cache | Redis 7, exposed port **6380** |
| Storage | MinIO, port **9000** (API) + **9001** (Console) |
| Nginx | Reverse proxy + SSL (Let's Encrypt) |
| Domain chính | `tasksphere.io.vn` |
| Domain API | `api.tasksphere.io.vn` |
| Domain Storage | `storage.tasksphere.io.vn` |

---

## Yêu cầu trước khi bắt đầu

1. **VPS tối thiểu**: 2 vCPU / 4 GB RAM / 40 GB SSD — Ubuntu 22.04 LTS
2. **DNS đã trỏ về IP mới** cho tất cả các domain sau (A record):
   - `tasksphere.io.vn`
   - `www.tasksphere.io.vn`
   - `api.tasksphere.io.vn`
   - `storage.tasksphere.io.vn`
3. SSH vào VPS với quyền **root**
4. Có sẵn tất cả API keys (xem phần [Biến môi trường](#biến-môi-trường))

---

## Bước 1 — Cài đặt hệ thống

```bash
apt-get update && apt-get upgrade -y
apt-get install -y curl wget git gnupg ca-certificates lsb-release \
  apt-transport-https software-properties-common ufw nginx certbot python3-certbot-nginx
```

### Cài Docker

```bash
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
```

---

## Bước 2 — Clone source code

```bash
cd /root
git clone https://github.com/lenhien06/tasksphere-backend.git
git clone https://github.com/lenhien06/tasksphere-frontend.git
```

---

## Bước 3 — Tạo file môi trường

### Backend — `/root/tasksphere-backend/.env`

> **Lấy giá trị thật từ server cũ**: `cat /root/tasksphere-backend/.env`

```bash
cat > /root/tasksphere-backend/.env << 'EOF'
GEMINI_API_KEY=<lấy từ server cũ>
SENDGRID_API_KEY=<lấy từ server cũ>
GOOGLE_CLIENT_ID=<lấy từ server cũ>
TURNSTILE_ENABLED=true
TURNSTILE_SECRET_KEY=<lấy từ server cũ>
GROQ_API_KEY=<lấy từ server cũ>
GROQ_MODEL=llama-3.3-70b-versatile
EOF
```

### Frontend — `/root/tasksphere-frontend/.env` và `.env.production`

> **Lấy giá trị thật từ server cũ**: `cat /root/tasksphere-frontend/.env`

```bash
cat > /root/tasksphere-frontend/.env << 'EOF'
NEXT_PUBLIC_API_URL=https://api.tasksphere.io.vn/api
NEXT_PUBLIC_APP_URL=https://tasksphere.io.vn
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<lấy từ server cũ>
NEXT_PUBLIC_TURNSTILE_SITE_KEY=<lấy từ server cũ>
EOF

# .env.production là bản copy y chang
cp /root/tasksphere-frontend/.env /root/tasksphere-frontend/.env.production
```

---

## Bước 4 — Sao chép các script vận hành về /root

Các script này **không nằm trong git** — phải tạo lại thủ công.

### `/root/setup.sh`

```bash
cat > /root/setup.sh << 'SCRIPT'
#!/usr/bin/env bash
set -euo pipefail

DOMAIN="tasksphere.io.vn"
API_DOMAIN="api.tasksphere.io.vn"
STORAGE_DOMAIN="storage.tasksphere.io.vn"
EMAIL="admin@tasksphere.io.vn"
FRONTEND_PORT=3000
BACKEND_PORT=8080
MINIO_PORT=9000
BE_DIR="$HOME/tasksphere-backend"
FE_DIR="$HOME/tasksphere-frontend"
NGINX_CONF_DIR="/etc/nginx/sites-available"
NGINX_ENABLED_DIR="/etc/nginx/sites-enabled"

log()  { echo -e "\033[0;36m[INFO]\033[0m  $*"; }
ok()   { echo -e "\033[0;32m[OK]\033[0m    $*"; }
warn() { echo -e "\033[1;33m[WARN]\033[0m  $*"; }
fail() { echo -e "\033[0;31m[ERROR]\033[0m $*"; exit 1; }

[[ $EUID -ne 0 ]] && fail "Run as root."

log "Configuring UFW firewall..."
ufw --force reset > /dev/null
ufw default deny incoming > /dev/null
ufw default allow outgoing > /dev/null
ufw allow ssh > /dev/null
ufw allow 80/tcp > /dev/null
ufw allow 443/tcp > /dev/null
ufw --force enable > /dev/null
ok "Firewall configured."

log "Writing Nginx configs..."
rm -f "$NGINX_ENABLED_DIR/default"

cat > "$NGINX_CONF_DIR/$DOMAIN" <<EOF
server {
    listen 80;
    server_name $DOMAIN www.$DOMAIN;
    client_max_body_size 50M;
    location / {
        proxy_pass         http://127.0.0.1:$FRONTEND_PORT;
        proxy_http_version 1.1;
        proxy_set_header   Upgrade \$http_upgrade;
        proxy_set_header   Connection 'upgrade';
        proxy_set_header   Host \$host;
        proxy_set_header   X-Real-IP \$remote_addr;
        proxy_set_header   X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto \$scheme;
        proxy_cache_bypass \$http_upgrade;
        proxy_read_timeout 60s;
    }
}
EOF

cat > "$NGINX_CONF_DIR/$API_DOMAIN" <<EOF
server {
    listen 80;
    server_name $API_DOMAIN;
    client_max_body_size 100M;
    location / {
        proxy_pass         http://127.0.0.1:$BACKEND_PORT;
        proxy_http_version 1.1;
        proxy_set_header   Host \$host;
        proxy_set_header   X-Real-IP \$remote_addr;
        proxy_set_header   X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto \$scheme;
        proxy_read_timeout 120s;
        proxy_connect_timeout 60s;
        proxy_send_timeout  120s;
    }
}
EOF

cat > "$NGINX_CONF_DIR/$STORAGE_DOMAIN" <<EOF
server {
    listen 80;
    server_name $STORAGE_DOMAIN;
    client_max_body_size 512M;
    ignore_invalid_headers off;
    location / {
        proxy_pass         http://127.0.0.1:$MINIO_PORT;
        proxy_http_version 1.1;
        proxy_set_header   Host \$host;
        proxy_set_header   X-Real-IP \$remote_addr;
        proxy_set_header   X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto \$scheme;
        proxy_set_header   Connection "";
        proxy_read_timeout    300s;
        proxy_connect_timeout  60s;
        proxy_send_timeout    300s;
        proxy_buffering    off;
        proxy_request_buffering off;
    }
}
EOF

ln -sf "$NGINX_CONF_DIR/$DOMAIN"         "$NGINX_ENABLED_DIR/$DOMAIN"
ln -sf "$NGINX_CONF_DIR/$API_DOMAIN"     "$NGINX_ENABLED_DIR/$API_DOMAIN"
ln -sf "$NGINX_CONF_DIR/$STORAGE_DOMAIN" "$NGINX_ENABLED_DIR/$STORAGE_DOMAIN"
ok "Nginx sites enabled."

nginx -t || fail "Nginx config invalid."
systemctl enable nginx
systemctl reload nginx
ok "Nginx started."

log "Starting Docker containers..."
if [[ -d "$BE_DIR" ]]; then
  cd "$BE_DIR" && docker compose up -d --build
  ok "Backend containers started."
fi
if [[ -d "$FE_DIR" ]]; then
  cd "$FE_DIR" && docker compose up -d --build
  ok "Frontend containers started."
fi

log "Obtaining SSL certificates..."
obtain_cert() {
  local domain="$1" extra="${2:-}"
  if certbot certificates 2>/dev/null | grep -q "Domains:.*$domain"; then
    certbot renew --quiet --nginx --cert-name "$domain" 2>/dev/null || true
  else
    certbot --nginx --non-interactive --agree-tos --email "$EMAIL" --redirect $extra -d "$domain" \
      || warn "Certbot failed for $domain — check DNS propagation."
  fi
}
obtain_cert "$DOMAIN" "-d www.$DOMAIN"
obtain_cert "$API_DOMAIN"
obtain_cert "$STORAGE_DOMAIN"

if ! crontab -l 2>/dev/null | grep -q "certbot renew"; then
  (crontab -l 2>/dev/null; echo "0 3 * * * certbot renew --quiet --nginx && systemctl reload nginx") | crontab -
  ok "Certbot auto-renewal cron added."
fi

nginx -t && systemctl reload nginx
ok "Done. Checking status..."
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
SCRIPT
chmod +x /root/setup.sh
```

### `/root/deploy.sh`

```bash
cat > /root/deploy.sh << 'SCRIPT'
#!/bin/bash
set -euo pipefail
LOG_FILE="/var/log/tasksphere-deploy.log"
TARGET="${1:-both}"
log() { local ts; ts=$(date '+%Y-%m-%d %H:%M:%S'); echo "[$ts] [DEPLOY] $*" | tee -a "$LOG_FILE"; }
touch "$LOG_FILE"; chmod 640 "$LOG_FILE"
log "TaskSphere deployment started | target=$TARGET"
case "$TARGET" in
  backend)  bash /root/deploy-backend.sh ;;
  frontend) bash /root/deploy-frontend.sh ;;
  both)     bash /root/deploy-backend.sh; bash /root/deploy-frontend.sh ;;
  *)        log "Unknown target: $TARGET. Use: both | backend | frontend"; exit 1 ;;
esac
log "All deployments finished."
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
SCRIPT
chmod +x /root/deploy.sh
```

### `/root/deploy-backend.sh`

```bash
cat > /root/deploy-backend.sh << 'SCRIPT'
#!/bin/bash
set -euo pipefail
DEPLOY_DIR="/root/tasksphere-backend"
CONTAINER_NAME="tasksphere-app"
HEALTH_URL="http://127.0.0.1:8080/actuator/health"
LOG_FILE="/var/log/tasksphere-deploy.log"
IMAGE_NAME="tasksphere-backend-app"
PREV_IMAGE_TAG="previous"

log() { local ts; ts=$(date '+%Y-%m-%d %H:%M:%S'); echo "[$ts] [BACKEND] $*" | tee -a "$LOG_FILE"; }
fail() { log "ERROR: $*"; exit 1; }

wait_healthy() {
  log "Waiting for backend to become healthy..."
  for i in $(seq 1 18); do
    if curl -sf "$HEALTH_URL" > /dev/null 2>&1; then log "Health check passed (attempt $i)."; return 0; fi
    log "Attempt $i/18 — waiting 10s..."; sleep 10
  done
  return 1
}

rollback() {
  log "ROLLING BACK..."
  local prev_id; prev_id=$(docker images -q "${IMAGE_NAME}:${PREV_IMAGE_TAG}" 2>/dev/null || true)
  [[ -z "$prev_id" ]] && { log "No previous image. Cannot rollback."; exit 1; }
  docker tag "${IMAGE_NAME}:${PREV_IMAGE_TAG}" "${IMAGE_NAME}:latest"
  cd "$DEPLOY_DIR" && docker compose up -d app
  wait_healthy && log "Rollback successful." || log "CRITICAL: Rollback also failed."
  exit 1
}

log "Starting backend deployment..."
command -v docker &>/dev/null || fail "Docker not found."
cd "$DEPLOY_DIR"
git fetch origin main && git pull origin main
log "Deployed commit: $(git rev-parse --short HEAD) — $(git log -1 --pretty=%s)"

local_id=$(docker images -q "${IMAGE_NAME}:latest" 2>/dev/null || true)
[[ -n "$local_id" ]] && docker tag "${IMAGE_NAME}:latest" "${IMAGE_NAME}:${PREV_IMAGE_TAG}" || true

docker compose build app || fail "Docker build failed."
docker compose up -d --no-deps app

if wait_healthy; then
  log "Deployment complete."
  docker ps --filter "name=$CONTAINER_NAME" --format "  Name: {{.Names}} | Status: {{.Status}}"
else
  log "Health check failed. Rolling back..."
  docker logs "$CONTAINER_NAME" --tail 300 || true
  rollback
fi
SCRIPT
chmod +x /root/deploy-backend.sh
```

### `/root/deploy-frontend.sh`

```bash
cat > /root/deploy-frontend.sh << 'SCRIPT'
#!/bin/bash
set -euo pipefail
DEPLOY_DIR="/root/tasksphere-frontend"
CONTAINER_NAME="tasksphere-frontend-webapp"
HEALTH_URL="http://127.0.0.1:3000"
LOG_FILE="/var/log/tasksphere-deploy.log"
IMAGE_NAME="tasksphere-frontend-tasksphere-webapp"
PREV_IMAGE_TAG="previous"

log() { local ts; ts=$(date '+%Y-%m-%d %H:%M:%S'); echo "[$ts] [FRONTEND] $*" | tee -a "$LOG_FILE"; }
fail() { log "ERROR: $*"; exit 1; }

wait_healthy() {
  for i in $(seq 1 12); do
    if curl -sf --max-time 5 "$HEALTH_URL" > /dev/null 2>&1; then log "Health check passed (attempt $i)."; return 0; fi
    log "Attempt $i/12 — waiting 10s..."; sleep 10
  done
  return 1
}

rollback() {
  local prev_id; prev_id=$(docker images -q "${IMAGE_NAME}:${PREV_IMAGE_TAG}" 2>/dev/null || true)
  [[ -z "$prev_id" ]] && { log "No previous image. Cannot rollback."; exit 1; }
  docker tag "${IMAGE_NAME}:${PREV_IMAGE_TAG}" "${IMAGE_NAME}:latest"
  cd "$DEPLOY_DIR" && docker compose up -d
  wait_healthy && log "Rollback successful." || log "CRITICAL: Rollback also failed."
  exit 1
}

env_file="$DEPLOY_DIR/.env.production"
[[ -f "$env_file" ]] || fail ".env.production not found."
api_url=$(grep "^NEXT_PUBLIC_API_URL=" "$env_file" | cut -d= -f2- | tr -d '"' || true)
echo "$api_url" | grep -q "localhost" && fail "NEXT_PUBLIC_API_URL contains localhost!"
log "API URL: $api_url — OK"

cd "$DEPLOY_DIR"
git pull origin main
log "Deployed commit: $(git rev-parse --short HEAD) — $(git log -1 --pretty=%s)"

local_id=$(docker images -q "${IMAGE_NAME}:latest" 2>/dev/null || true)
[[ -n "$local_id" ]] && docker tag "${IMAGE_NAME}:latest" "${IMAGE_NAME}:${PREV_IMAGE_TAG}" || true

docker compose build || fail "Docker build failed."
docker compose up -d --force-recreate

if wait_healthy; then
  log "Deployment complete."
  docker ps --filter "name=$CONTAINER_NAME" --format "  Name: {{.Names}} | Status: {{.Status}}"
else
  log "Health check failed. Rolling back..."
  rollback
fi
SCRIPT
chmod +x /root/deploy-frontend.sh
```

### `/root/backup.sh`

```bash
cat > /root/backup.sh << 'SCRIPT'
#!/bin/bash
set -euo pipefail
BACKUP_ROOT="/root/backups"
TIMESTAMP=$(date '+%Y-%m-%d_%H-%M-%S')
LABEL="${1:-manual}"
LABEL_SAFE=$(echo "$LABEL" | tr ' ' '-' | tr -cd '[:alnum:]_-')
BACKUP_DIR="$BACKUP_ROOT/${TIMESTAMP}_${LABEL_SAFE}"
BE_DIR="/root/tasksphere-backend"
FE_DIR="/root/tasksphere-frontend"
DB_CONTAINER="tasksphere-db"
DB_NAME="tasksphere_prod"
DB_USER="tasksphere_user"
DB_PASS="TaskSphere@2026#SecurePass"
BE_IMAGE="tasksphere-backend-app"
FE_IMAGE="tasksphere-frontend-tasksphere-webapp"
TAG_PREFIX="bk_${TIMESTAMP}"
LOG_FILE="/var/log/tasksphere-deploy.log"

log() { local ts; ts=$(date '+%Y-%m-%d %H:%M:%S'); echo "[$ts] [BACKUP] $*" | tee -a "$LOG_FILE"; }
ok() { echo "  [OK] $*"; }

mkdir -p "$BACKUP_DIR"
log "Starting backup: $TIMESTAMP | label=$LABEL"

if docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
  docker exec "$DB_CONTAINER" mysqldump -u"$DB_USER" -p"$DB_PASS" --single-transaction --routines --triggers "$DB_NAME" | gzip > "$BACKUP_DIR/db.sql.gz"
  ok "MySQL dump done ($(du -sh "$BACKUP_DIR/db.sql.gz" | cut -f1))"
fi

for img in "$BE_IMAGE" "$FE_IMAGE"; do
  id=$(docker images -q "${img}:latest" 2>/dev/null || true)
  [[ -n "$id" ]] && docker tag "${img}:latest" "${img}:${TAG_PREFIX}" && ok "Tagged ${img}:${TAG_PREFIX}"
done

{
  echo "timestamp=$TIMESTAMP"; echo "label=$LABEL"; echo "tag_prefix=$TAG_PREFIX"; echo ""
  [[ -d "$BE_DIR/.git" ]] && echo "backend_commit=$(cd "$BE_DIR" && git rev-parse HEAD)"
  [[ -d "$FE_DIR/.git" ]] && echo "frontend_commit=$(cd "$FE_DIR" && git rev-parse HEAD)"
} > "$BACKUP_DIR/info.txt"

log "Backup done: $BACKUP_DIR"
echo "Restore: ./restore.sh ${TIMESTAMP}_${LABEL_SAFE}"
SCRIPT
chmod +x /root/backup.sh
```

---

## Bước 5 — Chạy setup

> **Lưu ý**: DNS phải đã propagate trước khi chạy bước này (certbot cần resolve được domain).

```bash
chmod +x /root/setup.sh
bash /root/setup.sh
```

Script sẽ tự động:
- Cấu hình UFW firewall (chỉ mở SSH / 80 / 443)
- Tạo 3 Nginx virtual host
- Build và start tất cả Docker containers (MySQL, Redis, MinIO, Spring Boot, Next.js)
- Cấp SSL certificate Let's Encrypt cho cả 3 domain
- Thêm cron tự gia hạn SSL lúc 03:00 hàng ngày

---

## Bước 6 — Kiểm tra sau khi setup

```bash
# Xem trạng thái container
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# Test health backend
curl -s https://api.tasksphere.io.vn/actuator/health | python3 -m json.tool

# Test frontend
curl -I https://tasksphere.io.vn

# Xem log deploy
tail -50 /var/log/tasksphere-deploy.log
```

**Kết quả mong đợi**: 5 container đều `healthy`

```
tasksphere-frontend-webapp   Up X minutes (healthy)  0.0.0.0:3000->3000/tcp
tasksphere-app               Up X minutes (healthy)  0.0.0.0:8080->8080/tcp
tasksphere-redis             Up X minutes (healthy)  0.0.0.0:6380->6379/tcp
tasksphere-db                Up X minutes (healthy)  0.0.0.0:3308->3306/tcp
tasksphere-minio             Up X minutes (healthy)  0.0.0.0:9000-9001->9000-9001/tcp
```

---

## Bước 7 — Migrate dữ liệu từ server cũ (nếu cần)

Nếu cần giữ lại dữ liệu database:

### Trên server cũ — dump database

```bash
./backup.sh "truoc-khi-chuyen-server"
# File dump nằm ở: /root/backups/<timestamp>_truoc-khi-chuyen-server/db.sql.gz
```

### Copy sang server mới

```bash
# Chạy trên server cũ
scp /root/backups/<tên-backup>/db.sql.gz root@<IP-SERVER-MỚI>:/root/

# Chạy trên server mới — restore vào MySQL đang chạy
gunzip -c /root/db.sql.gz | docker exec -i tasksphere-db \
  mysql -utasksphere_user -p"TaskSphere@2026#SecurePass" tasksphere_prod
```

### Migrate MinIO data (file uploads)

```bash
# Trên server cũ
docker exec tasksphere-minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker exec tasksphere-minio mc mirror local/tasksphere-files /tmp/minio-export/
scp -r /tmp/minio-export/ root@<IP-SERVER-MỚI>:/tmp/

# Trên server mới
docker exec tasksphere-minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker cp /tmp/minio-export/. tasksphere-minio:/tmp/
docker exec tasksphere-minio mc mirror /tmp/minio-export/ local/tasksphere-files
```

---

## Biến môi trường

> Cập nhật các giá trị này nếu key hết hạn hoặc bị revoke.

| Biến | Dịch vụ | Nằm ở |
|---|---|---|
| `GEMINI_API_KEY` | Google Gemini AI | BE `.env` |
| `SENDGRID_API_KEY` | Email transactional | BE `.env` |
| `GOOGLE_CLIENT_ID` | OAuth2 login | BE `.env` + FE `.env` |
| `TURNSTILE_SECRET_KEY` | Cloudflare bot protection | BE `.env` |
| `GROQ_API_KEY` | Groq LLM (Llama) | BE `.env` |
| `NEXT_PUBLIC_TURNSTILE_SITE_KEY` | Cloudflare (public) | FE `.env` |

---

## Vận hành hàng ngày

| Tác vụ | Lệnh |
|---|---|
| Deploy cả hai | `bash /root/deploy.sh` |
| Deploy chỉ backend | `bash /root/deploy.sh backend` |
| Deploy chỉ frontend | `bash /root/deploy.sh frontend` |
| Tạo backup | `bash /root/backup.sh "ten-mo-ta"` |
| Xem danh sách backup | `bash /root/restore.sh` |
| Restore backup | `bash /root/restore.sh <tên-backup>` |
| Xem log deploy | `tail -f /var/log/tasksphere-deploy.log` |
| Xem log backend | `docker logs tasksphere-app -f --tail 100` |
| Xem log frontend | `docker logs tasksphere-frontend-webapp -f --tail 100` |
| Restart toàn bộ | `cd /root/tasksphere-backend && docker compose restart` |
