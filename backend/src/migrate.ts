import { readdir, readFile } from "node:fs/promises";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { pool } from "./db.js";

const here = dirname(fileURLToPath(import.meta.url));

async function main(): Promise<void> {
  const dir = join(here, "..", "db");
  const files = (await readdir(dir)).filter((f) => f.endsWith(".sql")).sort();
  for (const file of files) {
    const sql = await readFile(join(dir, file), "utf8");
    console.log(`Menjalankan ${file}...`);
    await pool.query(sql);
  }
  console.log(`Selesai: ${files.length} berkas.`);
  await pool.end();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
