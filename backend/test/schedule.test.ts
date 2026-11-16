import { test } from "node:test";
import assert from "node:assert/strict";
import {
  zonedParts,
  workDate,
  parseHhMm,
  evaluateCheckIn,
  evaluateCheckOut,
  effectiveMinutes,
  isHoliday,
  type DaySchedule,
} from "../src/schedule.js";

const TZ = "Asia/Jakarta";
const SENIN: DaySchedule = {
  day_of_week: 1,
  is_workday: true,
  jam_masuk: "07:30",
  jam_pulang: "15:00",
  toleransi_telat_menit: 10,
  istirahat_menit: 60,
};

// 2026-11-16 adalah hari Senin.
const at = (iso: string) => new Date(iso);

test("zonedParts: 2026-11-16T00:30Z = Senin 07:30 WIB", () => {
  const p = zonedParts(at("2026-11-16T00:30:00Z"), TZ);
  assert.equal(p.dow, 1);
  assert.equal(p.hour, 7);
  assert.equal(p.minute, 30);
});

test("workDate: pakai tanggal lokal desa, bukan UTC", () => {
  // 00:30 UTC Senin = 07:30 WIB Senin
  assert.equal(workDate(at("2026-11-16T00:30:00Z"), TZ), "2026-11-16");
  // 20:00 UTC Minggu = 03:00 WIB Senin
  assert.equal(workDate(at("2026-11-15T20:00:00Z"), TZ), "2026-11-16");
});

test("zonedParts: tengah malam WIB tidak terbaca sebagai jam 24", () => {
  const p = zonedParts(at("2026-11-15T17:00:00Z"), TZ);
  assert.equal(p.hour, 0);
  assert.equal(p.day, 16);
});

test("parseHhMm", () => {
  assert.equal(parseHhMm("07:30"), 450);
  assert.equal(parseHhMm("15:00"), 900);
  assert.equal(parseHhMm("00:00"), 0);
});

test("absen masuk 07:25 → tepat waktu", () => {
  const r = evaluateCheckIn(at("2026-11-16T00:25:00Z"), TZ, SENIN);
  assert.equal(r.status, "tepat_waktu");
  assert.equal(r.lateMinutes, 0);
});

test("absen masuk 07:40 → masih tepat waktu (toleransi 10 menit)", () => {
  const r = evaluateCheckIn(at("2026-11-16T00:40:00Z"), TZ, SENIN);
  assert.equal(r.status, "tepat_waktu");
  assert.equal(r.lateMinutes, 0);
});

test("absen masuk 07:41 → terlambat 1 menit", () => {
  const r = evaluateCheckIn(at("2026-11-16T00:41:00Z"), TZ, SENIN);
  assert.equal(r.status, "terlambat");
  assert.equal(r.lateMinutes, 1);
});

test("absen masuk 09:00 → terlambat 80 menit", () => {
  const r = evaluateCheckIn(at("2026-11-16T02:00:00Z"), TZ, SENIN);
  assert.equal(r.status, "terlambat");
  assert.equal(r.lateMinutes, 80);
});

test("absen pulang 14:50 → pulang cepat 10 menit", () => {
  const r = evaluateCheckOut(at("2026-11-16T07:50:00Z"), TZ, SENIN);
  assert.equal(r.status, "pulang_cepat");
  assert.equal(r.earlyMinutes, 10);
});

test("absen pulang 15:05 → tepat waktu", () => {
  const r = evaluateCheckOut(at("2026-11-16T08:05:00Z"), TZ, SENIN);
  assert.equal(r.status, "tepat_waktu");
});

test("jam kerja efektif: 07:30–15:00 dikurangi istirahat 60 menit = 390 menit", () => {
  const masuk = at("2026-11-16T00:30:00Z");
  const pulang = at("2026-11-16T08:00:00Z");
  assert.equal(effectiveMinutes(masuk, pulang, 60), 390);
});

test("jam kerja efektif tidak pernah negatif", () => {
  const masuk = at("2026-11-16T08:00:00Z");
  const pulang = at("2026-11-16T00:30:00Z");
  assert.equal(effectiveMinutes(masuk, pulang, 60), 0);
});

test("jadwal bisa diubah pengelola: toleransi 0 menit", () => {
  const ketat = { ...SENIN, toleransi_telat_menit: 0 };
  const r = evaluateCheckIn(at("2026-11-16T00:31:00Z"), TZ, ketat);
  assert.equal(r.status, "terlambat");
});

test("isHoliday", () => {
  assert.equal(isHoliday("2026-11-16", [{ tanggal: "2026-11-16" }]), true);
  assert.equal(isHoliday("2026-11-17", [{ tanggal: "2026-11-16" }]), false);
});
