-- Presensi Desa — skema PostgreSQL 14+
-- Semua waktu disimpan UTC (timestamptz). Zona desa hanya untuk tampilan.
create extension if not exists pgcrypto;

-- ============================ TENANT ============================
create table if not exists villages (
  id            uuid primary key default gen_random_uuid(),
  kode_desa     text not null unique,
  nama          text not null,
  kecamatan     text not null,
  kabupaten     text not null,
  provinsi      text not null,
  timezone      text not null default 'Asia/Jakarta',
  logo_url      text,
  pejabat_nama  text,
  pejabat_jabatan text not null default 'Kepala Desa',
  aktif         boolean not null default true,
  created_at    timestamptz not null default now()
);

-- ============================ USERS ============================
create table if not exists users (
  id            uuid primary key default gen_random_uuid(),
  village_id    uuid references villages(id) on delete cascade,
  nama          text not null,
  nipd          text not null,
  jabatan       text,
  role          text not null check (role in ('superadmin','admin_desa','kepala_desa','bendahara','staff')),
  pin_hash      text not null,
  device_limit  int not null default 1 check (device_limit between 1 and 10),
  foto_url      text,
  status        text not null default 'active' check (status in ('active','inactive')),
  created_at    timestamptz not null default now()
);

-- Satu NIPD unik per desa; superadmin (village_id null) tidak dibatasi
create unique index if not exists users_village_nipd
  on users (village_id, nipd) where village_id is not null;

-- SATU DESA TEPAT SATU PENGELOLA AKTIF (ditegakkan database, bukan UI)
create unique index if not exists one_pengelola_per_village
  on users (village_id)
  where role = 'admin_desa' and status = 'active' and village_id is not null;

-- ============================ DEVICES ============================
create table if not exists devices (
  id                uuid primary key default gen_random_uuid(),
  village_id        uuid references villages(id) on delete cascade,
  user_id           uuid not null references users(id) on delete cascade,
  slot              int not null default 1,
  label             text,
  public_key_pem    text not null,
  key_fingerprint   text not null,
  android_id_hash   text,
  keystore_strongbox boolean not null default false,
  model             text,
  manufacturer      text,
  os_version        text,
  app_version       text,
  integrity_verdict text,
  first_bound_at    timestamptz not null default now(),
  last_seen_at      timestamptz not null default now(),
  last_ip           inet,
  status            text not null default 'active' check (status in ('active','revoked')),
  revoked_at        timestamptz,
  revoked_by        uuid references users(id),
  revoke_reason     text
);

create index if not exists devices_user_active on devices (user_id) where status = 'active';
create unique index if not exists devices_key_fingerprint on devices (key_fingerprint) where status = 'active';

-- Batas HP per peran ditegakkan di database
create or replace function check_device_limit() returns trigger
language plpgsql as $$
declare
  lim int;
  n   int;
begin
  select device_limit into lim from users where id = new.user_id;
  if lim is null then
    raise exception 'USER_NOT_FOUND';
  end if;
  if new.status = 'active' then
    select count(*) into n from devices
      where user_id = new.user_id and status = 'active' and id <> new.id;
    if n >= lim then
      raise exception 'DEVICE_LIMIT_REACHED' using errcode = 'P0001';
    end if;
  end if;
  return new;
end $$;

drop trigger if exists trg_device_limit on devices;
create trigger trg_device_limit before insert or update on devices
  for each row execute function check_device_limit();

-- ============================ AKTIVASI ============================
create table if not exists activation_codes (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references users(id) on delete cascade,
  code        text not null,
  expires_at  timestamptz not null,
  used_at     timestamptz,
  bound_device_id uuid references devices(id),
  created_by  uuid references users(id),
  created_at  timestamptz not null default now()
);
create index if not exists activation_codes_user on activation_codes (user_id) where used_at is null;

create table if not exists auth_challenges (
  id         uuid primary key default gen_random_uuid(),
  nipd       text not null,
  nonce      text not null unique,
  expires_at timestamptz not null,
  used_at    timestamptz
);

create table if not exists refresh_tokens (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references users(id) on delete cascade,
  device_id  uuid not null references devices(id) on delete cascade,
  token_hash text not null unique,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  created_at timestamptz not null default now()
);

-- ============================ LOKASI & QR ============================
create table if not exists office_locations (
  id           uuid primary key default gen_random_uuid(),
  village_id   uuid not null references villages(id) on delete cascade,
  nama         text not null,
  lat          double precision not null,
  lng          double precision not null,
  radius_m     int not null default 50 check (radius_m between 10 and 500),
  acc_max_m    int not null default 60 check (acc_max_m between 10 and 500),
  alamat       text,
  qr_code      text not null unique,
  qr_secret    text not null,
  qr_version   int not null default 1,
  qr_printed_at timestamptz,
  aktif        boolean not null default true,
  created_at   timestamptz not null default now()
);
create index if not exists office_locations_village on office_locations (village_id) where aktif;

-- ============================ JADWAL ============================
create table if not exists work_schedules (
  id                   uuid primary key default gen_random_uuid(),
  village_id           uuid not null references villages(id) on delete cascade,
  day_of_week          int not null check (day_of_week between 1 and 7), -- 1=Senin
  is_workday           boolean not null default true,
  jam_masuk            time not null default '07:30',
  jam_pulang           time not null default '15:00',
  toleransi_telat_menit int not null default 10 check (toleransi_telat_menit between 0 and 180),
  istirahat_menit      int not null default 60 check (istirahat_menit between 0 and 240),
  unique (village_id, day_of_week)
);

create table if not exists holidays (
  id         uuid primary key default gen_random_uuid(),
  village_id uuid not null references villages(id) on delete cascade,
  tanggal    date not null,
  nama       text not null,
  jenis      text not null default 'nasional' check (jenis in ('nasional','cuti_bersama','desa')),
  unique (village_id, tanggal)
);

-- ============================ ABSENSI ============================
create table if not exists attendance (
  id             uuid primary key default gen_random_uuid(),
  village_id     uuid not null references villages(id) on delete cascade,
  user_id        uuid not null references users(id),
  office_id      uuid not null references office_locations(id),
  device_id      uuid not null references devices(id),
  type           text not null check (type in ('masuk','pulang')),
  work_date      date not null,
  ts_server      timestamptz not null default now(),
  lat            double precision not null,
  lng            double precision not null,
  acc_m          double precision not null,
  distance_m     double precision not null,
  method         text not null default 'qr' check (method in ('qr','manual','koreksi')),
  status         text not null check (status in ('tepat_waktu','terlambat','pulang_cepat','luar_radius_review','pending_approval')),
  qr_version     int not null,
  integrity_verdict text,
  clock_skew_sec int,
  source         text not null default 'online' check (source in ('online','offline')),
  hash_prev      text,
  hash_self      text not null,
  cancelled_at   timestamptz,
  unique (user_id, work_date, type)
);
create index if not exists attendance_village_date on attendance (village_id, work_date);
create index if not exists attendance_user_date on attendance (user_id, work_date desc);

create table if not exists attendance_attempts (
  id          bigserial primary key,
  village_id  uuid references villages(id) on delete cascade,
  user_id     uuid references users(id) on delete cascade,
  office_id   uuid references office_locations(id),
  ts          timestamptz not null default now(),
  lat         double precision,
  lng         double precision,
  acc_m       double precision,
  distance_m  double precision,
  result      text not null,
  reason      text,
  device_id   uuid references devices(id)
);
create index if not exists attempts_user_ts on attendance_attempts (user_id, ts desc);

-- ============================ IZIN ============================
create table if not exists leave_requests (
  id           uuid primary key default gen_random_uuid(),
  village_id   uuid not null references villages(id) on delete cascade,
  user_id      uuid not null references users(id) on delete cascade,
  jenis        text not null check (jenis in ('izin','sakit','cuti','dinas_luar')),
  mulai        date not null,
  selesai      date not null,
  alasan       text not null,
  lampiran_url text,
  approver_id  uuid references users(id),
  status       text not null default 'pending' check (status in ('pending','approved_pengelola','approved','rejected','cancelled')),
  catatan      text,
  created_at   timestamptz not null default now(),
  decided_at   timestamptz
);

-- ============================ AUDIT ============================
create table if not exists audit_log (
  id         bigserial primary key,
  village_id uuid references villages(id) on delete set null,
  actor_id   uuid references users(id) on delete set null,
  actor_role text,
  aksi       text not null,
  entitas    text not null,
  entitas_id text,
  before     jsonb,
  after      jsonb,
  ip         inet,
  ts         timestamptz not null default now()
);
create index if not exists audit_village_ts on audit_log (village_id, ts desc);

create table if not exists announcements (
  id         uuid primary key default gen_random_uuid(),
  village_id uuid not null references villages(id) on delete cascade,
  judul      text not null,
  isi        text not null,
  publish_at timestamptz not null default now(),
  audience   text not null default 'all'
);
