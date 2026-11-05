/** Shared notification SQL + mapping used by the query and dispatch services. */
const NOTIFICATION_SELECT = `
  SELECT n.*,
         s.release_date, s.release_time, s.notify_before_mins,
         a.name AS area_name, a.ward AS area_ward,
         r.owner_name, r.phone_number, r.email AS resident_email, r.fcm_token
  FROM notifications n
  JOIN water_schedules s ON s.id = n.schedule_id
  JOIN areas a ON a.id = s.area_id
  JOIN residents r ON r.id = n.resident_id`;

const mapNotificationResponse = (n) => ({
  id: n.id,
  scheduleId: n.schedule_id,
  areaName: n.area_name,
  residentId: n.resident_id,
  residentName: n.owner_name,
  residentPhone: n.phone_number,
  channel: n.channel,
  status: n.status,
  providerMessageId: n.provider_message_id,
  errorMessage: n.error_message,
  attemptCount: n.attempt_count,
  sentAt: n.sent_at,
  createdAt: n.created_at,
});

/** Row from NOTIFICATION_SELECT -> what the senders/templates need. */
const toDispatchable = (n) => ({
  id: n.id,
  channel: n.channel,
  schedule: {
    releaseDate: n.release_date,
    releaseTime: n.release_time,
    notifyBeforeMins: n.notify_before_mins,
    area: { name: n.area_name, ward: n.area_ward },
  },
  resident: {
    id: n.resident_id,
    ownerName: n.owner_name,
    phoneNumber: n.phone_number,
    email: n.resident_email,
    fcmToken: n.fcm_token,
  },
});

module.exports = { NOTIFICATION_SELECT, mapNotificationResponse, toDispatchable };
