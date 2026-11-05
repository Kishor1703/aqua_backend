/** Typed query/path parameter helpers. Bad input -> 400 (never a 500). */
const { BadRequestError } = require('./errors');
const { parseDate } = require('./dates');

const first = (v) => (Array.isArray(v) ? v[0] : v);
const isEmpty = (v) => v === undefined || v === null || String(v).trim() === '';

function parseId(value, name = 'id') {
  const s = String(value);
  if (!/^\d{1,15}$/.test(s) || Number(s) < 1) throw new BadRequestError(`Invalid ${name}: '${s}'`);
  return Number(s);
}

function optId(query, name) {
  const v = first(query[name]);
  return isEmpty(v) ? null : parseId(v, name);
}

function optString(query, name, fallback = '') {
  const v = first(query[name]);
  return isEmpty(v) ? fallback : String(v);
}

function optEnum(query, name, allowed) {
  const v = first(query[name]);
  if (isEmpty(v)) return null;
  const upper = String(v).trim().toUpperCase();
  if (!allowed.includes(upper)) {
    throw new BadRequestError(`Invalid value for parameter '${name}'. Allowed values: ${allowed.join(', ')}`);
  }
  return upper;
}

function optEnumDefault(query, name, allowed, fallback) {
  return optEnum(query, name, allowed) ?? fallback;
}

function reqEnum(query, name, allowed) {
  const v = optEnum(query, name, allowed);
  if (v === null) throw new BadRequestError(`Required parameter '${name}' is missing`);
  return v;
}

function optDate(query, name) {
  const v = first(query[name]);
  if (isEmpty(v)) return null;
  if (!parseDate(String(v).trim())) throw new BadRequestError(`Invalid value for parameter '${name}'. Expected YYYY-MM-DD`);
  return String(v).trim();
}

function optBool(query, name, fallback) {
  const v = first(query[name]);
  if (isEmpty(v)) return fallback;
  const s = String(v).trim().toLowerCase();
  if (s === 'true' || s === '1') return true;
  if (s === 'false' || s === '0') return false;
  throw new BadRequestError(`Invalid value for parameter '${name}'. Expected true or false`);
}

function reqBool(query, name) {
  const v = optBool(query, name, null);
  if (v === null) throw new BadRequestError(`Required parameter '${name}' is missing`);
  return v;
}

module.exports = { parseId, optId, optString, optEnum, optEnumDefault, reqEnum, optDate, optBool, reqBool };
