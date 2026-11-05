const { query } = require('../db/pool');
const { wardScope } = require('./userAccess');
const scheduleService = require('./scheduleService');
const { countsByStatus } = require('./notificationQueryService');
const { toLocalDate, addDays, addMonths, startOfDay, monthLabel } = require('../utils/dates');

const DAILY_CHART_DAYS = 14;
const MONTHLY_CHART_MONTHS = 6;
const UPCOMING_LIMIT = 10;

const round1 = (n) => Math.round(n * 10) / 10;
const successRate = (sent, failed) => (sent + failed === 0 ? 0 : round1((sent * 100) / (sent + failed)));

async function count(sql, params) {
  return (await query(sql, params)).rows[0].c;
}

async function summary(actor) {
  const ward = wardScope(actor);
  const today = toLocalDate();
  const scope = (n) => (ward ? ` AND LOWER(a.ward) = LOWER($${n})` : '');
  const withWard = (...p) => (ward ? [...p, ward] : p);
  const schedules = 'FROM water_schedules s JOIN areas a ON a.id = s.area_id';

  const [todayScheduled, upcoming, delayed, cancelled, residents, areas, notif] = await Promise.all([
    count(`SELECT COUNT(*) AS c ${schedules} WHERE s.release_date = $1${scope(2)}`, withWard(today)),
    count(`SELECT COUNT(*) AS c ${schedules} WHERE s.status = $1${scope(2)}`, withWard('SCHEDULED')),
    count(`SELECT COUNT(*) AS c ${schedules} WHERE s.status = $1${scope(2)}`, withWard('DELAYED')),
    count(`SELECT COUNT(*) AS c ${schedules} WHERE s.status = $1${scope(2)}`, withWard('CANCELLED')),
    count(`SELECT COUNT(*) AS c FROM residents r JOIN areas a ON a.id = r.area_id WHERE r.status = 'ACTIVE'${scope(1)}`, withWard()),
    count(`SELECT COUNT(*) AS c FROM areas a WHERE a.is_active = TRUE${scope(1)}`, withWard()),
    countsByStatus(ward),
  ]);

  return {
    todayScheduledCount: todayScheduled,
    upcomingCount: upcoming,
    delayedCount: delayed,
    cancelledCount: cancelled,
    totalActiveResidents: residents,
    totalActiveAreas: areas,
    notificationsSentTotal: notif.SENT,
    notificationsFailedTotal: notif.FAILED,
    successRatePercent: successRate(notif.SENT, notif.FAILED),
  };
}

const todaysReleases = (actor) => scheduleService.listForDate(actor, toLocalDate());
const upcomingReleases = (actor) => scheduleService.listUpcoming(actor, UPCOMING_LIMIT);

function tally(map, key, status) {
  const counts = map.get(key) || [0, 0];
  if (status === 'SENT') counts[0]++;
  else if (status === 'FAILED') counts[1]++;
  map.set(key, counts);
}

async function charts(actor) {
  const ward = wardScope(actor);
  const since = startOfDay(addMonths(toLocalDate(), -MONTHLY_CHART_MONTHS));
  const params = [since];
  let wardSql = '';
  if (ward) {
    params.push(ward);
    wardSql = 'AND LOWER(a.ward) = LOWER($2)';
  }
  const { rows } = await query(
    `SELECT n.created_at, n.status, a.name AS area_name
     FROM notifications n
     JOIN water_schedules s ON s.id = n.schedule_id
     JOIN areas a ON a.id = s.area_id
     WHERE n.created_at >= $1 ${wardSql}`,
    params
  );

  // Daily: last 14 days, zero-filled
  const today = toLocalDate();
  const dailyStart = addDays(today, -(DAILY_CHART_DAYS - 1));
  const byDay = new Map();
  for (const n of rows) {
    const day = toLocalDate(n.created_at);
    if (day >= dailyStart) tally(byDay, day, n.status);
  }
  const dailyNotifications = [];
  for (let i = 0; i < DAILY_CHART_DAYS; i++) {
    const date = addDays(dailyStart, i);
    const [sent, failed] = byDay.get(date) || [0, 0];
    dailyNotifications.push({ date, sent, failed });
  }

  // Per area: top 10 by volume
  const byArea = new Map();
  for (const n of rows) tally(byArea, n.area_name, n.status);
  const areaWiseNotifications = [...byArea.entries()]
    .map(([areaName, [sent, failed]]) => ({ areaName, sent, failed }))
    .sort((a, b) => b.sent + b.failed - (a.sent + a.failed))
    .slice(0, 10);

  // Monthly: last 6 calendar months, zero-filled
  const byMonth = new Map();
  for (const n of rows) tally(byMonth, toLocalDate(n.created_at).slice(0, 7), n.status);
  const monthlyNotifications = [];
  for (let i = MONTHLY_CHART_MONTHS - 1; i >= 0; i--) {
    const first = addMonths(`${today.slice(0, 8)}01`, -i);
    const [sent, failed] = byMonth.get(first.slice(0, 7)) || [0, 0];
    monthlyNotifications.push({ monthLabel: monthLabel(Number(first.slice(0, 4)), Number(first.slice(5, 7)) - 1), sent, failed });
  }

  return { dailyNotifications, areaWiseNotifications, monthlyNotifications };
}

module.exports = { summary, todaysReleases, upcomingReleases, charts };
