import "./setup.js";
import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, sign as nodeSign } from "node:crypto";
import {
  hashPin,
  verifyPin,
  signJwt,
  verifyJwt,
  keyFingerprint,
  publicKeyFromBase64Der,
  publicKeyToPem,
  verifyDeviceSignature,
  randomDigits,
  randomCode,
} from "../src/crypto.js";

test("PIN: hash lalu verifikasi benar", () => {
  const h = hashPin("123456");
  assert.ok(h.startsWith("scrypt$"));
  assert.equal(verifyPin("123456", h), true);
});

test("PIN: PIN salah ditolak", () => {
  assert.equal(verifyPin("654321", hashPin("123456")), false);
});

test("PIN: dua hash untuk PIN sama berbeda (salt acak)", () => {
  assert.notEqual(hashPin("123456"), hashPin("123456"));
});

test("PIN: format rusak tidak pernah cocok", () => {
  assert.equal(verifyPin("123456", "!pending"), false);
  assert.equal(verifyPin("123456", "scrypt$rusak"), false);
});

test("JWT: sign lalu verify mengembalikan klaim yang sama", () => {
  const token = signJwt({ sub: "u1", did: "d1", vid: "v1", role: "staff" });
  const claims = verifyJwt(token);
  assert.equal(claims?.sub, "u1");
  assert.equal(claims?.did, "d1");
  assert.equal(claims?.vid, "v1");
  assert.equal(claims?.role, "staff");
});

test("JWT: payload diubah → ditolak", () => {
  const token = signJwt({ sub: "u1", did: "d1", vid: "v1", role: "staff" });
  const [h, , s] = token.split(".");
  const forged = Buffer.from(
    JSON.stringify({ sub: "u1", did: "d1", vid: "v1", role: "superadmin", iat: 0, exp: 9_999_999_999 }),
  ).toString("base64url");
  assert.equal(verifyJwt(`${h}.${forged}.${s}`), null);
});

test("JWT: token kedaluwarsa ditolak", () => {
  const token = signJwt({ sub: "u1", did: "d1", vid: null, role: "staff" }, -10);
  assert.equal(verifyJwt(token), null);
});

test("JWT: sampah ditolak", () => {
  assert.equal(verifyJwt("bukan.token.valid"), null);
  assert.equal(verifyJwt(""), null);
});

test("device key: fingerprint stabil dan unik antar kunci", () => {
  const a = generateKeyPairSync("ec", { namedCurve: "P-256" });
  const b = generateKeyPairSync("ec", { namedCurve: "P-256" });
  const b64a = (a.publicKey.export({ format: "der", type: "spki" }) as Buffer).toString("base64");
  const b64b = (b.publicKey.export({ format: "der", type: "spki" }) as Buffer).toString("base64");

  assert.equal(keyFingerprint(b64a), keyFingerprint(b64a), "kunci sama → fingerprint sama");
  assert.notEqual(keyFingerprint(b64a), keyFingerprint(b64b), "kunci beda → fingerprint beda");
  assert.match(keyFingerprint(b64a), /^[0-9a-f]{64}$/);
});

test("device key: signature P-256 atas nonce terverifikasi", () => {
  const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "P-256" });
  const der = publicKey.export({ format: "der", type: "spki" }) as Buffer;
  const pem = publicKeyToPem(publicKeyFromBase64Der(der.toString("base64")));
  const nonce = "nonce-uji-123";
  const sig = nodeSign("sha256", Buffer.from(nonce), privateKey).toString("base64");
  assert.equal(verifyDeviceSignature(pem, nonce, sig), true);
});

test("device key: nonce berbeda / kunci lain → signature gagal", () => {
  const a = generateKeyPairSync("ec", { namedCurve: "P-256" });
  const b = generateKeyPairSync("ec", { namedCurve: "P-256" });
  const derA = a.publicKey.export({ format: "der", type: "spki" }) as Buffer;
  const pemA = publicKeyToPem(publicKeyFromBase64Der(derA.toString("base64")));
  const sig = nodeSign("sha256", Buffer.from("nonce-asli"), a.privateKey).toString("base64");
  assert.equal(verifyDeviceSignature(pemA, "nonce-lain", sig), false);

  const sigB = nodeSign("sha256", Buffer.from("nonce-asli"), b.privateKey).toString("base64");
  assert.equal(verifyDeviceSignature(pemA, "nonce-asli", sigB), false);
});

test("kode aktivasi 6 digit selalu numerik", () => {
  for (let i = 0; i < 50; i += 1) {
    const code = randomDigits(6);
    assert.match(code, /^\d{6}$/);
  }
});

test("kode QR tidak memuat karakter ambigu 0/1/I/O", () => {
  for (let i = 0; i < 20; i += 1) {
    assert.match(randomCode(16), /^[A-HJ-NP-Z2-9]{16}$/);
  }
});
