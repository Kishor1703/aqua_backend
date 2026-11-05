const { query } = require('../db/pool');
const { Where } = require('../utils/sqlWhere');
const { pagedResponse } = require('../utils/pagination');
const { NotFoundError, InvalidStateTransitionError, BadRequestError } = require('../utils/errors');
const { toLocalDate, toLocalDateTime, combine } = require('../utils/dates');
const { wardScope, requireWardAccess } = require('./userAccess');
const audit = require('./auditLog');

const SCHEDULE_SELECT = `
  SELECT s.*, a.name AS area_name, a.ward AS area_ward, u.full_name AS created_by_name
  FROM water_schedules s
  JOIN areas a ON a.id = s.area_id
  LEFT JOIN users u ON u.id = s.created_by`;

const SORTABLE = {
  id: 's.id', releaseDate: 's.release_date', releaseTime: 's.release_time', status: 's.status',
  notifyBeforeMins: 's.notify_before_mins', createdAt: 's.created_at', updatedAt: 's.updated_at',
  'area.name': 'a.name',
};

const ALLOWED_TRANSITIONS = {
  SCHEDULED: ['RELEASED', 'DELAYED', 'CANCELLED'],
  DELAYED: ['SCHEDULED', 'RELEASED', 'CANCELLED'],
  RELEASED: [],
  CANCELLED: [],
};

const mapSchedule = (s) => ({
  id: s.id,
  areaId: s.area_id,
  areaName: s.area_name,
  areaWard: s.area_ward,
  releaseDate: s.release_date,
  releaseTime: s.release_time,
  notifyBeforeMins: s.notify_before_mins,
  notifyAt: toLocalDateTime(combine(s.release_date, s.release_time, s.notify_before_mins)),
  status: s.status,
  notes: s.notes,
  createdByName: s.created_by_name ?? null,
  createdAt: s.created_at,
  updatedAt: s.updated_at,
});

async function findRow(id) {
  const { rows } = await query(`${SCHEDULE_SELECT} WHERE s.id = $1`, [id]);
  if (!rows[0]) throw new NotFoundError(`Schedule not found: ${id}`);
  return rows[0];
}

async function findArea(id) {
  const { rows } = await query('SELECT id, name, ward FROM areas WHERE id = $1', [id]);
  if (!rows[0]) throw new NotFoundError(`Area not found: ${id}`);
  return rows[0];
}

function validateNotInPast(date, time) {
  if (combine(date, time).getTime() < Date.now()) {
    throw new BadRequestError('Release date/time cannot be in the past');
  }
}

async function create(actor, req) {
  const area = await findArea(req.areaId);
  requireWardAccess(actor, area.ward);
  validateNotInPast(req.releaseDate, req.releaseTime);

  const { rows } = await query(
    `INSERT INTO water_schedules (area_id, release_date, release_time, notify_before_mins, notes, status, created_by)
     VALUES ($1, $2, $3, $4, $5, 'SCHEDULED', $6) RETURNING id`,
    [area.id, req.releaseDate, req.releaseTime, req.notifyBeforeMins, req.notes, actor.id]
  );
  const saved = mapSchedule(await findRow(rows[0].id));
  audit.log(actor, 'SCHEDULE_CREATED', 'WaterSchedule', saved.id,
    `Scheduled release for ${area.name} at ${saved.releaseTime} on ${saved.releaseDate}`);
  return saved;
}

async function update(actor, id, req) {
  const schedule = await findRow(id);
  requireWardAccess(actor, schedule.area_ward);
  if (schedule.status === 'RELEASED' || schedule.status === 'CANCELLED') {
    throw new InvalidStateTransitionError(`Cannot edit a schedule that is already ${schedule.status}`);
  }
  const area = await findArea(req.areaId);
  requireWardAccess(actor, area.ward);
  validateNotInPast(req.releaseDate, req.releaseTime);

  // Editing a DELAYED schedule puts it back on the calendar and re-arms its notification.
  const reset = schedule.status === 'DELAYED';
  await query(
    `UPDATE water_schedules
     SET area_id = $1, release_date = $2, release_time = $3, notify_before_mins = $4, notes = $5,
         status = CASE WHEN $7 THEN 'SCHEDULED' ELSE status END,
         notification_triggered_at = CASE WHEN $7 THEN NULL ELSE notification_triggered_at END,
         updated_at = now()
     WHERE id = $6`,
    [area.id, req.releaseDate, req.releaseTime, req.notifyBeforeMins, req.notes, id, reset]
  );
  audit.log(actor, 'SCHEDULE_UPDATED', 'WaterSchedule', id, `Updated schedule for ${area.name}`);
  return mapSchedule(await findRow(id));
}

async function updateStatus(actor, id, newStatus, notes) {
  const schedule = await findRow(id);
  requireWardAccess(actor, schedule.area_ward);
  if (!(ALLOWED_TRANSITIONS[schedule.status] || []).includes(newStatus)) {
    throw new InvalidStateTransitionError(`Cannot move schedule from ${schedule.status} to ${newStatus}`);
  }
  const newNotes = notes && notes.trim() ? notes : null;
  await query(
    `UPDATE water_schedules
     SET status = $1::varchar, notes = COALESCE($2, notes),
         notification_triggered_at = CASE WHEN $1::varchar = 'SCHEDULED' THEN NULL ELSE notification_triggered_at END,
         updated_at = now()
     WHERE id = $3`,
    [newStatus, newNotes, id]
  );
  audit.log(actor, 'SCHEDULE_STATUS_CHANGED', 'WaterSchedule', id, `Status changed to ${newStatus}`);
  return mapSchedule(await findRow(id));
}

async function remove(actor, id) {
  const schedule = await findRow(id);
  requireWardAccess(actor, schedule.area_ward);
  if (schedule.status === 'RELEASED') {
    throw new InvalidStateTransitionError('Cannot delete a schedule that has already been released');
  }
  await query('DELETE FROM water_schedules WHERE id = $1', [id]);
  audit.log(actor, 'SCHEDULE_DELETED', 'WaterSchedule', id, `Deleted schedule ${id}`);
}

async function getById(actor, id) {
  const schedule = await findRow(id);
  requireWardAccess(actor, schedule.area_ward);
  return mapSchedule(schedule);
}

async function search(actor, { areaId, status, fromDate, toDate }, pageable) {
  const ward = wardScope(actor);
  const where = new Where();
  if (areaId) where.add(`s.area_id = ${where.param(areaId)}`);
  if (status) where.add(`s.status = ${where.param(status)}`);
  if (fromDate) where.add(`s.release_date >= ${where.param(fromDate)}`);
  if (toDate) where.add(`s.release_date <= ${where.param(toDate)}`);
  if (ward) where.add(`LOWER(a.ward) = LOWER(${where.param(ward)})`);

  const total = (
    await query(`SELECT COUNT(*) AS c FROM water_schedules s JOIN areas a ON a.id = s.area_id ${where.sql()}`, where.params)
  ).rows[0].c;
  const { rows } = await query(
    `${SCHEDULE_SELECT} ${where.sql()} ORDER BY ${pageable.orderBy} LIMIT ${pageable.size} OFFSET ${pageable.offset}`,
    where.params
  );
  return pagedResponse(rows.map(mapSchedule), total, pageable);
}

async function listForDate(actor, date) {
  const ward = wardScope(actor);
  const params = [date];
  let wardSql = '';
  if (ward) {
    params.push(ward);
    wardSql = 'AND LOWER(a.ward) = LOWER($2)';
  }
  const { rows } = await query(
    `${SCHEDULE_SELECT} WHERE s.release_date = $1 ${wardSql} ORDER BY s.release_time ASC, s.id ASC`,
    params
  );
  return rows.map(mapSchedule);
}

async function listUpcoming(actor, limit) {
  const ward = wardScope(actor);
  const params = [toLocalDate()];
  let wardSql = '';
  if (ward) {
    params.push(ward);
    wardSql = 'AND LOWER(a.ward) = LOWER($2)';
  }
  const { rows } = await query(
    `${SCHEDULE_SELECT}
     WHERE s.status = 'SCHEDULED' AND s.release_date >= $1 ${wardSql}
     ORDER BY s.release_date ASC, s.release_time ASC, s.id ASC LIMIT ${Number(limit)}`,
    params
  );
  return rows.map(mapSchedule);
}

module.exports = { create, update, updateStatus, remove, getById, search, listForDate, listUpcoming, SORTABLE };
