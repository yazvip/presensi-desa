import { test } from "node:test";
import assert from "node:assert/strict";
import { haversine, pickBestSample, evaluateGeofence } from "../src/geo.js";

// Kantor Balai Desa fiktif di Sleman.
const OFFICE = { id: "off-1", lat: -7.7059, lng: 110.3554, radius_m: 50, acc_max_m: 60 };

test("haversine: jarak titik ke dirinya sendiri nol", () => {
  assert.equal(haversine(OFFICE.lat, OFFICE.lng, OFFICE.lat, OFFICE.lng), 0);
});

test("haversine: 1 derajat lintang ≈ 111,2 km", () => {
  const d = haversine(0, 0, 1, 0);
  assert.ok(Math.abs(d - 111_195) < 100, `dapat ${d}`);
});

test("haversine: simetris", () => {
  const a = haversine(-7.7059, 110.3554, -7.6079, 110.4036);
  const b = haversine(-7.6079, 110.4036, -7.7059, 110.3554);
  assert.ok(Math.abs(a - b) < 1e-6);
});

test("geofence: 0,0002 derajat (~22 m) diterima di radius 50 m", () => {
  const out = evaluateGeofence([{ lat: OFFICE.lat + 0.0002, lng: OFFICE.lng, acc: 8 }], OFFICE);
  assert.equal(out.ok, true);
  assert.ok(out.distanceM < 50);
});

test("geofence: ~111 m ditolak OUT_OF_RANGE", () => {
  const out = evaluateGeofence([{ lat: OFFICE.lat + 0.001, lng: OFFICE.lng, acc: 8 }], OFFICE);
  assert.equal(out.ok, false);
  assert.equal(out.reason, "OUT_OF_RANGE");
  assert.ok(out.distanceM > 100 && out.distanceM < 120);
});

test("geofence: akurasi 85 m ditolak ACCURACY_TOO_LOW walau jarak dekat", () => {
  const out = evaluateGeofence([{ lat: OFFICE.lat, lng: OFFICE.lng, acc: 85 }], OFFICE);
  assert.equal(out.ok, false);
  assert.equal(out.reason, "ACCURACY_TOO_LOW");
});

test("geofence: sampel kosong → NO_SAMPLE", () => {
  const out = evaluateGeofence([], OFFICE);
  assert.equal(out.ok, false);
  assert.equal(out.reason, "NO_SAMPLE");
});

test("best-of-3: ambil sampel dengan akurasi terbaik, bukan yang pertama", () => {
  const samples = [
    { lat: -7.9, lng: 110.35, acc: 40 },
    { lat: -7.7059, lng: 110.3554, acc: 6 },
    { lat: -7.85, lng: 110.355, acc: 22 },
  ];
  assert.deepEqual(pickBestSample(samples), samples[1]);
});

test("best-of-3: sampel dengan akurasi > 100 m dibuang sebelum memilih", () => {
  const chosen = pickBestSample([
    { lat: 1, lng: 1, acc: 500 },
    { lat: 2, lng: 2, acc: 30 },
  ]);
  assert.equal(chosen?.lat, 2);
});

test("best-of-3: semua sampel buruk → null", () => {
  assert.equal(pickBestSample([{ lat: 1, lng: 1, acc: 400 }]), null);
});

test("geofence: radius diubah jadi 100 m ikut dipakai", () => {
  const out = evaluateGeofence(
    [{ lat: OFFICE.lat + 0.0007, lng: OFFICE.lng, acc: 10 }],
    { ...OFFICE, radius_m: 100 },
  );
  assert.equal(out.ok, true);
});
