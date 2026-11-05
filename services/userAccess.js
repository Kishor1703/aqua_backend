/** Ward-scoping rules: admins see everything, operators only their own ward. */
const { ForbiddenError } = require('../utils/errors');

const isAdmin = (user) => user.role === 'ADMIN';
const hasWard = (user) => Boolean(user.ward && user.ward.trim());

/** null for admins (no restriction), the ward name for operators. */
function wardScope(user) {
  if (isAdmin(user)) return null;
  if (!hasWard(user)) throw new ForbiddenError('Operator ward is not configured');
  return user.ward;
}

function requireWardAccess(user, ward) {
  if (isAdmin(user)) return;
  if (!hasWard(user)) throw new ForbiddenError('Operator ward is not configured');
  if (!ward || ward.toLowerCase() !== user.ward.toLowerCase()) {
    throw new ForbiddenError('You do not have access to this ward');
  }
}

module.exports = { isAdmin, wardScope, requireWardAccess };
