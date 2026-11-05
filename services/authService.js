const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const { query, withTransaction } = require('../db/pool');
const jwtService = require('./jwtService');
const userStore = require('./userStore');
const dispatch = require('./notificationDispatchService');
const { InvalidCredentialsError, NotFoundError } = require('../utils/errors');
const logger = require('../utils/logger');

const BCRYPT_ROUNDS = 12;
const RESET_TOKEN_TTL_MS = 30 * 60 * 1000;

// Compared against when the email is unknown so response time doesn't reveal which emails exist.
let dummyHashPromise;
const dummyHash = () => (dummyHashPromise ||= bcrypt.hash('not-a-real-password', BCRYPT_ROUNDS));

const sha256 = (value) => crypto.createHash('sha256').update(value, 'utf8').digest('base64');

const summary = (u) => ({ id: u.id, fullName: u.fullName, email: u.email, role: u.role, ward: u.ward });

const buildLoginResponse = (user) => ({
  accessToken: jwtService.generateAccessToken(user),
  refreshToken: jwtService.generateRefreshToken(user),
  tokenType: 'Bearer',
  user: summary(user),
});

async function login({ email, password }) {
  const user = await userStore.findByEmail(email);
  const ok = await bcrypt.compare(password, user ? user.passwordHash : await dummyHash());
  if (!user || !ok || !user.active) throw new InvalidCredentialsError();
  return buildLoginResponse(user);
}

async function refresh({ refreshToken }) {
  let claims;
  try {
    claims = jwtService.verify(refreshToken);
  } catch {
    throw new InvalidCredentialsError();
  }
  if (claims.type !== 'refresh' || !claims.sub) throw new InvalidCredentialsError();
  const user = await userStore.findByEmail(claims.sub);
  if (!user || !user.active) throw new InvalidCredentialsError();
  return buildLoginResponse(user);
}

/** Behaves identically whether or not the email exists (no user-enumeration oracle). */
async function forgotPassword({ email }) {
  const user = await userStore.findByEmail(email);
  if (!user) return;

  const rawToken = crypto.randomBytes(32).toString('base64url');
  await query('INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) VALUES ($1, $2, $3)', [
    user.id,
    sha256(rawToken),
    new Date(Date.now() + RESET_TOKEN_TTL_MS),
  ]);

  // Don't make the caller wait on SMTP (also keeps timing uniform).
  dispatch.sendPasswordResetLink(user, rawToken).catch((err) => logger.warn(`Password reset email failed: ${err.message}`));
}

async function resetPassword({ token, newPassword }) {
  const passwordHash = await bcrypt.hash(newPassword, BCRYPT_ROUNDS);
  await withTransaction(async (client) => {
    // Atomic single-use claim: only one concurrent request can consume the token.
    const claimed = await query(
      `UPDATE password_reset_tokens SET used_at = now()
       WHERE token_hash = $1 AND used_at IS NULL AND expires_at > now()
       RETURNING user_id`,
      [sha256(token)],
      client
    );
    if (claimed.rowCount === 0) throw new NotFoundError('Invalid or expired reset link');
    await query('UPDATE users SET password_hash = $1, updated_at = now() WHERE id = $2', [passwordHash, claimed.rows[0].user_id], client);
  });
}

module.exports = { login, refresh, forgotPassword, resetPassword };
