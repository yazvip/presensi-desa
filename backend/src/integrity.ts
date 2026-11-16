import { createHash } from "node:crypto";

export interface AttendanceHashInput {
  id: string;
  userId: string;
  workDate: string;
  tsServer: string;
  lat: number;
  lng: number;
  type: string;
  hashPrev: string | null;
}

/**
 * Hash chain per user: setiap baris mengikat baris sebelumnya.
 * Mengubah satu baris di database akan memutus rantai di baris berikutnya.
 */
export function computeAttendanceHash(input: AttendanceHashInput): string {
  const canonical = [
    input.id,
    input.userId,
    input.workDate,
    input.tsServer,
    input.lat.toFixed(6),
    input.lng.toFixed(6),
    input.type,
    input.hashPrev ?? "GENESIS",
  ].join("|");
  return createHash("sha256").update(canonical).digest("hex");
}
