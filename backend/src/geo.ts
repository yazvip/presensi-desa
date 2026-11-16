export interface Sample {
  lat: number;
  lng: number;
  /** akurasi dalam meter */
  acc: number;
}

export interface Office {
  lat: number;
  lng: number;
  radius_m: number;
  acc_max_m: number;
}

const EARTH_RADIUS_M = 6_371_000;

/** Jarak permukaan bumi (meter) — Haversine, bukan selisih lat/lng. */
export function haversine(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const rad = Math.PI / 180;
  const dLat = (lat2 - lat1) * rad;
  const dLng = (lng2 - lng1) * rad;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
}

/** Buang sampel yang akurasinya buruk, lalu ambil yang paling akurat. */
export function pickBestSample(samples: Sample[], discardAboveM = 100): Sample | null {
  const usable = samples.filter(
    (s) => Number.isFinite(s.lat) && Number.isFinite(s.lng) && Number.isFinite(s.acc) && s.acc <= discardAboveM,
  );
  if (usable.length === 0) return null;
  return usable.reduce((best, s) => (s.acc < best.acc ? s : best));
}

export type GeofenceOutcome =
  | { ok: true; distanceM: number; acc: number; officeId: string }
  | { ok: false; reason: "NO_SAMPLE" | "ACCURACY_TOO_LOW" | "OUT_OF_RANGE"; distanceM: number; acc: number };

export function evaluateGeofence(
  samples: Sample[],
  office: Office & { id: string; acc_max_m?: number },
): GeofenceOutcome {
  const best = pickBestSample(samples);
  if (!best) return { ok: false, reason: "NO_SAMPLE", distanceM: Number.NaN, acc: Number.NaN };

  const distanceM = haversine(best.lat, best.lng, office.lat, office.lng);
  const accMax = office.acc_max_m ?? 60;

  if (best.acc > accMax) {
    return { ok: false, reason: "ACCURACY_TOO_LOW", distanceM, acc: best.acc };
  }
  if (distanceM > office.radius_m) {
    return { ok: false, reason: "OUT_OF_RANGE", distanceM, acc: best.acc };
  }
  return { ok: true, distanceM, acc: best.acc, officeId: office.id };
}
