/** Request validation rules (equivalent of the Java request DTO annotations). */
const E = require('../utils/enums');

const blankToNullTrimmed = (v) => {
  if (v === null) return null;
  if (typeof v === 'string') return v.trim() === '' ? null : v.trim();
  return v;
};

module.exports = {
  login: [
    { field: 'email', type: 'string', required: true, email: true },
    { field: 'password', type: 'string', required: true },
  ],
  refreshToken: [{ field: 'refreshToken', type: 'string', required: true }],
  forgotPassword: [{ field: 'email', type: 'string', required: true, email: true }],
  resetPassword: [
    { field: 'token', type: 'string', required: true },
    {
      field: 'newPassword', type: 'string', required: true, min: 8, max: 100,
      sizeMessage: 'Password must be between 8 and 100 characters',
    },
  ],

  area: [
    { field: 'name', type: 'string', required: true, max: 150 },
    { field: 'ward', type: 'string', required: true, max: 100 },
    { field: 'zone', type: 'string', required: true, max: 100 },
    { field: 'street', type: 'string', max: 150 },
  ],

  operatorCreate: [
    { field: 'fullName', type: 'string', required: true, max: 150 },
    { field: 'email', type: 'string', required: true, email: true, max: 150 },
    { field: 'password', type: 'string', required: true, min: 8, max: 100 },
    { field: 'ward', type: 'string', required: true, max: 100 },
    {
      field: 'phoneNumber', type: 'string', max: 20,
      pattern: /^[0-9+\-() ]*$/, patternMessage: 'Phone number contains invalid characters',
    },
  ],

  resident: [
    { field: 'houseNumber', type: 'string', required: true, max: 30 },
    { field: 'ownerName', type: 'string', required: true, max: 150 },
    {
      field: 'phoneNumber', type: 'string', required: true,
      pattern: /^[+]?[0-9]{10,15}$/, patternMessage: 'Enter a valid phone number (10-15 digits, optional +)',
    },
    {
      field: 'alternatePhoneNumber', type: 'string',
      pattern: /^$|^[+]?[0-9]{10,15}$/, patternMessage: 'Enter a valid alternate phone number',
    },
    {
      field: 'email', type: 'string', email: true, max: 254,
      emailMessage: 'Enter a valid email address', preprocess: blankToNullTrimmed,
    },
    { field: 'areaId', type: 'long', required: true },
    { field: 'notificationPreference', type: 'enum', required: true, values: E.NOTIFICATION_PREFERENCES },
    { field: 'preferredLanguage', type: 'string', max: 10 },
  ],

  schedule: [
    { field: 'areaId', type: 'long', required: true },
    {
      field: 'releaseDate', type: 'date', required: true,
      futureOrPresent: true, futureMessage: 'Release date cannot be in the past',
    },
    { field: 'releaseTime', type: 'time', required: true },
    { field: 'notifyBeforeMins', type: 'int', required: true, min: 5, max: 1440 },
    { field: 'notes', type: 'string', max: 500 },
  ],

  statusUpdate: [
    { field: 'status', type: 'enum', required: true, values: E.SCHEDULE_STATUSES },
    { field: 'notes', type: 'string', max: 500 },
  ],
};
