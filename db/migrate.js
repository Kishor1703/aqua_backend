/**
 * Minimal SQL migration runner (replaces Flyway). Applies migrations/V<n>__*.sql in
 * order, each inside its own transaction, and records them in schema_migrations.
 *
 * If the database was previously managed by Flyway, versions already recorded in
 * flyway_schema_history are adopted as applied - nothing is re-run.
 */
const fs = require('fs');
const path = require('path');
const { getPool, closePool } = require('./pool');
const logger = require('../utils/logger');

const LOCK_ID = 727274; // arbitrary app-wide advisory lock id

function listMigrations(dir) {
  return fs
    .readdirSync(dir)
    .map((file) => ({ file, match: /^V(\d+)__(.+)\.sql$/.exec(file) }))
    .filter((m) => m.match)
    .map((m) => ({ version: Number(m.match[1]), name: m.match[2], file: m.file }))
    .sort((a, b) => a.version - b.version);
}

async function runMigrations({ dir = path.resolve(__dirname, '../migrations') } = {}) {
  const client = await getPool().connect();
  try {
    await client.query('SELECT pg_advisory_lock($1)', [LOCK_ID]);
    await client.query(`
      CREATE TABLE IF NOT EXISTS schema_migrations (
        version    INTEGER PRIMARY KEY,
        name       TEXT NOT NULL,
        applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
      )`);

    const applied = new Set((await client.query('SELECT version FROM schema_migrations')).rows.map((r) => r.version));

    const flyway = await client.query(`SELECT to_regclass('flyway_schema_history') AS t`);
    if (flyway.rows[0].t) {
      const rows = (
        await client.query('SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL')
      ).rows;
      for (const row of rows) {
        const version = parseInt(row.version, 10);
        if (Number.isFinite(version) && !applied.has(version)) {
          await client.query('INSERT INTO schema_migrations (version, name) VALUES ($1, $2)', [version, 'adopted-from-flyway']);
          applied.add(version);
        }
      }
    }

    for (const migration of listMigrations(dir)) {
      if (applied.has(migration.version)) continue;
      const sql = fs.readFileSync(path.join(dir, migration.file), 'utf8');
      logger.info(`Applying migration V${migration.version} (${migration.name})`);
      try {
        await client.query('BEGIN');
        await client.query(sql);
        await client.query('INSERT INTO schema_migrations (version, name) VALUES ($1, $2)', [migration.version, migration.name]);
        await client.query('COMMIT');
      } catch (err) {
        await client.query('ROLLBACK');
        throw new Error(`Migration ${migration.file} failed: ${err.message}`);
      }
    }
  } finally {
    try {
      await client.query('SELECT pg_advisory_unlock($1)', [LOCK_ID]);
    } finally {
      client.release();
    }
  }
}

module.exports = { runMigrations, listMigrations };

if (require.main === module) {
  require('../config').validateConfig();
  runMigrations()
    .then(() => logger.info('Migrations complete'))
    .catch((err) => {
      logger.error('Migration failed', err);
      process.exitCode = 1;
    })
    .finally(closePool);
}
