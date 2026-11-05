const { Pool, types } = require('pg');
const config = require('../config');
const logger = require('../utils/logger');

// COUNT(*) etc. come back as int8 -> parse to Number; DATE/TIME stay plain strings
// ('2026-11-03', '10:30:00') so they never shift with timezones.
types.setTypeParser(20, (v) => Number(v));
types.setTypeParser(1082, (v) => v);
types.setTypeParser(1083, (v) => v);

let pool;

function getPool() {
  if (!pool) {
    pool = new Pool({
      connectionString: config.db.url,
      user: config.db.user,
      password: config.db.password,
      max: config.db.poolMax,
      min: config.db.poolMin,
      ssl: config.db.ssl ? { rejectUnauthorized: false } : undefined,
      connectionTimeoutMillis: 10000,
      idleTimeoutMillis: 30000,
    });
    pool.on('error', (err) => logger.error('Unexpected PostgreSQL pool error', err));
  }
  return pool;
}

/** Runs a query on the given client (inside a transaction) or on the pool. */
const query = (text, params, client) => (client || getPool()).query(text, params);

/** Runs fn(client) inside BEGIN/COMMIT, rolling back on any error. */
async function withTransaction(fn) {
  const client = await getPool().connect();
  try {
    await client.query('BEGIN');
    const result = await fn(client);
    await client.query('COMMIT');
    return result;
  } catch (err) {
    try {
      await client.query('ROLLBACK');
    } catch (rollbackErr) {
      logger.error('Rollback failed', rollbackErr);
    }
    throw err;
  } finally {
    client.release();
  }
}

async function closePool() {
  if (pool) {
    await pool.end();
    pool = undefined;
  }
}

module.exports = { getPool, query, withTransaction, closePool };
