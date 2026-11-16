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

      const text = await res.text();
      let data = {};
      try { data = text ? JSON.parse(text) : {}; } catch { data = { message: text }; }

      if (res.status === 401) {
        api.clear();
        document.dispatchEvent(new CustomEvent("pd:unauthorized"));
        throw new ApiError(data.message || "Sesi berakhir. Silakan masuk lagi.", data.code, 401);
      }
      if (!res.ok) {
        throw new ApiError(data.message || "Permintaan gagal (" + res.status + ").", data.code, res.status);
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
