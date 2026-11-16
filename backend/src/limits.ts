/** Batas HP default per peran (plan §2B.1). */
export const DEVICE_LIMITS: Record<string, number> = {
  superadmin: 5,
  admin_desa: 3,
  kepala_desa: 1,
  bendahara: 1,
  staff: 1,
};

export function deviceLimitFor(role: string): number {
  return DEVICE_LIMITS[role] ?? 1;
}

/** Label otomatis dari model HP bila pengguna tidak mengisi. */
export function autoLabel(manufacturer?: string | null, model?: string | null): string {
  const m = (manufacturer ?? "").trim();
  const md = (model ?? "").trim();
  if (m && md) return `${m} ${md}`;
  return md || m || "Perangkat";
}
