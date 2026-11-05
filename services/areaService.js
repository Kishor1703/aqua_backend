const { query } = require('../db/pool');
const { Where, escapeLike } = require('../utils/sqlWhere');
const { pagedResponse } = require('../utils/pagination');
const { NotFoundError, DuplicateResourceError } = require('../utils/errors');
const { isAdmin, wardScope, requireWardAccess } = require('./userAccess');
const audit = require('./auditLog');

const AREA_SELECT = `
  SELECT a.*, (SELECT COUNT(*) FROM residents r WHERE r.area_id = a.id) AS resident_count
  FROM areas a`;

const SORTABLE = {
  id: 'a.id', name: 'a.name', ward: 'a.ward', zone: 'a.zone', street: 'a.street',
  active: 'a.is_active', createdAt: 'a.created_at', updatedAt: 'a.updated_at',
};

const mapArea = (r) => ({
  id: r.id,
  name: r.name,
  ward: r.ward,
  zone: r.zone,
  street: r.street,
  active: r.is_active,
  residentCount: r.resident_count,
  createdAt: r.created_at,
  updatedAt: r.updated_at,
});

async function findRow(id) {
  const { rows } = await query(`${AREA_SELECT} WHERE a.id = $1`, [id]);
  if (!rows[0]) throw new NotFoundError(`Area not found: ${id}`);
  return rows[0];
}

async function nameTaken(name, ward) {
  const { rowCount } = await query('SELECT 1 FROM areas WHERE LOWER(name) = LOWER($1) AND LOWER(ward) = LOWER($2)', [name, ward]);
  return rowCount > 0;
}

const duplicate = (name, ward) => new DuplicateResourceError(`An area named '${name}' already exists in ward '${ward}'`);

async function create(actor, req) {
  requireWardAccess(actor, req.ward);
  if (await nameTaken(req.name, req.ward)) throw duplicate(req.name, req.ward);

  const { rows } = await query(
    'INSERT INTO areas (name, ward, zone, street, is_active, created_by) VALUES ($1, $2, $3, $4, TRUE, $5) RETURNING id',
    [req.name, req.ward, req.zone, req.street, actor.id]
  );
  const saved = mapArea(await findRow(rows[0].id));
  audit.log(actor, 'AREA_CREATED', 'Area', saved.id, `Created area '${saved.name}' (ward: ${saved.ward})`);
  return saved;
}

async function update(actor, id, req) {
  const area = await findRow(id);
  requireWardAccess(actor, area.ward);
  requireWardAccess(actor, req.ward);

  const changed = area.name.toLowerCase() !== req.name.toLowerCase() || area.ward.toLowerCase() !== req.ward.toLowerCase();
  if (changed && (await nameTaken(req.name, req.ward))) throw duplicate(req.name, req.ward);

  await query('UPDATE areas SET name = $1, ward = $2, zone = $3, street = $4, updated_at = now() WHERE id = $5', [
    req.name, req.ward, req.zone, req.street, id,
  ]);
  const saved = mapArea(await findRow(id));
  audit.log(actor, 'AREA_UPDATED', 'Area', id, `Updated area '${saved.name}'`);
  return saved;
}

async function setActive(actor, id, active) {
  const area = await findRow(id);
  requireWardAccess(actor, area.ward);
  await query('UPDATE areas SET is_active = $1, updated_at = now() WHERE id = $2', [active, id]);
  audit.log(actor, active ? 'AREA_ACTIVATED' : 'AREA_DEACTIVATED', 'Area', id, `Area '${area.name}' set to active=${active}`);
}

/** Areas that still have residents are deactivated instead of deleted. */
async function remove(actor, id) {
  const area = await findRow(id);
  requireWardAccess(actor, area.ward);

  if (area.resident_count > 0) {
    await query('UPDATE areas SET is_active = FALSE, updated_at = now() WHERE id = $1', [id]);
    audit.log(actor, 'AREA_SOFT_DELETED', 'Area', id, `Area '${area.name}' has ${area.resident_count} residents - deactivated instead of deleted`);
    return;
  }
  await query('DELETE FROM areas WHERE id = $1', [id]);
  audit.log(actor, 'AREA_DELETED', 'Area', id, `Deleted area '${area.name}'`);
}

async function getById(actor, id) {
  const area = await findRow(id);
  requireWardAccess(actor, area.ward);
  return mapArea(area);
}

async function search(actor, { search: text, activeOnly }, pageable) {
  const ward = isAdmin(actor) ? null : wardScope(actor);
  const where = new Where();
  if (text && text.trim()) {
    const p = where.param(`%${escapeLike(text)}%`);
    where.add(
      `(a.name ILIKE ${p} ESCAPE '\\' OR a.ward ILIKE ${p} ESCAPE '\\' OR a.zone ILIKE ${p} ESCAPE '\\' OR a.street ILIKE ${p} ESCAPE '\\')`
    );
  }
  if (activeOnly) where.add('a.is_active = TRUE');
  if (ward) where.add(`LOWER(a.ward) = LOWER(${where.param(ward)})`);

  const total = (await query(`SELECT COUNT(*) AS c FROM areas a ${where.sql()}`, where.params)).rows[0].c;
  const { rows } = await query(
    `${AREA_SELECT} ${where.sql()} ORDER BY ${pageable.orderBy} LIMIT ${pageable.size} OFFSET ${pageable.offset}`,
    where.params
  );
  return pagedResponse(rows.map(mapArea), total, pageable);
}

module.exports = { create, update, setActive, remove, getById, search, SORTABLE };
