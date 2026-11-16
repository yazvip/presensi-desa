window.Pages = window.Pages || {};

window.Pages.desa = async function (host) {
  host.innerHTML =
    '<div class="card">' +
    '<div class="card-header d-flex flex-wrap justify-content-between align-items-center">' +
    '  <span><i class="fas fa-landmark mr-2"></i>Desa &amp; Pengelola</span>' +
    '  <button class="btn btn-primary btn-sm mt-2 mt-sm-0" id="btn-new-village"><i class="fas fa-plus mr-1"></i>Desa Baru</button>' +
    "</div>" +
    '<div class="card-body" id="village-box"></div></div>';

  document.getElementById("btn-new-village").addEventListener("click", () => newVillage(load));

  async function load() {
    const box = document.getElementById("village-box");
    pd.loading(box);
    const data = await api.get("/super/villages");
    const villages = data.villages || [];
    pd.table(box, {
      rows: villages,
      empty: "Belum ada desa. Tambahkan desa pertama.",
      order: [[0, "asc"]],
      columns: [
        { label: "Desa", render: (r) => "<strong>" + pd.esc(r.nama) + '</strong><div class="text-muted small">' + pd.esc(r.kode_desa) + "</div>" },
        { label: "Kecamatan", render: (r) => pd.esc(r.kecamatan) + '<div class="text-muted small">' + pd.esc(r.kabupaten) + " · " + pd.esc(r.timezone) + "</div>" },
        { label: "Pengelola", render: (r) => r.pengelola ? pd.esc(r.pengelola) : '<span class="badge badge-soft-gold">Belum ada</span>' },
        { label: "Petugas", key: "users", className: "text-right" },
        { label: "Lokasi", key: "locations", className: "text-right" },
        {
          label: "", className: "text-right", render: (r) =>
            r.pengelola
              ? '<button class="btn btn-sm btn-outline-secondary pd-ganti" data-id="' + pd.esc(r.id) + '" data-nama="' + pd.esc(r.nama) + '"><i class="fas fa-user-shield mr-1"></i>Ganti pengelola</button>'
              : '<button class="btn btn-sm btn-primary pd-tunjuk" data-id="' + pd.esc(r.id) + '" data-nama="' + pd.esc(r.nama) + '"><i class="fas fa-user-plus mr-1"></i>Tunjuk pengelola</button>',
        },
      ],
    });

    box.onclick = (ev) => {
      const btn = ev.target.closest("button[data-id]");
      if (!btn) return;
      assignPengelola(btn.dataset.id, btn.dataset.nama, load);
    };
  }

  await load();
};

function newVillage(onDone) {
  pd.modal({
    title: "Tambah Desa",
    size: "modal-lg",
    body:
      '<div class="form-row">' +
      '<div class="form-group col-md-6"><label>Kode desa</label><input class="form-control" id="f-kode" maxlength="20" placeholder="mis. TLG01"><small class="text-muted">Huruf/angka, unik.</small></div>' +
      '<div class="form-group col-md-6"><label>Nama desa</label><input class="form-control" id="f-nama" placeholder="Desa Talangarah"></div>' +
      '<div class="form-group col-md-6"><label>Kecamatan</label><input class="form-control" id="f-kec"></div>' +
      '<div class="form-group col-md-6"><label>Kabupaten</label><input class="form-control" id="f-kab"></div>' +
      '<div class="form-group col-md-6"><label>Provinsi</label><input class="form-control" id="f-prov"></div>' +
      '<div class="form-group col-md-6"><label>Zona waktu</label><input class="form-control" id="f-tz" value="Asia/Jakarta"></div>' +
      "</div>",
    okText: "Simpan desa",
    onOk: async ({ node }) => {
      const val = (id) => node.querySelector("#" + id).value.trim();
      const payload = {
        kodeDesa: val("f-kode"), nama: val("f-nama"), kecamatan: val("f-kec"),
        kabupaten: val("f-kab"), provinsi: val("f-prov"), timezone: val("f-tz") || "Asia/Jakarta",
      };
      if (!payload.kodeDesa || !payload.nama || !payload.kecamatan || !payload.kabupaten || !payload.provinsi) {
        pd.toast("Lengkapi kode, nama, kecamatan, kabupaten, dan provinsi.", "err");
        return false;
      }
      const res = await api.post("/super/villages", payload);
      pd.toast("Desa " + (res.village ? res.village.nama : "") + " dibuat.");
      if (onDone) await onDone();
      return true;
    },
  });
}

function assignPengelola(villageId, villageName, onDone) {
  pd.modal({
    title: "Tunjuk Pengelola — " + villageName,
    body:
      '<p class="text-muted small">Satu desa hanya boleh punya satu pengelola aktif. Kode aktivasi berlaku 7 hari.</p>' +
      '<div class="form-group"><label>Nama pengelola</label><input class="form-control" id="f-nama"></div>' +
      '<div class="form-group"><label>NIPD</label><input class="form-control" id="f-nipd"></div>',
    okText: "Tunjuk",
    onOk: async ({ node }) => {
      const nama = node.querySelector("#f-nama").value.trim();
      const nipd = node.querySelector("#f-nipd").value.trim();
      if (nama.length < 2 || nipd.length < 2) { pd.toast("Nama dan NIPD wajib diisi.", "err"); return false; }
      const res = await api.post("/super/villages/" + villageId + "/pengelola", { nama, nipd });
      if (onDone) await onDone();
      pd.codeModal("Kode Aktivasi Pengelola", res.activationCode,
        "Berikan kepada " + nama + ". Pengelola memakainya sekali di aplikasi Android untuk mengaktifkan HP dan membuat PIN.");
      return true;
    },
  });
}
