# Rencana Produk & Teknis — Absensi Perangkat Desa

> Dokumen kerja. **Versi 1.2 — 15 November 2026.**
> Perubahan dari v1.0: QR **statis** (cetak, bukan kiosk berputar) → mode kiosk dihapus;
> radius **50 m**; **multi-desa** dengan hierarki superadmin → pengelola desa → perangkat;
> jam kerja default **Senin–Jumat 07.30–15.00 WIB**, dapat diatur pengelola desa.
>
> Perubahan dari v1.1: **device binding keras** — key perangkat dibaca saat login/aktivasi
> pertama; login dari HP lain **ditolak** sampai pengelola desa menghapus key device.
> Lihat §6B.
>
> Perubahan dari v1.2: **pengelola desa multi-HP** (maks 3, seperti superadmin maks 5);
> **satu desa tepat satu pengelola aktif**; peta alur pengguna end-to-end di **§2B**.

---

## 1. Ringkasan & prinsip produk

**Masalah:** rekap kehadiran perangkat desa masih manual (buku hadir / tanda tangan). Sulit dibuktikan, mudah dipalsukan, menyulitkan saat insentif dihitung.

**Prinsip:**

1. **Bukti kehadiran = orangnya hadir secara fisik di kantor.** Karena QR statis dan bisa difoto, satu-satunya penjaga objektif adalah **jarak ke titik kantor**. Jarak divalidasi server, bukan HP.
2. **Server yang memutuskan.** Jarak, waktu, dan validitas kode lokasi dihitung ulang di server.
3. **Jangan membuat petugas gagal absen karena alasan teknis.** GPS HP kelas menengah meleset 10–25 m; radius 50 m memberi ruang aman. Tetap ada jalur manual untuk kamera/izin/HP bermasalah.
4. **Satu platform, banyak desa.** Setiap desa punya data, jadwal, lokasi, dan pengelola sendiri. Tidak ada data yang bocor antar-desa.
5. **Satu akun terikat pada HP yang terdaftar sejak login pertama.** Key perangkat dibuat di Android Keystore saat aktivasi. Perangkat desa 1 HP, pengelola desa 3 HP. HP yang tidak terdaftar tidak bisa masuk, dan hanya pengelola/superadmin yang dapat menghapus ikatan itu.

**Cakupan produk:** satu aplikasi Android (peran perangkat + pengelola desa) + satu dashboard web untuk pengelola desa dan superadmin.

---

## 2. Hierarki & peran

```
Superadmin (platform) — multi HP (5)
   └── membuat Desa (tenant) + menunjuk tepat 1 Pengelola Desa
          ├── Pengelola Desa (admin_desa) — tepat 1 per desa, multi HP (3)
          │      ├── mendaftarkan Perangkat Desa (staff, 1 HP per orang)
          │      ├── mengatur koordinat kantor + radius + cetak QR
          │      ├── mengatur jam kerja, hari libur, kalender
          │      ├── hapus key device (reset HP) petugas
          │      └── menyetujui izin, mengoreksi absensi
          ├── Kepala Desa  (1 HP, lihat semua, setujui akhir, TTD rekap)
          └── Bendahara    (1 HP, rekap & ekspor untuk insentif, read-only)
```

| Peran | Kode | Cakupan | Hak utama |
|---|---|---|---|
| Superadmin | `superadmin` | Semua desa | Buat/nonaktifkan desa, tunjuk & ganti pengelola desa, dukungan teknis, lihat audit platform |
| Pengelola Desa | `admin_desa` | Satu desa | **Semua operasional desa**: daftarkan perangkat, atur koordinat/radius, cetak QR, jam kerja, libur, shift, approval, koreksi, rekap, ekspor, broadcast, **hapus key device (reset ikatan HP)** |
| Kepala Desa | `kepala_desa` | Satu desa | Lihat semua data, setujui izin tingkat akhir, tanda tangan rekap. **Tidak boleh mengubah data absensi.** |
| Bendahara | `bendahara` | Satu desa | Lihat & ekspor rekap untuk insentif (read-only) |
| Perangkat Desa | `staff` | Satu desa | Absen, riwayat pribadi, ajukan izin/cuti, profil |

**Aturan:**
- Superadmin membuat desa, lalu menghasilkan **kode aktivasi** untuk pengelola desa (tanpa email — lewat WhatsApp/SMS manual).
- Pengelola desa tidak bisa melihat atau menyentuh desa lain.
- **Satu desa tepat satu pengelola aktif.** Ditegakkan di database: unique partial index `(village_id) WHERE role='admin_desa' AND status='active'`.
- **Batas HP berbeda per peran** (§2B.1): staff/kades/bendahara **1**, pengelola **3**, superadmin **5**.
- Key perangkat dibaca dan diikat saat login pertama. Untuk peran batas 1, login dari HP lain **ditolak** — bukan sekadar mematikan sesi lama (§6B).
- Pengelola dapat mencabut HP tambahan miliknya sendiri, tetapi **tidak boleh mencabut yang terakhir** — itu wewenang superadmin.
- Data antar-desa terpisah di level baris (Row Level Security) — bukan sekadar filter di UI.

---

## 2B. Peta alur pengguna — superadmin sampai perangkat

### 2B.1 Siapa ada di sistem

| Peran | Jumlah | HP maksimal | Boleh reset key device siapa |
|---|---|---|---|
| Superadmin | 1–3 (platform) | **5** | Pengelola desa, semua perangkat, superadmin lain |
| **Pengelola Desa** | **tepat 1 per desa** | **3** | Staff, kepala desa, bendahara, dan **HP tambahan miliknya sendiri** (wajib sisa ≥ 1) |
| Kepala Desa | 1 per desa | 1 | — |
| Bendahara | 1 per desa | 1 | — |
| Perangkat Desa | banyak | **1** | — |

Batas HP disimpan di `users.device_limit`; superadmin dapat menaikkan per user tanpa membocorkan wewenang antar-desa.

### 2B.2 Alur onboarding — dari nol sampai absen pertama

```
FASE A — Platform (superadmin)
 A1  Superadmin login (HP-nya terikat, maks 5)
 A2  Buat desa: nama, kode desa, kecamatan, zona waktu (default WIB)
 A3  Tunjuk pengelola → sistem buat kode aktivasi 6 digit
 A4  Serahkan kode via WhatsApp/SMS (di luar sistem, tanpa email)
        │
        ▼
FASE B — Pengelola desa aktivasi
 B1  Pasang APK → NIPD + kode aktivasi → buat PIN
 B2  App buat key device di Keystore → terikat, sisa slot 2 HP
 B3  Isi profil desa: logo, kop surat, pejabat penanda tangan
 B4  Tandai titik kantor di peta (di depan pintu, akurasi dicek)
 B5  Set radius (default 50 m) → unduh & cetak PDF QR lokasi
 B6  Set jam kerja (default Sen–Jum 07.30–15.00) + kalender libur
 B7  Tambah perangkat desa + jabatan → kode aktivasi per orang
        │
        ▼
FASE C — Perangkat desa aktivasi
 C1  Pasang APK → NIPD + kode aktivasi → set PIN
 C2  App buat key device → terikat permanen ke HP ini (1 HP)
 C3  Setujui izin kamera + lokasi dengan penjelasan manusiawi
        │
        ▼
FASE D — Operasional harian
 D1  Datang ke kantor → buka app → PIN / sidik jari
 D2  Scan QR di dinding → 3 sampel GPS → kirim
 D3  Server validasi → sukses, atau tolak dengan alasan konkret
```

### 2B.3 Alur login & ikatan HP per peran

```
Login: NIPD + PIN
   │
   ├─ HP ini sudah terikat? ──ya──► tanda tangan challenge ──► MASUK
   │
   └─ HP baru
        ├─ staff / kades / bendahara (batas 1)
        │     └──► DITOLAK  "DEVICE_ALREADY_BOUND"
        │            └──► petugas hubungi pengelola → Hapus Key Device
        │
        └─ pengelola (3) / superadmin (5)
              ├─ slot masih ada ──► HP baru ditambahkan + notifikasi
              └─ slot penuh ──► DITOLAK "DEVICE_LIMIT_REACHED"
                     └──► cabut sendiri salah satu HP lama (sisa ≥ 1)
```

### 2B.4 Alur absensi (sequence ringkas)

```
Petugas        HP                        Server
   │            │                          │
   ├ scan QR ──►│                          │
   │            ├─ 3× sampel GPS           │
   │            ├─ POST /attendance ──────►│
   │            │  {qr, lat, lng, acc,     │
   │            │   device_id, signature}  │
   │            │                          ├─ verifikasi signature
   │            │                          ├─ kode lokasi valid?
   │            │                          ├─ akurasi ≤ 60 m?
   │            │                          ├─ jarak ≤ radius?
   │            │                          ├─ belum absen slot ini?
   │            │◄──── sukses / tolak ─────┤
   │◄─ tampil ──┤                          │
```

### 2B.5 Alur reset key device (HP rusak / hilang / ganti)

```
HP petugas hilang / rusak
  └─► lapor pengelola (lisan / WhatsApp)
        └─► Pengelola: Perangkat → [nama] → Hapus Key Device
              ├─ wajib pilih alasan
              ├─ audit log tercatat, sesi HP lama mati
              └─► petugas aktivasi ulang di HP baru

HP pengelola hilang (semua)
  └─► hubungi superadmin
        └─► superadmin Hapus Key Device pengelola
              └─► pengelola aktivasi ulang

Pengelola mau tambah slot HP ke-4
  └─► hanya superadmin yang bisa menaikkan device_limit
```

### 2B.6 Alur izin / cuti / dinas luar

```
Perangkat ajukan (form + lampiran)
  ├─ izin ≤ 1 hari ──► Pengelola setujui ──► selesai
  └─ cuti / dinas luar / > 1 hari
        └─► Pengelola setujui ──► Kepala Desa setujui ──► selesai

Ditolak di tahap mana pun → kembali ke pengaju + alasan wajib
```

### 2B.7 Alur pergantian pengelola desa

```
Superadmin: Ganti Pengelola
 1. Nonaktifkan pengelola lama → semua HP-nya dicabut
 2. Buat kode aktivasi pengelola baru
 3. Data desa tetap utuh; pergantian masuk audit log
 4. Pengelola lama tetap tercatat sebagai mantan pengelola (riwayat)

Batasan: selama transisi hanya boleh ada 1 pengelola AKTIF.
Pengelola lama otomatis read-only sampai dinonaktifkan penuh.
```

---

## 3. Alur absensi

```
Buka app → PIN / sidik jari (tanpa login ulang)
   → Home (jam besar, status hari ini, tombol "Absen")
   → Kamera terbuka + viewfinder
   → Pindai QR yang tercetak di dinding kantor
   → HP ambil 3 sampel GPS (interval 1,5 dtk, buang akurasi > 100 m)
   → Sheet konfirmasi: mini-map, "Anda 12 m dari Kantor Desa", akurasi, status
   → Kirim → server validasi kode lokasi + jarak + jam server
   → Animasi sukses + haptic + jam masuk + status (Tepat Waktu / Terlambat)
```

Server menentukan `type` (`masuk` / `pulang`) dari jadwal hari itu, bukan dari pilihan pengguna — mencegah absen pulang dua kali untuk mengakali durasi kerja.

### Cabang penting
| Situasi | Perilaku |
|---|---|
| Di luar radius | Tolak dengan angka konkret: "Anda 71 m dari titik kantor (maks 50 m)". Percobaan gagal tetap disimpan untuk audit. |
| Akurasi GPS buruk | Tombol "Coba lagi" + tips (keluar dari bangunan, nyalakan GPS akurasi tinggi). Bukan penolakan permanen. |
| Offline | Absen disimpan lokal, masuk antrean, ditandai `needs_review`. Jarak dihitung ulang server saat sinkron. |
| Kamera rusak / izin ditolak | **Absen Manual**: foto selfie + catatan → status `pending_approval` untuk pengelola desa. |
| QR rusak / tidak terbaca | Ketik **kode lokasi** (6 karakter, tertera di bawah QR) secara manual. Aman karena penjaga utama adalah jarak. |
| Dinas luar / tugas lapangan | Ajukan sebelumnya; saat disetujui, geofence tidak berlaku untuk rentang tanggal itu. |
| Sakit / izin mendadak | Form izin + lampiran, dicatat `izin`/`sakit`, bukan `alpa`. |
| Sudah absen masuk, lupa pulang | Pengelola desa bisa koreksi dengan alasan; atau auto-close setelah X jam (opsional, default mati). |

---

## 4. Geofence — spesifikasi 50 m

Rumus: **Haversine**, R = 6.371.000 m (bukan selisih lat/lng).

```kotlin
fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}
```

**Algoritma validasi (HP lalu diulang server):**

1. Ambil 3 sampel, interval 1,5 dtk, `PRIORITY_HIGH_ACCURACY`.
2. Buang sampel `accuracy > 100 m`.
3. Pilih sampel dengan `accuracy` terkecil → `(lat, lng, acc)`.
4. Tolak jika `acc > ACC_MAX` — **default 60 m** (bisa diatur per lokasi).
5. Hitung `d = haversine(...)` ke titik kantor aktif.
6. **Lolos jika `d <= radius`** — **default 50 m**.
7. Simpan `d`, `acc`, `lat`, `lng` mentah untuk audit dan kalibrasi.

**Kenapa 50 m jauh lebih baik daripada 20 m** (atau kenapa permintaan Anda tepat): pada 20 m, akurasi GNSS 15 m membuat orang yang *benar-benar* ada di kantor sering ditolak — terutama di bawah atap seng atau di dekat dinding beton. Pada 50 m, false-reject praktis hilang.

**Risiko yang berpindah:** pada 50 m, orang yang berdiri di jalan depan kantor atau di rumah tetangga (35–45 m) bisa lolos. Itu trade-off yang diterima secara sadar, dan tertutup oleh tiga hal:
- Jarak tetap 50 m dari **titik koordinat yang diatur pengelola desa**, bukan radius kabur.
- Setiap absenan menyimpan koordinat mentah → pola mencurigakan (banyak orang, koordinat identik persis, semua 48–49 m) terlihat di laporan anomali.
- Pengelola desa bisa memperketat radius kapan saja (mis. 30 m) tanpa update aplikasi.

**Yang harus dilakukan pengelola desa saat menandai titik:** turun ke lapangan, tanda titik di **depan pintu kantor** (bukan di peta satelit dari dalam ruangan), lalu uji dengan HP sendiri. UI admin menampilkan akurasi saat penandaan dan memperingatkan bila radius < 3× akurasi.

**Multi-lokasi:** desa bisa punya lebih dari satu titik (Kantor Desa, Balai Dusun), masing-masing radius sendiri. Absen diterima jika lolos di salah satu titik aktif.

---

## 5. QR statis — desain & keamanan

### Bentuk
- QR **dibuat oleh pengelola desa** di web admin: tombol **"Cetak QR Lokasi"** → PDF A5 siap cetak (300 DPI).
- Isi payload: `VDS1|<kodeLokasi 6 char>|<secret 16 char>` (±30 karakter, pindai cepat).
- Lembar cetak memuat: nama kantor, QR besar, **kode lokasi tercetak jelas**, tanggal cetak, dan instruksi singkat.
- Cetak, laminasi, tempel di dinding dekat pintu masuk. Satu lembar per titik lokasi.
- **Ganti kode lokasi** (rotate secret) tersedia kapan saja → QR lama otomatis tidak valid, cetak ulang. Dipakai bila lembar QR difoto orang luar atau hilang.

### Konsekuensi keamanan yang dinyatakan terbuka

Dengan QR statis, **siapa pun yang punya foto kode itu bisa mengirimkannya ke orang lain**. Jadi:

| Lapis | Menahan apa | Tidak menahan apa |
|---|---|---|
| **Jarak 50 m (penjaga utama)** | Orang yang tidak berada di sekitar kantor | Orang yang memang ada di sekitar kantor tapi tidak masuk kerja |
| **Mock-location detection** | GPS palsu di HP non-root | Rooted device dengan modul Xposed/LSPosed |
| **Play Integrity (`MEETS_DEVICE_INTEGRITY`)** | HP rooted, emulator, app dimodifikasi | Perangkat asli dengan GPS palsu tingkat OS pada sebagian ROM |
| **Binding 1 perangkat/akun** | Satu orang mengabsen banyak akun dari HP-nya | — |
| **Laporan anomali** | Mendeteksi pola, bukan mencegah | — |

**Dinyatakan jujur:** aplikasi ini mencegah **absen dari jauh**. Aplikasi ini **tidak** sepenuhnya mencegah "titip absen oleh orang yang kebetulan lewat depan kantor" atau pemalsuan GPS di perangkat yang sudah di-root. Itu keputusan produk yang sudah diambil (QR statis + radius 50 m), dan pelengkapnya adalah **lapisan sosial**: pengelola desa melihat laporan anomali dan berhak menolak/mengoreksi.

### Kontrol anti-curang yang tetap dipasang

1. **Mock location** — `Location.isMock` + deteksi app mock terpasang → absen ditolak, tercatat.
2. **Play Integrity API** — verdict per request; `MEETS_DEVICE_INTEGRITY` gagal → tolak (bisa dilonggarkan per desa jadi "tandai saja").
3. **Jam server otoritatif** — `ts_server` selalu dari server. Selisih jam HP > 5 menit → anomali tercatat.
4. **Impossible travel** — dua absenan lokasi berbeda dalam waktu mustahil → ditandai.
5. **Koordinat identik** — beberapa user dengan `lat/lng` persis sama pada menit sama → ditandai (indikasi satu HP).
6. **Device binding** — satu akun satu `install_id`; ganti perangkat lewat pengelola desa.
7. **Rate limit** — maksimum 10 percobaan absen per jam per akun.
8. **Kode lokasi dirotasi berkala** — pengingat otomatis tiap 6 bulan di dashboard.

### Integritas data (dipakai untuk insentif)
- **Hash chain**: `hash_self = SHA256(id|user|ts|lat|lng|type|hash_prev)`. Tombol "Verifikasi Integritas" menandai baris yang rantainya putus.
- Koreksi admin **tidak menimpa** data; selalu menulis entri baru + `before/after` + alasan + identitas admin.
- Audit log terpisah, tidak dapat diedit bahkan oleh pengelola desa.

---

## 6. Sesi 30 hari

| Komponen | Spesifikasi |
|---|---|
| Access token | JWT, umur 15 menit |
| Refresh token | Opaque, **30 hari**, rotating (single-use), di EncryptedSharedPreferences (Keystore-backed) |
| Binding | Refresh token terikat **key perangkat (Keystore)** + `device_id` + `user_id` (§6B) |
| Buka aplikasi | **PIN 6 digit atau biometrik** — bukan login ulang |
| Aktivasi awal | Pengelola desa membuat akun → kode aktivasi 6 digit → petugas set PIN |
| Habis 30 hari | Login ulang: NIPD + PIN + OTP WhatsApp (opsional per desa, default mati) |
| Revoke | Pengelola desa menghapus key device kapan saja; semua sesi perangkat itu mati seketika |
| Ganti HP | **Hanya** lewat pengelola desa (hapus key device → aktivasi ulang) |

Tanpa email sama sekali — perangkat desa umumnya tidak punya email dinas.

---

## 6B. Device binding — "satu akun, HP terdaftar"

**Kapan ikatan dibuat:** pada **login/aktivasi pertama** akun di HP petugas — bukan saat pengelola membuat akun di dashboard.

### 6B.1 Apa yang benar-benar bisa dibaca dari HP

| Sumber | Dipakai? | Catatan jujur |
|---|---|---|
| **Android Keystore keypair EC P-256** (StrongBox bila tersedia) | ✅ **inti** | Private key tidak dapat diekspor. *Inilah* "key device" yang sebenarnya. |
| `Settings.Secure.ANDROID_ID` (di-hash) | ✅ sekunder | Stabil per app + user profile; berubah setelah factory reset |
| `Build.MANUFACTURER / MODEL / DEVICE`, versi OS, versi app | ✅ metadata | Ditampilkan ke pengelola agar jelas HP mana yang terikat |
| IMEI | ❌ | Tidak dapat diakses sejak Android 10 tanpa hak istimewa operator |
| `Build.getSerial()` | ❌ | Mengembalikan `unknown` untuk aplikasi biasa |

Jangan menjanjikan yang tidak bisa dilakukan Android: **IMEI tidak bisa dibaca**, jadi key perangkat dibentuk dari Keystore + ANDROID_ID, bukan dari nomor hardware.

### 6B.2 Alur

1. Pengelola desa membuat akun → server menghasilkan **kode aktivasi 6 digit** (berlaku 7 hari).
2. Petugas buka app di HP-nya → masukkan NIPD + kode aktivasi + set PIN.
3. App **membuat keypair EC P-256 di Android Keystore** (StrongBox bila tersedia).
4. App mengirim: public key, `hash(ANDROID_ID)`, metadata perangkat, verdict Play Integrity.
5. Server menyimpan baris `devices` (status `active`) dengan `slot` 1..N; limit dibaca dari `users.device_limit` dan ditegakkan trigger database.
6. Login berikutnya: server mengirim `nonce` → app **menandatangani** nonce dengan private key → server memverifikasi dengan public key tersimpan.

### 6B.3 Saat login dari HP lain

Perilaku bergantung pada batas HP peran (`users.device_limit`):

**Peran batas 1 — staff, kepala desa, bendahara**

Server mengenali `device_id` berbeda → tolak dengan kode `DEVICE_ALREADY_BOUND`:

> **"Akun ini sudah terikat pada HP lain.** Hubungi Pengelola Desa untuk menghapus key device sebelum bisa masuk dari HP ini."

PIN yang benar **tetap tidak** membuka akses. Tidak ada jalur self-service.

**Peran multi-HP — pengelola (3), superadmin (5)**

| Kondisi | Perilaku |
|---|---|
| Slot masih tersedia | HP baru ditambahkan sebagai device aktif, sesi HP lama **tetap hidup**, pengelola menerima notifikasi "Perangkat baru terhubung" |
| Slot penuh | Tolak `DEVICE_LIMIT_REACHED` + tampilkan daftar HP aktif beserta nama & terakhir aktif |
| Mencabut HP sendiri | Diizinkan selama **sisa ≥ 1** device aktif; mencabut yang terakhir → arahkan ke superadmin |
| Menaikkan slot | Hanya superadmin yang dapat mengubah `device_limit` |

Setiap device diberi `label` (mis. "HP utama", "Tablet kantor") agar pengelola bisa membedakan saat mencabut.

### 6B.4 Saat pengelola menghapus key device

- Menu `Perangkat → [nama petugas] → Hapus Key Device`.
- Wajib pilih **alasan**: HP rusak / HP hilang / ganti HP / pindah tugas / lainnya.
- Efek: `devices.status = 'revoked'`, semua refresh token perangkat itu **mati seketika**, tercatat di audit log.
- Petugas aktivasi ulang di HP baru: minta kode aktivasi baru, atau NIPD + PIN + OTP WhatsApp (bila diaktifkan desa).

### 6B.5 Siapa boleh mereset siapa

| Target | Boleh direset oleh |
|---|---|
| Perangkat desa, kepala desa, bendahara | Pengelola desa |
| **HP tambahan milik pengelola** | **Pengelola sendiri**, syarat sisa ≥ 1 device aktif |
| **Device terakhir pengelola** atau menaikkan slot | **Superadmin** — mencegah pengelola mengunci dirinya sendiri |
| Superadmin | Dukungan platform (prosedur manual + audit) |
| Penggantian pengelola desa (orangnya) | **Superadmin** (§2B.7) |

### 6B.6 Detail teknis yang menentukan berhasil-tidaknya

- **Jangan** set `setUserAuthenticationRequired(true)` pada key device. Kalau diset, mengganti PIN layar kunci HP akan **menghapus** key dan petugas terkunci tanpa sebab. Key ini untuk identitas perangkat, bukan gerbang biometrik.
- Key **tidak ikut** Google Backup (memang tidak bisa diekspor). Restore ke HP baru = perangkat baru = perlu reset pengelola.
- Factory reset / ganti ROM = key hilang + ANDROID_ID berubah. Sama: perlu reset pengelola.
- Emulator: Play Integrity gagal → kebijakan default tolak.
- Setiap absenan membawa `device_id`; server menolak absenan dari perangkat selain yang terikat, walau token sesi masih hidup.

### 6B.7 Trade-off yang harus disadari

Kebijakan ini memindahkan beban ke pengelola: setiap HP rusak / hilang / ganti berarti satu tiket reset. Tanpa antisipasi, petugas bisa gagal absen berhari-hari. Pengelola yang multi-HP lebih aman karena kehilangan satu HP tidak mengunci akunnya (selama sisa ≥ 1 device aktif).

| Mitigasi | Status |
|---|---|
| Tombol Hapus Key Device ≤ 3 ketukan dari dashboard | Wajib ada |
| SOP tertulis: "HP rusak → hubungi pengelola → reset → aktivasi ulang" | Wajib ada |
| Pengingat dashboard: daftar perangkat yang lama tidak aktif (indikasi HP hilang) | Disarankan |
| Reset mandiri 1× per 6 bulan lewat OTP WhatsApp | **Opsional, default mati** |

---

## 7. Jam kerja default & konfigurasi

**Default setiap desa baru:**

| Hari | Jam kerja | Keterangan |
|---|---|---|
| Senin | 07.30 – 15.00 | Hari kerja |
| Selasa | 07.30 – 15.00 | Hari kerja |
| Rabu | 07.30 – 15.00 | Hari kerja |
| Kamis | 07.30 – 15.00 | Hari kerja |
| Jumat | 07.30 – 15.00 | Hari kerja |
| Sabtu | — | Libur (bisa diaktifkan) |
| Minggu | — | Libur |
| Zona waktu | **Asia/Jakarta (WIB)** | Bisa diubah ke WITA/WIT per desa |

**Yang dapat diatur pengelola desa per hari:**
- Hari kerja / libur
- Jam masuk & jam pulang
- **Toleransi keterlambatan** (default **10 menit** — masuk sampai 07.40 tidak dihitung telat)
- Batas absen pulang (default 15.00)
- Shift ganda untuk hari tertentu (mis. Jumat 07.30–11.00), atau jadwal berbeda per jabatan (opsional)
- Kalender libur nasional, cuti bersama, libur desa
- Hari apel / upacara (jam masuk lebih awal, mis. Senin 07.00)

**Perhitungan status:**

| Status | Aturan (default) |
|---|---|
| `tepat_waktu` | `ts_masuk <= jam_masuk + toleransi` |
| `terlambat` | `ts_masuk > jam_masuk + toleransi` |
| `pulang_cepat` | `ts_pulang < jam_pulang − 15 menit` |
| `izin` / `sakit` / `cuti` / `dinas_luar` | Dari pengajuan yang disetujui |
| `alpa` | Hari kerja, tidak ada absenan, tidak ada izin |
| Jam kerja efektif | `ts_pulang − ts_masuk`, dikurangi istirahat (default 60 menit, bisa diatur) |

Zona waktu: semua waktu disimpan **UTC** di database, ditampilkan dalam zona desa. Ini mencegah bug paling umum di aplikasi absensi Indonesia.

---

## 8. Fitur — Aplikasi Perangkat Desa

1. **Onboarding & aktivasi** — kode aktivasi dari pengelola desa, set PIN, **pembuatan key perangkat di Keystore** (layar "HP ini akan dikunci sebagai perangkat resmi Anda"), izin kamera & lokasi dengan penjelasan manusiawi.
2. **Login** — PIN + biometrik, indikator "sesi aktif 30 hari".
3. **Home** — hero jam besar (tabular numerals), sapaan, status hari ini (belum absen / masuk 07.42 / terlambat 12 mnt), ring progres kehadiran bulan, tombol **Absen**, pengumuman desa.
4. **Scan** — kamera penuh, viewfinder custom, senter, haptic, indikator online/offline, input manual kode lokasi.
5. **Konfirmasi** — mini-map, **jarak konkret dalam meter**, akurasi, status.
6. **Hasil** — animasi centang + haptic, jam, status, streak kehadiran.
7. **Riwayat** — timeline + kalender bulanan, filter, detail (peta, jam masuk/pulang, durasi kerja).
8. **Izin / Sakit / Cuti / Dinas Luar** — form + lampiran + pelacakan status.
9. **Banding** — ajukan keberatan saat absen ditolak.
10. **Kalender kerja** — libur nasional, cuti bersama, jadwal apel.
11. **Notifikasi** — pengingat masuk/pulang, hasil approval, pengumuman.
12. **Profil** — foto, jabatan, NIPD, **kartu "Perangkat Terikat"** (model HP, tanggal ikat, status), ganti PIN, logout. Tidak ada tombol "ganti perangkat" — diarahkan ke pengelola.
13. **Mode offline** — banner + antrean sinkron transparan.

---

## 9. Fitur — Pengelola Desa (Android)

1. Dashboard hari ini (hadir / belum / terlambat / izin).
2. **Kelola perangkat desa**: tambah, edit jabatan, aktif/nonaktif, kode aktivasi, dan **Hapus Key Device** (reset ikatan HP, wajib alasan).
3. **Kelola lokasi & radius**: pilih titik di peta, slider radius (default 50 m), preview akurasi.
4. **Cetak / ganti QR lokasi**: bagikan PDF, rotate secret.
5. Antrean approval izin, cuti, dinas luar, banding.
6. Koreksi absensi (wajib alasan).
7. Broadcast pengumuman.
8. Laporan anomali ringkas.

---

## 10. Fitur — Dashboard Web (Pengelola Desa)

1. **Analitik** — tren kehadiran, persentase, distribusi status, jam masuk rata-rata, "paling sering terlambat".
2. **Rekap harian / bulanan** — filter rentang, perangkat, jabatan, status; total jam kerja.
3. **Ekspor** — Excel + PDF berkop desa, kolom tanda tangan Kepala Desa, QR verifikasi keaslian dokumen.
4. **Jam kerja & shift** — editor per hari (default Senin–Jumat 07.30–15.00), toleransi, istirahat.
5. **Kalender libur** — nasional, cuti bersama, libur desa, jadwal apel.
6. **Kelola pengguna & key device** — tambah perangkat, atur peran, kode aktivasi, lihat HP terikat (model, OS, tanggal ikat, terakhir aktif), **Hapus Key Device** dengan alasan wajib.
7. **Lokasi & QR** — peta titik kantor, radius, cetak PDF QR, riwayat rotasi kode.
8. **Audit log** — siapa mengubah apa, kapan, dari IP mana.
9. **Verifikasi hash chain**.
10. **Peta kehadiran** — sebaran titik absen, heatmap, deteksi titik di luar radius.
11. **Laporan anomali** — mock location, GPS tidak wajar, koordinat identik, selisih jam HP, percobaan gagal berulang.
12. **Pengaturan desa** — nama, kecamatan, kabupaten, logo, kop surat, pejabat penanda tangan, zona waktu.
13. **Manajemen sesi & perangkat** — daftar HP terikat, sesi aktif, riwayat reset key device, revoke sesi.

## 10b. Fitur — Panel Superadmin

1. Buat / nonaktifkan desa, kode desa, kuota user.
2. Tunjuk / ganti pengelola desa (kode aktivasi).
3. Statistik platform: desa aktif, jumlah perangkat, absenan per hari.
4. Dukungan: **impersonate** dengan audit trail — default read-only, mode tulis hanya aktif per sesi dan berbatas waktu.
5. **Ganti pengelola desa** (§2B.7): nonaktifkan lama, terbitkan kode aktivasi baru, cabut semua HP lama.
6. Naikkan/turunkan `device_limit` per user.
7. Kelola template QR, versi aplikasi minimum.
8. Audit platform lintas desa.

---

## 11. Design system — premium, berwibawa, terbaca

**Arah:** premium *stationery pemerintahan* — bukan startup gelap. Referensi rasa: kop surat resmi, tinta emerald, aksen kuningan. Berkelas tanpa mengorbankan keterbacaan pengguna 45+ di bawah lampu neon kantor.

### Palet
| Token | Light "Ivory Ledger" | Dark "Midnight Emerald" |
|---|---|---|
| `background` | `#FAF8F3` | `#0A0F0D` |
| `surface` | `#FFFFFF` | `#121A17` |
| `surfaceElevated` | `#FFFDF8` | `#17211D` |
| `primary` | `#0F3D2E` | `#2E8B6F` |
| `onPrimary` | `#FFFDF8` | `#04120D` |
| `accent` (emas) | `#C9A227` | `#E0BE5A` |
| `success` | `#1F8A5B` | `#3FB183` |
| `warning` | `#B5761F` | `#E0A24A` |
| `danger` | `#A93F3F` | `#E07070` |
| `outline` | `#0F3D2E` @ 8% | `#FFFFFF` @ 10% |
| `textPrimary` | `#101915` | `#F2F5F3` |
| `textSecondary` | `#5A665F` | `#9FB0A7` |

Larangan: tanpa `#000` murni, tanpa `#FFF` murni, maksimum **dua** warna aksen.

### Tipografi
- **Plus Jakarta Sans** (Tokotype, Indonesia) untuk semua teks.
- Skala: Display 40/44 · H1 28/34 · H2 22/28 · Body 16/24 · Caption 13/18.
- **Angka jam wajib tabular numerals** (`fontFeatureSettings = "tnum"`) supaya tidak bergeser saat detik berjalan.
- Bobot 600 judul · 500 body · 400 caption.

### Bentuk, ruang, bayangan
- Grid 4 pt · padding layar 20 dp · radius kartu 20 dp, tombol 16 dp, sheet 28 dp.
- Bayangan berlapis lembut `0 8 24 rgba(15,61,46,.08)` + `0 1 2 rgba(15,61,46,.06)`.
- Garis rambut 1 px `rgba(15,61,46,.06)`.
- Efek kaca **disimulasikan** dengan gradient border — **jangan pakai blur**, berat di HP 3 GB.

### Gerak
| Elemen | Spesifikasi |
|---|---|
| Easing | `cubic-bezier(0.2, 0.8, 0.2, 1)`, 220–280 ms |
| Tombol absen | pulse ring 2,4 dtk, tekanan skala 0,96 |
| Sukses | centang digambar 400 ms + ring melebar + haptic `CONFIRM` |
| Masuk daftar | stagger 40 ms, translateY 12 dp + fade |
| Skeleton | shimmer 1,2 dtk |
| Hormati | `animator_duration_scale = 0` |

### Enam aturan yang membuat terasa premium
1. **Satu momen megah per layar** — di Home jam raksasa, di Scan viewfinder.
2. **Angka tidak pernah berkedip** — tabular + transisi halus.
3. **Peta selalu kecil dan jujur** — tampilkan "12 m dari Kantor Desa", bukan hanya centang hijau.
4. **Haptic di setiap keputusan** — ringan untuk tap, `CONFIRM` untuk sukses, `REJECT` untuk gagal.
5. **Ilustrasi garis, bukan clipart** — satu set 6 gambar untuk kosong/sukses/gagal/offline/izin/kalender.
6. **Status tidak pernah hanya warna** — selalu ikon + teks + warna.

### Aset
- App icon: perisai emerald dengan busur kuningan — abstrak, bisa diganti logo desa.
- Splash: monogram + shimmer halus.
- **Lembar QR cetak** mengikuti bahasa desain yang sama: kop desa, garis kuningan, tipografi rapi — supaya terlihat resmi di dinding kantor.

---

## 12. Arsitektur & stack

```
┌────────────────────┐   ┌────────────────────┐
│ Android App        │   │ Web Admin          │
│ Kotlin + Compose   │   │ Next.js            │
│ (staff + pengelola)│   │ (desa + superadmin)│
└─────────┬──────────┘   └────────┬───────────┘
          │ HTTPS/JSON             │
          └───────────┬────────────┘
                      ▼
          ┌──────────────────────────┐
          │ API (validasi otoritatif)│
          │ Supabase Edge / Node     │
          └────────────┬─────────────┘
                       ▼
          ┌──────────────────────────┐
          │ PostgreSQL + Storage     │
          │ RLS per desa + Auth      │
          └──────────────────────────┘
```

**Android:** Kotlin, Jetpack Compose, Material 3 (token custom), CameraX + ML Kit Barcode, FusedLocationProvider, Room, WorkManager, DataStore, EncryptedSharedPreferences, **Android Keystore / StrongBox (key device + challenge-response)**, Hilt, Retrofit/Ktor, Play Integrity, FCM. `minSdk 24`, `targetSdk 36`.

**Backend:** Supabase (Postgres + Auth + Storage + Edge Functions + RLS). Validasi kode lokasi, jarak, dan jam **wajib** di server.

**Web admin:** Next.js + TanStack Table + Recharts; generator PDF QR & rekap di server.

**CI/CD:** GitHub Actions — APK debug tiap push, APK release bertanda tangan saat tag, deploy web admin saat `main`.

---

## 13. Model data ringkas

| Tabel | Kolom kunci |
|---|---|
| `villages` (tenant) | id, kode_desa, nama, kecamatan, kabupaten, provinsi, **timezone**, logo, pejabat_ttd, aktif, **pengelola_user_id**. Unique partial index pada `users`: `(village_id) WHERE role='admin_desa' AND status='active'` → satu desa satu pengelola |
| `users` | id, village_id, nama, nipd, jabatan, role, foto, status, device_limit |
| `devices` | id, village_id, user_id, **slot**, **label**, **public_key_pem**, **android_id_hash**, keystore_strongbox, model, manufacturer, os_version, app_version, integrity_verdict, first_bound_at, last_seen_at, last_ip, status (active/revoked), revoked_at, revoked_by, revoke_reason. Batas aktif ditegakkan **trigger** `check_device_limit()` terhadap `users.device_limit` (staff 1 / pengelola 3 / superadmin 5) |
| `office_locations` | id, village_id, nama, lat, lng, **radius_m (default 50)**, acc_max_m (60), alamat, aktif, **qr_code (6 char)**, **qr_secret**, qr_printed_at, qr_version |
| `work_schedules` | id, village_id, **day_of_week**, is_workday, jam_masuk, jam_pulang, toleransi_telat_menit, istirahat_menit |
| `holidays` | id, village_id, tanggal, nama, jenis |
| `attendance` | id, village_id, user_id, office_id, type, ts_server, lat, lng, acc_m, distance_m, method (qr/manual/koreksi), status, device_id, integrity_verdict, source, hash_prev, hash_self |
| `leave_requests` | id, user_id, jenis, mulai, selesai, alasan, lampiran, approver_id, status, catatan |
| `corrections` | id, attendance_id, admin_id, before, after, alasan, created_at |
| `announcements` | id, village_id, judul, isi, publish_at, audience |
| `audit_log` | id, village_id, actor, aksi, entitas, before, after, ip, ts |
| `activation_codes` | id, user_id, code, expires_at, used_at, **bound_device_id** |
| `auth_challenges` | id, device_id, nonce, expires_at, used_at |

**RLS:** setiap tabel ber-`village_id` punya policy `village_id = current_user_village()`; `superadmin` bypass dengan audit.

---

## 14. Roadmap

Estimasi untuk **1 developer**; bukan janji tanggal.

| Fase | Isi | Estimasi |
|---|---|---|
| **0 — Fondasi** | Repo, CI Actions, design system Compose, navigasi, tema terang/gelap | 3–4 hari |
| **1 — MVP absensi** | **Device binding (Keystore + challenge-response, tolak HP lain, Hapus Key Device)**, login 30 hari, cetak QR lokasi + validasi, geofence 50 m, absen masuk/pulang, riwayat, pengelola daftar perangkat & atur titik, rekap harian | 3 minggu |
| **2 — Administrasi** | Izin/cuti/dinas, approval, koreksi, jam kerja & kalender, multi-lokasi, audit log, hash chain, offline queue, notifikasi | 2–3 minggu |
| **3 — Multi-desa & laporan** | Panel superadmin, RLS, analitik, ekspor PDF bertanda tangan + QR verifikasi, peta kehadiran, laporan anomali, Play Integrity | 2 minggu |
| **4 — Kilap** | Polishing UI, biometrik, onboarding interaktif, mode gelap, widget | 1–2 minggu |

**Layak dipakai di kantor desa nyata = akhir Fase 2** (±6 minggu). Mode kiosk dihapus, jadi Fase 1 lebih ringan daripada versi 1.0.

---

## 15. Risiko & mitigasi

| Risiko | Dampak | Mitigasi |
|---|---|---|
| **QR statis difoto & dibagikan** | Absen dari jauh bila GPS dipalsukan | Penjaga utama jarak 50 m + mock detection + Play Integrity + **device binding keras** + laporan anomali. **Risiko residual diterima secara sadar.** |
| **HP petugas rusak / hilang / ganti** | Tidak bisa absen sampai pengelola reset | Tombol Hapus Key Device ≤ 3 ketukan, SOP tertulis, pengingat perangkat lama tidak aktif |
| **Pengelola lupa mereset** | Petugas absen berhari-hari, data bolong | Notifikasi ke pengelola saat ada login ditolak `DEVICE_ALREADY_BOUND`, eskalasi ke superadmin |
| **Pengelola mengunci akun sendiri** | Desa kehilangan akses admin | Reset key device pengelola hanya oleh superadmin |
| Radius 50 m terlalu longgar | Orang di sekitar kantor tapi tidak masuk bisa lolos | Pengelola desa bisa memperketat; laporan koordinat identik; koreksi & approval manual |
| GPS tidak akurat | Salah tolak (kecil kemungkinan pada 50 m) | Best-of-3 sampel, ACC_MAX 60 m, jalur banding |
| Internet kantor mati | Absen terhenti | Antrean offline + sinkron otomatis |
| Petugas tidak terbiasa | Adopsi gagal | UI 2 ketukan, tombol besar, onboarding singkat, jalur manual |
| Data dipakai untuk insentif | Sengketa | Hash chain, audit log, koreksi berjejak, PDF bertanda tangan |
| Privasi lokasi (data pribadi) | Kepatuhan | Persetujuan eksplisit, retensi terbatas, akses berbasis peran. Rujuk UU 27/2022 (PDP) — **validasi dengan pendamping hukum desa** |
| Dasar hukum internal | Absensi dianggap tidak sah | Sarankan Peraturan Desa / SK Kepala Desa tentang kehadiran — **konsultasikan ke kecamatan** |
| Antar-desa bocor data | Fatal untuk platform | RLS di level database, bukan filter UI; uji penetrasi antar-tenant sebelum rilis |

---

## 16. Keputusan yang masih menunggu

| # | Pertanyaan | Usulan saya |
|---|---|---|
| 1 | Toleransi keterlambatan default? | **10 menit** (masuk ≤ 07.40 = tepat waktu) |
| 2 | Istirahat siang dihitung? | Default **60 menit** dikurangi saat menghitung jam kerja efektif |
| 3 | Radius 50 m berlaku semua desa, atau tiap desa bisa beda? | **Tiap desa**, default 50 m |
| 4 | Pengelola desa boleh melihat rekap desa lain? | **Tidak** |
| 5 | Backend: Supabase atau VPS yang sudah ada? | **Supabase** untuk mulai, VPS untuk skala |
| 6 | Format rekap yang diminta kecamatan — ada contoh Excel/PDF? | Mohon contohnya bila ada |
| 7 | Tanda tangan digital Kepala Desa di PDF, atau kolom basah? | **Kolom basah + QR verifikasi** dulu |
| 8 | Distribusi: sideload atau Play Store? | **Sideload** dulu (lebih cepat, tanpa review) |
| 9 | Face recognition? | **Tidak dulu** sampai ada kebutuhan & dasar hukumnya |
| 10 | Nama aplikasi & branding desa? | Usul: **"Presensi Desa"** |
| 11 | Reset mandiri (OTP WhatsApp) boleh untuk desa tertentu? | **Default mati**; aktifkan hanya bila pengelola sulit dihubungi |
| 12 | Berapa batas HP pengelola desa? | **3** (staff/kades/bendahara tetap 1) |
| 13 | Bila pengelola cuti panjang / sakit, siapa yang menyetujui? | Superadmin **impersonate dengan audit**, atau tunjuk pengelola sementara (butuh keputusan Anda) |
| 14 | Label HP diisi pengguna atau otomatis dari model? | Otomatis dari model, bisa diganti manual |

---

## 17. Definisi "selesai" untuk MVP

- [ ] Superadmin bisa membuat desa & menunjuk pengelola desa.
- [ ] Pengelola desa bisa mendaftarkan perangkat desa dan menghasilkan kode aktivasi.
- [ ] Pengelola desa bisa menandai titik kantor di peta, mengatur radius, dan mengunduh PDF QR.
- [ ] Perangkat bisa aktivasi, login, dan sesi bertahan 30 hari tanpa login ulang.
- [ ] Key perangkat dibuat di Keystore saat aktivasi pertama dan ditandatangani ulang (challenge-response) setiap login.
- [ ] **Login dengan NIPD + PIN benar dari HP kedua ditolak** dengan pesan `DEVICE_ALREADY_BOUND` (diuji, bukan diasumsikan).
- [ ] **Hapus Key Device oleh pengelola** langsung mematikan sesi di HP lama; petugas bisa aktivasi di HP baru.
- [ ] Pengelola bisa login di **3 HP** sekaligus; HP ke-4 ditolak `DEVICE_LIMIT_REACHED`.
- [ ] Pengelola **tidak bisa** mencabut HP terakhirnya sendiri — tombolnya nonaktif / diarahkan ke superadmin.
- [ ] Mencabut satu HP pengelola **tidak** mematikan sesi HP lain miliknya.
- [ ] Desa **tidak bisa** memiliki dua pengelola aktif (uji di level database, bukan UI).
- [ ] Pergantian pengelola tidak menghilangkan data desa dan tercatat di audit log.
- [ ] Absenan dari perangkat yang tidak terikat ditolak server walau token sesi masih valid.
- [ ] Mengganti PIN layar kunci HP **tidak** merusak key perangkat.
- [ ] Absen diterima server hanya jika: kode lokasi valid + jarak ≤ radius + akurasi ≤ batas + belum absen di slot itu.
- [ ] Absen gagal menampilkan alasan konkret ("Anda 71 m dari titik kantor"), bukan "terjadi kesalahan".
- [ ] Jam kerja default Senin–Jumat 07.30–15.00 dan bisa diubah pengelola desa.
- [ ] Rekap harian benar dan bisa diekspor.
- [ ] Semua perubahan data tercatat di audit log.
- [ ] Data desa A tidak bisa diakses akun desa B (diuji, bukan diasumsikan).
- [ ] Berjalan lancar di HP 3 GB RAM Android 9.
