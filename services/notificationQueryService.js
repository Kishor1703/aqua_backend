const { query } = require('../db/pool');
const { Where } = require('../utils/sqlWhere');
const { pagedResponse } = require('../utils/pagination');
const { NotFoundError } = require('../utils/errors');
const { wardScope, requireWardAccess } = require('./userAccess');
const { NOTIFICATION_SELECT, mapNotificationResponse } = require('./notificationStore');

const SORTABLE = {
  id: 'n.id', createdAt: 'n.created_at', sentAt: 'n.sent_at', status: 'n.status',
  channel: 'n.channel', attemptCount: 'n.attempt_count',
};

async function search(actor, { scheduleId, residentId, status, channel }, pageable) {
  const ward = wardScope(actor);
  const where = new Where();
  if (scheduleId) where.add(`n.schedule_id = ${where.param(scheduleId)}`);
  if (residentId) where.add(`n.resident_id = ${where.param(residentId)}`);
  if (status) where.add(`n.status = ${where.param(status)}`);
  if (channel) where.add(`n.channel = ${where.param(channel)}`);
  if (ward) where.add(`LOWER(a.ward) = LOWER(${where.param(ward)})`);

  const from = `FROM notifications n
    JOIN water_schedules s ON s.id = n.schedule_id
    JOIN areas a ON a.id = s.area_id`;
  const total = (await query(`SELECT COUNT(*) AS c ${from} ${where.sql()}`, where.params)).rows[0].c;
  const { rows } = await query(
    `${NOTIFICATION_SELECT} ${where.sql()} ORDER BY ${pageable.orderBy} LIMIT ${pageable.size} OFFSET ${pageable.offset}`,
    where.params
  );
  return pagedResponse(rows.map(mapNotificationResponse), total, pageable);
}

async function getById(actor, id) {
  const { rows } = await query(`${NOTIFICATION_SELECT} WHERE n.id = $1`, [id]);
  if (!rows[0]) throw new NotFoundError(`Notification not found: ${id}`);
  requireWardAccess(actor, rows[0].area_ward);
  return mapNotificationResponse(rows[0]);
}

/** { SENT, FAILED, PENDING, RETRYING } counts, optionally limited to one ward. */
async function countsByStatus(ward) {
  const params = ward ? [ward] : [];
  const { rows } = await query(
    `SELECT n.status, COUNT(*) AS c
     FROM notifications n
     JOIN water_schedules s ON s.id = n.schedule_id
     JOIN areas a ON a.id = s.area_id
     ${ward ? 'WHERE LOWER(a.ward) = LOWER($1)' : ''}
     GROUP BY n.status`,
    params
  );
  const counts = { SENT: 0, FAILED: 0, PENDING: 0, RETRYING: 0 };
  for (const r of rows) counts[r.status] = r.c;
  return counts;
}

async function stats(actor) {
  const c = await countsByStatus(wardScope(actor));
  const attempted = c.SENT + c.FAILED;
  const rate = attempted === 0 ? 0 : (c.SENT * 100) / attempted;
  return {
    totalSent: c.SENT,
    totalFailed: c.FAILED,
    totalPending: c.PENDING,
    totalRetrying: c.RETRYING,
    successRatePercent: Math.round(rate * 10) / 10,
  };
}

module.exports = { search, getById, stats, countsByStatus, SORTABLE };
