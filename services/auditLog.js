const { query } = require('../db/pool');
const logger = require('../utils/logger');

/**
 * Fire-and-forget audit trail entry. Never awaited and never throws, so a logging
 * failure can't break (or slow down) the operation that triggered it.
 */
function log(actor, action, entityType, entityId, details) {
  query(
    'INSERT INTO audit_logs (user_id, action, entity_type, entity_id, details) VALUES ($1, $2, $3, $4, $5)',
    [actor ? actor.id : null, action, entityType ?? null, entityId ?? null, details ?? null]
  ).catch((err) => logger.warn(`Failed to write audit log action=${action} entity=${entityType}/${entityId}: ${err.message}`));
}

module.exports = { log };
