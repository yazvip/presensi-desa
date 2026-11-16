import pg from "pg";
import { env } from "./env.js";

export const pool = new pg.Pool({ connectionString: env.DATABASE_URL, max: 10 });

export async function tx<T>(fn: (c: pg.PoolClient) => Promise<T>): Promise<T> {
  const client = await pool.connect();
  try {
    await client.query("begin");
    const out = await fn(client);
    await client.query("commit");
    return out;
  } catch (err) {
    await client.query("rollback").catch(() => undefined);
    throw err;
  } finally {
    client.release();
  }
}

export class AppError extends Error {
  constructor(
    public code: string,
    message: string,
    public status = 400,
    public detail: Record<string, unknown> = {},
  ) {
    super(message);
  }
}

export async function audit(
  c: pg.PoolClient | typeof pool,
  row: {
    villageId?: string | null;
    actorId?: string | null;
    actorRole?: string | null;
    aksi: string;
    entitas: string;
    entitasId?: string | null;
    before?: unknown;
    after?: unknown;
    ip?: string | null;
  },
): Promise<void> {
  await c.query(
    `insert into audit_log (village_id, actor_id, actor_role, aksi, entitas, entitas_id, before, after, ip)
     values ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
    [
      row.villageId ?? null,
      row.actorId ?? null,
      row.actorRole ?? null,
      row.aksi,
      row.entitas,
      row.entitasId ?? null,
      row.before ? JSON.stringify(row.before) : null,
      row.after ? JSON.stringify(row.after) : null,
      row.ip ?? null,
    ],
  );
}
