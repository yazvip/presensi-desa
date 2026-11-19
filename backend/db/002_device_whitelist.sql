-- Whitelist perangkat khusus oleh Superadmin:
-- Perangkat yang di-whitelist dapat bertindak sebagai Pengelola dan/atau perangkat absensi
-- untuk desa tertentu (termasuk perangkat akun Superadmin).
alter table devices add column if not exists whitelisted boolean not null default false;
alter table devices add column if not exists whitelist_village_id uuid references villages(id) on delete set null;
alter table devices add column if not exists can_manage boolean not null default false;
alter table devices add column if not exists can_attend boolean not null default true;
