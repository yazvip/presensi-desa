import type { FastifyInstance } from "fastify";
import { z } from "zod";
import type { Pool, PoolClient } from "pg";
import { pool, tx, audit, AppError } from "../db.js";
import {
  hashPin,
  verifyPin,
  signJwt,
  randomToken,
  sha256Hex,
  publicKeyFromBase64Der,
  publicKeyToPem,
  keyFingerprint,
  verifyDeviceSignature,
  PENDING_PIN,
} from "../crypto.js";
import { deviceLimitFor, autoLabel } from "../limits.js";
import { env } from "../env.js";

const DeviceSchema = z.object({
  publicKey: z.string().min(32, "publicKey (base64 DER) wajib dikirim"),
  label: z.string().max(60).optional(),
  androidIdHash: z.string().max(128).optional(),
  strongbox: z.boolean().optional(),
  model: z.string().max(80).optional(),
  manufacturer: z.string().max(80).optional(),
  osVersion: z.string().max(40).optional(),
  appVersion: z.string().max(40).optional(),
  integrityVerdict: z.string().max(60).optional(),
});

const ActivateSchema = z.object({
  nipd: z.string().min(1),
  code: z.string().min(4).max(12),
  pin: z.string().regex(/^\d{6}$/, "PIN harus 6 digit"),
  device: DeviceSchema,
});

const ActivateDeviceSchema = z.object({
  nipd: z.string().min(1),
  pin: z.string().regex(/^\d{6}$/),
  device: DeviceSchema,
});

const LoginSchema = z.object({
  nipd: z.string().min(1),
  deviceId: z.string().uuid(),
  signature: z.string().min(16),
  pin: z.string().regex(/^\d{6}$/).optional(),
});

interface UserRow {
  id: string;
  village_id: string | null;
  nama: string;
  nipd: string;
  jabatan: string | null;
  role: string;
  pin_hash: string;
  status: string;
  device_limit: number;
}

interface DeviceRow {
  id: string;
  user_id: string;
  village_id: string | null;
  status: string;
  label: string | null;
  public_key_pem: string;
}

type Queryable = Pool | PoolClient;

/** Simpan perangkat; batas slot ditegakkan trigger `check_device_limit()` di database. */
async function bindDevice(
  c: Queryable,
  user: { id: string; village_id: string | null; role: string },
  device: z.infer<typeof DeviceSchema>,
  ip: string | null,
): Promise<DeviceRow> {
  const pem = publicKeyToPem(publicKeyFromBase64Der(device.publicKey));
  const fp = keyFingerprint(device.publicKey);

  const existing = await c.query<DeviceRow>(
    `select id, user_id, village_id, status, label, public_key_pem
       from devices where key_fingerprint = $1 and status = 'active'`,
    [fp],
  );
  const owner = existing.rows[0];
  if (owner) {
    if (owner.user_id !== user.id) {
      throw new AppError("DEVICE_KEY_IN_USE", "Perangkat ini sudah terikat ke akun lain.", 409);
    }
    return owner;
  }

  try {
    const { rows } = await c.query<DeviceRow>(
      `insert into devices
         (village_id, user_id, public_key_pem, key_fingerprint, android_id_hash,
          keystore_strongbox, model, manufacturer, os_version, app_version,
          integrity_verdict, label, last_ip)
       values ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)
       returning id, user_id, village_id, status, label, public_key_pem`,
      [
        user.village_id,
        user.id,
        pem,
        fp,
        device.androidIdHash ?? null,
        device.strongbox ?? false,
        device.model ?? null,
        device.manufacturer ?? null,
        device.osVersion ?? null,
        device.appVersion ?? null,
        device.integrityVerdict ?? "unknown",
        device.label ?? autoLabel(device.manufacturer, device.model),
        ip,
      ],
    );
    return rows[0]!;
  } catch (err) {
    const code = (err as { code?: string }).code;
    const message = (err as Error).message ?? "";
    if (code === "P0001" || message.includes("DEVICE_LIMIT_REACHED")) {
      const limit = deviceLimitFor(user.role);
      throw new AppError(
        "DEVICE_LIMIT_REACHED",
        `Batas ${limit} HP untuk akun ini sudah terpakai. Cabut salah satu HP terlebih dahulu.`,
        409,
        { deviceLimit: limit },
      );
    }
    throw err;
  }
}

/** Terbitkan access + refresh token untuk perangkat yang sudah terverifikasi. */
async function issueSession(
  c: Queryable,
  user: { id: string; village_id: string | null; role: string },
  deviceId: string,
  ip: string | null,
) {
  const accessToken = signJwt({
    sub: user.id,
    did: deviceId,
    vid: user.village_id,
    role: user.role,
  });

  const refresh = randomToken(48);
  const expires = new Date(Date.now() + env.REFRESH_TTL_DAYS * 86_400_000);
  // Wajib memakai klien transaksi pemanggil: baris devices baru terlihat oleh
  // transaksi itu, bukan oleh pool. Memakai pool di sini melanggar FK
  // refresh_tokens.device_id tepat saat aktivasi.
  await c.query(
    `insert into refresh_tokens (user_id, device_id, token_hash, expires_at)
     values ($1,$2,$3,$4)`,
    [user.id, deviceId, sha256Hex(refresh), expires],
  );
  await c.query("update devices set last_seen_at = now(), last_ip = $2 where id = $1", [
    deviceId,
    ip,
  ]);

  return {
    accessToken,
    refreshToken: refresh,
    accessExpiresIn: env.ACCESS_TTL_SEC,
    refreshExpiresAt: expires.toISOString(),
  };
}

function publicUser(u: UserRow) {
  return { id: u.id, nama: u.nama, role: u.role, nipd: u.nipd, jabatan: u.jabatan };
}

export default async function authRoutes(app: FastifyInstance): Promise<void> {
  /** Aktivasi pertama: NIPD + kode pengelola + PIN + key perangkat. */
  app.post("/auth/activate", async (req) => {
    const body = ActivateSchema.parse(req.body);
    return tx(async (c) => {
      const { rows } = await c.query<UserRow>(
        `select u.* from users u
           join activation_codes ac on ac.user_id = u.id
          where u.nipd = $1 and ac.code = $2 and ac.used_at is null and ac.expires_at > now()
          order by ac.created_at desc limit 1`,
        [body.nipd.trim(), body.code.trim().toUpperCase()],
      );
      const user = rows[0];
      if (!user) {
        throw new AppError("INVALID_ACTIVATION", "Kode aktivasi salah atau sudah kedaluwarsa.", 400);
      }

      const device = await bindDevice(c, user, body.device, req.ip ?? null);
      const limit = deviceLimitFor(user.role);

      await c.query("update users set pin_hash = $2, device_limit = $3 where id = $1", [
        user.id,
        hashPin(body.pin),
        limit,
      ]);
      await c.query(
        `update activation_codes set used_at = now(), bound_device_id = $2
          where user_id = $1 and used_at is null`,
        [user.id, device.id],
      );
      await audit(c, {
        villageId: user.village_id,
        actorId: user.id,
        actorRole: user.role,
        aksi: "device.bind",
        entitas: "devices",
        entitasId: device.id,
        after: { label: device.label, slot: 1, limit },
        ip: req.ip ?? null,
      });

      const session = await issueSession(c, user, device.id, req.ip ?? null);
      return {
        ...session,
        user: publicUser(user),
        device: { id: device.id, label: device.label, deviceLimit: limit },
      };
    });
  });

  /** Tambah HP ke-2/3 (pengelola) atau ke-5 (superadmin) memakai NIPD + PIN. */
  app.post("/auth/activate-device", async (req) => {
    const body = ActivateDeviceSchema.parse(req.body);
    return tx(async (c) => {
      const { rows } = await c.query<UserRow>(
        "select * from users where nipd = $1 and status = 'active'",
        [body.nipd.trim()],
      );
      const user = rows[0];
      if (!user || user.pin_hash === PENDING_PIN || !verifyPin(body.pin, user.pin_hash)) {
        throw new AppError("INVALID_CREDENTIALS", "NIPD atau PIN salah.", 401);
      }

      const device = await bindDevice(c, user, body.device, req.ip ?? null);
      await audit(c, {
        villageId: user.village_id,
        actorId: user.id,
        actorRole: user.role,
        aksi: "device.bind_additional",
        entitas: "devices",
        entitasId: device.id,
        after: { label: device.label },
        ip: req.ip ?? null,
      });

      const session = await issueSession(c, user, device.id, req.ip ?? null);
      return {
        ...session,
        user: publicUser(user),
        device: { id: device.id, label: device.label, deviceLimit: user.device_limit },
      };
    });
  });

  /** Langkah 1 login: minta nonce untuk ditandatangani key perangkat. */
  app.post("/auth/challenge", async (req) => {
    const body = z
      .object({ nipd: z.string().min(1), deviceId: z.string().uuid() })
      .parse(req.body);

    const { rows } = await pool.query(
      `select d.id, d.status, d.user_id, u.status as user_status
         from devices d join users u on u.id = d.user_id
        where d.id = $1 and u.nipd = $2`,
      [body.deviceId, body.nipd.trim()],
    );
    const device = rows[0];
    if (!device) {
      throw new AppError(
        "DEVICE_ALREADY_BOUND",
        "Akun ini sudah terikat pada HP lain. Hubungi Pengelola Desa untuk menghapus key device.",
        409,
      );
    }
    if (device.status !== "active") {
      throw new AppError(
        "DEVICE_REVOKED",
        "Key device untuk HP ini sudah dihapus pengelola. Silakan aktivasi ulang.",
        403,
      );
    }
    if (device.user_status !== "active") {
      throw new AppError("USER_INACTIVE", "Akun Anda dinonaktifkan oleh pengelola.", 403);
    }

    const nonce = randomToken(32);
    await pool.query(
      `insert into auth_challenges (nipd, nonce, expires_at)
       values ($1, $2, now() + interval '2 minutes')`,
      [body.nipd.trim(), nonce],
    );
    return { nonce, algorithm: "SHA256withECDSA" };
  });

  /** Langkah 2 login: signature atas nonce (+ PIN opsional) → sesi 30 hari. */
  app.post("/auth/login", async (req) => {
    const body = LoginSchema.parse(req.body);
    return tx(async (c) => {
      const { rows } = await c.query<UserRow & { device_status: string; device_public_key: string }>(
        `select u.*, d.status as device_status, d.public_key_pem as device_public_key
           from users u join devices d on d.user_id = u.id
          where u.nipd = $1 and d.id = $2`,
        [body.nipd.trim(), body.deviceId],
      );
      const user = rows[0];
      if (!user) {
        throw new AppError(
          "DEVICE_ALREADY_BOUND",
          "Akun ini sudah terikat pada HP lain. Hubungi Pengelola Desa untuk menghapus key device.",
          409,
        );
      }
      if (user.device_status !== "active") {
        throw new AppError(
          "DEVICE_REVOKED",
          "Key device untuk HP ini sudah dihapus pengelola. Aktivasi ulang diperlukan.",
          403,
        );
      }
      if (user.status !== "active") {
        throw new AppError("USER_INACTIVE", "Akun Anda dinonaktifkan oleh pengelola.", 403);
      }
      if (user.pin_hash === PENDING_PIN) {
        throw new AppError("NOT_ACTIVATED", "Akun belum diaktivasi. Gunakan kode dari pengelola.", 409);
      }
      if (body.pin && !verifyPin(body.pin, user.pin_hash)) {
        throw new AppError("INVALID_CREDENTIALS", "NIPD atau PIN salah.", 401);
      }

      const { rows: pending } = await c.query<{ id: string; nonce: string }>(
        `select id, nonce from auth_challenges
          where nipd = $1 and used_at is null and expires_at > now()
          order by expires_at desc limit 1`,
        [body.nipd.trim()],
      );
      const challenge = pending[0];
      if (!challenge) {
        throw new AppError("CHALLENGE_EXPIRED", "Sesi login kedaluwarsa. Coba lagi.", 400);
      }

      const signed = verifyDeviceSignature(
        user.device_public_key,
        challenge.nonce,
        body.signature,
      );
      if (!signed) {
        throw new AppError(
          "DEVICE_ALREADY_BOUND",
          "Handshake perangkat gagal — HP ini tidak terdaftar untuk akun tersebut.",
          403,
        );
      }

      await c.query("update auth_challenges set used_at = now() where id = $1", [challenge.id]);
      const session = await issueSession(c, user, body.deviceId, req.ip ?? null);
      return { ...session, user: publicUser(user) };
    });
  });

  /** Tukar refresh token (rotating, single-use, umur 30 hari). */
  app.post("/auth/refresh", async (req) => {
    const body = z.object({ refreshToken: z.string().min(16) }).parse(req.body);
    return tx(async (c) => {
      const { rows } = await c.query(
        `select rt.id, rt.user_id, rt.device_id, rt.expires_at, rt.revoked_at,
                u.role, u.village_id, u.status as user_status, d.status as device_status
           from refresh_tokens rt
           join users u on u.id = rt.user_id
           join devices d on d.id = rt.device_id
          where rt.token_hash = $1`,
        [sha256Hex(body.refreshToken)],
      );
      const row = rows[0];
      if (!row || row.revoked_at || new Date(row.expires_at) < new Date()) {
        throw new AppError("REFRESH_INVALID", "Sesi berakhir. Silakan login ulang.", 401);
      }
      if (row.device_status !== "active") {
        throw new AppError(
          "DEVICE_REVOKED",
          "Key device sudah dihapus pengelola. Aktivasi ulang diperlukan.",
          403,
        );
      }
      if (row.user_status !== "active") {
        throw new AppError("USER_INACTIVE", "Akun Anda dinonaktifkan.", 403);
      }

      await c.query("update refresh_tokens set revoked_at = now() where id = $1", [row.id]);
      return issueSession(
        c,
        { id: row.user_id, village_id: row.village_id, role: row.role },
        row.device_id,
        req.ip ?? null,
      );
    });
  });

  app.post("/auth/logout", async (req) => {
    const body = z.object({ refreshToken: z.string().min(16) }).parse(req.body);
    await pool.query("update refresh_tokens set revoked_at = now() where token_hash = $1", [
      sha256Hex(body.refreshToken),
    ]);
    return { ok: true };
  });

  /** Daftar HP terikat milik akun — layar "Perangkat Terikat". */
  app.get("/auth/devices", { preHandler: [app.authenticate] }, async (req) => {
    const claims = req.claims!;
    const { rows } = await pool.query(
      `select id, label, model, manufacturer, os_version, first_bound_at, last_seen_at, status
         from devices where user_id = $1 order by first_bound_at asc`,
      [claims.sub],
    );
    const { rows: limitRows } = await pool.query<{ device_limit: number }>(
      "select device_limit from users where id = $1",
      [claims.sub],
    );
    return { devices: rows, deviceLimit: limitRows[0]?.device_limit ?? 1 };
  });
}
