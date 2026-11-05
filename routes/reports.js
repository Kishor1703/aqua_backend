const router = require('express').Router();
const h = require('../utils/asyncHandler');
const { optEnumDefault } = require('../utils/params');
const { REPORT_PERIODS } = require('../utils/enums');
const reportService = require('../services/reportService');

const period = (req) => optEnumDefault(req.query, 'period', REPORT_PERIODS, 'WEEKLY');

router.get('/', h(async (req, res) => {
  res.json(await reportService.build(req.user, period(req)));
}));

router.get('/export.csv', h(async (req, res) => {
  const p = period(req);
  const csv = await reportService.exportCsv(req.user, p);
  res.set({
    'Content-Type': 'text/csv; charset=utf-8',
    'Content-Disposition': `attachment; filename="aquaalert-report-${p.toLowerCase()}.csv"`,
  });
  res.send(csv);
}));

router.get('/export.pdf', h(async (req, res) => {
  const p = period(req);
  const pdf = await reportService.exportPdf(req.user, p);
  res.set({
    'Content-Type': 'application/pdf',
    'Content-Disposition': `attachment; filename="aquaalert-report-${p.toLowerCase()}.pdf"`,
  });
  res.send(pdf);
}));

module.exports = router;
