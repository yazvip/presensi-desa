import { z } from "zod";

const Schema = z.object({
  PORT: z.coerce.number().default(8080),
  // Di produksi API hanya mendengar di loopback; nginx yang menghadap publik.
  HOST: z.string().default("0.0.0.0"),
  DATABASE_URL: z.string().min(1),
  JWT_SECRET: z.string().min(32, "JWT_SECRET minimal 32 karakter"),
  ACCESS_TTL_SEC: z.coerce.number().default(900),
  REFRESH_TTL_DAYS: z.coerce.number().default(30),
  CORS_ORIGIN: z.string().default("http://localhost:3000"),
});

export const env = Schema.parse(process.env);
export const corsOrigins = env.CORS_ORIGIN.split(",").map((s) => s.trim()).filter(Boolean);
