/* Klien API panel. Token disimpan di localStorage, dikirim sebagai Bearer. */
(function () {
  const KEY = "pd_web_token";

  class ApiError extends Error {
    constructor(message, code, status) {
      super(message);
      this.name = "ApiError";
      this.code = code || "ERROR";
      this.status = status || 0;
    }
  }

  const api = {
    token() { return localStorage.getItem(KEY) || ""; },
    setToken(t) { localStorage.setItem(KEY, t); },
    clear() { localStorage.removeItem(KEY); },

    async req(method, path, body) {
      const headers = { accept: "application/json" };
      if (body !== undefined) headers["content-type"] = "application/json";
      const t = api.token();
      if (t) headers.authorization = "Bearer " + t;

      let res;
      try {
        res = await fetch(path, {
          method,
          headers,
          body: body === undefined ? undefined : JSON.stringify(body),
        });
      } catch (e) {
        throw new ApiError("Tidak dapat menghubungi server. Periksa koneksi.", "NETWORK", 0);
      }

      const contentType = res.headers.get("content-type") || "";
      const text = await res.text();
      let data = null;
      try { data = text ? JSON.parse(text) : {}; } catch { data = null; }

      if (res.status === 401) {
        api.clear();
        document.dispatchEvent(new CustomEvent("pd:unauthorized"));
        throw new ApiError((data && data.message) || "Sesi berakhir. Silakan masuk lagi.", data && data.code, 401);
      }
      if (!res.ok) {
        const msg = (data && data.message)
          ? data.message
          : (res.status === 405
            ? "Endpoint API tidak diteruskan ke backend (Nginx 405). Perbarui konfigurasi Nginx."
            : "Permintaan gagal (" + res.status + ").");
        throw new ApiError(msg, data && data.code, res.status);
      }
      if (!data || (!contentType.includes("application/json") && text.trim().startsWith("<"))) {
        throw new ApiError("Server mengembalikan HTML, bukan JSON API. Periksa proxy Nginx untuk /admin/*.", "BAD_GATEWAY", res.status);
      }
      return data;
    },

    get(p) { return api.req("GET", p); },
    post(p, b) { return api.req("POST", p, b === undefined ? {} : b); },
    put(p, b) { return api.req("PUT", p, b === undefined ? {} : b); },
  };

  window.ApiError = ApiError;
  window.api = api;
})();
