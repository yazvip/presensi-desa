import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { pool, tx, audit, AppError } from "../db.js";
import { hashPin, randomDigits, randomCode, PENDING_PIN } from "../crypto.js";
import { deviceLimitFor } from "../limits.js";
import { haversine } from "../geo.js";

function requireManager(app: FastifyInstance) {
  return [app.authenticate, app.requireRole("admin_desa", "superadmin")];
}

const StaffSchema = z.object({
  nama: z.string().min(2),
  nipd: z.string().min(2),
  jabatan: z.string().max(60).optional(),
  role: z.enum(["staff", "kepala_desa", "bendahara"]).default("staff"),
});

const LocationSchema = z.object({
  nama: z.string().min(2),
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  radiusM: z.number().int().min(10).max(500).default(50),
  accMaxM: z.number().int().min(10).max(500).default(60),
  alamat: z.string().max(200).optional(),
});

const ScheduleSchema = z.object({
  days: z
    .array(
      z.object({
        dayOfWeek: z.number().int().min(1).max(7),
        isWorkday: z.boolean(),
        jamMasuk: z.string().regex(/^\d{2}:\d{2}$/),
        jamPulang: z.string().regex(/^\d{2}:\d{2}$/),
        toleransiTelatMenit: z.number().int().min(0).max(180).default(10),
        istirahatMenit: z.number().int().min(0).max(240).default(60),
      }),
    )
    .min(1)
    .max(7),
});

export default async function adminRoutes(app: FastifyInstance): Promise<void> {
  /* ------------------------------ PERANGKAT ------------------------------ */

  app.post("/admin/staff", { preHandler: requireManager(app) }, async (req) => {
    const claims = req.claims!;
    const body = StaffSchema.parse(req.body);
    if (!claims.vid) throw new AppError("NO_VILLAGE", "Akun tidak terikat desa.", 400);

    return tx(async (c) => {
      const limit = deviceLimitFor(body.role);
      let rows;
      try {
        ({ rows } = await c.query(
          `insert into users (village_id, nama, nipd, jabatan, role, pin_hash, device_limit, status)
           values ($1,$2,$3,$4,$5,$6,$7,'active')
           returning id, nama, nipd, jabatan, role, device_limit, status`,
          [
            claims.vid,
            body.nama.trim(),
            body.nipd.trim(),
            body.jabatan ?? null,
            body.role,
            PENDING_PIN,
            limit,
          ],
        ));
      } catch (err) {
        if ((err as { code?: string }).code === "23505") {
          throw new AppError("NIPD_DUPLICATE", "NIPD tersebut sudah terdaftar di desa ini.", 409);
        }
        throw err;
      }
      const user = rows[0]!;
      const code = randomDigits(6);
      await c.query(
        `insert into activation_codes (user_id, code, expires_at, created_by)
         values ($1,$2, now() + interval '7 days', $3)`,
        [user.id, code, claims.sub],
      );
      await audit(c, {
        villageId: claims.vid,
        actorId: claims.sub,
        actorRole: claims.role,
        aksi: "staff.create",
        entitas: "users",
        entitasId: user.id,
        after: { nama: user.nama, role: user.role },
        ip: req.ip ?? null,
      });
      return { user, activationCode: code, expiresInDays: 7 };
    });
  });

  app.get("/admin/staff", { preHandler: requireManager(app) }, async (req) => {
    const claims = req.claims!;
    const { rows } = await pool.query(
      `select u.id, u.nama, u.nipd, u.jabatan, u.role, u.status, u.device_limit,
              count(d.id) filter (where d.status = 'active')::int as devices_active
         from users u left join devices d on d.user_id = u.id
        where u.village_id = $1 and u.role <> 'admin_desa'
        group by u.id order by u.nama`,
      [claims.vid],
    );
    return { staff: rows };
  });

  /** Re-issue kode aktivasi (mis. kode lama hilang atau kedaluwarsa). */
  app.post("/admin/staff/:id/activation-code", { preHandler: requireManager(app) }, async (req) => {
    const claims = req.claims!;
    const { id } = z.object({ id: z.string().uuid() }).parse(req.params);
    const code = randomDigits(6);
    const { rowCount } = await pool.query(
      `insert into activation_codes (user_id, code, expires_at, created_by)
       select id, $2, now() + interval '7 days', $3 from users
        where id = $1 and village_id = $4`,
      [id, code, claims.sub, claims.vid],
    );
    if (rowCount === 0) throw new AppError("NOT_FOUND", "Perangkat tidak ditemukan di desa Anda.", 404);
    return { activationCode: code, expiresInDays: 7 };
  });

  /* ---------------------------- HAPUS KEY DEVICE --------------------------- */

  app.post("/admin/devices/:deviceId/revoke", { preHandler: requireManager(app) }, async (req) => {
    const claims = req.claims!;
    const { deviceId } = z.object({ deviceId: z.string().uuid() }).parse(req.params);
    const body = z
      .object({ reason: z.enum(["hp_rusak", "hp_hilang", "ganti_hp", "pindah_tugas", "lainnya"]) })
      .parse(req.body);

    return tx(async (c) => {
      const { rows } = await c.query(
        `select d.id, d.user_id, d.label, d.status, u.role, u.village_id
           from devices d join users u on u.id = d.user_id where d.id = $1`,
        [deviceId],
      );
      const device = rows[0];
      if (!device || device.village_id !== claims.vid) {
        throw new AppError("NOT_FOUND", "Perangkat tidak ditemukan di desa Anda.", 404);
      }
      if (device.status !== "active") {
        throw new AppError("ALREADY_REVOKED", "Perangkat ini sudah dilepas.", 409);
      }

      // Pengelola tidak boleh mencabut HP terakhirnya sendiri.
      if (device.user_id === claims.sub) {
        const { rows: countRows } = await c.query<{ n: number }>(
          "select count(*)::int as n from devices where user_id = $1 and status = 'active'",
          [claims.sub],
        );
        if ((countRows[0]?.n ?? 0) <= 1) {
          throw new AppError(
            "LAST_DEVICE",
            "Ini HP terakhir Anda. Hubungi superadmin untuk melepas perangkat terakhir.",
            409,
          );
        }
      }

      await c.query(
        `update devices set status = 'revoked', revoked_at = now(), revoked_by = $2, revoke_reason = $3
          where id = $1`,
        [deviceId, claims.sub, body.reason],
      );
      await c.query(
        "update refresh_tokens set revoked_at = now() where device_id = $1 and revoked_at is null",
        [deviceId],
      );
      await audit(c, {
        villageId: claims.vid,
        actorId: claims.sub,
        actorRole: claims.role,
        aksi: "device.revoke",
        entitas: "devices",
        entitasId: deviceId,
        before: { status: "active", label: device.label },
        after: { status: "revoked", reason: body.reason },
        ip: req.ip ?? null,
      });
      return { ok: true };
    });
  });

  app.get("/admin/devices", { preHandler: requireManager(app) }, async (req) => {
    const claims = req.claims!;
    const { rows } = await pool.query(
      `select d.id, d.label, d.model, d.manufacturer, d.os_version, d.status,
              d.first_bound_at, d.last_seen_at, d.revoke_reason,
              u.nama, u.nipd, u.role
         from devices d join users u on u.id = d.user_id
        where d.village_id = $1
        order by d.status, d.first_bound_at desc`,
      [claims.vid],
    );
    return { devices: rows };
  });

  /* ------------------------------- LOKASI -------------------------------- */

  app.post("/admin/locations", { preHandler: requireManager(app) }, async (req) => {
    const claims = req.claims!;
    const body = LocationSchema.parse(req.body);
    const qrCode = randomCode(6);
    const qrSecret = randomCode(16);

    const { rows } = await pool.query(
      `insert into office_locations (village_id, nama, lat, lng, radius_m, acc_max_m, alamat, qr_code, qr_secret)
       values ($1,$2,$3,$4,$5,$6,$7,$8,$9)
       returning id, nama, lat, lng, radius_m, acc_max_m, qr_code, qr_version, aktif`,
      [claims.vid, body.nama, body.lat, body.lng, body.radiusM, body.accMaxM, body.alamat ?? null, qrCode, qrSecret],
    );
    const office = rows[0]!;
    await audit(pool, {
      villageId: claims.vid,
      actorId: claims.sub,
      actorRole: claims.role,
      aksi: "location.create",
      entitas: "office_locations",
      entitasId: office.id,
      after: { nama: office.nama, radiusM: office.radius_m },
      ip: req.ip ?? null,
    });
    return { location: office, qrPayload: `VDS1|${qrCode}|${qrSecret}` };
  });

  app.get("/admin/locations", { preHandler: [app.authenticate] }, async (req) => {
    const claims = req.claims!;
    const { rows } = await pool.query(
      `select id, nama, lat, lng, radius_m, acc_max_m, alamat, qr_code, qr_version, qr_printed_at, aktif
         from office_locations where village_id = $1 order by nama`,
      [claims.vid],
    );
    return { locations: rows };
  });

  /** Ganti kode QR: lembar cetak lama otomatis tidak berlaku. */
  app.post("/admin/locations/:id/rotate-qr", { preHandler: requireManager(app) }, async (req) => {
    const claims = req.claims!;
    const { id } = z.object({ id: z.string().uuid() }).parse(req.params);
    const qrCode = randomCode(6);
    const qrSecret = randomCode(16);
    const { rows } = await pool.query(
      `update office_locations
          set qr_code = $2, qr_secret = $3, qr_version = qr_version + 1, qr_printed_at = null
        where id = $1 and village_id = $4
        returning id, qr_code, qr_version`,
      [id, qrCode, qrSecret, claims.vid],
    );
    const office = rows[0];
    if (!office) throw new AppError("NOT_FOUND", "Lokasi tidak ditemukan.", 404);
    await audit(pool, {
      villageId: claims.vid,
      actorId: claims.sub,
      actorRole: claims.role,
      aksi: "location.rotate_qr",
      entitas: "office_locations",
      entitasId: id,
      after: { qrVersion: office.qr_version },
      ip: req.ip ?? null,
    });
    return { location: office, qrPayload: `VDS1|${qrCode}|${qrSecret}` };
  });

  /* ------------------------------- JADWAL -------------------------------- */

  app.get("/admin/schedule", { preHandler: [app.authenticate] }, async (req) => {
    const claims = req.claims!;
    const { rows } = await pool.query(
      `select day_of_week, is_workday, to_char(jam_masuk,'HH24:MI') as jam_masuk,
              to_char(jam_pulang,'HH24:MI') as jam_pulang,
              toleransi_telat_menit, istirahat_menit
         from work_schedules where village_id = $1 order by day_of_week`,
      [claims.vid],
    );
    return { days: rows };
  });

  app.put("/admin/schedule", { preHandler: requireManager(app) }, async (req) => {
    const claims = req.claims!;
    const body = ScheduleSchema.parse(req.body);
    return tx(async (c) => {
      for (const d of body.days) {
        await c.query(
          `insert into work_schedules
             (village_id, day_of_week, is_workday, jam_masuk, jam_pulang, toleransi_telat_menit, istirahat_menit)
           values ($1,$2,$3,$4,$5,$6,$7)
           on conflict (village_id, day_of_week) do update
             set is_workday = excluded.is_workday,
                 jam_masuk = excluded.jam_masuk,
                 jam_pulang = excluded.jam_pulang,
                 toleransi_telat_menit = excluded.toleransi_telat_menit,
                 istirahat_menit = excluded.istirahat_menit`,
          [
            claims.vid,
            d.dayOfWeek,
            d.isWorkday,
            d.jamMasuk,
            d.jamPulang,
            d.toleransiTelatMenit,
            d.istirahatMenit,
          ],
        );
      }
      await audit(c, {
        villageId: claims.vid,
        actorId: claims.sub,
        actorRole: claims.role,
        aksi: "schedule.update",
        entitas: "work_schedules",
        after: { days: body.days.length },
        ip: req.ip ?? null,
      });
      return { ok: true, days: body.days.length };
    });
  });

  /* ------------------------------- REKAP --------------------------------- */

  app.get("/admin/rekap", { preHandler: requireManager(app) }, async (req) => {
    const claims = req.claims!;
    const q = z
      .object({ from: z.string(), to: z.string(), userId: z.string().uuid().optional() })
      .parse(req.query);

    const { rows } = await pool.query(
      `select a.work_date, u.nama, u.nipd, u.jabatan,
              max(a.ts_server) filter (where a.type='masuk') as masuk_at,
              max(a.ts_server) filter (where a.type='pulang') as pulang_at,
              max(a.status) filter (where a.type='masuk') as status_masuk,
              max(round(a.distance_m))::int as max_distance_m,
              max(a.method) as method
         from attendance a join users u on u.id = a.user_id
        where a.village_id = $1 and a.work_date between $2 and $3
          and a.cancelled_at is null
          and ($4::uuid is null or a.user_id = $4)
        group by a.work_date, u.id, u.nama, u.nipd, u.jabatan
        order by a.work_date desc, u.nama`,
      [claims.vid, q.from, q.to, q.userId ?? null],
    );
    return { rows };
  });

  /** Laporan anomali — dasar peninjauan pengelola. */
  app.get("/admin/anomalies", { preHandler: requireManager(app) }, async (req) => {
    const claims = req.claims!;
    const q = z.object({ days: z.coerce.number().min(1).max(90).default(30) }).parse(req.query);

    const { rows: rejected } = await pool.query(
      `select u.nama, a.ts, a.result, a.reason, round(a.distance_m)::int as distance_m
         from attendance_attempts a join users u on u.id = a.user_id
        where a.village_id = $1 and a.ts > now() - ($2 || ' days')::interval
          and a.result = 'rejected'
        order by a.ts desc limit 100`,
      [claims.vid, q.days],
    );

    const { rows: sameCoords } = await pool.query(
      `select work_date, round(lat::numeric, 5) as lat, round(lng::numeric, 5) as lng,
              count(distinct user_id)::int as n_user
         from attendance
        where village_id = $1 and ts_server > now() - ($2 || ' days')::interval
        group by work_date, round(lat::numeric,5), round(lng::numeric,5)
       having count(distinct user_id) > 1
        order by work_date desc limit 50`,
      [claims.vid, q.days],
    );

    const { rows: clockSkew } = await pool.query(
      `select u.nama, a.work_date, a.clock_skew_sec, a.type
         from attendance a join users u on u.id = a.user_id
        where a.village_id = $1 and abs(a.clock_skew_sec) > 300
          and a.ts_server > now() - ($2 || ' days')::interval
        order by abs(a.clock_skew_sec) desc limit 50`,
      [claims.vid, q.days],
    );

    return { rejected, sameCoords, clockSkew };
  });

  /* ------------------------------- IZIN ---------------------------------- */

  app.post("/leave", { preHandler: [app.authenticate] }, async (req) => {
    const claims = req.claims!;
    const body = z
      .object({
        jenis: z.enum(["izin", "sakit", "cuti", "dinas_luar"]),
        mulai: z.string(),
        selesai: z.string(),
        alasan: z.string().min(3),
      })
      .parse(req.body);
    const { rows } = await pool.query(
      `insert into leave_requests (village_id, user_id, jenis, mulai, selesai, alasan)
       values ($1,$2,$3,$4,$5,$6)
       returning id, jenis, mulai, selesai, status`,
      [claims.vid, claims.sub, body.jenis, body.mulai, body.selesai, body.alasan],
    );
    return { request: rows[0] };
  });

  app.get("/leave/me", { preHandler: [app.authenticate] }, async (req) => {
    const claims = req.claims!;
    const { rows } = await pool.query(
      `select id, jenis, mulai, selesai, alasan, status, catatan, created_at
         from leave_requests where user_id = $1 order by created_at desc limit 50`,
      [claims.sub],
    );
    return { requests: rows };
  });

  app.get("/admin/leave", { preHandler: requireManager(app) }, async (req) => {
    const claims = req.claims!;
    const { rows } = await pool.query(
      `select l.id, u.nama, u.jabatan, l.jenis, l.mulai, l.selesai, l.alasan, l.status, l.created_at
         from leave_requests l join users u on u.id = l.user_id
        where l.village_id = $1 and l.status in ('pending','approved_pengelola')
        order by l.created_at`,
      [claims.vid],
    );
    return { requests: rows };
  });

  app.post("/admin/leave/:id/decide", { preHandler: requireManager(app) }, async (req) => {
    const claims = req.claims!;
    const { id } = z.object({ id: z.string().uuid() }).parse(req.params);
    const body = z
      .object({ approve: z.boolean(), catatan: z.string().max(300).optional() })
      .parse(req.body);

    const { rows } = await pool.query(
      `update leave_requests
          set status = $2, approver_id = $3, catatan = $4, decided_at = now()
        where id = $1 and village_id = $5
        returning id, status, user_id`,
      [id, body.approve ? "approved" : "rejected", claims.sub, body.catatan ?? null, claims.vid],
    );
    const row = rows[0];
    if (!row) throw new AppError("NOT_FOUND", "Pengajuan tidak ditemukan.", 404);
    await audit(pool, {
      villageId: claims.vid,
      actorId: claims.sub,
      actorRole: claims.role,
      aksi: "leave.decide",
      entitas: "leave_requests",
      entitasId: id,
      after: { status: row.status },
      ip: req.ip ?? null,
    });
    return { request: row };
  });

  /* ------------------------------ VERIFIKASI ----------------------------- */

  /** Cek jarak dari titik kantor — dipakai pengelola saat menandai lokasi. */
  app.post("/admin/check-point", { preHandler: requireManager(app) }, async (req) => {
    const body = z
      .object({ lat: z.number(), lng: z.number(), officeId: z.string().uuid() })
      .parse(req.body);
    const { rows } = await pool.query<{ lat: number; lng: number; radius_m: number }>(
      `select lat, lng, radius_m from office_locations
        where id = $1 and village_id = $2`,
      [body.officeId, req.claims!.vid],
    );
    const office = rows[0];
    if (!office) throw new AppError("NOT_FOUND", "Lokasi tidak ditemukan.", 404);
    const d = haversine(body.lat, body.lng, office.lat, office.lng);
    return { distanceM: Math.round(d), inRange: d <= office.radius_m };
  });
}
