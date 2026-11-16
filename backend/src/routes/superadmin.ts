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
