import Fastify from "fastify";
import cors from "@fastify/cors";
import { ZodError } from "zod";
import { env, corsOrigins } from "./env.js";
import { AppError } from "./db.js";
import { authenticate, requireRole } from "./auth.js";
import authRoutes from "./routes/auth.js";
import attendanceRoutes from "./routes/attendance.js";
import adminRoutes from "./routes/admin.js";
import superadminRoutes from "./routes/superadmin.js";
import integrityRoutes from "./routes/integrity.js";

export async function buildServer() {
  // trustProxy: API hanya mendengar di loopback, jadi X-Forwarded-For dari
  // nginx boleh dipercaya — IP pelaku di audit log jadi IP asli, bukan 127.0.0.1.
  const app = Fastify({
    logger: { level: process.env.LOG_LEVEL ?? "info" },
    trustProxy: true,
  });

  await app.register(cors, { origin: corsOrigins, credentials: true });

  app.decorate("authenticate", authenticate);
  app.decorate("requireRole", requireRole);

  app.get("/health", async () => ({ ok: true, service: "presensi-desa-api", ts: new Date().toISOString() }));

  // Harus dipasang SEBELUM register(): konteks anak menyalin error handler
  // saat dibuat, jadi handler yang dipasang belakangan tidak dipakai rute plugin.
  app.setErrorHandler((err, req, reply) => {
    if (err instanceof ZodError) {
      return reply.code(400).send({
        code: "VALIDATION_ERROR",
        message: "Data yang dikirim tidak lengkap atau tidak valid.",
        detail: err.issues.map((i) => ({ path: i.path.join("."), message: i.message })),
      });
    }
    if (err instanceof AppError) {
      return reply.code(err.status).send({ code: err.code, message: err.message, detail: err.detail });
    }
    const status = (err as { statusCode?: number }).statusCode ?? 500;
    const code = (err as { code?: string }).code ?? "INTERNAL_ERROR";
    if (status >= 500) req.log.error(err);
    return reply.code(status).send({
      code,
      message: status >= 500 ? "Terjadi kesalahan pada server." : err.message,
    });
  });

  await app.register(authRoutes);
  await app.register(attendanceRoutes);
  await app.register(adminRoutes);
  await app.register(superadminRoutes);
  await app.register(integrityRoutes);

  return app;
}

const isMain = process.argv[1]?.endsWith("index.js") || process.argv[1]?.endsWith("index.ts");
if (isMain) {
  buildServer()
    .then((app) => app.listen({ port: env.PORT, host: env.HOST }))
    .then(() => console.log(`API siap di ${env.HOST}:${env.PORT}`))
    .catch((err) => {
      console.error("Gagal start:", err);
      process.exit(1);
    });
}
