/* Helper tampilan: tabel, modal, toast, format tanggal WIB. */
(function () {
  const rtf = new Intl.RelativeTimeFormat("id-ID", { numeric: "auto" });

  function esc(v) {
    return String(v === null || v === undefined ? "" : v)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  function when(v) {
    if (!v) return null;
    const d = v instanceof Date ? v : new Date(v);
    return isNaN(d.getTime()) ? null : d;
  }
  function fmtDate(v) {
    const d = when(v);
    return d ? new Intl.DateTimeFormat("id-ID", { timeZone: "Asia/Jakarta", day: "2-digit", month: "short", year: "numeric" }).format(d) : "—";
  }
  function fmtDateTime(v) {
    const d = when(v);
    return d ? new Intl.DateTimeFormat("id-ID", { timeZone: "Asia/Jakarta", day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }).format(d) + " WIB" : "—";
  }
  function fmtTime(v) {
    const d = when(v);
    return d ? new Intl.DateTimeFormat("id-ID", { timeZone: "Asia/Jakarta", hour: "2-digit", minute: "2-digit" }).format(d).replace(":", ".") : "—";
  }
  function ago(v) {
    const d = when(v);
    if (!d) return "—";
    const diff = Math.round((d.getTime() - Date.now()) / 60000);
    if (Math.abs(diff) < 60) return rtf.format(diff, "minute");
    if (Math.abs(diff) < 1440) return rtf.format(Math.round(diff / 60), "hour");
    return rtf.format(Math.round(diff / 1440), "day");
  }
  /** Tanggal ISO (YYYY-MM-DD) menurut zona Asia/Jakarta. */
  function jktDate(offsetDays) {
    const d = new Date(Date.now() + (offsetDays || 0) * 86400000);
    return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Jakarta", year: "numeric", month: "2-digit", day: "2-digit" }).format(d);
  }

  const ROLE = { superadmin: "Superadmin", admin_desa: "Pengelola Desa", kepala_desa: "Kepala Desa", bendahara: "Bendahara", staff: "Staff" };
  function roleLabel(r) { return ROLE[r] || r || "—"; }

  function badge(text, variant) {
    const cls = variant === "gold" ? "badge-soft-gold" : variant === "danger" ? "badge-danger" : variant === "muted" ? "badge-secondary" : "badge-soft";
    return '<span class="badge ' + cls + '">' + esc(text) + "</span>";
  }
  function statusBadge(s) {
    const map = { active: ["Aktif", ""], inactive: ["Nonaktif", "muted"], revoked: ["Dilepas", "danger"], pending: ["Menunggu", "gold"], approved: ["Disetujui", ""], approved_pengelola: ["Disetujui Pengelola", "gold"], rejected: ["Ditolak", "danger"] };
    const m = map[s] || [s, ""];
    return badge(m[0], m[1]);
  }

  /* ------------------------------- toast ------------------------------- */
  function toast(msg, type, ms) {
    const holder = document.getElementById("toast-holder");
    const el = document.createElement("div");
    el.className = "pd-toast " + (type === "err" ? "err" : type === "info" ? "info" : "ok");
    el.innerHTML = '<i class="fas ' + (type === "err" ? "fa-exclamation-circle" : type === "info" ? "fa-info-circle" : "fa-check-circle") + ' mr-2"></i>' + esc(msg);
    holder.appendChild(el);
    setTimeout(() => { el.style.opacity = "0"; el.style.transition = "opacity .3s"; setTimeout(() => el.remove(), 320); }, ms || 4200);
  }

  /* ------------------------------- modal ------------------------------- */
  function modal(opts) {
    const id = "pd-modal-" + Math.random().toString(36).slice(2, 8);
    const holder = document.getElementById("modal-holder");
    const footer = opts.hideFooter ? "" :
      '<div class="modal-footer">' +
      '<button class="btn btn-light" data-dismiss="modal">' + esc(opts.cancelText || "Tutup") + "</button>" +
      '<button class="btn ' + (opts.okClass || "btn-primary") + ' pd-ok">' + esc(opts.okText || "Simpan") + "</button>" +
      "</div>";

    holder.insertAdjacentHTML("beforeend",
      '<div class="modal fade" id="' + id + '" tabindex="-1" role="dialog">' +
      '<div class="modal-dialog ' + esc(opts.size || "") + ' modal-dialog-centered" role="document"><div class="modal-content">' +
      '<div class="modal-header"><h5 class="modal-title">' + esc(opts.title || "") + "</h5>" +
      '<button type="button" class="close" data-dismiss="modal" aria-label="Tutup"><span aria-hidden="true">&times;</span></button></div>' +
      '<div class="modal-body">' + (opts.body || "") + "</div>" + footer +
      "</div></div></div>");

    const node = document.getElementById(id);
    const instance = window.jQuery(node).modal({ backdrop: "static", keyboard: false });
    window.jQuery(node).on("hidden.bs.modal", () => { window.jQuery(node).modal("dispose"); node.remove(); if (opts.onClose) opts.onClose(); });

    const okBtn = node.querySelector(".pd-ok");
    if (okBtn) {
      okBtn.addEventListener("click", async () => {
        if (!opts.onOk) { instance.hide(); return; }
        const label = okBtn.innerHTML;
        okBtn.disabled = true;
        okBtn.innerHTML = '<span class="spinner-border spinner-border-sm mr-1"></span>Memproses…';
        try {
          const keepOpen = await opts.onOk({ node, close: () => instance.hide() });
          if (keepOpen !== false) instance.hide();
        } catch (err) {
          toast(err && err.message ? err.message : "Gagal menyimpan.", "err");
        } finally {
          okBtn.disabled = false;
          okBtn.innerHTML = label;
        }
      });
    }
    if (opts.onShown) setTimeout(() => opts.onShown(node), 200);
    return { node, close: () => instance.hide() };
  }

  function confirmDialog(message, opts) {
    opts = opts || {};
    return new Promise((resolve) => {
      const m = modal({
        title: opts.title || "Konfirmasi",
        body: '<p class="mb-0">' + esc(message) + "</p>",
        okText: opts.okText || "Ya, lanjutkan",
        okClass: opts.danger ? "btn-danger" : "btn-primary",
        onOk: () => { resolve(true); return true; },
        onClose: () => resolve(false),
      });
      return m;
    });
  }

  function codeModal(title, code, note) {
    const m = modal({
      title: title,
      hideFooter: true,
      body: '<p class="text-muted small mb-2">' + esc(note || "Berikan kode ini kepada petugas. Kode hanya ditampilkan sekali di sini.") + "</p>" +
        '<div class="pd-code">' + esc(code) + "</div>" +
        '<div class="text-right mt-3"><button class="btn btn-sm btn-outline-secondary pd-copy"><i class="far fa-copy mr-1"></i>Salin kode</button></div>',
    });
    const btn = m.node.querySelector(".pd-copy");
    if (btn) btn.addEventListener("click", async () => {
      try { await navigator.clipboard.writeText(code); toast("Kode disalin."); }
      catch { toast("Tidak bisa menyalin, catat manual.", "info"); }
    });
  }

  /* ------------------------------- tabel ------------------------------- */
  function table(container, cfg) {
    const el = typeof container === "string" ? document.querySelector(container) : container;
    if (!el) return null;
    const rows = cfg.rows || [];
    if (rows.length === 0) {
      el.innerHTML = '<div class="pd-empty"><i class="far fa-folder-open mr-2"></i>' + esc(cfg.empty || "Belum ada data.") + "</div>";
      return null;
    }
    const head = cfg.columns.map((c) => '<th class="' + (c.className || "") + '">' + esc(c.label) + "</th>").join("");
    const body = rows.map((r) => "<tr>" + cfg.columns.map((c) => '<td class="' + (c.className || "") + '">' + (c.render ? c.render(r) : esc(r[c.key] === null || r[c.key] === undefined ? "—" : r[c.key])) + "</td>").join("") + "</tr>").join("");
    el.innerHTML = '<div class="table-responsive"><table class="table table-sm table-hover table-striped w-100"><thead><tr>' + head + "</tr></thead><tbody>" + body + "</tbody></table></div>";

    const jq = window.jQuery;
    const tbl = el.querySelector("table");
    if (jq && jq.fn && jq.fn.dataTable && tbl && !el.dataset.plain) {
      return jq(tbl).DataTable({
        order: cfg.order || [],
        pageLength: 25,
        lengthMenu: [[10, 25, 50, -1], [10, 25, 50, "Semua"]],
        responsive: true,
        autoWidth: false,
        dom: (cfg.buttons && cfg.buttons.length ? "B" : "") + "frtip",
        buttons: cfg.buttons || [],
        language: {
          search: "Cari:",
          lengthMenu: "_MENU_ baris",
          info: "_START_–_END_ dari _TOTAL_",
          infoEmpty: "Kosong",
          infoFiltered: "(disaring dari _MAX_)",
          zeroRecords: "Tidak ada data yang cocok",
          paginate: { previous: "‹", next: "›" },
        },
      });
    }
    return null;
  }

  function loading(el, text) {
    if (el) el.innerHTML = '<div class="text-center py-5 text-muted"><div class="spinner-border text-success mb-2"></div><div>' + esc(text || "Memuat data…") + "</div></div>";
  }
  function errorBox(el, err) {
    if (el) el.innerHTML = '<div class="alert alert-danger mb-0"><i class="fas fa-exclamation-triangle mr-2"></i>' + esc(err && err.message ? err.message : "Gagal memuat data.") + "</div>";
  }

  window.pd = { esc, fmtDate, fmtDateTime, fmtTime, ago, jktDate, roleLabel, badge, statusBadge, toast, modal, confirmDialog, codeModal, table, loading, errorBox };
})();
