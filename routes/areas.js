const router = require('express').Router();
const h = require('../utils/asyncHandler');
const { validate } = require('../utils/validate');
const { parsePageable } = require('../utils/pagination');
const { parseId, optString, optBool, reqBool } = require('../utils/params');
const schemas = require('../validation/schemas');
const areaService = require('../services/areaService');

router.get('/', h(async (req, res) => {
  const pageable = parsePageable(req.query, {
    defaultSize: 20, defaultSort: [['name', 'asc']], sortable: areaService.SORTABLE, tieBreak: 'a.id ASC',
  });
  res.json(await areaService.search(
    req.user,
    { search: optString(req.query, 'search'), activeOnly: optBool(req.query, 'activeOnly', false) },
    pageable
  ));
}));

router.get('/:id', h(async (req, res) => {
  res.json(await areaService.getById(req.user, parseId(req.params.id)));
}));

router.post('/', h(async (req, res) => {
  res.status(201).json(await areaService.create(req.user, validate(req.body, schemas.area)));
}));

router.put('/:id', h(async (req, res) => {
  res.json(await areaService.update(req.user, parseId(req.params.id), validate(req.body, schemas.area)));
}));

router.patch('/:id/status', h(async (req, res) => {
  await areaService.setActive(req.user, parseId(req.params.id), reqBool(req.query, 'active'));
  res.status(204).end();
}));

router.delete('/:id', h(async (req, res) => {
  await areaService.remove(req.user, parseId(req.params.id));
  res.status(204).end();
}));

module.exports = router;
