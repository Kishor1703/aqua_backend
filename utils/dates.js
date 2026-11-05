/**
 * Date helpers mirroring java.time semantics:
 *  - "LocalDate"     -> 'YYYY-MM-DD' string
 *  - "LocalTime"     -> 'HH:mm:ss' string
 *  - "LocalDateTime" -> 'YYYY-MM-DDTHH:mm:ss' string
 * "Local" means the process timezone (set APP_TIMEZONE to pin it).
 */
const pad = (n, width = 2) => String(n).padStart(width, '0');
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function toLocalDate(d = new Date()) {
  return `${pad(d.getFullYear(), 4)}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function toLocalDateTime(d) {
  return `${toLocalDate(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

/** Returns {y, m, d} for a valid 'YYYY-MM-DD', otherwise null. */
function parseDate(s) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(s));
  if (!match) return null;
  const y = Number(match[1]);
  const m = Number(match[2]);
  const d = Number(match[3]);
  const dt = new Date(Date.UTC(y, m - 1, d));
  if (dt.getUTCFullYear() !== y || dt.getUTCMonth() !== m - 1 || dt.getUTCDate() !== d) return null;
  return { y, m, d };
}

function fmtUtc(dt) {
  return `${pad(dt.getUTCFullYear(), 4)}-${pad(dt.getUTCMonth() + 1)}-${pad(dt.getUTCDate())}`;
}

function addDays(dateStr, n) {
  const p = parseDate(dateStr);
  return fmtUtc(new Date(Date.UTC(p.y, p.m - 1, p.d + n)));
}

/** Like LocalDate.plusMonths: clamps the day to the target month's length. */
function addMonths(dateStr, n) {
  const p = parseDate(dateStr);
  const total = p.y * 12 + (p.m - 1) + n;
  const y = Math.floor(total / 12);
  const m = total - y * 12;
  const daysInMonth = new Date(Date.UTC(y, m + 1, 0)).getUTCDate();
  return `${pad(y, 4)}-${pad(m + 1)}-${pad(Math.min(p.d, daysInMonth))}`;
}

const firstOfMonth = (dateStr) => `${dateStr.slice(0, 8)}01`;

/** Local midnight at the start of the given date. */
function startOfDay(dateStr) {
  const p = parseDate(dateStr);
  return new Date(p.y, p.m - 1, p.d, 0, 0, 0, 0);
}

/** Local Date for dateStr + timeStr, optionally minus N minutes (wall-clock arithmetic). */
function combine(dateStr, timeStr, minusMinutes = 0) {
  const p = parseDate(dateStr);
  const [hh, mm, ss] = String(timeStr).split(':');
  return new Date(p.y, p.m - 1, p.d, Number(hh), Number(mm) - minusMinutes, parseInt(ss, 10) || 0, 0);
}

/** 'HH:mm:ss' -> 'h:mm AM/PM' (matches DateTimeFormatter "h:mm a"). */
function formatTime12(timeStr) {
  const [hh, mm] = String(timeStr).split(':');
  const hour = Number(hh);
  return `${hour % 12 || 12}:${mm} ${hour >= 12 ? 'PM' : 'AM'}`;
}

const monthLabel = (year, monthIndex) => `${MONTHS[monthIndex]} ${year}`;

module.exports = {
  toLocalDate,
  toLocalDateTime,
  parseDate,
  addDays,
  addMonths,
  firstOfMonth,
  startOfDay,
  combine,
  formatTime12,
  monthLabel,
};
