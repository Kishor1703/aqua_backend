const { query } = require('../db/pool');
const { wardScope } = require('./userAccess');
const { toCsv } = require('../utils/csv');
const { buildSimplePdf } = require('../utils/simplePdf');
const { toLocalDate, addDays, firstOfMonth, startOfDay } = require('../utils/dates');

/** Java-style double text: 100 -> "100.0". */
const dbl = (n) => (Number.isInteger(n) ? n.toFixed(1) : String(n));

function windowFor(period) {
  const toDate = toLocalDate();
  const fromDate = period === 'DAILY' ? toDate : period === 'WEEKLY' ? addDays(toDate, -6) : firstOfMonth(toDate);
  return {
    fromDate,
    toDate,
    fromInstant: startOfDay(fromDate),
    toInstant: new Date(startOfDay(addDays(toDate, 1)).getTime() - 1),
  };
}

async function build(actor, period) {
  const ward = wardScope(actor);
  const win = windowFor(period);
  const wardSql = (n) => (ward ? ` AND LOWER(a.ward) = LOWER($${n})` : '');
  const wardParams = (...p) => (ward ? [...p, ward] : p);

  const [schedules, notifications, areaCount, residentCount] = await Promise.all([
    query(
      `SELECT s.release_date, s.status FROM water_schedules s JOIN areas a ON a.id = s.area_id
       WHERE s.release_date BETWEEN $1 AND $2${wardSql(3)}`,
      wardParams(win.fromDate, win.toDate)
    ),
    query(
      `SELECT n.created_at, n.status FROM notifications n
       JOIN water_schedules s ON s.id = n.schedule_id JOIN areas a ON a.id = s.area_id
       WHERE n.created_at BETWEEN $1 AND $2${wardSql(3)}`,
      wardParams(win.fromInstant, win.toInstant)
    ),
    query(`SELECT COUNT(*) AS c FROM areas a WHERE a.is_active = TRUE${wardSql(1)}`, wardParams()),
    query(
      `SELECT COUNT(*) AS c FROM residents r JOIN areas a ON a.id = r.area_id WHERE r.status = 'ACTIVE'${wardSql(1)}`,
      wardParams()
    ),
  ]);

  // [scheduled, released, delayed, cancelled, sent, failed] per day, zero-filled
  const byDay = new Map();
  for (let d = win.fromDate; d <= win.toDate; d = addDays(d, 1)) byDay.set(d, [0, 0, 0, 0, 0, 0]);
  const row = (day) => {
    if (!byDay.has(day)) byDay.set(day, [0, 0, 0, 0, 0, 0]);
    return byDay.get(day);
  };

  for (const s of schedules.rows) {
    const r = row(s.release_date);
    r[0]++;
    if (s.status === 'RELEASED') r[1]++;
    else if (s.status === 'DELAYED') r[2]++;
    else if (s.status === 'CANCELLED') r[3]++;
  }
  for (const n of notifications.rows) {
    const r = row(toLocalDate(n.created_at));
    if (n.status === 'SENT') r[4]++;
    else if (n.status === 'FAILED') r[5]++;
  }

  const total = [0, 0, 0, 0, 0, 0];
  const breakdown = [];
  for (const [date, r] of byDay) {
    r.forEach((v, i) => (total[i] += v));
    breakdown.push({
      date, scheduledCount: r[0], releasedCount: r[1], delayedCount: r[2], cancelledCount: r[3],
      notificationsSent: r[4], notificationsFailed: r[5],
    });
  }
  const attempted = total[4] + total[5];
  return {
    summary: {
      period,
      fromDate: win.fromDate,
      toDate: win.toDate,
      totalSchedules: total[0],
      releasedCount: total[1],
      delayedCount: total[2],
      cancelledCount: total[3],
      activeAreas: areaCount.rows[0].c,
      activeResidents: residentCount.rows[0].c,
      notificationsSent: total[4],
      notificationsFailed: total[5],
      successRatePercent: attempted === 0 ? 0 : Math.round((total[4] * 1000) / attempted) / 10,
    },
    breakdown,
  };
}

async function exportCsv(actor, period) {
  const { summary: s, breakdown } = await build(actor, period);
  const rows = [
    ['metric', 'value'],
    ['period', s.period], ['fromDate', s.fromDate], ['toDate', s.toDate],
    ['totalSchedules', s.totalSchedules], ['releasedCount', s.releasedCount],
    ['delayedCount', s.delayedCount], ['cancelledCount', s.cancelledCount],
    ['activeAreas', s.activeAreas], ['activeResidents', s.activeResidents],
    ['notificationsSent', s.notificationsSent], ['notificationsFailed', s.notificationsFailed],
    ['successRatePercent', dbl(s.successRatePercent)],
  ];
  const text =
    toCsv(rows, '\n') + '\n' +
    toCsv([
      ['date', 'scheduled', 'released', 'delayed', 'cancelled', 'notificationsSent', 'notificationsFailed'],
      ...breakdown.map((p) => [p.date, p.scheduledCount, p.releasedCount, p.delayedCount, p.cancelledCount, p.notificationsSent, p.notificationsFailed]),
    ], '\n');
  return Buffer.from(text, 'utf8');
}

async function exportPdf(actor, period) {
  const { summary: s, breakdown } = await build(actor, period);
  const lines = [
    'AquaAlert Report',
    `Period: ${s.period}`,
    `From: ${s.fromDate} To: ${s.toDate}`,
    `Schedules: ${s.totalSchedules}`,
    `Released: ${s.releasedCount} Delayed: ${s.delayedCount} Cancelled: ${s.cancelledCount}`,
    `Areas: ${s.activeAreas} Residents: ${s.activeResidents}`,
    `Sent: ${s.notificationsSent} Failed: ${s.notificationsFailed} Success: ${dbl(s.successRatePercent)}%`,
    'Breakdown:',
    ...breakdown.map((p) => `${p.date} | S:${p.scheduledCount} R:${p.releasedCount} D:${p.delayedCount} C:${p.cancelledCount} N+${p.notificationsSent} N-${p.notificationsFailed}`),
  ];
  return buildSimplePdf(lines);
}

module.exports = { build, exportCsv, exportPdf };
