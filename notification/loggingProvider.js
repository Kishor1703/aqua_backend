/**
 * Default SMS / voice / push / WhatsApp provider: logs and returns a simulated success so the
 * whole pipeline (scheduler -> dispatch -> status tracking -> retry) runs without live credentials.
 * To go live, implement the same four functions against MSG91/Twilio/Exotel/FCM/WhatsApp Cloud API
 * and swap the export used in notificationDispatchService.
 */
const crypto = require('crypto');
const logger = require('../utils/logger');
const { ok, failure } = require('./sendResult');

const blank = (v) => v === null || v === undefined || String(v).trim() === '';
const fakeId = (prefix) => `${prefix}_${crypto.randomBytes(8).toString('hex')}`;

function sendSms(resident) {
  if (blank(resident.phoneNumber)) return failure('Resident has no phone number on file');
  logger.info('Simulated SMS notification accepted');
  return ok(fakeId('sms'));
}
function sendVoiceCall(resident) {
  if (blank(resident.phoneNumber)) return failure('Resident has no phone number on file');
  logger.info('Simulated voice notification accepted');
  return ok(fakeId('voice'));
}
function sendPushNotification(resident) {
  if (blank(resident.fcmToken)) return failure('Resident has no registered device (no FCM token)');
  logger.info('Simulated push notification accepted');
  return ok(fakeId('push'));
}
function sendWhatsApp(resident) {
  if (blank(resident.phoneNumber)) return failure('Resident has no phone number on file');
  logger.info('Simulated WhatsApp notification accepted');
  return ok(fakeId('whatsapp'));
}

module.exports = { sendSms, sendVoiceCall, sendPushNotification, sendWhatsApp };
