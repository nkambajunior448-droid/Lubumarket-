const fs = require('node:fs');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');

function createDatabase(dbPath = process.env.SQLITE_DB_PATH || path.join(__dirname, '..', 'data', 'lubumarket_auth.sqlite')) {
  if (dbPath !== ':memory:') {
    const dir = path.dirname(path.resolve(dbPath));
    fs.mkdirSync(dir, { recursive: true });
  }

  const db = new DatabaseSync(dbPath);
  db.exec(`
    PRAGMA journal_mode = WAL;
    PRAGMA foreign_keys = ON;

    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      email TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      is_verified INTEGER NOT NULL DEFAULT 0,
      verification_token_hash TEXT,
      verification_expires_at INTEGER,
      reset_token_hash TEXT,
      reset_expires_at INTEGER,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
    CREATE INDEX IF NOT EXISTS idx_users_verification_token ON users(verification_token_hash);
    CREATE INDEX IF NOT EXISTS idx_users_reset_token ON users(reset_token_hash);
  `);

  const stmts = {
    findByEmail: db.prepare('SELECT * FROM users WHERE email = ?'),
    findById: db.prepare('SELECT id, email, is_verified, created_at, updated_at FROM users WHERE id = ?'),
    findByVerificationToken: db.prepare('SELECT * FROM users WHERE verification_token_hash = ?'),
    findByResetToken: db.prepare('SELECT * FROM users WHERE reset_token_hash = ?'),
    insertUser: db.prepare(`
      INSERT INTO users (
        email, password_hash, is_verified, verification_token_hash, verification_expires_at, created_at, updated_at
      ) VALUES (?, ?, 0, ?, ?, ?, ?)
    `),
    markVerified: db.prepare(`
      UPDATE users
      SET is_verified = 1,
          verification_token_hash = NULL,
          verification_expires_at = NULL,
          updated_at = ?
      WHERE id = ?
    `),
    updateVerificationToken: db.prepare(`
      UPDATE users
      SET verification_token_hash = ?,
          verification_expires_at = ?,
          updated_at = ?
      WHERE id = ?
    `),
    setResetToken: db.prepare(`
      UPDATE users
      SET reset_token_hash = ?,
          reset_expires_at = ?,
          updated_at = ?
      WHERE id = ?
    `),
    updatePasswordAfterReset: db.prepare(`
      UPDATE users
      SET password_hash = ?,
          reset_token_hash = NULL,
          reset_expires_at = NULL,
          updated_at = ?
      WHERE id = ?
    `)
  };

  return { db, stmts };
}

module.exports = { createDatabase };
