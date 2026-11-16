import type { FastifyReply, FastifyRequest } from "fastify";
import { pool } from "./db.js";
import { verifyJwt, type JwtClaims } from "./crypto.js";

declare module "fastify" {
  interface FastifyRequest {
    claims?: JwtClaims;
    deviceActive?: boolean;
  }
  interface FastifyInstance {
    authenticate: (req: FastifyRequest, reply: FastifyReply) => Promise<void>;
    requireRole: (...roles: string[]) => (req: FastifyRequest, reply: FastifyReply) => Promise<void>;
  }
}

export async function authenticate(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const header = req.headers.authorization ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  const claims = token ? verifyJwt(token) : null;
  if (!claims) {
    reply.code(401).send({ code: "UNAUTHORIZED", message: "Sesi tidak valid atau kedaluwarsa." });
    return;
  }

  const { rows } = await pool.query(
    `select d.id, d.status, u.status as user_status
       from devices d join users u on u.id = d.user_id
      where d.id = $1 and d.user_id = $2`,
    [claims.did, claims.sub],
  );
  const device = rows[0];
  if (!device || device.status !== "active") {
    reply.code(401).send({
      code: "DEVICE_REVOKED",
      message: "Perangkat ini sudah dilepas oleh pengelola. Silakan aktivasi ulang.",
    });
    return;
  }
  if (device.user_status !== "active") {
    reply.code(403).send({ code: "USER_INACTIVE", message: "Akun Anda dinonaktifkan." });
    return;
  }

  req.claims = claims;
  req.deviceActive = true;
}

export function requireRole(...roles: string[]) {
  return async (req: FastifyRequest, reply: FastifyReply): Promise<void> => {
    const role = req.claims?.role;
    if (!role || !roles.includes(role)) {
      reply.code(403).send({ code: "FORBIDDEN", message: "Anda tidak berwenang untuk aksi ini." });
    }
  };
}

export function villageOf(req: FastifyRequest): string {
  const vid = req.claims?.vid;
  if (!vid) {
    throw Object.assign(new Error("Akun ini tidak terikat ke desa mana pun."), { statusCode: 400, code: "NO_VILLAGE" });
  }
  return vid;
}
