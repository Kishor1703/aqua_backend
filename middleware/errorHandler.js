const http = require('http');
const logger = require('../utils/logger');

function body(status, message, req, fieldErrors = [], label) {
  return {
    timestamp: new Date().toISOString(),
    status,
    error: label || http.STATUS_CODES[status] || 'Error',
    message,
    path: req.originalUrl.split('?')[0],
    fieldErrors,
  };
}

function notFound(req, res) {
  res.status(404).json(body(404, 'Resource not found', req));
}

// eslint-disable-next-line no-unused-vars
function errorHandler(err, req, res, next) {
  if (res.headersSent) return next(err);

  // Our own typed errors
  if (err && typeof err.status === 'number' && err.name !== 'MulterError' && err.type === undefined) {
    return res.status(err.status).json(body(err.status, err.message, req, err.fieldErrors, err.label));
  }
  // body-parser
  if (err && err.type === 'entity.parse.failed') {
    return res.status(400).json(body(400, 'Malformed JSON request body', req));
  }
  if (err && err.type === 'entity.too.large') {
    return res.status(413).json(body(413, 'Request body too large', req));
  }
  // multer
  if (err && err.name === 'MulterError') {
    const tooLarge = err.code === 'LIMIT_FILE_SIZE';
    return res.status(tooLarge ? 413 : 400).json(body(tooLarge ? 413 : 400, tooLarge ? 'Uploaded file is too large' : err.message, req));
  }
  // PostgreSQL integrity / data errors (classes 22 and 23) -> 409, like DataIntegrityViolationException
  if (err && typeof err.code === 'string' && /^(22|23)/.test(err.code)) {
    logger.warn(`Database constraint error ${err.code}: ${err.message}`);
    return res.status(409).json(body(409, 'The request conflicts with existing data', req));
  }

  // Never leak internals to the client
  logger.error(`Unhandled error on ${req.method} ${req.originalUrl}`, err);
  return res.status(500).json(body(500, 'An unexpected error occurred', req));
}

module.exports = { errorHandler, notFound };
