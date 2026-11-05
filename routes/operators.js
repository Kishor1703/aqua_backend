const router = require('express').Router();
const h = require('../utils/asyncHandler');
const { validate } = require('../utils/validate');
const { requireRole } = require('../middleware/auth');
const schemas = require('../validation/schemas');
const operatorService = require('../services/operatorService');

router.use(requireRole('ADMIN'));

router.get('/', h(async (req, res) => {
  res.json(await operatorService.list());
}));

router.post('/', h(async (req, res) => {
  res.status(201).json(await operatorService.create(req.user, validate(req.body, schemas.operatorCreate)));
}));

module.exports = router;
