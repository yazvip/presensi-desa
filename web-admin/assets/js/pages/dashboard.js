window.Pages = window.Pages || {};

window.Pages.dashboard = async function (host) {
  const me = window.APP.me;
  const role = me.user.role;
  const today = pd.jktDate(0);

  if (role === "superadmin") return renderSuperadmin(host);

  host.innerHTML =
    '<div class="pd-quick-actions">' +
    '  <a class="pd-quick-btn" href="#/staff"><i class="fas fa-user-plus"></i>Kelola Petugas</a>' +
    '  <a class="pd-quick-btn" href="#/lokasi"><i class="fas fa-qrcode"></i>Lokasi &amp; Cetak QR</a>' +
    '  <a class="pd-quick-btn" href="#/rekap"><i class="fas fa-table"></i>Rekap Absensi</a>' +
    '  <a class="pd-quick-btn" href="#/cuti"><i class="fas fa-envelope-open-text"></i>Izin &amp; Cuti</a>' +
    '  <a class="pd-quick-btn" href="#/integritas"><i class="fas fa-shield-alt"></i>Cek Integritas</a>' +
    "</div>" +
    '<div class="row" id="kpi-row"></div>' +
    '<div class="row">' +
    '  <div class="col-lg-7"><div class="card"><div class="card-header d-flex justify-content-between align-items-center">' +
    '    <span><i class="fas fa-chart-bar mr-2"></i>Kehadiran 7 hari terakhir</span></div>' +
    '    <div class="card-body"><canvas id="chart-7d" height="150"></canvas></div></div></div>' +
    '  <div class="col-lg-5"><div class="card"><div class="card-header"><i class="fas fa-clock mr-2"></i>Absen hari ini</div>' +
    '    <div class="card-body p-0" id="today-table"></div></div></div>' +
    "</div>" +
    '<div class="row"><div class="col-12"><div class="card"><div class="card-header"><i class="fas fa-list mr-2"></i>Petugas belum absen hari ini</div>' +
    '  <div class="card-body" id="missing-box"></div></div></div></div>';

  const [rekap7, rekapToday, staff, cuti] = await Promise.all([
    api.get("/admin/rekap?from=" + pd.jktDate(-6) + "&to=" + today),
    api.get("/admin/rekap?from=" + today + "&to=" + today),
    api.get("/admin/staff"),
    api.get("/admin/leave").catch(() => ({ requests: [] })),
  ]);

  const hadirToday = rekapToday.rows.length;
  const terlambat = rekapToday.rows.filter((r) => r.status_masuk === "terlambat").length;
  const izinHariIni = (cuti.requests || []).filter((r) => r.status === "approved" && String(r.mulai).slice(0, 10) <= today && String(r.selesai).slice(0, 10) >= today).length;
  const belum = Math.max(0, staff.staff.length - hadirToday - izinHariIni);

  document.getElementById("kpi-row").innerHTML = [
    kpi("Hadir hari ini", hadirToday, "fa-user-check", "bg-emerald", staff.staff.length + " petugas terdaftar"),
    kpi("Terlambat", terlambat, "fa-hourglass-half", "bg-gold", "dari " + hadirToday + " yang absen"),
    kpi("Belum absen", belum, "fa-user-clock", "bg-ink", "termasuk yang belum memindai"),
    kpi("Izin / cuti", izinHariIni, "fa-envelope-open-text", "bg-emerald", "disetujui pengelola"),
  ].join("");

  drawChart(rekap7.rows);

  pd.table(document.getElementById("today-table"), {
    rows: rekapToday.rows,
    empty: "Belum ada yang absen hari ini.",
    columns: [
      { label: "Nama", render: (r) => pd.esc(r.nama) + '<div class="text-muted small">' + pd.esc(r.jabatan || r.nipd) + "</div>" },
      { label: "Masuk", render: (r) => r.masuk_at ? '<span class="badge badge-soft">' + pd.fmtTime(r.masuk_at) + "</span>" : "—" },
      { label: "Status", render: (r) => r.status_masuk === "terlambat" ? '<span class="badge badge-warning">Terlambat</span>' : r.status_masuk ? '<span class="badge badge-success">Tepat waktu</span>' : "—" },
    ],
  });

  const hadirIds = {};
  rekapToday.rows.forEach((r) => { hadirIds[r.nipd] = true; });
  (cuti.requests || []).forEach((r) => { hadirIds["izin:" + r.nama] = true; });
  const missing = staff.staff.filter((s) => !hadirIds[s.nipd] && !hadirIds["izin:" + s.nama]);
  const box = document.getElementById("missing-box");
  if (missing.length === 0) {
    box.innerHTML = '<div class="text-success font-weight-semibold"><i class="fas fa-check-circle mr-2"></i>Semua petugas sudah absen atau berizin.</div>';
  } else {
    box.innerHTML = missing.map((s) =>
      '<span class="badge badge-soft mr-2 mb-2">' + pd.esc(s.nama) + " · " + pd.esc(pd.roleLabel(s.role)) + "</span>"
    ).join("");
  }
};

function kpi(label, value, icon, cls, sub) {
  return '<div class="col-6 col-lg-3"><div class="small-box ' + cls + '">' +
    '<div class="inner"><h3>' + value + "</h3><p>" + pd.esc(label) + "</p></div>" +
    '<div class="icon"><i class="fas ' + icon + '"></i></div>' +
    '<div class="small-box-footer px-3 pb-2" style="font-size:.78rem;opacity:.88">' + pd.esc(sub) + "</div>" +
    "</div></div>";
}

function drawChart(rows) {
  const canvas = document.getElementById("chart-7d");
  if (!canvas || !window.Chart) return;
  const days = [];
  for (let i = 6; i >= 0; i--) days.push(pd.jktDate(-i));
  const label = (iso) => new Intl.DateTimeFormat("id-ID", { weekday: "short", day: "numeric" }).format(new Date(iso + "T00:00:00+07:00"));
  const counts = days.map((d) => rows.filter((r) => String(r.work_date).slice(0, 10) === d).length);

  new Chart(canvas, {
    type: "bar",
    data: {
      labels: days.map(label),
      datasets: [{
        label: "Petugas hadir",
        data: counts,
        backgroundColor: "#0b3b2b",
        hoverBackgroundColor: "#c8a035",
        borderRadius: 8,
        maxBarThickness: 36,
      }],
    },
    options: {
      responsive: true,
      plugins: { legend: { display: false } },
      scales: {
        y: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: "rgba(11,59,43,.08)" } },
        x: { grid: { display: false } },
      },
    },
  });
}

async function renderSuperadmin(host) {
  const data = await api.get("/super/villages");
  const villages = data.villages || [];
  const totalUsers = villages.reduce((a, v) => a + Number(v.users || 0), 0);
  const tanpaPengelola = villages.filter((v) => !v.pengelola).length;

  host.innerHTML =
    '<div class="pd-quick-actions">' +
    '  <a class="pd-quick-btn" href="#/desa"><i class="fas fa-landmark"></i>Kelola Desa &amp; Pengelola</a>' +
    '  <a class="pd-quick-btn" href="#/perangkat"><i class="fas fa-mobile-alt"></i>Perangkat Terikat</a>' +
    '  <a class="pd-quick-btn" href="#/audit"><i class="fas fa-clipboard-list"></i>Audit Log</a>' +
    '  <a class="pd-quick-btn" href="#/integritas"><i class="fas fa-shield-alt"></i>Integritas Data</a>' +
    "</div>" +
    '<div class="row">' +
    kpi("Desa terdaftar", villages.length, "fa-landmark", "bg-emerald", "seluruh desa") +
    kpi("Pengguna aktif", totalUsers, "fa-users", "bg-ink", "petugas + pengelola") +
    kpi("Lokasi kantor", villages.reduce((a, v) => a + Number(v.locations || 0), 0), "fa-map-marker-alt", "bg-emerald", "titik geofence") +
    kpi("Tanpa pengelola", tanpaPengelola, "fa-user-slash", "bg-gold", "perlu ditunjuk") +
    "</div>" +
    '<div class="card"><div class="card-header"><i class="fas fa-landmark mr-2"></i>Desa</div><div class="card-body" id="village-table"></div></div>';

  pd.table(document.getElementById("village-table"), {
    rows: villages,
    columns: [
      { label: "Desa", render: (r) => "<strong>" + pd.esc(r.nama) + '</strong><div class="text-muted small">' + pd.esc(r.kode_desa) + "</div>" },
      { label: "Kecamatan", render: (r) => pd.esc(r.kecamatan) + '<div class="text-muted small">' + pd.esc(r.kabupaten) + "</div>" },
      { label: "Pengelola", render: (r) => r.pengelola ? pd.esc(r.pengelola) : '<span class="badge badge-soft-gold">Belum ada</span>' },
      { label: "Petugas", key: "users" },
      { label: "Lokasi", key: "locations" },
      { label: "Status", render: (r) => (r.aktif ? '<span class="badge badge-success">Aktif</span>' : '<span class="badge badge-secondary">Nonaktif</span>') },
    ],
  });
}
