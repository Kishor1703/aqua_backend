const { formatTime12, combine } = require('../utils/dates');

// "schedule" shape: { releaseDate: 'YYYY-MM-DD', releaseTime: 'HH:mm:ss', area: { name } }
const areaName = (s) => s.area.name;

const emailText = (s) =>
  `Water supply alert\nArea: ${areaName(s)}\nDate: ${s.releaseDate}\nTime: ${formatTime12(s.releaseTime)}\nPlease store water.`;

const smsText = (s) =>
  `Municipal Corporation: Water supply will begin today at ${formatTime12(s.releaseTime)} in ${areaName(s)}. Please store water.`;

const voiceScript = (s) =>
  `Hello. This is a notice from the Municipal Corporation. Water supply in ${areaName(s)} will begin today at ${formatTime12(s.releaseTime)}. Please store water. Thank you.`;

const pushTitle = () => 'Water Supply Alert';

function pushBody(s, now = new Date()) {
  const minutesUntil = Math.trunc((combine(s.releaseDate, s.releaseTime).getTime() - now.getTime()) / 60000);
  if (minutesUntil <= 0) return `Water supply starts now in ${areaName(s)}.`;
  if (minutesUntil < 60) return `Water supply starts in ${minutesUntil} minutes.`;
  const hours = Math.round(minutesUntil / 60);
  return `Water supply starts in ${hours} hour${hours === 1 ? '' : 's'}.`;
}

const whatsAppText = (s) => `*Water Supply Alert*\nArea: ${areaName(s)}\nTime: ${formatTime12(s.releaseTime)}`;

module.exports = { emailText, smsText, voiceScript, pushTitle, pushBody, whatsAppText };
