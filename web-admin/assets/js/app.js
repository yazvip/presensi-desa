/* Kerangka aplikasi: login, sidebar, router hash, profil. */
(function () {
  const state = { me: null };

  function routes() {
    return [
      { path: "/", title: "Ringkasan", icon: "fa-chart-pie", group: "Utama", roles: ["admin_desa", "superadmin"], render: window.Pages.dashboard },
      { path: "/desa", title: "Desa & Pengelola", icon: "fa-landmark", group: "Manajemen", roles: ["superadmin"], render: window.Pages.desa },
      { path: "/staff", title: "Petugas", icon: "fa-users", group: "Operasional", roles: ["admin_desa"], render: window.Pages.staff },
      { path: "/perangkat", title: "Perangkat Terikat", icon: "fa-mobile-alt", group: "Operasional", roles: ["admin_desa", "superadmin"], render: window.Pages.devices },
      { path: "/lokasi", title: "Lokasi & QR", icon: "fa-qrcode", group: "Operasional", roles: ["admin_desa"], render: window.Pages.lokasi },
      { path: "/jadwal", title: "Jam Kerja", icon: "fa-clock", group: "Operasional", roles: ["admin_desa"], render: window.Pages.jadwal },
      { path: "/rekap", title: "Rekap Absensi", icon: "fa-table", group: "Laporan & Keamanan", roles: ["admin_desa"], render: window.Pages.rekap },
      { path: "/cuti", title: "Izin & Cuti", icon: "fa-envelope-open-text", group: "Laporan & Keamanan", roles: ["admin_desa"], render: window.Pages.cuti },
      { path: "/anomali", title: "Anomali", icon: "fa-exclamation-triangle", group: "Laporan & Keamanan", roles: ["admin_desa"], render: window.Pages.anomali },
      { path: "/integritas", title: "Integritas Data", icon: "fa-shield-alt", group: "Laporan & Keamanan", roles: ["admin_desa", "superadmin"], render: window.Pages.integritas },
      { path: "/audit", title: "Audit Log", icon: "fa-clipboard-list", group: "Laporan & Keamanan", roles: ["superadmin"], render: window.Pages.audit },
      { path: "/profil", title: "Profil & PIN", icon: "fa-id-card", group: "Akun", roles: ["admin_desa", "superadmin"], render: window.Pages.profil },
    ];
  }

  function visibleRoutes() {
    const role = state.me && state.me.user ? state.me.user.role : "";
    return routes().filter((r) => !r.roles || r.roles.indexOf(role) >= 0);
  }

  function buildSidebar() {
    const nav = document.getElementById("sidebar-nav");
    const current = currentPath();
    let lastGroup = "";
    nav.innerHTML = visibleRoutes().map((r) => {
      let header = "";
      if (r.group && r.group !== lastGroup) {
        lastGroup = r.group;
        header = '<li class="nav-header">' + pd.esc(r.group) + "</li>";
      }
      return header +
        '<li class="nav-item">' +
        '<a class="nav-link' + (r.path === current ? " active" : "") + '" href="#' + r.path + '">' +
        '<i class="nav-icon fas ' + r.icon + '"></i><p>' + pd.esc(r.title) + "</p></a></li>";
    }).join("");
  }

  function currentPath() {
    const h = (location.hash || "#/").replace(/^#/, "");
    return h.startsWith("/") ? h.split("?")[0] : "/";
  }

  async function renderRoute() {
    if (!state.me) return;
    const path = currentPath();
    const list = routes();
    const route = list.filter((r) => r.path === path)[0] || list[0];
    const allowed = !route.roles || route.roles.indexOf(state.me.user.role) >= 0;

    document.getElementById("nav-title").textContent = route.title;
    document.title = route.title + " — Presensi Desa";
    buildSidebar();

    const host = document.getElementById("page");
    if (!allowed) {
      host.innerHTML = '<div class="alert alert-warning"><i class="fas fa-lock mr-2"></i>Halaman ini hanya untuk peran lain.</div>';
      return;
    }
    pd.loading(host);
    try {
      await route.render(host);
    } catch (err) {
      pd.errorBox(host, err);
    }
  }

  function startClock() {
    const el = document.getElementById("nav-clock");
    const tick = () => {
      el.textContent = new Intl.DateTimeFormat("id-ID", {
        timeZone: "Asia/Jakarta", weekday: "long", day: "2-digit", month: "short",
        hour: "2-digit", minute: "2-digit",
      }).format(new Date()) + " WIB";
    };
    tick();
    setInterval(tick, 30000);
  }

  function showApp() {
    document.getElementById("login-view").classList.add("d-none");
    document.getElementById("app-view").classList.remove("d-none");
    document.getElementById("nav-user").textContent = state.me.user.nama;
    document.getElementById("footer-village").textContent =
      state.me.user.village_nama ? state.me.user.village_nama + " · " + (state.me.user.kecamatan || "") : "Superadmin";
    buildSidebar();
    startClock();
    window.APP = { me: state.me, reload: loadMe };
    if (window.jQuery && jQuery.fn && jQuery.fn.dataTable) {
      jQuery.fn.dataTable.defaults.retrieve = false;
    }
    renderRoute();
  }

  function showLogin() {
    state.me = null;
    window.APP = { me: null };
    document.getElementById("app-view").classList.add("d-none");
    document.getElementById("login-view").classList.remove("d-none");
    const nipd = document.getElementById("login-nipd");
    if (nipd) nipd.focus();
  }

  async function loadMe() {
    const me = await api.get("/auth/me");
    state.me = me;
    return me;
  }

  async function onSubmit(e) {
    e.preventDefault();
    const btn = document.getElementById("login-submit");
    const errBox = document.getElementById("login-error");
    const nipd = document.getElementById("login-nipd").value.trim();
    const pin = document.getElementById("login-pin").value.trim();
    errBox.classList.add("d-none");
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm mr-2"></span>Memeriksa…';
    try {
      const res = await api.post("/auth/web-login", { nipd, pin });
      api.setToken(res.accessToken);
      await loadMe();
      showApp();
      document.getElementById("login-pin").value = "";
    } catch (err) {
      errBox.textContent = err.message || "Gagal masuk.";
      errBox.classList.remove("d-none");
    } finally {
      btn.disabled = false;
      btn.innerHTML = '<i class="fas fa-sign-in-alt mr-2"></i>Masuk ke Panel';
    }
  }

  function boot() {
    document.getElementById("login-form").addEventListener("submit", onSubmit);
    document.getElementById("btn-logout").addEventListener("click", async (e) => {
      e.preventDefault();
      await api.post("/auth/logout").catch(() => {});
      api.clear();
      location.hash = "#/";
      location.reload();
    });
    window.addEventListener("hashchange", () => { renderRoute(); });
    document.addEventListener("pd:unauthorized", () => {
      showLogin();
      const box = document.getElementById("login-error");
      box.textContent = "Sesi berakhir. Silakan masuk lagi.";
      box.classList.remove("d-none");
    });

    if (api.token()) {
      loadMe().then(showApp).catch(() => showLogin());
    } else {
      showLogin();
    }
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
  else boot();
})();
