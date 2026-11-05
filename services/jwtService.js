const jwt = require('jsonwebtoken');
const config = require('../config');

const ALGORITHM = 'HS256';

function sign(email, claims, expiresInSeconds) {
  return jwt.sign(claims, config.jwt.secret, { algorithm: ALGORITHM, subject: email, expiresIn: expiresInSeconds });
}

const generateAccessToken = (user) =>
  sign(user.email, { uid: user.id, role: user.role, type: 'access' }, config.jwt.accessTokenExpiryMinutes * 60);

const generateRefreshToken = (user) =>
  sign(user.email, { uid: user.id, type: 'refresh' }, config.jwt.refreshTokenExpiryDays * 24 * 3600);

/** Verifies signature + expiry. Throws a jsonwebtoken error if invalid. */
const verify = (token) => jwt.verify(token, config.jwt.secret, { algorithms: [ALGORITHM] });

module.exports = { generateAccessToken, generateRefreshToken, verify };
