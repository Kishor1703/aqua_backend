const router = require('express').Router();
const h = require('../utils/asyncHandler');
const { validate } = require('../utils/validate');
const { parsePageable } = require('../utils/pagination');
const { parseId, optId, optEnum, optDate } = require('../utils/params');
const { SCHEDULE_STATUSES } = require('../utils/enums');
const schemas = require('../validation/schemas');
const scheduleService = require('../services/scheduleService');

router.get('/', h(async (req, res) => {
  const pageable = parsePageable(req.query, {
    defaultSize: 20,
    defaultSort: [['releaseDate', 'asc'], ['releaseTime', 'asc']],
    sortable: scheduleService.SORTABLE,
    tieBreak: 's.id ASC',
  });
  res.json(await scheduleService.search(
    req.user,
    {
      areaId: optId(req.query, 'areaId'),
      status: optEnum(req.query, 'status', SCHEDULE_STATUSES),
      fromDate: optDate(req.query, 'fromDate'),
      toDate: optDate(req.query, 'toDate'),
    },
    pageable
  ));
}));

router.get('/:id', h(async (req, res) => {
  res.json(await scheduleService.getById(req.user, parseId(req.params.id)));
}));

router.post('/', h(async (req, res) => {
  res.status(201).json(await scheduleService.create(req.user, validate(req.body, schemas.schedule)));
}));

router.put('/:id', h(async (req, res) => {
  res.json(await scheduleService.update(req.user, parseId(req.params.id), validate(req.body, schemas.schedule)));
}));

router.patch('/:id/status', h(async (req, res) => {
  const body = validate(req.body, schemas.statusUpdate);
  res.json(await scheduleService.updateStatus(req.user, parseId(req.params.id), body.status, body.notes));
}));

router.delete('/:id', h(async (req, res) => {
  await scheduleService.remove(req.user, parseId(req.params.id));
  res.status(204).end();
}));

module.exports = router;
