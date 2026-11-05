const test = require('node:test');
const assert = require('node:assert/strict');

const { parseRows, parseRecords, toCsv } = require('../src/utils/csv');
const { validate } = require('../src/utils/validate');
const dates = require('../src/utils/dates');
const { parsePageable, pagedResponse } = require('../src/utils/pagination');
const { buildSimplePdf } = require('../src/utils/simplePdf');
const templates = require('../src/notification/templates');
const { Where, escapeLike } = require('../src/utils/sqlWhere');
const schemas = require('../src/validation/schemas');
const { ValidationError, BadRequestError } = require('../src/utils/errors');

// ---------------------------------------------------------------- csv
test('csv: quotes, escaped quotes, embedded newlines, CRLF, BOM, blank lines', () => {
  const text = '\uFEFFa,b,c\r\n1,"x, y","say ""hi"""\r\n\r\n"multi\nline",,z\n';
  assert.deepEqual(parseRows(text), [
    ['a', 'b', 'c'],
    ['1', 'x, y', 'say "hi"'],
    ['multi\nline', '', 'z'],
  ]);
});

test('csv: final row without trailing newline; unterminated quote throws', () => {
  assert.deepEqual(parseRows('a,b\n1,2'), [['a', 'b'], ['1', '2']]);
  assert.throws(() => parseRows('a,"b'), /unterminated/);
});

test('csv: records are case-insensitive, trimmed, isSet/get work', () => {
  const [r] = parseRecords('HouseNumber, OwnerName ,email\n 12A , Ramesh ,\n');
  assert.equal(r.get('houseNumber'), '12A');
  assert.equal(r.get('ownerName'), 'Ramesh');
  assert.equal(r.isSet('email'), true);
  assert.equal(r.get('email'), '');
  assert.equal(r.isSet('zone'), false);
});

test('csv: toCsv round-trips', () => {
  const rows = [['a', 'b,c', 'd"e'], ['', null, 'line\nbreak']];
  const out = toCsv(rows);
  assert.deepEqual(parseRows(out), [['a', 'b,c', 'd"e'], ['', '', 'line\nbreak']]);
});

// ---------------------------------------------------------------- validate
test('validate: login rules report every failing field', () => {
  assert.throws(
    () => validate({ email: 'nope' }, schemas.login),
    (e) => e instanceof ValidationError
      && e.fieldErrors.some((f) => f.field === 'email' && /well-formed/.test(f.message))
      && e.fieldErrors.some((f) => f.field === 'password' && f.message === 'must not be blank')
  );
});

test('validate: resident normalises blank email to null and checks phone', () => {
  const ok = validate({
    houseNumber: '1', ownerName: 'A', phoneNumber: '9876543210', email: '  ', areaId: '5',
    notificationPreference: 'sms',
  }, schemas.resident);
  assert.equal(ok.email, null);
  assert.equal(ok.areaId, 5);
  assert.equal(ok.notificationPreference, 'SMS');
  assert.throws(() => validate({ houseNumber: '1', ownerName: 'A', phoneNumber: '123', areaId: 1, notificationPreference: 'SMS' }, schemas.resident), ValidationError);
});

test('validate: schedule normalises time, enforces min/max and past dates', () => {
  const future = dates.addDays(dates.toLocalDate(), 2);
  const ok = validate({ areaId: 1, releaseDate: future, releaseTime: '06:30', notifyBeforeMins: 60 }, schemas.schedule);
  assert.equal(ok.releaseTime, '06:30:00');
  assert.throws(() => validate({ areaId: 1, releaseDate: future, releaseTime: '06:30', notifyBeforeMins: 2 }, schemas.schedule), ValidationError);
  assert.throws(() => validate({ areaId: 1, releaseDate: '2001-01-01', releaseTime: '06:30', notifyBeforeMins: 60 }, schemas.schedule),
    (e) => e.fieldErrors[0].message === 'Release date cannot be in the past');
  assert.throws(() => validate([], schemas.schedule), BadRequestError);
});

test('validate: operator password/phone rules', () => {
  assert.throws(() => validate({ fullName: 'x', email: 'a@b.co', password: 'short', ward: 'W1', phoneNumber: 'abc' }, schemas.operatorCreate),
    (e) => e.fieldErrors.length === 2);
});

// ---------------------------------------------------------------- dates
test('dates: arithmetic mirrors java.time', () => {
  assert.equal(dates.addDays('2026-02-27', 3), '2026-03-02');
  assert.equal(dates.addMonths('2026-08-31', -6), '2026-02-28');
  assert.equal(dates.addMonths('2026-01-15', -1), '2025-12-15');
  assert.equal(dates.firstOfMonth('2026-11-05'), '2026-11-01');
  assert.equal(dates.parseDate('2026-02-30'), null);
  assert.equal(dates.formatTime12('00:05:00'), '12:05 AM');
  assert.equal(dates.formatTime12('13:45:00'), '1:45 PM');
  assert.equal(dates.monthLabel(2026, 10), 'Nov 2026');
});

test('dates: notify-at is release minus N minutes (crossing midnight)', () => {
  const at = dates.combine('2026-11-05', '00:30:00', 60);
  assert.equal(dates.toLocalDateTime(at), '2026-11-04T23:30:00');
});

// ---------------------------------------------------------------- pagination
test('pagination: defaults, whitelist, direction, bad values', () => {
  const opts = { defaultSize: 20, defaultSort: [['name', 'asc']], sortable: { name: 'a.name', ward: 'a.ward' }, tieBreak: 'a.id ASC' };
  assert.deepEqual(parsePageable({}, opts), { page: 0, size: 20, offset: 0, orderBy: 'a.name ASC, a.id ASC' });
  const p = parsePageable({ page: '2', size: '5', sort: ['ward,desc', 'name'] }, opts);
  assert.equal(p.offset, 10);
  assert.equal(p.orderBy, 'a.ward DESC, a.name ASC, a.id ASC');
  assert.equal(parsePageable({ page: '-1', size: 'abc' }, opts).size, 20);
  assert.throws(() => parsePageable({ sort: 'password;drop table' }, opts), BadRequestError);
  assert.deepEqual(pagedResponse([1], 41, { page: 2, size: 20 }), { content: [1], pageNumber: 2, pageSize: 20, totalElements: 41, totalPages: 3, last: true });
  assert.equal(pagedResponse([], 0, { page: 0, size: 20 }).last, true);
});

// ---------------------------------------------------------------- sql helpers
test('sqlWhere: placeholders and LIKE escaping', () => {
  const w = new Where();
  const a = w.param('x');
  const b = w.param(5);
  w.add(`n = ${a}`).add(`m = ${b}`);
  assert.equal(w.sql(), 'WHERE n = $1 AND m = $2');
  assert.deepEqual(w.params, ['x', 5]);
  assert.equal(escapeLike('50%_off\\'), '50\\%\\_off\\\\');
});

// ---------------------------------------------------------------- pdf
test('pdf: structurally valid, escapes text, xref offsets are exact', () => {
  const buf = buildSimplePdf(['AquaAlert Report', 'Name (test) \\ é']);
  const text = buf.toString('latin1');
  assert.ok(text.startsWith('%PDF-1.4'));
  assert.ok(text.includes('\\(test\\)'));
  assert.ok(text.includes('?')); // non-ASCII replaced
  const xrefPos = Number(/startxref\n(\d+)/.exec(text)[1]);
  assert.equal(text.slice(xrefPos, xrefPos + 4), 'xref');
  const offsets = [...text.matchAll(/(\d{10}) 00000 n/g)].map((m) => Number(m[1]));
  offsets.forEach((o, i) => assert.equal(text.slice(o, o + 6), `${i + 1} 0 ob`));
});

// ---------------------------------------------------------------- templates
test('templates: message text', () => {
  const s = { releaseDate: '2026-11-05', releaseTime: '06:30:00', area: { name: 'Gandhi Nagar' } };
  assert.equal(templates.smsText(s), 'Municipal Corporation: Water supply will begin today at 6:30 AM in Gandhi Nagar. Please store water.');
  assert.match(templates.emailText(s), /^Water supply alert\nArea: Gandhi Nagar\nDate: 2026-11-05\nTime: 6:30 AM\nPlease store water\.$/);
  const now = new Date(2026, 10, 5, 5, 30, 0);
  assert.equal(templates.pushBody(s, now), 'Water supply starts in 1 hour.');
  assert.equal(templates.pushBody(s, new Date(2026, 10, 5, 6, 0, 0)), 'Water supply starts in 30 minutes.');
  assert.equal(templates.pushBody(s, new Date(2026, 10, 5, 7, 0, 0)), 'Water supply starts now in Gandhi Nagar.');
});
