const config = require('../config'); // loads .env and applies APP_TIMEZONE first
const logger = require('../utils/logger');
const { createApp } = require('../app');
const { runMigrations } = require('../db/migrate');
const { closePool } = require('../db/pool');
const { NotificationScheduler } = require('../scheduler/notificationScheduler');

async function main() {
  config.validateConfig();

  if (config.runMigrations) await runMigrations();

  const app = createApp();
  const server = app.listen(config.port, () => logger.info(`AquaAlert API listening on port ${config.port}`));

  const scheduler = new NotificationScheduler(config.scheduler.pollIntervalMs);
  scheduler.start();

  let shuttingDown = false;
  const shutdown = async (signal) => {
    if (shuttingDown) return;
    shuttingDown = true;
    logger.info(`${signal} received - shutting down`);
    server.close();
    await scheduler.stop();
    await closePool();
    process.exit(0);
  };
  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('SIGINT', () => shutdown('SIGINT'));
}

process.on('unhandledRejection', (reason) => logger.error('Unhandled promise rejection', reason));

main().catch((err) => {
  logger.error('Failed to start AquaAlert', err);
  process.exit(1);
});
