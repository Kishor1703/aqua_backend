const router = require('express').Router();
const h = require('../utils/asyncHandler');
const { query } = require('../db/pool');
const { toLocalDate } = require('../utils/dates');
const { optString } = require('../utils/params');

// Unauthenticated: today's schedules, optionally for one ward.
router.get('/today-schedules', h(async (req, res) => {
  const ward = optString(req.query, 'ward').trim();
  const params = [toLocalDate()];
  if (ward) params.push(ward);
  const { rows } = await query(
    `SELECT a.name AS area_name, a.ward, a.zone, s.release_date, s.release_time,
            s.notify_before_mins, s.status, s.notes
     FROM water_schedules s JOIN areas a ON a.id = s.area_id
     WHERE s.release_date = $1 ${ward ? 'AND LOWER(a.ward) = LOWER($2)' : ''}
     ORDER BY s.release_time ASC, s.id ASC`,
    params
  );
  res.json(rows.map((r) => ({
    areaName: r.area_name,
    ward: r.ward,
    zone: r.zone,
    releaseDate: r.release_date,
    releaseTime: r.release_time,
    notifyBeforeMins: r.notify_before_mins,
    status: r.status,
    notes: r.notes,
  })));
}));

module.exports = router;
