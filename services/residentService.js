const crypto = require('crypto');
const { query, withTransaction } = require('../db/pool');
const { Where, escapeLike } = require('../utils/sqlWhere');
const { pagedResponse } = require('../utils/pagination');
const { parseRecords, toCsv } = require('../utils/csv');
const { NotFoundError, DuplicateResourceError, BadRequestError } = require('../utils/errors');
const { NOTIFICATION_PREFERENCES } = require('../utils/enums');
const { wardScope, requireWardAccess } = require('./userAccess');
const audit = require('./auditLog');

const AUTO_IMPORTED_ZONE = 'Imported';
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // no O/0/I/1 ambiguity
const PHONE_RE = /^[+]?[0-9]{10,15}$/;
const IMPORT_EMAIL_RE = /^[^\s@,;]+@[^\s@,;]+\.[^\s@,;]+$/;

const RESIDENT_SELECT = `
  SELECT r.*, a.name AS area_name, a.ward AS area_ward, a.zone AS area_zone, a.street AS area_street
  FROM residents r JOIN areas a ON a.id = r.area_id`;

const SORTABLE = {
  id: 'r.id', ownerName: 'r.owner_name', houseNumber: 'r.house_number', residentCode: 'r.resident_code',
  phoneNumber: 'r.phone_number', email: 'r.email', status: 'r.status',
  createdAt: 'r.created_at', updatedAt: 'r.updated_at', 'area.name': 'a.name',
};

const blankToNull = (v) => (v === null || v === undefined || String(v).trim() === '' ? null : v);

const mapResident = (r) => ({
  id: r.id,
  residentCode: r.resident_code,
  houseNumber: r.house_number,
  ownerName: r.owner_name,
  phoneNumber: r.phone_number,
  alternatePhoneNumber: r.alternate_phone_number,
  email: r.email,
  areaId: r.area_id,
  areaName: r.area_name,
  notificationPreference: r.notification_preference,
  status: r.status,
  preferredLanguage: r.preferred_language,
  createdAt: r.created_at,
  updatedAt: r.updated_at,
});

/** Human-scannable code like RES-A3F9K2, independent of the DB id. */
async function generateResidentCode(client) {
  for (let attempt = 0; attempt < 10; attempt++) {
    let suffix = '';
    for (let i = 0; i < 6; i++) suffix += CODE_ALPHABET[crypto.randomInt(CODE_ALPHABET.length)];
    const code = `RES-${suffix}`;
    const { rowCount } = await query('SELECT 1 FROM residents WHERE resident_code = $1', [code], client);
    if (rowCount === 0) return code;
  }
  throw new Error('Could not generate a unique resident code after 10 attempts');
}

async function findRow(id) {
  const { rows } = await query(`${RESIDENT_SELECT} WHERE r.id = $1`, [id]);
  if (!rows[0]) throw new NotFoundError(`Resident not found: ${id}`);
  return rows[0];
}

async function findArea(id) {
  const { rows } = await query('SELECT * FROM areas WHERE id = $1', [id]);
  if (!rows[0]) throw new NotFoundError(`Area not found: ${id}`);
  return rows[0];
}

async function phoneTaken(phone, areaId, client) {
  const { rowCount } = await query('SELECT 1 FROM residents WHERE phone_number = $1 AND area_id = $2', [phone, areaId], client);
  return rowCount > 0;
}

const dupPhone = (phone) => new DuplicateResourceError(`A resident with phone number ${phone} already exists in this area`);

async function insertResident(data, client) {
  const { rows } = await query(
    `INSERT INTO residents (resident_code, house_number, owner_name, phone_number, alternate_phone_number,
                            email, area_id, notification_preference, status, preferred_language)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, 'ACTIVE', $9) RETURNING id`,
    [data.residentCode, data.houseNumber, data.ownerName, data.phoneNumber, data.alternatePhoneNumber,
     data.email, data.areaId, data.notificationPreference, data.preferredLanguage],
    client
  );
  return rows[0].id;
}

async function create(actor, req) {
  const area = await findArea(req.areaId);
  requireWardAccess(actor, area.ward);
  if (await phoneTaken(req.phoneNumber, area.id)) throw dupPhone(req.phoneNumber);

  const id = await insertResident({
    residentCode: await generateResidentCode(),
    houseNumber: req.houseNumber,
    ownerName: req.ownerName,
    phoneNumber: req.phoneNumber,
    alternatePhoneNumber: blankToNull(req.alternatePhoneNumber),
    email: blankToNull(req.email),
    areaId: area.id,
    notificationPreference: req.notificationPreference,
    preferredLanguage: blankToNull(req.preferredLanguage) || 'en',
  });
  const saved = mapResident(await findRow(id));
  audit.log(actor, 'RESIDENT_CREATED', 'Resident', id, `Added resident ${saved.ownerName} (${saved.residentCode})`);
  return saved;
}

async function update(actor, id, req) {
  const resident = await findRow(id);
  const area = await findArea(req.areaId);
  requireWardAccess(actor, resident.area_ward);
  requireWardAccess(actor, area.ward);

  const changed = resident.phone_number !== req.phoneNumber || resident.area_id !== area.id;
  if (changed && (await phoneTaken(req.phoneNumber, area.id))) throw dupPhone(req.phoneNumber);

  await query(
    `UPDATE residents
     SET house_number = $1, owner_name = $2, phone_number = $3, alternate_phone_number = $4, email = $5,
         area_id = $6, notification_preference = $7, preferred_language = COALESCE($8, preferred_language),
         updated_at = now()
     WHERE id = $9`,
    [req.houseNumber, req.ownerName, req.phoneNumber, blankToNull(req.alternatePhoneNumber), blankToNull(req.email),
     area.id, req.notificationPreference, blankToNull(req.preferredLanguage), id]
  );
  const saved = mapResident(await findRow(id));
  audit.log(actor, 'RESIDENT_UPDATED', 'Resident', id, `Updated resident ${saved.residentCode}`);
  return saved;
}

async function setStatus(actor, id, status) {
  const resident = await findRow(id);
  requireWardAccess(actor, resident.area_ward);
  await query('UPDATE residents SET status = $1, updated_at = now() WHERE id = $2', [status, id]);
  audit.log(actor, 'RESIDENT_STATUS_CHANGED', 'Resident', id, `Resident ${resident.resident_code} set to ${status}`);
}

async function remove(actor, id) {
  const resident = await findRow(id);
  requireWardAccess(actor, resident.area_ward);
  await query('DELETE FROM residents WHERE id = $1', [id]);
  audit.log(actor, 'RESIDENT_DELETED', 'Resident', id, `Deleted resident ${resident.owner_name} (${resident.resident_code})`);
}

async function getById(actor, id) {
  const resident = await findRow(id);
  requireWardAccess(actor, resident.area_ward);
  return mapResident(resident);
}

function filters({ search, areaId, status }, ward) {
  const where = new Where();
  if (search && search.trim()) {
    const p = where.param(`%${escapeLike(search)}%`);
    const like = (col) => `${col} ILIKE ${p} ESCAPE '\\'`;
    where.add(
      `(${like('r.owner_name')} OR ${like(`COALESCE(r.email, '')`)} OR ${like('r.house_number')} OR ${like('r.phone_number')} OR ${like('r.resident_code')})`
    );
  }
  if (areaId) where.add(`r.area_id = ${where.param(areaId)}`);
  if (status) where.add(`r.status = ${where.param(status)}`);
  if (ward) where.add(`LOWER(a.ward) = LOWER(${where.param(ward)})`);
  return where;
}

async function search(actor, criteria, pageable) {
  const where = filters(criteria, wardScope(actor));
  const total = (
    await query(`SELECT COUNT(*) AS c FROM residents r JOIN areas a ON a.id = r.area_id ${where.sql()}`, where.params)
  ).rows[0].c;
  const { rows } = await query(
    `${RESIDENT_SELECT} ${where.sql()} ORDER BY ${pageable.orderBy} LIMIT ${pageable.size} OFFSET ${pageable.offset}`,
    where.params
  );
  return pagedResponse(rows.map(mapResident), total, pageable);
}

const EXPORT_HEADER = [
  'residentCode', 'houseNumber', 'ownerName', 'phoneNumber', 'alternatePhoneNumber', 'email',
  'areaName', 'ward', 'zone', 'street', 'notificationPreference', 'status', 'preferredLanguage',
];

async function exportCsv(actor, { areaId, status }) {
  const where = filters({ areaId, status }, wardScope(actor));
  const { rows } = await query(`${RESIDENT_SELECT} ${where.sql()} ORDER BY a.name, r.house_number`, where.params);
  const lines = rows.map((r) => [
    r.resident_code, r.house_number, r.owner_name, r.phone_number, r.alternate_phone_number, r.email,
    r.area_name, r.area_ward, r.area_zone, r.area_street, r.notification_preference, r.status, r.preferred_language,
  ]);
  return Buffer.from(toCsv([EXPORT_HEADER, ...lines]), 'utf8');
}

// ---------------------------------------------------------------- CSV import

const required = (record, column) => {
  const value = record.isSet(column) ? record.get(column) : null;
  if (!value) throw new BadRequestError(`Missing required column: ${column}`);
  return value;
};
const optional = (record, column) => (record.isSet(column) ? record.get(column) || null : null);

function parsePreference(record) {
  const raw = optional(record, 'notificationPreference');
  if (!raw) return 'SMS';
  const upper = raw.toUpperCase();
  if (!NOTIFICATION_PREFERENCES.includes(upper)) {
    throw new BadRequestError(`Invalid notificationPreference '${raw}' - expected one of ${NOTIFICATION_PREFERENCES.join(', ')}`);
  }
  return upper;
}

async function findOrCreateArea(client, record, areasByKey, actor, operatorWard, areaName, ward, audits, createdKeys) {
  const key = `${areaName}|${ward}`.toLowerCase();
  const existing = areasByKey.get(key);
  if (existing) return existing;
  if (operatorWard && ward.toLowerCase() !== operatorWard.toLowerCase()) {
    throw new BadRequestError('Cannot auto-create areas outside your assigned ward');
  }
  const { rows } = await query(
    `INSERT INTO areas (name, ward, zone, street, is_active, created_by)
     VALUES ($1, $2, $3, $4, TRUE, $5) RETURNING *`,
    [areaName, ward, optional(record, 'zone') || AUTO_IMPORTED_ZONE, optional(record, 'street'), actor.id],
    client
  );
  areasByKey.set(key, rows[0]);
  createdKeys.push(key);
  audits.push(['AREA_AUTO_CREATED_FROM_RESIDENT_CSV', 'Area', rows[0].id,
    `Auto-created area '${areaName}' in ward '${ward}' during resident CSV import`]);
  return rows[0];
}

async function importRow(client, record, areasByKey, actor, operatorWard, audits, createdKeys) {
  const houseNumber = required(record, 'houseNumber');
  const ownerName = required(record, 'ownerName');
  const phoneNumber = required(record, 'phoneNumber');
  const areaName = required(record, 'areaName');
  const ward = required(record, 'ward');
  const email = optional(record, 'email');
  const alternatePhoneNumber = optional(record, 'alternatePhoneNumber');

  if (operatorWard && ward.toLowerCase() !== operatorWard.toLowerCase()) {
    throw new BadRequestError(`CSV row ward '${ward}' does not match your assigned ward '${operatorWard}'`);
  }
  if (!PHONE_RE.test(phoneNumber)) throw new BadRequestError(`Invalid phone number format: ${phoneNumber}`);
  if (alternatePhoneNumber && !PHONE_RE.test(alternatePhoneNumber)) {
    throw new BadRequestError(`Invalid alternate phone number format: ${alternatePhoneNumber}`);
  }
  if (email && (!IMPORT_EMAIL_RE.test(email) || email.length > 254)) throw new BadRequestError('Invalid email address');

  const area = await findOrCreateArea(client, record, areasByKey, actor, operatorWard, areaName, ward, audits, createdKeys);
  if (await phoneTaken(phoneNumber, area.id, client)) {
    throw new BadRequestError(`A resident with phone number ${phoneNumber} already exists in this area`);
  }
  const notificationPreference = parsePreference(record);
  await insertResident(
    {
      residentCode: await generateResidentCode(client),
      houseNumber, ownerName, phoneNumber, alternatePhoneNumber, email,
      areaId: area.id,
      notificationPreference,
      preferredLanguage: optional(record, 'preferredLanguage') || 'en',
    },
    client
  );
}

/** Row-level failures roll back only that row (savepoint); everything else commits together. */
async function importCsv(actor, buffer) {
  const operatorWard = wardScope(actor);
  let records;
  try {
    records = parseRecords(buffer.toString('utf8'));
  } catch (err) {
    throw new BadRequestError(`Could not read CSV file: ${err.message}`);
  }

  const errors = [];
  const audits = [];
  let success = 0;

  await withTransaction(async (client) => {
    const { rows: areas } = await query('SELECT * FROM areas', [], client);
    const areasByKey = new Map();
    for (const area of areas) {
      if (operatorWard && area.ward.toLowerCase() !== operatorWard.toLowerCase()) continue;
      const key = `${area.name}|${area.ward}`.toLowerCase();
      if (!areasByKey.has(key)) areasByKey.set(key, area);
    }

    for (let i = 0; i < records.length; i++) {
      const rowNumber = i + 2; // header is row 1
      const rowAudits = [];
      const createdKeys = [];
      await client.query('SAVEPOINT import_row');
      try {
        await importRow(client, records[i], areasByKey, actor, operatorWard, rowAudits, createdKeys);
        await client.query('RELEASE SAVEPOINT import_row');
        audits.push(...rowAudits);
        success++;
      } catch (err) {
        await client.query('ROLLBACK TO SAVEPOINT import_row');
        createdKeys.forEach((k) => areasByKey.delete(k)); // area creation was rolled back too
        const reason = err instanceof BadRequestError ? err.message
          : err.code === '22001' ? 'A value is too long for its column'
          : 'Row could not be saved';
        errors.push({ rowNumber, reason });
      }
    }
  });

  for (const [action, type, id, details] of audits) audit.log(actor, action, type, id, details);
  audit.log(actor, 'RESIDENT_CSV_IMPORT', 'Resident', null,
    `Imported ${success}/${records.length} residents (${errors.length} failed)`);
  return { totalRows: records.length, successCount: success, failureCount: errors.length, errors };
}

module.exports = { create, update, setStatus, remove, getById, search, exportCsv, importCsv, SORTABLE };
