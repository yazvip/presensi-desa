export interface DaySchedule {
  day_of_week: number; // 1 = Senin ... 7 = Minggu
  is_workday: boolean;
  jam_masuk: string; // "07:30"
  jam_pulang: string; // "15:00"
  toleransi_telat_menit: number;
  istirahat_menit: number;
}

export interface ZonedParts {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
  second: number;
  /** 1 = Senin ... 7 = Minggu (ISO) */
  dow: number;
}

const formatterCache = new Map<string, Intl.DateTimeFormat>();

function formatter(tz: string): Intl.DateTimeFormat {
  let f = formatterCache.get(tz);
  if (!f) {
    f = new Intl.DateTimeFormat("en-GB", {
      timeZone: tz,
      hour12: false,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      weekday: "short",
    });
    formatterCache.set(tz, f);
  }
  return f;
}

const DOW: Record<string, number> = { Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6, Sun: 7 };

/** Ubah waktu UTC menjadi komponen waktu lokal desa. */
export function zonedParts(date: Date, tz: string): ZonedParts {
  const parts = formatter(tz).formatToParts(date);
  const get = (type: string): string => parts.find((p) => p.type === type)?.value ?? "0";
  const hour = Number(get("hour")) % 24;
  return {
    year: Number(get("year")),
    month: Number(get("month")),
    day: Number(get("day")),
    hour,
    minute: Number(get("minute")),
    second: Number(get("second")),
    dow: DOW[get("weekday")] ?? 1,
  };
}

/** Tanggal kerja lokal desa dalam format YYYY-MM-DD. */
export function workDate(date: Date, tz: string): string {
  const p = zonedParts(date, tz);
  const mm = String(p.month).padStart(2, "0");
  const dd = String(p.day).padStart(2, "0");
  return `${p.year}-${mm}-${dd}`;
}

export function parseHhMm(value: string): number {
  const [h, m] = value.split(":");
  return Number(h) * 60 + Number(m ?? 0);
}

export function minutesOfDay(parts: ZonedParts): number {
  return parts.hour * 60 + parts.minute + parts.second / 60;
}

export type CheckInStatus = "tepat_waktu" | "terlambat";
export type CheckOutStatus = "tepat_waktu" | "pulang_cepat";

export function evaluateCheckIn(
  at: Date,
  tz: string,
  schedule: DaySchedule,
): { status: CheckInStatus; lateMinutes: number } {
  const p = zonedParts(at, tz);
  const nowMin = minutesOfDay(p);
  const startMin = parseHhMm(schedule.jam_masuk);
  const lateMinutes = Math.max(0, Math.round(nowMin - (startMin + schedule.toleransi_telat_menit)));
  return { status: lateMinutes > 0 ? "terlambat" : "tepat_waktu", lateMinutes };
}

export function evaluateCheckOut(
  at: Date,
  tz: string,
  schedule: DaySchedule,
): { status: CheckOutStatus; earlyMinutes: number } {
  const p = zonedParts(at, tz);
  const nowMin = minutesOfDay(p);
  const endMin = parseHhMm(schedule.jam_pulang);
  const earlyMinutes = Math.max(0, Math.round(endMin - nowMin));
  return { status: earlyMinutes > 0 ? "pulang_cepat" : "tepat_waktu", earlyMinutes };
}

/** Jam kerja efektif dalam menit, dikurangi istirahat. */
export function effectiveMinutes(masuk: Date, pulang: Date, istirahatMenit: number): number {
  const raw = Math.round((pulang.getTime() - masuk.getTime()) / 60000);
  return Math.max(0, raw - istirahatMenit);
}

export function isHoliday(tanggal: string, holidays: { tanggal: string }[]): boolean {
  return holidays.some((h) => h.tanggal === tanggal);
}
