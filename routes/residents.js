const router = require('express').Router();
const multer = require('multer');
const h = require('../utils/asyncHandler');
const { validate } = require('../utils/validate');
const { parsePageable } = require('../utils/pagination');
const { parseId, optId, optString, optEnum, reqEnum } = require('../utils/params');
const { toLocalDate } = require('../utils/dates');
const { BadRequestError } = require('../utils/errors');
const { RESIDENT_STATUSES } = require('../utils/enums');
const schemas = require('../validation/schemas');
const residentService = require('../services/residentService');

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 5 * 1024 * 1024, files: 1 } });

const sendCsv = (res, filename, buffer) => {
  res.set({
    'Content-Type': 'text/csv; charset=utf-8',
    'Content-Disposition': `attachment; filename="${filename}"`,
  });
  res.send(buffer);
};

router.get('/', h(async (req, res) => {
  const pageable = parsePageable(req.query, {
    defaultSize: 20, defaultSort: [['ownerName', 'asc']], sortable: residentService.SORTABLE, tieBreak: 'r.id ASC',
  });
  res.json(await residentService.search(
    req.user,
    {
      search: optString(req.query, 'search'),
      areaId: optId(req.query, 'areaId'),
      status: optEnum(req.query, 'status', RESIDENT_STATUSES),
    },
    pageable
  ));
}));

// Static paths first so they don't match /:id
router.get('/export', h(async (req, res) => {
  const csv = await residentService.exportCsv(req.user, {
    areaId: optId(req.query, 'areaId'),
    status: optEnum(req.query, 'status', RESIDENT_STATUSES),
  });
  sendCsv(res, `residents-export-${toLocalDate()}.csv`, csv);
}));

router.get('/import-template', (req, res) => {
  const header = 'houseNumber,ownerName,phoneNumber,alternatePhoneNumber,email,areaName,ward,zone,street,notificationPreference,preferredLanguage\n';
  const example = '12A,Ramesh Kumar,9876543210,,ramesh@example.com,Gandhi Nagar,Ward 5,Central Zone,Main Road,SMS,en\n';
  sendCsv(res, 'resident-import-template.csv', Buffer.from(header + example, 'utf8'));
});

router.post('/import', upload.single('file'), h(async (req, res) => {
  if (!req.file || req.file.size === 0) throw new BadRequestError('Uploaded file is empty');
  res.json(await residentService.importCsv(req.user, req.file.buffer));
}));

router.get('/:id', h(async (req, res) => {
  res.json(await residentService.getById(req.user, parseId(req.params.id)));
}));

router.post('/', h(async (req, res) => {
  res.status(201).json(await residentService.create(req.user, validate(req.body, schemas.resident)));
}));

router.put('/:id', h(async (req, res) => {
  res.json(await residentService.update(req.user, parseId(req.params.id), validate(req.body, schemas.resident)));
}));

router.patch('/:id/status', h(async (req, res) => {
  await residentService.setStatus(req.user, parseId(req.params.id), reqEnum(req.query, 'status', RESIDENT_STATUSES));
  res.status(204).end();
}));

router.delete('/:id', h(async (req, res) => {
  await residentService.remove(req.user, parseId(req.params.id));
  res.status(204).end();
}));

module.exports = router;
