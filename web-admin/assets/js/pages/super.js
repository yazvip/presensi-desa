window.Pages = window.Pages || {};

/* ------------------------------- AUDIT --------------------------------- */
window.Pages.audit = async function (host) {
  host.innerHTML =
    '<div class="card"><div class="card-header d-flex flex-wrap justify-content-between align-items-center">' +
    '<span><i class="fas fa-clipboard-list mr-2"></i>Audit Log</span>' +
    '<select class="form-control form-control-sm mt-2 mt-sm-0" id="f-limit" style="width:auto">' +
    '<option value="100">100 terbaru</option><option value="200" selected>200 terbaru</option><option value="500">500 terbaru</option></select>' +
    '</div><div class="card-body" id="audit-box"></div></div>';

  document.getElementById("f-limit").addEventListener("change", load);

  async function load() {
    const box = document.getElementById("audit-box");
    pd.loading(box);
    const limit = document.getElementById("f-limit").value;
    const data = await api.get("/super/audit?limit=" + limit);
    pd.table(box, {
      rows: data.logs || [],
      empty: "Belum ada aktivitas tercatat.",
      order: [[0, "desc"]],
      columns: [
        { label: "Waktu", render: (r) => '<span class="small">' + pd.fmtDateTime(r.ts) + "</span>" },
        { label: "Pelaku", render: (r) => "<strong>" + pd.esc(r.actor || "sistem") + '</strong><div class="text-muted small">' + pd.esc(pd.roleLabel(r.actor_role)) + "</div>" },
        { label: "Desa", render: (r) => pd.esc(r.village || "—") },
        { label: "Aksi", render: (r) => '<span class="badge badge-soft">' + pd.esc(r.aksi) + "</span>" },
        { label: "Entitas", render: (r) => '<span class="small">' + pd.esc(r.entitas) + "</span>" },
        {
          label: "Perubahan", render: (r) => {
            const payload = JSON.stringify({ sebelum: r.before, sesudah: r.after });
            if (!payload || payload === "{}") return "—";
            return '<details><summary class="small text-muted">lihat</summary><pre class="small mt-1" style="max-width:320px;white-space:pre-wrap">' + pd.esc(payload) + "</pre></details>";
          },
        },
        { label: "IP", render: (r) => '<span class="small text-muted">' + pd.esc(r.ip || "—") + "</span>" },
      ],
    });
  }

  await load();
};

/* -------------------------------- PROFIL -------------------------------- */
window.Pages.profil = async function (host) {
  const me = window.APP.me;
  const u = me.user;
  host.innerHTML =
    '<div class="row">' +
    '<div class="col-lg-5"><div class="card"><div class="card-header"><i class="fas fa-id-card mr-2"></i>Akun</div><div class="card-body">' +
    row("Nama", u.nama) + row("NIPD", u.nipd) + row("Peran", pd.roleLabel(u.role)) + row("Jabatan", u.jabatan) +
    row("Status", u.status === "active" ? "Aktif" : "Nonaktif") +
    row("Desa", u.village_nama || "— (akun pusat)") + row("Kode desa", u.kode_desa || "—") +
    row("Wilayah", [u.kecamatan, u.kabupaten].filter(Boolean).join(", ") || "—") +
    row("Zona waktu", u.timezone || "—") +
    "</div></div></div>" +
    '<div class="col-lg-7"><div class="card"><div class="card-header"><i class="fas fa-shield-alt mr-2"></i>Sesi &amp; Keamanan</div><div class="card-body">' +
    "<p class='text-muted small'>Sesi panel web berlaku 8 jam dan terikat pada akun ini, bukan pada perangkat. Untuk logout, gunakan menu akun di kanan atas.</p>" +
    row("Channel", me.channel === "web" ? "Panel web" : "Aplikasi Android") +
    '<div class="mt-3"><button class="btn btn-outline-danger" id="btn-out"><i class="fas fa-sign-out-alt mr-1"></i>Keluar dari panel</button></div>' +
    "</div></div></div>" +
    '<div class="col-lg-7"><div class="card"><div class="card-header"><i class="fas fa-key mr-2"></i>Ganti PIN</div><div class="card-body">' +
    '<p class="text-muted small">PIN dipakai untuk masuk ke aplikasi Android dan panel ini.</p>' +
    '<form id="f-pin" class="row">' +
    '<div class="form-group col-md-4"><label>PIN lama</label><input type="password" inputmode="numeric" maxlength="6" class="form-control" id="p-lama" required></div>' +
    '<div class="form-group col-md-4"><label>PIN baru</label><input type="password" inputmode="numeric" maxlength="6" class="form-control" id="p-baru" required></div>' +
    '<div class="form-group col-md-4"><label>Ulangi PIN baru</label><input type="password" inputmode="numeric" maxlength="6" class="form-control" id="p-ulang" required></div>' +
    "</form>" +
    '<button class="btn btn-primary btn-sm" id="btn-pin"><i class="fas fa-save mr-1"></i>Simpan PIN baru</button>' +
    "</div></div></div></div>";

  document.getElementById("btn-out").addEventListener("click", () => {
    api.clear();
    location.hash = "#/";
    location.reload();
  });

  document.getElementById("btn-pin").addEventListener("click", async (ev) => {
    const btn = ev.currentTarget;
    const lama = document.getElementById("p-lama").value;
    const baru = document.getElementById("p-baru").value;
    const ulang = document.getElementById("p-ulang").value;
    if (baru !== ulang) return pd.toast("PIN baru tidak sama dengan ulangannya.", "err");
    if (!/^[0-9]{6}$/.test(baru)) return pd.toast("PIN harus 6 angka.", "err");
    btn.disabled = true;
    try {
      await api.post("/auth/web-change-pin", { currentPin: lama, newPin: baru });
      pd.toast("PIN berhasil diganti.");
      ["p-lama", "p-baru", "p-ulang"].forEach((id) => { document.getElementById(id).value = ""; });
    } catch (err) { pd.toast(err.message, "err"); }
    finally { btn.disabled = false; }
  });
};

function row(label, value) {
  return '<div class="d-flex justify-content-between border-bottom py-2">' +
    '<span class="text-muted small">' + pd.esc(label) + "</span>" +
    '<span class="text-right"><strong>' + pd.esc(value === null || value === undefined || value === "" ? "—" : value) + "</strong></span></div>";
}
