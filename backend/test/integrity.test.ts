import { test } from "node:test";
import assert from "node:assert/strict";
import { computeAttendanceHash } from "../src/integrity.js";

const base = {
  id: "att-1",
  userId: "user-1",
  workDate: "2026-11-16",
  tsServer: "2026-11-16T00:30:00.000Z",
  lat: -7.7059,
  lng: 110.3554,
  type: "masuk",
  hashPrev: null as string | null,
};

test("hash deterministik", () => {
  assert.equal(computeAttendanceHash(base), computeAttendanceHash({ ...base }));
});

test("hash berubah bila koordinat berubah 1 meter", () => {
  const moved = { ...base, lat: base.lat + 0.000009 };
  assert.notEqual(computeAttendanceHash(base), computeAttendanceHash(moved));
});

test("hash berubah bila tipe absen berubah", () => {
  assert.notEqual(
    computeAttendanceHash(base),
    computeAttendanceHash({ ...base, type: "pulang" }),
  );
});

test("hash terikat baris sebelumnya", () => {
  const withPrev = { ...base, id: "att-2", hashPrev: computeAttendanceHash(base) };
  assert.notEqual(
    computeAttendanceHash(withPrev),
    computeAttendanceHash({ ...withPrev, hashPrev: null }),
  );
});

test("mengubah satu baris memutus rantai baris berikutnya", () => {
  const h1 = computeAttendanceHash(base);
  const h2 = computeAttendanceHash({ ...base, id: "att-2", type: "pulang", hashPrev: h1 });

  // Penyerang mengubah koordinat baris pertama lewat SQL langsung.
  const h1Tampered = computeAttendanceHash({ ...base, lat: -7.6 });
  const h2AfterTamper = computeAttendanceHash({
    ...base,
    id: "att-2",
    type: "pulang",
    hashPrev: h1Tampered,
  });

  assert.notEqual(h2, h2AfterTamper);
  assert.notEqual(h1, h1Tampered);
});
