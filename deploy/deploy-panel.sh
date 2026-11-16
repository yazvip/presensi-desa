#!/usr/bin/env bash
# Pasang API + panel admin Presensi Desa di VPS.
#   ssh vps 'bash -s' < deploy/deploy-panel.sh
# Prasyarat: /tmp/presensi-backend.tar.gz dan /tmp/presensi-webadmin.tar.gz sudah diunggah.
set -euo pipefail
DOMAIN="absen.talangarah.web.id"
WEBROOT="/var/www/presensi-desa/admin"
CONF="/etc/nginx/sites-available/presensi-desa.conf"

echo "== 1. backend: build + restart =="
sudo -n rm -rf /opt/presensi-desa/src /opt/presensi-desa/dist /opt/presensi-desa/db /opt/presensi-desa/test
tar xzf /tmp/presensi-backend.tar.gz -C /opt/presensi-desa --strip-components=1
cd /opt/presensi-desa && npm run build --silent
sudo -n systemctl restart presensi-api
sleep 3
curl -s -m 5 http://127.0.0.1:8181/health; echo

echo "== 2. panel: pasang di \$WEBROOT =="
sudo -n rm -rf "$WEBROOT"
sudo -n mkdir -p /var/www/presensi-desa
rm -rf /tmp/web-admin
tar xzf /tmp/presensi-webadmin.tar.gz -C /tmp
sudo -n cp -r /tmp/web-admin "$WEBROOT"
sudo -n chown -R root:root "$WEBROOT"
sudo -n find "$WEBROOT" -type d -exec chmod 755 {} +
sudo -n find "$WEBROOT" -type f -exec chmod 644 {} +
find "$WEBROOT" -maxdepth 2 -type f | sed "s#\$WEBROOT/##" | sort | head -20

echo "== 3. nginx: lokasi /admin/ =="
sudo -n python3 - <<'PYEOF'
conf = "/etc/nginx/sites-available/presensi-desa.conf"
s = open(conf).read()
s = s.replace("\\$uri", "$uri")            # perbaiki escape yang salah
block = """    location = /admin {
        return 301 /admin/;
    }

    location /admin/ {
        alias /var/www/presensi-desa/admin/;
        index index.html;
        try_files $uri $uri/ /admin/index.html;
    }

"""
marker = "    location / {\n        proxy_pass http://127.0.0.1:8181;"
if "location /admin/" not in s:
    assert marker in s, "penanda proxy_pass tidak ditemukan"
    s = s.replace(marker, block + marker, 1)
    open(conf, "w").write(s)
    print("vhost: blok /admin/ ditambahkan")
else:
    open(conf, "w").write(s)
    print("vhost: blok /admin/ sudah ada")
PYEOF
sudo -n nginx -t 2>&1 | tail -1
sudo -n systemctl reload nginx
echo "nginx: reload ok"

echo "== 4. PIN superadmin =="
PIN=$(python3 -c 'import random; print(random.randint(100000, 999999))')
cat > /tmp/hashpin.mjs <<'JSEOF'
import { hashPin } from "/opt/presensi-desa/dist/crypto.js";
console.log(hashPin(process.argv[2]));
JSEOF
set -a; . /etc/presensi-desa.env; set +a
HASH=$(node /tmp/hashpin.mjs "$PIN")
sudo -n -u postgres psql -d presensi -tAc \
  "update users set pin_hash = '$HASH' where nipd = 'SUPERADMIN' returning nipd" | tr -d ' '
sudo -n -u postgres psql -d presensi -tAc \
  "delete from activation_codes where used_at is null and user_id = (select id from users where nipd = 'SUPERADMIN')" >/dev/null

echo "== 5. uji panel & API =="
code=$(curl -s -o /dev/null -w '%{http_code}' "https://$DOMAIN/admin/")
echo "GET /admin/ -> $code"
curl -s "https://$DOMAIN/admin/" | grep -o '<title>[^<]*</title>' | head -1
for f in assets/css/app.css assets/js/app.js assets/js/api.js assets/js/pages/operasional.js; do
  echo "  $f -> $(curl -s -o /dev/null -w '%{http_code}' "https://$DOMAIN/admin/$f")"
done

BODY=$(printf '{"nipd":"SUPERADMIN","pin":"%s"}' "$PIN")
TOK=$(curl -s -m 10 -X POST "https://$DOMAIN/auth/web-login" -H 'content-type: application/json' -d "$BODY" \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("accessToken",""))')
if [ -z "$TOK" ]; then echo "LOGIN_GAGAL"; else echo "login web: ok"; fi
echo "  /auth/me        -> $(curl -s -o /dev/null -w '%{http_code}' -H "authorization: Bearer $TOK" "https://$DOMAIN/auth/me")"
echo "  /super/villages -> $(curl -s -o /dev/null -w '%{http_code}' -H "authorization: Bearer $TOK" "https://$DOMAIN/super/villages")"
echo "  /super/devices  -> $(curl -s -o /dev/null -w '%{http_code}' -H "authorization: Bearer $TOK" "https://$DOMAIN/super/devices")"
echo "  /super/audit    -> $(curl -s -o /dev/null -w '%{http_code}' -H "authorization: Bearer $TOK" "https://$DOMAIN/super/audit?limit=5")"
echo "  PIN salah       -> $(curl -s -o /dev/null -w '%{http_code}' -X POST "https://$DOMAIN/auth/web-login" -H 'content-type: application/json' -d "{\"nipd\":\"SUPERADMIN\",\"pin\":\"000000\"}")"
echo "  panel tanpa /   -> $(curl -s -o /dev/null -w '%{http_code}' "https://$DOMAIN/admin")"

echo "== 6. MutasiHub tetap hidup =="
echo "  mutasi https    -> $(curl -s -o /dev/null -w '%{http_code}' https://mutasi.rajacek.com/)"
systemctl is-active mutasi-hub-api mutasi-hub-web 2>/dev/null | tr '\n' ' '; echo

echo "PIN_SUPERADMIN=$PIN"
