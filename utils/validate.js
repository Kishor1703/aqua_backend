/**
 * Tiny declarative request validator (stands in for Jakarta Bean Validation).
 * Messages intentionally match Spring's defaults so the frontend sees the same text.
 *
 * validate(body, rules) -> normalised object, or throws ValidationError (400)
 * with {field, message} entries for *every* failing field.
 */
const { BadRequestError, ValidationError } = require('./errors');
const { parseDate, toLocalDate } = require('./dates');

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const TIME_RE = /^([01]\d|2[0-3]):([0-5]\d)(?::([0-5]\d))?(?:\.\d+)?$/;

function checkString(rule, value, fail) {
  if (value === null) {
    if (rule.required) fail('must not be blank');
    return null;
  }
  if (typeof value !== 'string') {
    fail('must be a string');
    return null;
  }
  if (rule.required && value.trim() === '') {
    fail('must not be blank');
    return value;
  }
  if ((rule.max !== undefined && value.length > rule.max) || (rule.min !== undefined && value.length < rule.min)) {
    fail(rule.sizeMessage || `size must be between ${rule.min ?? 0} and ${rule.max ?? 2147483647}`);
  }
  if (rule.email && value !== '' && !EMAIL_RE.test(value)) {
    fail(rule.emailMessage || 'must be a well-formed email address');
  }
  if (rule.pattern && !rule.pattern.test(value)) {
    fail(rule.patternMessage || `must match "${rule.pattern.source}"`);
  }
  return value;
}

function checkInt(rule, value, fail) {
  if (value === null) {
    if (rule.required) fail('must not be null');
    return null;
  }
  let n;
  if (typeof value === 'number' && Number.isInteger(value)) n = value;
  else if (typeof value === 'string' && /^-?\d+$/.test(value.trim())) n = Number(value.trim());
  else {
    fail('must be a whole number');
    return null;
  }
  if (rule.min !== undefined && n < rule.min) fail(`must be greater than or equal to ${rule.min}`);
  if (rule.max !== undefined && n > rule.max) fail(`must be less than or equal to ${rule.max}`);
  return n;
}

function checkEnum(rule, value, fail) {
  if (value === null) {
    if (rule.required) fail('must not be null');
    return null;
  }
  const upper = typeof value === 'string' ? value.trim().toUpperCase() : '';
  if (!rule.values.includes(upper)) {
    fail(`must be one of: ${rule.values.join(', ')}`);
    return null;
  }
  return upper;
}

function checkDate(rule, value, fail) {
  if (value === null) {
    if (rule.required) fail('must not be null');
    return null;
  }
  if (typeof value !== 'string' || !parseDate(value)) {
    fail('must be a valid date (YYYY-MM-DD)');
    return null;
  }
  if (rule.futureOrPresent && value < toLocalDate()) {
    fail(rule.futureMessage || 'must be a date in the present or in the future');
  }
  return value;
}

function checkTime(rule, value, fail) {
  if (value === null) {
    if (rule.required) fail('must not be null');
    return null;
  }
  const match = typeof value === 'string' ? TIME_RE.exec(value.trim()) : null;
  if (!match) {
    fail('must be a valid time (HH:mm or HH:mm:ss)');
    return null;
  }
  return `${match[1]}:${match[2]}:${match[3] || '00'}`;
}

const CHECKS = { string: checkString, long: checkInt, int: checkInt, enum: checkEnum, date: checkDate, time: checkTime };

function validate(body, rules) {
  if (body === null || typeof body !== 'object' || Array.isArray(body)) {
    throw new BadRequestError('Request body must be a JSON object');
  }
  const errors = [];
  const out = {};
  for (const rule of rules) {
    const fail = (message) => errors.push({ field: rule.field, message });
    let value = body[rule.field] === undefined ? null : body[rule.field];
    if (rule.preprocess) value = rule.preprocess(value);
    out[rule.field] = CHECKS[rule.type](rule, value, fail);
  }
  if (errors.length > 0) throw new ValidationError(errors);
  return out;
}

module.exports = { validate, EMAIL_RE };
