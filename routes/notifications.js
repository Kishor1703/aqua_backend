const router = require('express').Router();
const h = require('../utils/asyncHandler');
const { parsePageable } = require('../utils/pagination');
const { parseId, optId, optEnum } = require('../utils/params');
const { requireRole } = require('../middleware/auth');
const { BadRequestError } = require('../utils/errors');
const { NOTIFICATION_STATUSES, NOTIFICATION_CHANNELS } = require('../utils/enums');
const queryService = require('../services/notificationQueryService');
const dispatchService = require('../services/notificationDispatchService');

// Admin-only SMTP check. Deliberately accepts no recipient/message/parameters.
router.post('/test-email', requireRole('ADMIN'), h(async (req, res) => {
  const hasBody =
    Number(req.headers['content-length'] || 0) > 0 || (req.body && Object.keys(req.body).length > 0);
  if (hasBody || Object.keys(req.query).length > 0) {
    throw new BadRequestError('Test email accepts no recipient, message, or other parameters');
  }
  const result = await dispatchService.sendTestEmail();
  res.status(result.success ? 200 : 503).json(result);
}));

router.get('/', h(async (req, res) => {
  const pageable = parsePageable(req.query, {
    defaultSize: 20, defaultSort: [['createdAt', 'asc']], sortable: queryService.SORTABLE, tieBreak: 'n.id ASC',
  });
  res.json(await queryService.search(
    req.user,
    {
      scheduleId: optId(req.query, 'scheduleId'),
      residentId: optId(req.query, 'residentId'),
      status: optEnum(req.query, 'status', NOTIFICATION_STATUSES),
      channel: optEnum(req.query, 'channel', NOTIFICATION_CHANNELS),
    },
    pageable
  ));
}));

router.get('/stats', h(async (req, res) => {
  res.json(await queryService.stats(req.user));
}));

router.post('/retry-failed', h(async (req, res) => {
  await dispatchService.retryAllEligibleFailed(req.user);
  res.status(202).end();
}));

router.post('/:id/retry', h(async (req, res) => {
  const id = parseId(req.params.id);
  await queryService.getById(req.user, id); // 404 / ward check first
  await dispatchService.retry(req.user, id);
  res.json(await queryService.getById(req.user, id));
}));

module.exports = router;
