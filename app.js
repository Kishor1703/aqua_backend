const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const config = require('./config');
const { authenticate } = require('./middleware/auth');
const { errorHandler, notFound } = require('./middleware/errorHandler');

function createApp() {
  const app = express();
  app.disable('x-powered-by');

  app.use(helmet());
  app.use(cors({
    origin: config.cors.allowedOrigins,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    credentials: true,
  }));
  app.use(express.json({ limit: '1mb' }));

  const health = (req, res) => res.json({ status: 'UP' });
  app.get('/health', health);
  app.get('/actuator/health', health);

  const api = '/api/v1';

  // Public
  app.use(`${api}/auth`, require('./routes/auth'));
  app.use(`${api}/public`, require('./routes/public'));

  // Everything below requires a valid access token
  app.use(api, authenticate);
  app.use(`${api}/areas`, require('./routes/areas'));
  app.use(`${api}/residents`, require('./routes/residents'));
  app.use(`${api}/schedules`, require('./routes/schedules'));
  app.use(`${api}/notifications`, require('./routes/notifications'));
  app.use(`${api}/operators`, require('./routes/operators')); // ADMIN only
  app.use(`${api}/dashboard`, require('./routes/dashboard'));
  app.use(`${api}/reports`, require('./routes/reports'));

  app.use(notFound);
  app.use(errorHandler);
  return app;
}

module.exports = { createApp };
