/**
 * Automatic notifications: polls for SCHEDULED, not-yet-triggered water schedules whose
 * notify-at moment (release date/time - notifyBeforeMins) has arrived and fans them out.
 *
 * Each schedule is claimed with an atomic UPDATE ... WHERE notification_triggered_at IS NULL,
 * so it can never fire twice - even across overlapping polls or multiple server instances.
 */
const { query } = require('../db/pool');
const dispatchService = require('../services/notificationDispatchService');
const { combine } = require('../utils/dates');
const logger = require('../utils/logger');

class NotificationScheduler {
  constructor(pollIntervalMs) {
    this.pollIntervalMs = pollIntervalMs;
    this.timer = null;
    this.running = false;
    this.inFlight = new Set();
  }

  start() {
    if (this.running) return;
    this.running = true;
    logger.info(`Notification scheduler started (poll every ${this.pollIntervalMs} ms)`);
    this.scheduleNext();
  }

  /** fixedDelay semantics: the next poll is scheduled only after the previous one finishes. */
  scheduleNext() {
    if (!this.running) return;
    this.timer = setTimeout(async () => {
      try {
        await this.checkAndDispatchDueSchedules();
      } catch (err) {
        logger.error('Notification scheduler poll failed', err);
      }
      this.scheduleNext();
    }, this.pollIntervalMs);
  }

  async checkAndDispatchDueSchedules(now = new Date()) {
    const { rows } = await query(
      `SELECT id, release_date, release_time, notify_before_mins
       FROM water_schedules WHERE status = 'SCHEDULED' AND notification_triggered_at IS NULL`
    );
    if (rows.length === 0) return 0;

    let dispatched = 0;
    for (const s of rows) {
      const notifyAt = combine(s.release_date, s.release_time, s.notify_before_mins);
      if (notifyAt > now) continue;

      const claimed = await query(
        `UPDATE water_schedules SET notification_triggered_at = now()
         WHERE id = $1 AND status = 'SCHEDULED' AND notification_triggered_at IS NULL`,
        [s.id]
      );
      if (claimed.rowCount === 0) continue; // someone else got it

      // Fire-and-forget (like @Async): a slow provider never blocks the next poll.
      const job = dispatchService
        .dispatchForSchedule(s.id)
        .catch((err) => logger.error(`Dispatch failed for schedule ${s.id}`, err))
        .finally(() => this.inFlight.delete(job));
      this.inFlight.add(job);
      dispatched++;
    }
    if (dispatched > 0) {
      logger.info(`Notification scheduler: dispatched ${dispatched} of ${rows.length} candidate schedules this cycle`);
    }
    return dispatched;
  }

  /** Stops polling and waits (up to timeoutMs) for in-flight dispatches. */
  async stop(timeoutMs = 10000) {
    this.running = false;
    clearTimeout(this.timer);
    if (this.inFlight.size > 0) {
      await Promise.race([Promise.allSettled([...this.inFlight]), new Promise((r) => setTimeout(r, timeoutMs))]);
    }
  }
}

module.exports = { NotificationScheduler };
