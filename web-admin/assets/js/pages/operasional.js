window.Pages = window.Pages || {};

/* ------------------------------- PETUGAS ------------------------------- */
window.Pages.staff = async function (host) {
  host.innerHTML =
    '<div class="card"><div class="card-header d-flex flex-wrap justify-content-between align-items-center">' +
    '<span><i class="fas fa-users mr-2"></i>Petugas</span>' +
    '<button class="btn btn-primary btn-sm mt-2 mt-sm-0" id="btn-add-staff"><i class="fas fa-user-plus mr-1"></i>Tambah Petugas</button>' +
    '</div><div class="card-body" id="staff-box"></div></div>';

  document.getElementById("btn-add-staff").addEventListener("click", addStaff);

  async function load() {
    const box = document.getElementById("staff-box");
    pd.loading(box);
    const data = await api.get("/admin/staff");
    pd.table(box, {
      rows: data.staff || [],
      empty: "Belum ada petugas. Tambahkan petugas pertama.",
      order: [[0, "asc"]],
      columns: [
        { label: "Nama", render: (r) => "<strong>" + pd.esc(r.nama) + '</strong><div class="text-muted small">NIPD ' + pd.esc(r.nipd) + "</div>" },
        { label: "Jabatan", render: (r) => pd.esc(r.jabatan || "—") + '<div class="text-muted small">' + pd.esc(pd.roleLabel(r.role)) + "</div>" },
        { label: "HP terikat", render: (r) => '<span class="badge badge-soft">' + Number(r.devices_active || 0) + " / " + Number(r.device_limit || 1) + "</span>" },
        { label: "Status", render: (r) => pd.statusBadge(r.status) },
        {
          label: "", className: "text-right", render: (r) =>
            '<button class="btn btn-sm btn-outline-primary pd-code" data-id="' + pd.esc(r.id) + '" data-nama="' + pd.esc(r.nama) + '">' +
            '<i class="fas fa-key mr-1"></i>Kode aktivasi</button>',
        },
      ],
    });

    box.addEventListener("click", async (ev) => {
      const btn = ev.target.closest("button.pd-code");
      if (!btn) return;
      btn.disabled = true;
      try {
        const res = await api.post("/admin/staff/" + btn.dataset.id + "/activation-code");
        pd.codeModal("Kode Aktivasi — " + btn.dataset.nama, res.activationCode,
          "Berikan kepada petugas. Berlaku " + (res.expiresInDays || 7) + " hari, sekali pakai.");
      } catch (err) { pd.toast(err.message, "err"); }
      finally { btn.disabled = false; }
    });
  }

  function addStaff() {
    pd.modal({
      title: "Tambah Petugas",
      body:
        '<div class="form-group"><label>Nama lengkap</label><input class="form-control" id="f-nama"></div>' +
        '<div class="form-group"><label>NIPD</label><input class="form-control" id="f-nipd"><small class="text-muted">Unik di desa ini.</small></div>' +
        '<div class="form-group"><label>Jabatan</label><input class="form-control" id="f-jab" placeholder="mis. Staf Kaur Keuangan"></div>' +
        '<div class="form-group"><label>Peran</label><select class="form-control" id="f-role">' +
        '<option value="staff">Staff (1 HP)</option><option value="kepala_desa">Kepala Desa (1 HP)</option>' +
        '<option value="bendahara">Bendahara (1 HP)</option></select></div>',
      okText: "Simpan &amp; buat kode",
      onOk: async ({ node }) => {
        const payload = {
          nama: node.querySelector("#f-nama").value.trim(),
          nipd: node.querySelector("#f-nipd").value.trim(),
          jabatan: node.querySelector("#f-jab").value.trim() || undefined,
          role: node.querySelector("#f-role").value,
        };
        if (payload.nama.length < 2 || payload.nipd.length < 2) { pd.toast("Nama dan NIPD wajib diisi.", "err"); return false; }
        const res = await api.post("/admin/staff", payload);
        await load();
        pd.codeModal("Kode Aktivasi — " + payload.nama, res.activationCode,
          "Petugas memakainya sekali di aplikasi Android untuk mengaktifkan HP dan membuat PIN.");
        return true;
      },
    });
  }

  await load();
};

/* ------------------------------ PERANGKAT ------------------------------ */
window.Pages.devices = async function (host) {
  const role = window.APP.me.user.role;
  const superMode = role === "superadmin";

  host.innerHTML =
    '<div class="card"><div class="card-header d-flex flex-wrap justify-content-between align-items-center">' +
    '<span><i class="fas fa-mobile-alt mr-2"></i>Perangkat Terikat</span>' +
    '<div class="btn-group btn-group-sm mt-2 mt-sm-0" id="filter-device">' +
    '<button class="btn btn-outline-secondary active" data-status="">Semua</button>' +
    '<button class="btn btn-outline-secondary" data-status="active">Aktif</button>' +
    '<button class="btn btn-outline-secondary" data-status="revoked">Dilepas</button>' +
    "</div></div>" +
    '<div class="card-body" id="device-box"></div></div>';

  async function load(status) {
    const box = document.getElementById("device-box");
    pd.loading(box);
    const path = superMode
      ? "/super/devices" + (status ? "?status=" + status : "")
      : "/admin/devices";
    const data = await api.get(path);
    let rows = data.devices || [];
    if (status) rows = rows.filter((r) => r.status === status);

    pd.table(box, {
      rows,
      empty: "Belum ada perangkat terikat.",
      order: [[0, "asc"]],
      columns: [
        { label: "Petugas", render: (r) => "<strong>" + pd.esc(r.nama) + '</strong><div class="text-muted small">' + pd.esc(r.nipd) + " · " + pd.esc(pd.roleLabel(r.role)) + "</div>" + (superMode && r.village ? '<div class="text-muted small">' + pd.esc(r.village) + "</div>" : "") },
        { label: "Perangkat", render: (r) => pd.esc(r.label || "—") + '<div class="text-muted small">' + pd.esc([r.manufacturer, r.model].filter(Boolean).join(" ")) + "</div>" },
        { label: "Android", render: (r) => '<span class="text-muted small">' + pd.esc(r.os_version || "—") + "</span>" },
        { label: "Terikat", render: (r) => '<span class="small">' + pd.fmtDate(r.first_bound_at) + "</span>" },
        { label: "Terakhir aktif", render: (r) => '<span class="small">' + pd.ago(r.last_seen_at) + "</span>" },
        { label: "Status", render: (r) => pd.statusBadge(r.status) + (r.revoke_reason ? '<div class="text-muted small">' + pd.esc(r.revoke_reason) + "</div>" : "") },
        {
          label: "", className: "text-right", render: (r) => r.status === "active"
            ? '<button class="btn btn-sm btn-outline-danger pd-revoke" data-id="' + pd.esc(r.id) + '" data-nama="' + pd.esc(r.nama) + '"><i class="fas fa-unlink mr-1"></i>Lepas</button>'
            : "",
        },
      ],
    });

    box.addEventListener("click", async (ev) => {
      const btn = ev.target.closest("button.pd-revoke");
      if (!btn) return;
      revokeDevice(btn.dataset.id, btn.dataset.nama, () => load(status));
    });
  }

  document.getElementById("filter-device").addEventListener("click", (ev) => {
    const btn = ev.target.closest("button[data-status]");
    if (!btn) return;
    document.querySelectorAll("#filter-device button").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    load(btn.dataset.status);
  });

  function revokeDevice(id, nama, onDone) {
    pd.modal({
      title: "Lepas Perangkat — " + nama,
      body:
        '<div class="form-group mb-0"><label>Alasan</label><select class="form-control" id="f-reason">' +
        '<option value="ganti_hp">Ganti HP</option><option value="hp_rusak">HP rusak</option>' +
        '<option value="hp_hilang">HP hilang</option><option value="pindah_tugas">Pindah tugas</option>' +
        '<option value="lainnya">Lainnya</option></select>' +
        '<small class="text-muted d-block mt-2">Setelah dilepas, petugas harus aktivasi ulang dengan kode dari pengelola.</small></div>',
      okText: "Lepas perangkat",
      okClass: "btn-danger",
      onOk: async ({ node }) => {
        const reason = node.querySelector("#f-reason").value;
        await api.post((superMode ? "/super/devices/" : "/admin/devices/") + id + "/revoke", { reason });
        pd.toast("Perangkat dilepas.");
        await onDone();
        return true;
      },
    });
  }

  await load("");
};

/* -------------------------- LOKASI & QR ------------------------------- */
window.Pages.lokasi = async function (host) {
  host.innerHTML =
    '<div class="card"><div class="card-header d-flex flex-wrap justify-content-between align-items-center">' +
    '<span><i class="fas fa-qrcode mr-2"></i>Lokasi Kantor &amp; QR</span>' +
    '<button class="btn btn-primary btn-sm mt-2 mt-sm-0" id="btn-add-loc"><i class="fas fa-map-marker-alt mr-1"></i>Lokasi Baru</button>' +
    '</div><div class="card-body" id="loc-box"></div></div>';

  document.getElementById("btn-add-loc").addEventListener("click", addLocation);

  async function load() {
    const box = document.getElementById("loc-box");
    pd.loading(box);
    const data = await api.get("/admin/locations");
    pd.table(box, {
      rows: data.locations || [],
      empty: "Belum ada titik kantor. Tambahkan lokasi agar petugas bisa absen.",
      columns: [
        { label: "Lokasi", render: (r) => "<strong>" + pd.esc(r.nama) + '</strong><div class="text-muted small">' + pd.esc(r.alamat || "") + "</div>" },
        { label: "Koordinat", render: (r) => '<span class="small">' + Number(r.lat).toFixed(5) + ", " + Number(r.lng).toFixed(5) + "</span>" },
        { label: "Radius", render: (r) => '<span class="badge badge-soft">' + r.radius_m + " m</span>" },
        { label: "Akurasi maks", render: (r) => '<span class="small text-muted">' + r.acc_max_m + " m</span>" },
        { label: "QR versi", render: (r) => "v" + r.qr_version + (r.qr_printed_at ? '<div class="text-muted small">dicetak ' + pd.fmtDate(r.qr_printed_at) + "</div>" : '<div class="text-warning small">belum dicetak</div>') },
        {
          label: "", className: "text-right", render: (r) =>
            '<div class="btn-group btn-group-sm">' +
            '<button class="btn btn-outline-primary pd-qr" data-id="' + pd.esc(r.id) + '" data-nama="' + pd.esc(r.nama) + '"><i class="fas fa-qrcode mr-1"></i>QR</button>' +
            '<button class="btn btn-outline-danger pd-rotate" data-id="' + pd.esc(r.id) + '" data-nama="' + pd.esc(r.nama) + '">Ganti QR</button>' +
            "</div>",
        },
      ],
    });

    box.addEventListener("click", async (ev) => {
      const qr = ev.target.closest("button.pd-qr");
      const rot = ev.target.closest("button.pd-rotate");
      if (qr) return showQr(qr.dataset.id, qr.dataset.nama, () => load());
      if (rot) {
        const yes = await pd.confirmDialog("Ganti kode QR " + rot.dataset.nama + "? Lembar QR yang sudah dicetak tidak akan berlaku lagi.", { danger: true, okText: "Ganti QR" });
        if (!yes) return;
        try {
          const res = await api.post("/admin/locations/" + rot.dataset.id + "/rotate-qr");
          pd.toast("QR baru dibuat (versi " + res.location.qr_version + ").");
          showQr(rot.dataset.id, rot.dataset.nama, () => load());
        } catch (err) { pd.toast(err.message, "err"); }
      }
    });
  }

  function addLocation() {
    pd.modal({
      title: "Lokasi Kantor Baru",
      size: "modal-lg",
      body:
        '<div class="form-row">' +
        '<div class="form-group col-md-8"><label>Nama lokasi</label><input class="form-control" id="f-nama" placeholder="Kantor Desa"></div>' +
        '<div class="form-group col-md-4"><label>Alamat</label><input class="form-control" id="f-alamat"></div>' +
        '<div class="form-group col-md-6"><label>Latitude</label><input class="form-control" id="f-lat" inputmode="decimal"></div>' +
        '<div class="form-group col-md-6"><label>Longitude</label><input class="form-control" id="f-lng" inputmode="decimal"></div>' +
        '<div class="form-group col-md-6"><label>Radius (meter)</label><input class="form-control" id="f-radius" type="number" min="10" max="500" value="50"></div>' +
        '<div class="form-group col-md-6"><label>Akurasi GPS maks (meter)</label><input class="form-control" id="f-acc" type="number" min="10" max="500" value="60"></div>' +
        "</div>" +
        '<button class="btn btn-outline-secondary btn-sm" id="btn-geo"><i class="fas fa-location-arrow mr-1"></i>Ambil dari lokasi perangkat ini</button>' +
        '<div class="alert alert-info py-2 small mt-3 mb-0">Radius maksimal 50 m dianjurkan agar petugas benar-benar berada di kantor.</div>',
      okText: "Simpan lokasi",
      onOk: async ({ node }) => {
        const lat = parseFloat(node.querySelector("#f-lat").value);
        const lng = parseFloat(node.querySelector("#f-lng").value);
        const payload = {
          nama: node.querySelector("#f-nama").value.trim(),
          alamat: node.querySelector("#f-alamat").value.trim() || undefined,
          lat, lng,
          radiusM: parseInt(node.querySelector("#f-radius").value, 10) || 50,
          accMaxM: parseInt(node.querySelector("#f-acc").value, 10) || 60,
        };
        if (payload.nama.length < 2 || !isFinite(lat) || !isFinite(lng)) {
          pd.toast("Nama, latitude, dan longitude wajib benar.", "err");
          return false;
        }
        const res = await api.post("/admin/locations", payload);
        await load();
        showQr(res.location.id, res.location.nama, () => load());
        return true;
      },
      onShown: (node) => {
        node.querySelector("#btn-geo").addEventListener("click", () => {
          if (!navigator.geolocation) return pd.toast("Perangkat ini tidak mendukung GPS.", "err");
          pd.toast("Mengambil koordinat…", "info");
          navigator.geolocation.getCurrentPosition(
            (pos) => {
              node.querySelector("#f-lat").value = pos.coords.latitude.toFixed(6);
              node.querySelector("#f-lng").value = pos.coords.longitude.toFixed(6);
              pd.toast("Koordinat diambil (akurasi " + Math.round(pos.coords.accuracy) + " m).");
            },
            (err) => pd.toast("Gagal mengambil lokasi: " + err.message, "err"),
            { enableHighAccuracy: true, timeout: 15000 }
          );
        });
      },
    });
  }

  async function showQr(locationId, nama, onDone) {
    const m = pd.modal({
      title: "QR Kantor — " + nama,
      hideFooter: true,
      body: '<div class="text-center pd-qr" id="qr-area"><div class="spinner-border text-success"></div></div>' +
        '<p class="text-muted small mt-3 mb-0">Cetak dan tempel di kantor. Petugas memindai QR ini untuk absen. Jangan menyebar foto QR — pertahanannya adalah radius.</p>',
    });
    try {
      const data = await api.get("/admin/locations/" + locationId + "/qr");
      const area = m.node.querySelector("#qr-area");
      area.innerHTML = '<canvas id="qr-canvas"></canvas><div class="mt-2 text-muted small">Versi ' + data.qrVersion + " · " + pd.esc(nama) + "</div>" +
        '<div class="mt-3"><button class="btn btn-sm btn-primary mr-2" id="btn-print"><i class="fas fa-print mr-1"></i>Cetak</button>' +
        '<button class="btn btn-sm btn-outline-secondary" id="btn-tandai">Tandai sudah dicetak</button></div>';
      if (window.QRCode) {
        await QRCode.toCanvas(document.getElementById("qr-canvas").getContext ? document.getElementById("qr-canvas") : null, data.qrPayload, { width: 260, margin: 1 });
      } else {
        area.insertAdjacentHTML("beforeend", '<div class="alert alert-warning small mt-2">Pustaka QR gagal dimuat. Muatan: <code>' + pd.esc(data.qrPayload) + "</code></div>");
      }
      m.node.querySelector("#btn-print").addEventListener("click", () => {
        const canvas = m.node.querySelector("#qr-canvas");
        const img = canvas ? canvas.toDataURL("image/png") : "";
        const w = window.open("", "_blank", "width=600,height=700");
        w.document.write('<html><head><title>QR ' + pd.esc(nama) + '</title></head><body style="text-align:center;font-family:sans-serif">' +
          "<h2>" + pd.esc(nama) + "</h2><p>Pindai QR ini untuk absen</p>" +
          '<img src="' + img + '" width="380" height="380"><p style="color:#666;font-size:12px">VDS1 · versi ' + data.qrVersion + "</p></body></html>");
        w.document.close();
        w.focus();
        setTimeout(() => w.print(), 400);
      });
      m.node.querySelector("#btn-tandai").addEventListener("click", async () => {
        try {
          await api.post("/admin/locations/" + locationId + "/qr-printed");
          pd.toast("Ditandai sudah dicetak.");
          if (onDone) await onDone();
          m.close();
        } catch (err) { pd.toast(err.message, "err"); }
      });
    } catch (err) {
      m.node.querySelector("#qr-area").innerHTML = '<div class="alert alert-danger mb-0">' + pd.esc(err.message) + "</div>";
    }
  }

  await load();
};

/* ------------------------------- JADWAL -------------------------------- */
window.Pages.jadwal = async function (host) {
  const NAMES = ["", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu"];
  host.innerHTML =
    '<div class="card"><div class="card-header d-flex flex-wrap justify-content-between align-items-center">' +
    '<span><i class="fas fa-clock mr-2"></i>Jam Kerja Desa</span>' +
    '<button class="btn btn-primary btn-sm mt-2 mt-sm-0" id="btn-save"><i class="fas fa-save mr-1"></i>Simpan</button>' +
    "</div><div class="card-body" id="sched-box"></div></div>";

  const data = await api.get("/admin/schedule");
  const days = data.days || [];
  const box = document.getElementById("sched-box");

  box.innerHTML = '<div class="table-responsive"><table class="table table-sm align-middle schedule-row"><thead><tr>' +
    "<th>Hari</th><th>Hari kerja</th><th>Masuk</th><th>Pulang</th><th>Toleransi telat (menit)</th><th>Istirahat (menit)</th>" +
    "</tr></thead><tbody>" +
    [1, 2, 3, 4, 5, 6, 7].map((d) => {
      const row = days.filter((x) => Number(x.day_of_week) === d)[0] || {
        is_workday: d <= 5, jam_masuk: "07:30", jam_pulang: "15:00", toleransi_telat_menit: 10, istirahat_menit: 60,
      };
      return '<tr data-day="' + d + '">' +
        "<td><strong>" + NAMES[d] + "</strong></td>" +
        '<td><div class="custom-control custom-switch"><input type="checkbox" class="custom-control-input f-kerja" id="k' + d + '"' + (row.is_workday ? " checked" : "") + '><label class="custom-control-label" for="k' + d + '"></label></div></td>' +
        '<td><input type="time" class="form-control form-control-sm f-masuk" value="' + pd.esc(row.jam_masuk) + '"></td>' +
        '<td><input type="time" class="form-control form-control-sm f-pulang" value="' + pd.esc(row.jam_pulang) + '"></td>' +
        '<td><input type="number" min="0" max="180" class="form-control form-control-sm f-tol" value="' + Number(row.toleransi_telat_menit) + '"></td>' +
        '<td><input type="number" min="0" max="240" class="form-control form-control-sm f-ist" value="' + Number(row.istirahat_menit) + '"></td>' +
        "</tr>";
    }).join("") + "</tbody></table></div>" +
    '<p class="text-muted small mb-0">Toleransi telat menentukan kapan absen masuk dihitung terlambat. Istirahat dipakai untuk menghitung jam pulang cepat.</p>';

  document.getElementById("btn-save").addEventListener("click", async (ev) => {
    const btn = ev.currentTarget;
    btn.disabled = true;
    try {
      const payload = { days: [] };
      box.querySelectorAll("tr[data-day]").forEach((tr) => {
        payload.days.push({
          dayOfWeek: Number(tr.dataset.day),
          isWorkday: tr.querySelector(".f-kerja").checked,
          jamMasuk: tr.querySelector(".f-masuk").value || "07:30",
          jamPulang: tr.querySelector(".f-pulang").value || "15:00",
          toleransiTelatMenit: Number(tr.querySelector(".f-tol").value) || 0,
          istirahatMenit: Number(tr.querySelector(".f-ist").value) || 0,
        });
      });
      await api.put("/admin/schedule", payload);
      pd.toast("Jam kerja disimpan.");
    } catch (err) { pd.toast(err.message, "err"); }
    finally { btn.disabled = false; }
  });
};
