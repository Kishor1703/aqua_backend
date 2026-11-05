const router = require('express').Router();
const h = require('../utils/asyncHandler');
const { validate } = require('../utils/validate');
const schemas = require('../validation/schemas');
const authService = require('../services/authService');

router.post('/login', h(async (req, res) => {
  res.json(await authService.login(validate(req.body, schemas.login)));
}));

router.post('/refresh', h(async (req, res) => {
  res.json(await authService.refresh(validate(req.body, schemas.refreshToken)));
}));

// Always 204 - never reveal whether the email exists.
router.post('/forgot-password', h(async (req, res) => {
  await authService.forgotPassword(validate(req.body, schemas.forgotPassword));
  res.status(204).end();
}));

router.post('/reset-password', h(async (req, res) => {
  await authService.resetPassword(validate(req.body, schemas.resetPassword));
  res.status(204).end();
}));

module.exports = router;
