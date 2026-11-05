const path = require('path');
const dotenv = require('dotenv');

// .env.local wins over .env; real environment variables win over both.
dotenv.config({ path: path.resolve(process.cwd(), '.env.local') });
dotenv.config({ path: path.resolve(process.cwd(), '.env') });

if (process.env.APP_TIMEZONE) {
  process.env.TZ = process.env.APP_TIMEZONE;
}

const DEFAULT_JWT_SECRET = 'CHANGE_ME_IN_PROD_min_256_bit_secret_key_______';

const bool = (value, fallback = false) =>
  value === undefined || value === '' ? fallback : String(value).toLowerCase() === 'true';
const int = (value, fallback) => {
  const n = parseInt(value, 10);
  return Number.isFinite(n) ? n : fallback;
};
const list = (value, fallback = '') =>
  (value === undefined || value === '' ? fallback : value)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);

const config = {
  env: process.env.NODE_ENV || 'development',
  port: int(process.env.PORT, 8080),
  runMigrations: bool(process.env.RUN_MIGRATIONS, true),

  db: {
    url: (process.env.DB_URL || process.env.DATABASE_URL || '').replace(/^jdbc:/, ''),
    user: process.env.DB_USERNAME || undefined,
    password: process.env.DB_PASSWORD || undefined,
    ssl: bool(process.env.DB_SSL, false),
    poolMax: 10,
    poolMin: 2,
  },

  frontendUrl: (process.env.AQUAALERT_FRONTEND_URL || 'https://aqua-aleart.vercel.app').replace(/\/+$/, ''),

  jwt: {
    secret: process.env.JWT_SECRET || DEFAULT_JWT_SECRET,
    accessTokenExpiryMinutes: 30,
    refreshTokenExpiryDays: 7,
  },

  cors: {
    allowedOrigins: list(process.env.CORS_ALLOWED_ORIGINS, 'http://localhost:5173,https://aqua-aleart.vercel.app'),
  },

  scheduler: {
    pollIntervalMs: int(process.env.NOTIFICATION_POLL_INTERVAL_MS, 60000),
  },

  mail: {
    host: 'smtp.gmail.com',
    port: 587,
    username: process.env.AQUAALERT_MAIL_USERNAME || '',
    password: process.env.AQUAALERT_MAIL_PASSWORD || '',
  },

  email: {
    enabled: bool(process.env.AQUAALERT_MAIL_ENABLED, false),
    testRecipient: process.env.AQUAALERT_TEST_EMAIL || '',
    from: process.env.AQUAALERT_MAIL_FROM || '',
    senderName: process.env.AQUAALERT_MAIL_SENDER_NAME || 'AquaAlert',
  },
};

/** Fail fast on misconfiguration instead of misbehaving at request time. */
function validateConfig() {
  if (!config.db.url) {
    throw new Error('DB_URL is not set. Example: postgresql://user:pass@localhost:5432/aquaalert');
  }
  if (config.env === 'production') {
    if (config.jwt.secret === DEFAULT_JWT_SECRET || config.jwt.secret.length < 32) {
      throw new Error('JWT_SECRET must be set to a random value of at least 32 characters in production');
    }
  } else if (config.jwt.secret === DEFAULT_JWT_SECRET) {
    require('../utils/logger').warn('Using the default JWT_SECRET - fine for local dev, never for production');
  }
}

module.exports = config;
module.exports.validateConfig = validateConfig;
