const { query } = require('../db/pool');

const mapUser = (r) =>
  r && {
    id: r.id,
    fullName: r.full_name,
    email: r.email,
    passwordHash: r.password_hash,
    role: r.role,
    phoneNumber: r.phone_number,
    ward: r.ward,
    active: r.is_active,
    createdAt: r.created_at,
    updatedAt: r.updated_at,
  };

async function findByEmail(email, client) {
  const { rows } = await query('SELECT * FROM users WHERE LOWER(email) = LOWER($1)', [email], client);
  return mapUser(rows[0]);
}

async function existsByEmail(email) {
  const { rowCount } = await query('SELECT 1 FROM users WHERE LOWER(email) = LOWER($1)', [email]);
  return rowCount > 0;
}

async function listOperators() {
  const { rows } = await query(`SELECT * FROM users WHERE role = 'OPERATOR' ORDER BY created_at DESC`);
  return rows.map(mapUser);
}

module.exports = { mapUser, findByEmail, existsByEmail, listOperators };
