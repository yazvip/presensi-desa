import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { pool, tx, audit, AppError } from "../db.js";
import { evaluateGeofence, haversine, type Sample } from "../geo.js";
import { workDate, zonedParts, evaluateCheckIn, evaluateCheckOut, type DaySchedule } from "../schedule.js";
import { computeAttendanceHash } from "../integrity.js";
import { randomCode } from "../crypto.js";

const QrSchema = z.object({
  qr: z.string().min(1),
  samples: z
    .array(z.object({ lat: z.number().min(-90).max(90), lng: z.number().min(-180).max(180), acc: z.number().min(0) }))
    .min(1)
    .max(10),
  deviceClock: z.string().datetime().optional(),
  source: z.enum(["online", "offline"]).default("online"),
});

interface OfficeRow {
  id: string;
  nama: string;
  lat: number;
  lng: number;
  radius_m: number;
  acc_max_m: number;
  qr_code: string;
  qr_secret: string;
  qr_version: number;
}

export function parseQrPayload(payload: string): { kode: string; secret: string } | null {
  const parts = payload.trim().split("|");
  if (parts.length !== 3 || parts[0] !== "VDS1") return null;
  const [, kode, secret] = parts as [string, string, string];
  if (!kode || !secret) return null;
  return { kode, secret };
}

export default async function attendanceRoutes(app: FastifyInstance): Promise<void> {
  /** Inti absensi — semua keputusan dibuat di sini, bukan di HP. */
  app.post("/attendance", { preHandler: [app.authenticate] }, async (req) => {
    if (req.deviceCanAttend === false) {
      throw new AppError("DEVICE_ATTEND_DISABLED", "Perangkat ini dinonaktifkan untuk absensi oleh Superadmin.", 403);
    }
    const claims = req.claims!;
    const body = QrSchema.parse(req.body);
    const villageId = claims.vid;
    if (!villageId) throw new AppError("NO_VILLAGE", "Akun ini tidak terikat ke desa.", 400);

    const parsed = parseQrPayload(body.qr);
    if (!parsed) {
      throw new AppError("QR_INVALID", "Kode QR tidak dikenali. Pastikan memindai QR kantor desa.", 400);
    }

    const { rows: officeRows } = await pool.query<OfficeRow>(
      `select id, nama, lat, lng, radius_m, acc_max_m, qr_code, qr_secret, qr_version
         from office_locations
        where village_id = $1 and qr_code = $2 and aktif`,
      [villageId, parsed.kode.toUpperCase()],
    );
    const office = officeRows[0];
    if (!office || office.qr_secret !== parsed.secret) {
      await pool.query(
        `insert into attendance_attempts (village_id, user_id, ts, result, reason, device_id)
         values ($1,$2, now(), 'rejected', 'QR_TIDAK_COCOK', $3)`,
        [villageId, claims.sub, claims.did],
      );
      throw new AppError("QR_TIDAK_COCOK", "QR tidak cocok dengan kantor desa Anda.", 400);
    }

    const { rows: vRows } = await pool.query<{ timezone: string }>(
      "select timezone from villages where id = $1",
      [villageId],
    );
    const tz = vRows[0]?.timezone ?? "Asia/Jakarta";

    const now = new Date();
    const today = workDate(now, tz);
    const parts = zonedParts(now, tz);

    const { rows: scheduleRows } = await pool.query<DaySchedule>(
      `select day_of_week, is_workday, jam_masuk, jam_pulang,
              toleransi_telat_menit, istirahat_menit
         from work_schedules where village_id = $1 and day_of_week = $2`,
      [villageId, parts.dow],
    );
    const schedule = scheduleRows[0];
    if (!schedule || !schedule.is_workday) {
      throw new AppError("HARI_LIBUR", "Hari ini bukan hari kerja menurut kalender desa Anda.", 400);
    }

    const { rows: holidayRows } = await pool.query(
      "select tanggal from holidays where village_id = $1 and tanggal = $2",
      [villageId, today],
    );
    if (holidayRows.length > 0) {
      throw new AppError("HARI_LIBUR", "Hari ini libur menurut kalender desa Anda.", 400);
    }

    const { rows: existing } = await pool.query<{ type: string }>(
      "select type from attendance where user_id = $1 and work_date = $2 and cancelled_at is null",
      [claims.sub, today],
    );
    const hasMasuk = existing.some((r) => r.type === "masuk");
    const hasPulang = existing.some((r) => r.type === "pulang");
    if (hasPulang) {
      throw new AppError("SUDAH_ABSEN", "Absen masuk dan pulang hari ini sudah lengkap.", 409);
    }
    const type: "masuk" | "pulang" = hasMasuk ? "pulang" : "masuk";

    const samples: Sample[] = body.samples;
    const geo = evaluateGeofence(samples, office);
    const clockSkew = body.deviceClock
      ? Math.round((now.getTime() - new Date(body.deviceClock).getTime()) / 1000)
      : null;

    if (!geo.ok) {
      await pool.query(
        `insert into attendance_attempts
           (village_id, user_id, office_id, ts, lat, lng, acc_m, distance_m, result, reason, device_id)
         values ($1,$2,$3, now(), $4,$5,$6,$7,'rejected',$8,$9)`,
        [
          villageId,
          claims.sub,
          office.id,
          samples[0]?.lat ?? null,
          samples[0]?.lng ?? null,
          geo.acc,
          geo.distanceM,
          geo.reason,
          claims.did,
        ],
      );
      const message =
        geo.reason === "OUT_OF_RANGE"
          ? `Anda ${Math.round(geo.distanceM)} m dari titik kantor (maks ${office.radius_m} m).`
          : geo.reason === "ACCURACY_TOO_LOW"
            ? `Akurasi GPS ${Math.round(geo.acc)} m melebihi batas ${office.acc_max_m} m. Coba di area terbuka.`
            : "Lokasi GPS tidak terbaca. Nyalakan GPS akurasi tinggi lalu coba lagi.";
      throw new AppError(geo.reason, message, 422, {
        distanceM: Math.round(geo.distanceM),
        radiusM: office.radius_m,
        accM: Math.round(geo.acc),
      });
    }

    return tx(async (c) => {
      const status =
        type === "masuk"
          ? evaluateCheckIn(now, tz, schedule).status
          : evaluateCheckOut(now, tz, schedule).status;

      const { rows: prevRows } = await c.query<{ hash_self: string }>(
        `select hash_self from attendance
          where user_id = $1 and cancelled_at is null
          order by ts_server desc limit 1`,
        [claims.sub],
      );
      const hashPrev = prevRows[0]?.hash_self ?? null;
      const id = randomCode(26).toLowerCase();

      const hashSelf = computeAttendanceHash({
        id,
        userId: claims.sub,
        workDate: today,
        tsServer: now.toISOString(),
        lat: samples[0]!.lat,
        lng: samples[0]!.lng,
        type,
        hashPrev,
      });

      try {
        const { rows } = await c.query(
          `insert into attendance
             (id, village_id, user_id, office_id, device_id, type, work_date, ts_server,
              lat, lng, acc_m, distance_m, method, status, qr_version, clock_skew_sec,
              source, hash_prev, hash_self)
           values ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,'qr',$13,$14,$15,$16,$17,$18)
           returning id, type, ts_server, status, distance_m, acc_m`,
          [
            id,
            villageId,
            claims.sub,
            office.id,
            claims.did,
            type,
            today,
            now,
            samples[0]!.lat,
            samples[0]!.lng,
            geo.acc,
            geo.distanceM,
            status,
            office.qr_version,
            clockSkew,
            body.source,
            hashPrev,
            hashSelf,
          ],
        );
        const row = rows[0]!;
        await c.query(
          `insert into attendance_attempts (village_id, user_id, office_id, ts, lat, lng, acc_m, distance_m, result, device_id)
           values ($1,$2,$3, now(), $4,$5,$6,$7,'accepted',$8)`,
          [villageId, claims.sub, office.id, samples[0]!.lat, samples[0]!.lng, geo.acc, geo.distanceM, claims.did],
        );
        await audit(c, {
          villageId,
          actorId: claims.sub,
          actorRole: claims.role,
          aksi: `attendance.${type}`,
          entitas: "attendance",
          entitasId: row.id,
          after: { status: row.status, distanceM: Math.round(row.distance_m) },
          ip: req.ip ?? null,
        });
        return {
          ok: true,
          attendance: {
            id: row.id,
            type: row.type,
            tsServer: row.ts_server,
            workDate: today,
            status: row.status,
            distanceM: Math.round(row.distance_m),
            accM: Math.round(row.acc_m),
            officeName: office.nama,
          },
        };
      } catch (err) {
        if ((err as { code?: string }).code === "23505") {
          throw new AppError("SUDAH_ABSEN", `Absen ${type} hari ini sudah tercatat.`, 409);
        }
        throw err;
      }
    });
  });

  /** Riwayat pribadi + ringkasan bulan berjalan. */
  app.get("/attendance/me", { preHandler: [app.authenticate] }, async (req) => {
    const claims = req.claims!;
    const q = z
      .object({ from: z.string().optional(), to: z.string().optional(), limit: z.coerce.number().max(200).default(60) })
      .parse(req.query);

    const { rows } = await pool.query<{
      id: string;
      type: string;
      work_date: string;
      ts_server: Date;
      status: string;
      method: string;
      distance_m: number | null;
      acc_m: number | null;
      office_name: string;
    }>(
      `select a.id, a.type, to_char(a.work_date,'YYYY-MM-DD') as work_date, a.ts_server, a.status, a.method,
              round(a.distance_m)::int as distance_m, round(a.acc_m)::int as acc_m,
              o.nama as office_name
         from attendance a join office_locations o on o.id = a.office_id
        where a.user_id = $1
          and ($2::date is null or a.work_date >= $2)
          and ($3::date is null or a.work_date <= $3)
          and a.cancelled_at is null
        order by a.ts_server desc limit $4`,
      [claims.sub, q.from ?? null, q.to ?? null, q.limit],
    );

    const { rows: summary } = await pool.query(
      `select status, count(*)::int as n
         from attendance
        where user_id = $1 and cancelled_at is null
          and work_date >= date_trunc('month', now())::date
        group by status`,
      [claims.sub],
    );
    return {
      items: rows.map((r) => ({
        id: r.id,
        type: r.type,
        workDate: r.work_date,
        tsServer: r.ts_server,
        status: r.status,
        method: r.method,
        distanceM: r.distance_m,
        accM: r.acc_m,
        officeName: r.office_name,
      })),
      summary: summary.map((s) => ({ status: s.status, count: s.n })),
    };
  });

  /** Pratinjau jarak sebelum kirim — dipakai layar konfirmasi. */
  app.post("/attendance/preview", { preHandler: [app.authenticate] }, async (req) => {
    if (req.deviceCanAttend === false) {
      throw new AppError("DEVICE_ATTEND_DISABLED", "Perangkat ini dinonaktifkan untuk absensi oleh Superadmin.", 403);
    }
    const claims = req.claims!;
    const body = QrSchema.pick({ qr: true, samples: true }).parse(req.body);
    const parsed = parseQrPayload(body.qr);
    if (!parsed) throw new AppError("QR_INVALID", "Kode QR tidak dikenali.", 400);

    const { rows } = await pool.query<OfficeRow>(
      `select id, nama, lat, lng, radius_m, acc_max_m, qr_code, qr_secret, qr_version
         from office_locations where village_id = $1 and qr_code = $2 and aktif`,
      [claims.vid, parsed.kode.toUpperCase()],
    );
    const office = rows[0];
    if (!office) throw new AppError("QR_TIDAK_COCOK", "QR tidak cocok dengan desa Anda.", 400);

    const geo = evaluateGeofence(body.samples, office);
    const best = body.samples.reduce((a, b) => (a.acc < b.acc ? a : b));
    return {
      officeName: office.nama,
      distanceM: Math.round(haversine(best.lat, best.lng, office.lat, office.lng)),
      radiusM: office.radius_m,
      accM: Math.round(best.acc),
      accMaxM: office.acc_max_m,
      inRange: geo.ok,
      reason: geo.ok ? null : geo.reason,
    };
  });
}
