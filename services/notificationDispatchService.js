const config = require('../config');
const { query } = require('../db/pool');
const { getEmailProvider } = require('../notification/emailProvider');
const logging = require('../notification/loggingProvider');
const templates = require('../notification/templates');
const { failure } = require('../notification/sendResult');
const { NOTIFICATION_SELECT, toDispatchable } = require('./notificationStore');
const { wardScope, requireWardAccess } = require('./userAccess');
const { NotFoundError, InvalidStateTransitionError } = require('../utils/errors');
const logger = require('../utils/logger');

const MAX_RETRY_ATTEMPTS = 3;
const sendTestEmail = () => getEmailProvider().sendTestEmail();

async function sendPasswordResetLink(user, rawToken) {
  const link = `${config.frontendUrl}/reset-password?token=${encodeURIComponent(rawToken)}`;
  const result = await getEmailProvider().sendPasswordResetLink(user.email, link);
  if (!result.success) logger.warn(`Password reset email could not be sent (reason: ${result.errorMessage})`);
}

async function sendOnChannel({ channel, schedule, resident }) {
  switch (channel) {
    case 'EMAIL': return getEmailProvider().sendScheduleAlert(resident.email, templates.emailText(schedule));
    case 'SMS': return logging.sendSms(resident, templates.smsText(schedule));
    case 'VOICE': return logging.sendVoiceCall(resident, templates.voiceScript(schedule));
    case 'PUSH': return logging.sendPushNotification(resident, templates.pushTitle(), templates.pushBody(schedule));
    case 'WHATSAPP': return logging.sendWhatsApp(resident, templates.whatsAppText(schedule));
    default: return failure(`Unsupported channel: ${channel}`);
  }
}

/** Sends one notification and records the outcome. Provider exceptions become FAILED, never crashes. */
async function attemptSend(notification) {
  let result;
  try {
    result = await sendOnChannel(notification);
  } catch (err) {
    logger.error(`Provider threw while sending notification ${notification.id}`, err);
    result = failure('Unexpected error while contacting the provider');
  }

  if (result.success) {
    await query(
      `UPDATE notifications
       SET attempt_count = attempt_count + 1, status = 'SENT', provider_message_id = $2,
           sent_at = now(), error_message = NULL
       WHERE id = $1`,
      [notification.id, result.providerMessageId]
    );
  } else {
    await query(
      `UPDATE notifications
       SET attempt_count = attempt_count + 1, status = 'FAILED', error_message = $2
       WHERE id = $1`,
      [notification.id, String(result.errorMessage || 'Unknown error').slice(0, 500)]
    );
  }
}

/**
 * Fans a due schedule out to every ACTIVE resident of its area: one EMAIL notification row per
 * resident. Takes an id (not an object) so it always works from fresh data.
 */
async function dispatchForSchedule(scheduleId) {
  const sched = await query(
    `SELECT s.id, s.release_date, s.release_time, s.notify_before_mins,
            a.id AS area_id, a.name AS area_name, a.ward AS area_ward
     FROM water_schedules s JOIN areas a ON a.id = s.area_id WHERE s.id = $1`,
    [scheduleId]
  );
  if (!sched.rows[0]) {
    logger.warn(`Schedule ${scheduleId} no longer exists - skipping notification dispatch`);
    return;
  }
  const s = sched.rows[0];
  const schedule = {
    releaseDate: s.release_date,
    releaseTime: s.release_time,
    notifyBeforeMins: s.notify_before_mins,
    area: { name: s.area_name, ward: s.area_ward },
  };

  const residents = (
    await query(
      `SELECT id, owner_name, phone_number, email, fcm_token
       FROM residents WHERE area_id = $1 AND status = 'ACTIVE' ORDER BY id`,
      [s.area_id]
    )
  ).rows;
  logger.info(`Dispatching notifications for schedule ${s.id} (${s.area_name}) to ${residents.length} residents`);

  for (const r of residents) {
    try {
      const inserted = await query(
        `INSERT INTO notifications (schedule_id, resident_id, channel, status, attempt_count)
         VALUES ($1, $2, 'EMAIL', 'PENDING', 0) RETURNING id`,
        [s.id, r.id]
      );
      await attemptSend({
        id: inserted.rows[0].id,
        channel: 'EMAIL',
        schedule,
        resident: { id: r.id, ownerName: r.owner_name, phoneNumber: r.phone_number, email: r.email, fcmToken: r.fcm_token },
      });
    } catch (err) {
      // One resident's failure must not stop the rest of the fan-out.
      logger.error(`Failed to notify resident ${r.id} for schedule ${s.id}`, err);
    }
  }
}

async function loadNotification(id) {
  const { rows } = await query(`${NOTIFICATION_SELECT} WHERE n.id = $1`, [id]);
  return rows[0];
}

/** FAILED -> RETRYING (atomic) -> attemptSend. */
async function retry(actor, id) {
  const row = await loadNotification(id);
  if (!row) throw new NotFoundError(`Notification not found: ${id}`);
  requireWardAccess(actor, row.area_ward);

  if (row.status !== 'FAILED') {
    throw new InvalidStateTransitionError(`Only FAILED notifications can be retried (current status: ${row.status})`);
  }
  if (row.attempt_count >= MAX_RETRY_ATTEMPTS) {
    throw new InvalidStateTransitionError(`Notification has already reached the maximum of ${MAX_RETRY_ATTEMPTS} attempts`);
  }
  const claimed = await query(`UPDATE notifications SET status = 'RETRYING' WHERE id = $1 AND status = 'FAILED'`, [id]);
  if (claimed.rowCount === 0) {
    throw new InvalidStateTransitionError('Notification is already being retried');
  }
  await attemptSend(toDispatchable(row));
}

/** Retries every FAILED notification still under the attempt ceiling (scoped to the operator's ward). */
async function retryAllEligibleFailed(actor) {
  const ward = wardScope(actor);
  const params = [MAX_RETRY_ATTEMPTS];
  let wardSql = '';
  if (ward) {
    params.push(ward);
    wardSql = 'AND LOWER(a.ward) = LOWER($2)';
  }
  const { rows } = await query(
    `${NOTIFICATION_SELECT} WHERE n.status = 'FAILED' AND n.attempt_count < $1 ${wardSql} ORDER BY n.id`,
    params
  );

  let retried = 0;
  for (const row of rows) {
    const claimed = await query(`UPDATE notifications SET status = 'RETRYING' WHERE id = $1 AND status = 'FAILED'`, [row.id]);
    if (claimed.rowCount === 0) continue;
    await attemptSend(toDispatchable(row));
    retried++;
  }
  logger.info(`Retry pass: ${retried} of ${rows.length} failed notifications retried`);
}

module.exports = {
  sendTestEmail,
  sendPasswordResetLink,
  dispatchForSchedule,
  attemptSend,
  retry,
  retryAllEligibleFailed,
  MAX_RETRY_ATTEMPTS,
};
