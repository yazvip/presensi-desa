import {
  createHash,
  createHmac,
  createPublicKey,
  randomBytes,
  scryptSync,
  timingSafeEqual,
  verify as cryptoVerify,
  type KeyObject,
} from "node:crypto";
import { env } from "./env.js";

/* ------------------------------ JWT (HS256) ------------------------------ */

const b64url = (buf: Buffer | string): string =>
  Buffer.from(buf).toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

const fromB64url = (s: string): Buffer =>
  Buffer.from(s.replace(/-/g, "+").replace(/_/g, "/"), "base64");

const hmac = (data: string): string =>
  createHmac("sha256", env.JWT_SECRET).update(data).digest("base64url");

export interface JwtClaims {
  sub: string;
  /** Perangkat pengikat. Kosong untuk sesi panel web. */
  did: string;
  vid: string | null;
  role: string;
  /** "web" = sesi panel admin; tidak terikat perangkat. */
  ch?: "app" | "web";
  iat: number;
  exp: number;
}

export function signJwt(claims: Omit<JwtClaims, "iat" | "exp">, ttlSec = env.ACCESS_TTL_SEC): string {
  const now = Math.floor(Date.now() / 1000);
  const payload: JwtClaims = { ...claims, iat: now, exp: now + ttlSec };
  const header = b64url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const body = b64url(JSON.stringify(payload));
  return `${header}.${body}.${hmac(`${header}.${body}`)}`;
}

export function verifyJwt(token: string): JwtClaims | null {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [header, body, sig] = parts as [string, string, string];
  const expected = hmac(`${header}.${body}`);
  const a = Buffer.from(sig);
  const b = Buffer.from(expected);
  if (a.length !== b.length || !timingSafeEqual(a, b)) return null;
  try {
    const claims = JSON.parse(fromB64url(body).toString("utf8")) as JwtClaims;
    if (claims.exp <= Math.floor(Date.now() / 1000)) return null;
    return claims;
  } catch {
    return null;
  }
}

/* ------------------------------- PIN (scrypt) ------------------------------ */

export function hashPin(pin: string): string {
  const salt = randomBytes(16);
  const derived = scryptSync(pin, salt, 64);
  return `scrypt$${salt.toString("base64")}$${derived.toString("base64")}`;
}

export function verifyPin(pin: string, stored: string): boolean {
  if (!stored.startsWith("scrypt$")) return false;
  const [, saltB64, hashB64] = stored.split("$");
  if (!saltB64 || !hashB64) return false;
  const derived = scryptSync(pin, Buffer.from(saltB64, "base64"), 64);
  const expected = Buffer.from(hashB64, "base64");
  if (derived.length !== expected.length) return false;
  return timingSafeEqual(derived, expected);
}

/** PIN belum diset — akun menunggu aktivasi perangkat pertama. */
export const PENDING_PIN = "!pending";

/* --------------------------------- Random -------------------------------- */

export function randomToken(bytes = 32): string {
  return randomBytes(bytes).toString("base64url");
}

export function randomDigits(length: number): string {
  let out = "";
  while (out.length < length) {
    for (const byte of randomBytes(length)) {
      if (out.length >= length) break;
      if (byte < 250) out += String(byte % 10);
    }
  }
  return out;
}

export function randomCode(chars = 16): string {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let out = "";
  for (const byte of randomBytes(chars)) {
    out += alphabet[byte % alphabet.length];
  }
  return out;
}

export function sha256Hex(input: string): string {
  return createHash("sha256").update(input).digest("hex");
}

/* ------------------------------ Device key ------------------------------- */

/** Android mengirim SubjectPublicKeyInfo dalam base64 DER. */
export function publicKeyFromBase64Der(b64: string): KeyObject {
  return createPublicKey({ key: Buffer.from(b64, "base64"), format: "der", type: "spki" });
}

export function publicKeyToPem(key: KeyObject): string {
  return key.export({ format: "pem", type: "spki" }).toString();
}

export function keyFingerprint(base64Der: string): string {
  return createHash("sha256").update(Buffer.from(base64Der, "base64")).digest("hex");
}

/** Android Keystore "SHA256withECDSA" menghasilkan DER signature. */
export function verifyDeviceSignature(
  publicKeyPem: string,
  data: string,
  signatureBase64: string,
): boolean {
  try {
    const key = createPublicKey(publicKeyPem);
    return cryptoVerify(
      "sha256",
      Buffer.from(data, "utf8"),
      key,
      Buffer.from(signatureBase64, "base64"),
    );
  } catch {
    return false;
  }
}
