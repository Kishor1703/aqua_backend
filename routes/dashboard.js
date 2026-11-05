const router = require('express').Router();
const h = require('../utils/asyncHandler');
const dashboardService = require('../services/dashboardService');

router.get('/summary', h(async (req, res) => res.json(await dashboardService.summary(req.user))));
router.get('/today', h(async (req, res) => res.json(await dashboardService.todaysReleases(req.user))));
router.get('/upcoming', h(async (req, res) => res.json(await dashboardService.upcomingReleases(req.user))));
router.get('/charts', h(async (req, res) => res.json(await dashboardService.charts(req.user))));

module.exports = router;
