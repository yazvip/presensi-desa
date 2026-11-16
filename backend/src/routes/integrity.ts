import type { FastifyInstance } from "fastify";
import { pool } from "../db.js";
import { computeAttendanceHash } from "../integrity.js";

export default async function integrityRoutes(app: FastifyInstance): Promise<void> {
  /** Verifikasi rantai hash per user — mendeteksi baris yang diubah lewat database langsung. */
  app.get("/admin/integrity", { preHandler: [app.authenticate, app.requireRole("admin_desa", "superadmin")] }, async (req) => {
    const claims = req.claims!;
    const { rows } = await pool.query(
      `select id, user_id, work_date, ts_server, lat, lng, type, hash_prev, hash_self
         from attendance
        where village_id = $1 and cancelled_at is null
        order by user_id, ts_server asc`,
      [claims.vid],
    );

    const lastHash = new Map<string, string | null>();
    const broken: string[] = [];

    for (const row of rows) {
      const expectedPrev = lastHash.get(row.user_id) ?? null;
      const expectedSelf = computeAttendanceHash({
        id: row.id,
        userId: row.user_id,
        workDate: row.work_date instanceof Date
          ? row.work_date.toISOString().slice(0, 10)
          : String(row.work_date).slice(0, 10),
        tsServer: new Date(row.ts_server).toISOString(),
        lat: Number(row.lat),
        lng: Number(row.lng),
        type: row.type,
        hashPrev: expectedPrev,
      });
      if (row.hash_self !== expectedSelf) broken.push(row.id);
      lastHash.set(row.user_id, row.hash_self);
    }

    return { checked: rows.length, broken, valid: broken.length === 0 };
  });
}
