import "./setup.js";
import test from "node:test";
import assert from "node:assert/strict";
import { buildServer } from "../src/index.js";

// Regresi: error handler dulu dipasang setelah register(), sehingga ZodError dan
// AppError dari rute plugin berubah jadi 500 "Internal Server Error".
test("badan tidak valid dibalas 400 VALIDATION_ERROR", async () => {
  const app = await buildServer();
  try {
    const res = await app.inject({
      method: "POST",
      url: "/auth/activate",
      payload: { nipd: "X", code: "kode-jauh-terlalu-panjang", pin: "123456", device: { publicKey: "a".repeat(40) } },
    });
    assert.equal(res.statusCode, 400);
    const body = res.json() as { code: string; detail: unknown[] };
    assert.equal(body.code, "VALIDATION_ERROR");
    assert.ok(Array.isArray(body.detail) && body.detail.length > 0);
  } finally {
    await app.close();
  }
});

test("deviceId bukan uuid dibalas 400, bukan 500", async () => {
  const app = await buildServer();
  try {
    const res = await app.inject({
      method: "POST",
      url: "/auth/challenge",
      payload: { nipd: "X", deviceId: "bukan-uuid" },
    });
    assert.equal(res.statusCode, 400);
    assert.equal((res.json() as { code: string }).code, "VALIDATION_ERROR");
  } finally {
    await app.close();
  }
});

test("health tetap 200", async () => {
  const app = await buildServer();
  try {
    const res = await app.inject({ method: "GET", url: "/health" });
    assert.equal(res.statusCode, 200);
    assert.equal((res.json() as { ok: boolean }).ok, true);
  } finally {
    await app.close();
  }
});
