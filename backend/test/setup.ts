// Variabel lingkungan default untuk unit test — tidak menyentuh database.
process.env.DATABASE_URL ??= "postgres://test:test@localhost:5432/test";
process.env.JWT_SECRET ??= "test-secret-yang-panjangnya-lebih-dari-32-karakter";
process.env.ACCESS_TTL_SEC ??= "900";
process.env.REFRESH_TTL_DAYS ??= "30";
