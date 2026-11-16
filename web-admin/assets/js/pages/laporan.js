window.Pages = window.Pages || {};

/* -------------------------------- REKAP -------------------------------- */
window.Pages.rekap = async function (host) {
  host.innerHTML =
    '<div class="card"><div class="card-header"><i class="fas fa-table mr-2"></i>Rekap Absensi</div>' +
    '<div class="card-body">' +
    '<form class="form-row align-items-end" id="f-rekap">' +
    '<div class="form-group col-6 col-md-3"><label>Dari</label><input type="date" class="form-control" id="d-from"></div>' +
    '<div class="form-group col-6 col-md-3"><label>Sampai</label><input type="date" class="form-control" id="d-to"></div>' +
    '<div class="form-group col-8 col-md-3"><label>Petugas</label><select class="form-control" id="d-user"><option value="">Semua petugas</option></select></div>' +
    '<div class="form-group col-4 col-md-3"><button class="btn btn-primary btn-block" type="submit"><i class="fas fa-search mr-1"></i>Tampilkan</button></div>' +
    "</form>" +
    '<div class="row mb-3" id="rekap-kpi"></div>' +
    '<div id="rekap-box"></div></div></div>';

  document.getElementById("d-from").value = pd.jktDate(-14);
  document.getElementById("d-to").value = pd.jktDate(0);

  try {
    const staff = await api.get("/admin/staff");
    const sel = document.getElementById("d-user");
    (staff.staff || []).forEach((s) => {
      const opt = document.createElement("option");
      opt.value = s.id;
      opt.textContent = s.nama + " (" + s.nipd + ")";
      sel.appendChild(opt);
    });
  } catch (e) { /* daftar petugas tidak wajib untuk rekap */ }

  document.getElementById("f-rekap").addEventListener("submit", async (e) => {
    e.preventDefault();
    await load();
  });

  async function load() {
    const box = document.getElementById("rekap-box");
    const from = document.getElementById("d-from").value;
    const to = document.getElementById("d-to").value;
    const userId = document.getElementById("d-user").value;
    if (!from || !to) return;
    pd.loading(box);

    let path = "/admin/rekap?from=" + from + "&to=" + to;
    if (userId) path += "&userId=" + userId;
    const data = await api.get(path);
    const rows = data.rows || [];

    const hadir = rows.filter((r) => r.masuk_at).length;
    const terlambat = rows.filter((r) => r.status_masuk === "terlambat").length;
    const belumPulang = rows.filter((r) => r.masuk_at && !r.pulang_at).length;
    document.getElementById("rekap-kpi").innerHTML = [
      miniKpi("Total catatan", rows.length),
      miniKpi("Hadir", hadir, "success"),
      miniKpi("Terlambat", terlambat, "warning"),
      miniKpi("Belum absen pulang", belumPulang, "danger"),
    ].join("");

    pd.table(box, {
      rows,
      empty: "Tidak ada data pada rentang ini.",
      order: [[0, "desc"]],
      buttons: window.jQuery && jQuery.fn && jQuery.fn.dataTable && jQuery.fn.dataTable.ext && jQuery.fn.dataTable.ext.buttons
        ? [{ extend: "csv", text: "CSV", className: "btn btn-sm btn-outline-secondary" },
           { extend: "print", text: "Cetak", className: "btn btn-sm btn-outline-secondary" }]
        : [],
      columns: [
        { label: "Tanggal", render: (r) => pd.esc(String(r.work_date).slice(0, 10)) },
        { label: "Nama", render: (r) => "<strong>" + pd.esc(r.nama) + '</strong><div class="text-muted small">' + pd.esc(r.nipd) + "</div>" },
        { label: "Jabatan", render: (r) => pd.esc(r.jabatan || "—") },
        { label: "Masuk", render: (r) => r.masuk_at ? '<span class="badge badge-soft">' + pd.fmtTime(r.masuk_at) + "</span>" : "—" },
        { label: "Pulang", render: (r) => r.pulang_at ? '<span class="badge badge-soft">' + pd.fmtTime(r.pulang_at) + "</span>" : "—" },
        { label: "Status", render: (r) => r.status_masuk === "terlambat" ? '<span class="badge badge-warning">Terlambat</span>' : r.status_masuk ? '<span class="badge badge-success">Tepat waktu</span>' : "—" },
        { label: "Jarak", render: (r) => r.max_distance_m === null || r.max_distance_m === undefined ? "—" : r.max_distance_m + " m" },
        { label: "Metode", render: (r) => pd.esc(r.method || "—") },
      ],
    });
  }

  await load();
};

function miniKpi(label, value, tone) {
  const color = tone === "success" ? "#1f7a55" : tone === "warning" ? "#a8861d" : tone === "danger" ? "#b3261e" : "#0f3d2e";
  return '<div class="col-6 col-md-3"><div class="card" style="border-left:4px solid ' + color + '"><div class="card-body py-2">' +
    '<div class="text-muted small">' + pd.esc(label) + "</div>" +
    '<div class="h4 mb-0" style="color:' + color + '">' + value + "</div></div></div></div>";
}

/* -------------------------------- CUTI --------------------------------- */
window.Pages.cuti = async function (host) {
  host.innerHTML =
    '<div class="card"><div class="card-header"><i class="fas fa-envelope-open-text mr-2"></i>Permintaan Izin &amp; Cuti</div>' +
    '<div class="card-body" id="cuti-box"></div></div>';

  async function load() {
    const box = document.getElementById("cuti-box");
    pd.loading(box);
    const data = await api.get("/admin/leave");
    const rows = data.requests || [];
    pd.table(box, {
      rows,
      empty: "Tidak ada permintaan yang menunggu keputusan.",
      columns: [
        { label: "Petugas", render: (r) => "<strong>" + pd.esc(r.nama) + '</strong><div class="text-muted small">' + pd.esc(r.jabatan || "") + "</div>" },
        { label: "Jenis", render: (r) => pd.badge(r.jenis, "gold") },
        { label: "Periode", render: (r) => '<span class="small">' + pd.esc(String(r.mulai).slice(0, 10)) + " → " + pd.esc(String(r.selesai).slice(0, 10)) + "</span>" },
        { label: "Alasan", render: (r) => '<span class="small">' + pd.esc(r.alasan) + "</span>" },
        { label: "Diajukan", render: (r) => '<span class="small text-muted">' + pd.ago(r.created_at) + "</span>" },
        {
          label: "", className: "text-right", render: (r) =>
            '<div class="btn-group btn-group-sm">' +
            '<button class="btn btn-outline-success pd-setuju" data-id="' + pd.esc(r.id) + '" data-nama="' + pd.esc(r.nama) + '"><i class="fas fa-check mr-1"></i>Setujui</button>' +
            '<button class="btn btn-outline-danger pd-tolak" data-id="' + pd.esc(r.id) + '" data-nama="' + pd.esc(r.nama) + '"><i class="fas fa-times mr-1"></i>Tolak</button>' +
            "</div>",
        },
      ],
    });

    box.addEventListener("click", (ev) => {
      const ok = ev.target.closest("button.pd-setuju");
      const no = ev.target.closest("button.pd-tolak");
      if (!ok && !no) return;
      decide((ok || no).dataset.id, (ok || no).dataset.nama, !!ok);
    });
  }

  function decide(id, nama, approve) {
    pd.modal({
      title: (approve ? "Setujui" : "Tolak") + " — " + nama,
      body: '<div class="form-group mb-0"><label>Catatan (opsional)</label><textarea class="form-control" id="f-cat" rows="3"></textarea></div>',
      okText: approve ? "Setujui" : "Tolak",
      okClass: approve ? "btn-success" : "btn-danger",
      onOk: async ({ node }) => {
        const catatan = node.querySelector("#f-cat").value.trim();
        await api.post("/admin/leave/" + id + "/decide", { approve, catatan: catatan || undefined });
        pd.toast(approve ? "Pengajuan disetujui." : "Pengajuan ditolak.");
        await load();
        return true;
      },
    });
  }

  await load();
};

/* ------------------------------- ANOMALI ------------------------------- */
window.Pages.anomali = async function (host) {
  host.innerHTML =
    '<div class="card"><div class="card-header d-flex flex-wrap justify-content-between align-items-center">' +
    '<span><i class="fas fa-exclamation-triangle mr-2"></i>Anomali</span>' +
    '<select class="form-control form-control-sm mt-2 mt-sm-0" id="f-days" style="width:auto">' +
    '<option value="7">7 hari</option><option value="30" selected>30 hari</option><option value="90">90 hari</option></select>' +
    '</div><div class="card-body" id="anom-box"></div></div>';

  document.getElementById("f-days").addEventListener("change", load);

  async function load() {
    const box = document.getElementById("anom-box");
    pd.loading(box);
    const days = document.getElementById("f-days").value;
    const data = await api.get("/admin/anomalies?days=" + days);

    box.innerHTML =
      '<h6 class="text-muted mt-2">Percobaan ditolak</h6><div id="a-rejected"></div>' +
      '<h6 class="text-muted mt-4">Koordinat sama antar petugas</h6><div id="a-same"></div>' +
      '<h6 class="text-muted mt-4">Selisih jam perangkat &gt; 5 menit</h6><div id="a-skew"></div>';

    pd.table(document.getElementById("a-rejected"), {
      rows: data.rejected || [],
      empty: "Tidak ada percobaan yang ditolak.",
      columns: [
        { label: "Petugas", render: (r) => pd.esc(r.nama) },
        { label: "Waktu", render: (r) => pd.fmtDateTime(r.ts) },
        { label: "Alasan", render: (r) => '<span class="badge badge-soft-gold">' + pd.esc(r.reason) + "</span>" },
        { label: "Jarak", render: (r) => r.distance_m === null ? "—" : r.distance_m + " m" },
      ],
    });

    pd.table(document.getElementById("a-same"), {
      rows: data.sameCoords || [],
      empty: "Tidak ada koordinat yang dipakai lebih dari satu petugas.",
      columns: [
        { label: "Tanggal", render: (r) => pd.esc(String(r.work_date).slice(0, 10)) },
        { label: "Koordinat", render: (r) => '<span class="small">' + Number(r.lat) + ", " + Number(r.lng) + "</span>" },
        { label: "Jumlah petugas", key: "n_user" },
      ],
    });

    pd.table(document.getElementById("a-skew"), {
      rows: data.clockSkew || [],
      empty: "Jam perangkat selalu sesuai dengan server.",
      columns: [
        { label: "Petugas", render: (r) => pd.esc(r.nama) },
        { label: "Tanggal", render: (r) => pd.esc(String(r.work_date).slice(0, 10)) },
        { label: "Jenis", render: (r) => pd.esc(r.type) },
        { label: "Selisih", render: (r) => '<span class="badge badge-soft-gold">' + Math.round(Number(r.clock_skew_sec)) + " dtk</span>" },
      ],
    });
  }

  await load();
};

/* ------------------------------ INTEGRITAS ------------------------------ */
window.Pages.integritas = async function (host) {
  host.innerHTML = '<div class="card"><div class="card-body" id="int-box"></div></div>';
  const box = document.getElementById("int-box");
  pd.loading(box);
  const data = await api.get("/admin/integritas");
  const ok = data.valid;
  box.innerHTML =
    '<div class="text-center py-3">' +
    '<i class="fas ' + (ok ? "fa-shield-alt" : "fa-exclamation-triangle") + ' fa-3x mb-3" style="color:' + (ok ? "#1f7a55" : "#b3261e") + '"></i>' +
    "<h4>" + (ok ? "Rantai hash utuh" : "Ditemukan manipulasi data") + "</h4>" +
    '<p class="text-muted mb-0">' + Number(data.checked) + " catatan diperiksa · " + Number((data.broken || []).length) + " tidak cocok</p>" +
    (ok ? "" : '<div class="alert alert-danger mt-3 text-left"><strong>ID yang tidak cocok:</strong><br><code>' + pd.esc((data.broken || []).join("<br>")) + "</code></div>") +
    "</div>" +
    '<p class="text-muted small mt-3 mb-0">Setiap catatan absensi menyimpan hash isi dan hash catatan sebelumnya. Perubahan langsung di database terlihat di sini sebagai rantai yang putus.</p>';
};
