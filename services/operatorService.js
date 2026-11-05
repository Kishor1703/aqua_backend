const bcrypt = require('bcryptjs');
const { query } = require('../db/pool');
const userStore = require('./userStore');
const audit = require('./auditLog');
const { DuplicateResourceError } = require('../utils/errors');

const blankToNull = (v) => (v === null || v === undefined || v.trim() === '' ? null : v.trim());

const toResponse = (u) => ({
  id: u.id,
  fullName: u.fullName,
  email: u.email,
  ward: u.ward,
  phoneNumber: u.phoneNumber,
  active: u.active,
  createdAt: u.createdAt,
});

async function create(admin, req) {
  if (await userStore.existsByEmail(req.email)) {
    throw new DuplicateResourceError(`A user with email '${req.email}' already exists`);
  }
  const passwordHash = await bcrypt.hash(req.password, 12);
  const { rows } = await query(
    `INSERT INTO users (full_name, email, password_hash, role, ward, phone_number, is_active)
     VALUES ($1, $2, $3, 'OPERATOR', $4, $5, TRUE) RETURNING *`,
    [req.fullName, req.email.trim(), passwordHash, req.ward.trim(), blankToNull(req.phoneNumber)]
  );
  const saved = userStore.mapUser(rows[0]);
  audit.log(admin, 'OPERATOR_CREATED', 'User', saved.id, `Created operator '${saved.email}'`);
  return toResponse(saved);
}

async function list() {
  return (await userStore.listOperators()).map(toResponse);
}

module.exports = { create, list };
