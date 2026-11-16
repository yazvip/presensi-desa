# Presensi Desa

Aplikasi absensi perangkat desa: satu QR statis ditempel di kantor desa, absensi hanya sah
bila HP petugas berada di dalam radius kantor. Sesi login 30 hari, dan setiap akun terikat
pada perangkatnya sendiri.

Terdiri dari dua bagian:

| Bagian | Isi |
| --- | --- |
| `backend/` | API Fastify + PostgreSQL — otorisasi, geofence, keputusan absensi, rekap |
| `web-admin/` | Panel pengelola & superadmin (AdminLTE 3, berkas statis) |
| `android/` | Aplikasi petugas (Kotlin + Jetpack Compose) |
| `deploy/` | Konfigurasi nginx, systemd, skrip deploy, smoke test |
| `docker-compose.yml` | Stack VPS: postgres 16 + api |
| `docs/PLAN-ABSENSI-DESA.md` | Rencana lengkap: alur, model data, design system, roadmap |
| `.github/workflows/build.yml` | CI: tes backend + build APK debug |

## Alamat produksi

| Layanan | Alamat |
| --- | --- |
| API | `https://absen.talangarah.web.id` (`/health` untuk cek) |
| Panel admin | `https://absen.talangarah.web.id/admin/` |
| APK | artifact CI `presensi-desa-debug` — alamat API sudah tertanam di build |

## Cara kerja

1. Superadmin membuat desa dan menunjuk **satu** pengelola aktif per desa.
2. Pengelola mendaftarkan staff, mengatur titik kantor (koordinat + radius, maksimal 50 m),
   dan jam kerja desa.
3. Pengelola menerbitkan **kode aktivasi** 6 digit untuk petugas. Petugas memasukkannya sekali
   di HP-nya; saat itu aplikasi membuat kunci EC P-256 di Android Keystore dan mendaftarkannya
   ke server. Setelah itu akun hanya bisa dipakai dari HP tersebut.
4. Petugas memindai QR di kantor. Server memeriksa jam, hari kerja, jarak, dan riwayat absen,
   lalu menyimpan catatan beserta jarak, akurasi GPS, dan hash integritas.

Semua keputusan absensi (diterima, terlambat, ditolak) diambil **server**. Aplikasi hanya
mengirim sampel GPS; angka jarak dari HP tidak dipercaya.

### Aturan bawaan

- Radius kantor **50 m** (dapat diubah 10–500 m per desa, default 50).
- Akurasi GPS maksimum **60 m** — sampel yang lebih buruk dibuang.
- Sampel dikirim 3 buah, jarak 1,5 detik, server memilih yang terakurat.
- Jam kerja **Senin–Jumat 07.30–15.00 WIB**, toleransi terlambat 10 menit, istirahat 60 menit.
- Batas perangkat: superadmin 5, pengelola desa 3, staff/kades/bendahara 1.
- Access token 15 menit, refresh token 30 hari dengan rotasi.
- Sesi panel web 8 jam, tanpa keterikatan perangkat (channel `web` di klaim JWT).

## Panel admin

Berkas statis di `web-admin/`, disajikan nginx dari `/var/www/presensi-desa/admin` pada
`/admin/`. Semua data diambil dari API yang sama; tidak ada backend terpisah.

- Masuk dengan NIPD + PIN. Hanya peran **superadmin** dan **pengelola desa**; delapan
  kegagalan berturut-turut menahan percobaan berikutnya selama 15 menit.
- Superadmin: desa, penunjukan pengelola (kode aktivasi 7 hari), semua perangkat lintas desa,
  audit log.
- Pengelola: petugas + kode aktivasi, perangkat terikat + cabut, titik kantor (koordinat,
  radius, cetak QR, ganti QR), jam kerja, rekap (ekspor CSV/cetak), izin & cuti, anomali,
  integritas rantai hash, ganti PIN.

Memasang ulang panel di VPS:

```bash
tar czf /tmp/presensi-backend.tar.gz --exclude=node_modules --exclude=.git backend
tar czf /tmp/presensi-webadmin.tar.gz web-admin
scp /tmp/presensi-*.tar.gz vps:/tmp/
ssh vps 'bash -s' < deploy/deploy-panel.sh
```

## Menjalankan backend

```bash
cp backend/.env.example backend/.env      # isi JWT_SECRET minimal 32 karakter
docker compose up -d                      # postgres + api di 127.0.0.1:8080
curl http://127.0.0.1:8080/health
```

Pengembangan lokal tanpa Docker:

```bash
cd backend
npm install
npm run db:migrate        # butuh DATABASE_URL yang bisa dihubungi
npm run dev
npm test                  # 43 tes unit: geo, jadwal, kripto, integritas
npx tsc --noEmit          # typecheck
```

Skema database ada di `backend/db/schema.sql` dan dijalankan otomatis oleh container
PostgreSQL saat volume masih kosong. Batas jumlah HP ditegakkan trigger
`check_device_limit()`, bukan validasi aplikasi.

## Build APK

Repo ini tidak menyimpan hasil build. APK dibuat oleh GitHub Actions:

1. Push ke branch `main`.
2. Buka tab **Actions** → **Build** → job **Android — APK debug**.
3. Unduh artifact `presensi-desa-debug`.

Alamat API bawaan adalah `https://absen.talangarah.web.id/`, jadi build tetap benar
tanpa menyetel apa pun. Secret `API_BASE_URL` hanya perlu diisi bila alamatnya berubah.

Build lokal (butuh JDK 17 + Android SDK 35):

```bash
cd android
gradle assembleDebug -PAPI_BASE_URL="http://192.168.1.10:8080/"
```

## Endpoint utama

- `POST /auth/activate`, `/auth/activate-device` — aktivasi HP + kunci perangkat
- `POST /auth/challenge`, `/auth/login`, `/auth/refresh`, `/auth/logout`
- `GET  /auth/devices` — daftar HP terikat
- `POST /attendance`, `POST /attendance/preview`, `GET /attendance/me`
- `GET/POST /admin/staff`, `/admin/activation-code`, `/admin/devices`
- `GET/POST /admin/locations`, `/admin/schedule`, `/admin/rekap`, `/admin/anomalies`
- `POST /admin/locations/:id/rotate-qr` — menggugurkan lembar QR lama
- `GET/POST /super/villages`, `/super/devices`, `/super/devices/:id/revoke`, `/super/audit`
- `GET /admin/integrity` — verifikasi rantai hash absensi
- `GET /admin/locations/:id/qr` — muatan QR siap cetak (rahasia hanya lewat sini)
- `POST /auth/web-login`, `GET /auth/me`, `POST /auth/web-change-pin` — sesi panel web

## Keamanan dan batasannya

Yang sudah ditangani: device binding dengan challenge-response (nonce ditandatangani kunci
Keystore, bukan pencocokan string), PIN di-hash scrypt, refresh token dirotasi dan disimpan
sebagai hash, rantai hash absensi, audit log, dan trigger database untuk batas HP.

Yang **belum** ditangani dan perlu diketahui:

- QR statis bisa difoto dan dikirim ke orang lain. Pertahanannya adalah radius 50 m, bukan
  kerahasiaan QR. Rotasi QR menggugurkan lembar lama, tetapi tidak mendeteksi foto.
- Belum ada deteksi *mock location* di sisi server maupun Play Integrity.
- Aplikasi memakai lokasi yang dilaporkan OS; pengguna dengan akses root dapat memalsukannya.
- Pemulihan bila HP hilang: pengelola mencabut device, petugas aktivasi ulang dengan kode baru.

## Status

Backend: 46/46 tes lulus, typecheck bersih, sudah berjalan di VPS (systemd + nginx + PostgreSQL
16 native, sertifikat Let's Encrypt aktif). Panel web: terpasang dan teruji di server. Android:
kode lengkap dan sedang diverifikasi kompilasinya; build resmi tetap lewat CI.
