import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { pool, tx, audit, AppError } from "../db.js";
import { randomDigits, PENDING_PIN } from "../crypto.js";
import { deviceLimitFor } from "../limits.js";

const VillageSchema = z.object({
  kodeDesa: z.string().min(3).max(20),
  nama: z.string().min(2),
  kecamatan: z.string().min(2),
  kabupaten: z.string().min(2),
  provinsi: z.string().min(2),
  timezone: z.string().default("Asia/Jakarta"),
});

const DEFAULT_DAYS = [
  { d: 1, work: true },
  { d: 2, work: true },
  { d: 3, work: true },
  { d: 4, work: true },
  { d: 5, work: true },
  { d: 6, work: false },
  { d: 7, work: false },
];

export default async function superadminRoutes(app: FastifyInstance): Promise<void> {
  const guard = [app.authenticate, app.requireRole("superadmin")];

  /** Buat desa + jadwal default Senin–Jumat 07.30–15.00 WIB. */
  app.post("/super/villages", { preHandler: guard }, async (req) => {
    const body = VillageSchema.parse(req.body);
    return tx(async (c) => {
      let village;
      try {
        ({ rows: [village] } = await c.query(
          `insert into villages (kode_desa, nama, kecamatan, kabupaten, provinsi, timezone)
           values ($1,$2,$3,$4,$5,$6)
           returning id, kode_desa, nama, timezone`,
          [body.kodeDesa.toUpperCase(), body.nama, body.kecamatan, body.kabupaten, body.provinsi, body.timezone],
        ));
      } catch (err) {
        if ((err as { code?: string }).code === "23505") {
          throw new AppError("KODE_DESA_DUPLICATE", "Kode desa sudah dipakai.", 409);
        }
        throw err;
      }

      for (const day of DEFAULT_DAYS) {
        await c.query(
          `insert into work_schedules (village_id, day_of_week, is_workday, jam_masuk, jam_pulang,
                                        toleransi_telat_menit, istirahat_menit)
           values ($1,$2,$3,'07:30','15:00',10,60)`,
          [village!.id, day.d, day.work],
        );
      }

      await audit(c, {
        villageId: village!.id,
        actorId: req.claims!.sub,
        actorRole: "superadmin",
        aksi: "village.create",
        entitas: "villages",
        entitasId: village!.id,
        after: { nama: body.nama, timezone: body.timezone },
        ip: req.ip ?? null,
      });
      return { village };
    });
  });

  app.get("/super/villages", { preHandler: guard }, async () => {
    const { rows } = await pool.query(
      `select v.id, v.kode_desa, v.nama, v.kecamatan, v.kabupaten, v.timezone, v.aktif,
              (select count(*)::int from users u where u.village_id = v.id and u.status='active') as users,
              (select count(*)::int from office_locations o where o.village_id = v.id and o.aktif) as locations,
              (select u.nama from users u where u.village_id = v.id and u.role='admin_desa' and u.status='active' limit 1) as pengelola
         from villages v order by v.nama`,
    );
    return { villages: rows };
  });

  /** Tunjuk pengelola desa — satu desa tepat satu pengelola aktif. */
  app.post("/super/villages/:id/pengelola", { preHandler: guard }, async (req) => {
    const { id } = z.object({ id: z.string().uuid() }).parse(req.params);
    const body = z
      .object({ nama: z.string().min(2), nipd: z.string().min(2) })
      .parse(req.body);

    return tx(async (c) => {
      const { rows: vRows } = await c.query<{ id: string }>(
        "select id from villages where id = $1",
        [id],
      );
      if (!vRows[0]) throw new AppError("NOT_FOUND", "Desa tidak ditemukan.", 404);

      const { rows: existing } = await c.query<{ id: string; nama: string }>(
        `select id, nama from users
          where village_id = $1 and role = 'admin_desa' and status = 'active'`,
        [id],
      );
      if (existing.length > 0) {
        throw new AppError(
          "PENGELOLA_EXISTS",
          `Desa ini sudah punya pengelola aktif (${existing[0]!.nama}). Nonaktifkan dulu sebelum menunjuk yang baru.`,
          409,
        );
      }

      const limit = deviceLimitFor("admin_desa");
      let user;
      try {
        ({ rows: [user] } = await c.query(
          `insert into users (village_id, nama, nipd, jabatan, role, pin_hash, device_limit, status)
           values ($1,$2,$3,'Pengelola Desa','admin_desa',$4,$5,'active')
           returning id, nama, nipd, role, device_limit`,
          [id, body.nama.trim(), body.nipd.trim(), PENDING_PIN, limit],
        ));
      } catch (err) {
        if ((err as { code?: string }).code === "23505") {
          throw new AppError(
            "NIPD_DUPLICATE",
            "NIPD sudah terdaftar, atau desa ini sudah punya pengelola aktif.",
            409,
          );
        }
        throw err;
      }

      const code = randomDigits(6);
      await c.query(
        `insert into activation_codes (user_id, code, expires_at, created_by)
         values ($1,$2, now() + interval '7 days', $3)`,
        [user!.id, code, req.claims!.sub],
      );
      await audit(c, {
        villageId: id,
        actorId: req.claims!.sub,
        actorRole: "superadmin",
        aksi: "pengelola.assign",
        entitas: "users",
        entitasId: user!.id,
        after: { nama: user!.nama, deviceLimit: limit },
        ip: req.ip ?? null,
      });
      return { pengelola: user, activationCode: code, expiresInDays: 7 };
    });
  });

  /** Lepas perangkat terakhir pengelola — hanya superadmin yang boleh. */
  app.post("/super/devices/:deviceId/revoke", { preHandler: guard }, async (req) => {
    const { deviceId } = z.object({ deviceId: z.string().uuid() }).parse(req.params);
    const body = z
      .object({ reason: z.enum(["hp_rusak", "hp_hilang", "ganti_hp", "pindah_tugas", "lainnya"]) })
      .parse(req.body);

    const { rows } = await pool.query(
      `update devices set status='revoked', revoked_at=now(), revoked_by=$2, revoke_reason=$3
        where id=$1 and status='active' returning id, user_id, village_id, label`,
      [deviceId, req.claims!.sub, body.reason],
    );
    const device = rows[0];
    if (!device) throw new AppError("NOT_FOUND", "Perangkat aktif tidak ditemukan.", 404);
    await pool.query(
      "update refresh_tokens set revoked_at = now() where device_id = $1 and revoked_at is null",
      [deviceId],
    );
    await audit(pool, {
      villageId: device.village_id,
      actorId: req.claims!.sub,
      actorRole: "superadmin",
      aksi: "device.revoke_super",
      entitas: "devices",
      entitasId: deviceId,
      after: { reason: body.reason },
      ip: req.ip ?? null,
    });
    return { ok: true };
  });

  /** Semua perangkat lintas desa — superadmin perlu ini untuk melepas HP terakhir pengelola. */
  app.get("/super/devices", { preHandler: guard }, async (req) => {
    const q = z
      .object({
        villageId: z.string().uuid().optional(),
        status: z.enum(["active", "revoked"]).optional(),
      })
      .parse(req.query);
    const { rows } = await pool.query(
      `select d.id, d.label, d.model, d.manufacturer, d.os_version, d.status,
              d.first_bound_at, d.last_seen_at, d.revoke_reason,
              d.whitelisted, d.whitelist_village_id, wv.nama as whitelist_village,
              d.can_manage, d.can_attend,
              u.nama, u.nipd, u.role, v.nama as village
         from devices d
         join users u on u.id = d.user_id
         left join villages v on v.id = d.village_id
         left join villages wv on wv.id = d.whitelist_village_id
        where ($1::uuid is null or d.village_id = $1)
          and ($2::text is null or d.status = $2)
        order by d.status, d.first_bound_at desc
        limit 500`,
      [q.villageId ?? null, q.status ?? null],
    );
    return { devices: rows };
  });


  /** Whitelist perangkat khusus Superadmin: bisa bertindak sebagai Pengelola dan/atau perangkat absensi. */
  app.post("/super/devices/:deviceId/whitelist", { preHandler: guard }, async (req) => {
    const { deviceId } = z.object({ deviceId: z.string().uuid() }).parse(req.params);
    const body = z
      .object({
        whitelisted: z.boolean(),
        villageId: z.string().uuid().nullable().optional(),
        canManage: z.boolean().default(false),
        canAttend: z.boolean().default(true),
      })
      .parse(req.body);

    const { rows } = await pool.query(
      `update devices
          set whitelisted = $2,
              whitelist_village_id = case when $2 then $3::uuid else null end,
              can_manage = case when $2 then $4 else false end,
              can_attend = case when $2 then $5 else true end
        where id = $1
        returning id, user_id, village_id, whitelisted, whitelist_village_id, can_manage, can_attend`,
      [deviceId, body.whitelisted, body.villageId ?? null, body.canManage, body.canAttend],
    );
    const device = rows[0];
    if (!device) throw new AppError("NOT_FOUND", "Perangkat tidak ditemukan.", 404);

    await audit(pool, {
      villageId: device.whitelist_village_id ?? device.village_id,
      actorId: req.claims!.sub,
      actorRole: "superadmin",
      aksi: "device.whitelist",
      entitas: "devices",
      entitasId: deviceId,
      after: {
        whitelisted: device.whitelisted,
        villageId: device.whitelist_village_id,
        canManage: device.can_manage,
        canAttend: device.can_attend,
      },
      ip: req.ip ?? null,
    });
    return { device };
  });

  app.get("/super/audit", { preHandler: guard }, async (req) => {
    const q = z.object({ limit: z.coerce.number().max(500).default(100) }).parse(req.query);
    const { rows } = await pool.query(
      `select a.id, a.ts, a.aksi, a.entitas, a.entitas_id, a.before, a.after, a.ip,
              u.nama as actor, a.actor_role, v.nama as village
         from audit_log a
         left join users u on u.id = a.actor_id
         left join villages v on v.id = a.village_id
        order by a.ts desc limit $1`,
      [q.limit],
    );
    return { logs: rows };
  });
}
