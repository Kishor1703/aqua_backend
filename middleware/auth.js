const { UnauthorizedError, ForbiddenError } = require('../utils/errors');
const jwtService = require('../services/jwtService');
const userStore = require('../services/userStore');

/**
 * Requires a valid *access* token (refresh tokens are rejected) belonging to an
 * active user. Sets req.user to the current DB row so role/ward changes apply immediately.
 */
async function authenticate(req, res, next) {
  try {
    const header = req.headers.authorization;
    if (!header || !header.startsWith('Bearer ')) throw new UnauthorizedError();

    let claims;
    try {
      claims = jwtService.verify(header.slice(7));
    } catch {
      throw new UnauthorizedError('Invalid or expired token');
    }
    if (claims.type !== 'access' || !claims.sub) throw new UnauthorizedError('Invalid or expired token');

    const user = await userStore.findByEmail(claims.sub);
    if (!user || !user.active) throw new UnauthorizedError('Invalid or expired token');

    req.user = user;
    next();
  } catch (err) {
    next(err);
  }
}

const requireRole = (role) => (req, res, next) =>
  req.user && req.user.role === role ? next() : next(new ForbiddenError());

module.exports = { authenticate, requireRole };
