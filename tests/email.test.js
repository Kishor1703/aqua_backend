const test = require('node:test');
const assert = require('node:assert/strict');
const { EmailProvider, singleAddress } = require('../src/notification/emailProvider');

const base = () => ({
  email: { enabled: true, testRecipient: 'me@example.com', from: '', senderName: 'AquaAlert' },
  smtp: { host: 'smtp.gmail.com', port: 587, username: 'sender@gmail.com', password: 'secret' },
});
const okTransport = (sent) => ({ sendMail: async (m) => { sent.push(m); return {}; } });

test('singleAddress rejects groups, display names and header injection', () => {
  assert.equal(singleAddress('a@b.com'), true);
  assert.equal(singleAddress('a@b.com\r\nBcc: x@y.com'), false);
  assert.equal(singleAddress('Name <a@b.com>'), false);
  assert.equal(singleAddress('a@b.com,c@d.com'), false);
  assert.equal(singleAddress(''), false);
});

test('disabled / unconfigured email fails with a clear reason', async () => {
  const p = new EmailProvider({ ...base(), email: { ...base().email, enabled: false } });
  assert.match((await p.sendTestEmail()).errorMessage, /disabled/);
  const q = new EmailProvider({ ...base(), smtp: { host: 'h', username: '', password: '' } });
  assert.match((await q.sendScheduleAlert('x@y.com', 't')).errorMessage, /SMTP configuration/);
});

test('schedule alert: sends, validates recipient', async () => {
  const sent = [];
  const p = new EmailProvider({ ...base(), transporter: okTransport(sent) });
  assert.equal((await p.sendScheduleAlert('res@example.com', 'hello')).success, true);
  assert.equal(sent[0].to, 'res@example.com');
  assert.equal(sent[0].from.address, 'sender@gmail.com');
  assert.match((await p.sendScheduleAlert(null, 'x')).errorMessage, /missing or invalid/);
});

test('test email is throttled to one per 60s even after a failure', async () => {
  let t = 1_000_000;
  const failing = { sendMail: async () => { const e = new Error('boom'); e.code = 'ETIMEDOUT'; throw e; } };
  const p = new EmailProvider({ ...base(), transporter: failing, now: () => t });
  assert.equal((await p.sendTestEmail()).success, false);
  assert.match((await p.sendTestEmail()).errorMessage, /wait 60 seconds/);
  t += 61_000;
  assert.doesNotMatch((await p.sendTestEmail()).errorMessage, /wait 60/);
});

test('auth failures are reported distinctly', async () => {
  const bad = { sendMail: async () => { const e = new Error('x'); e.code = 'EAUTH'; throw e; } };
  const p = new EmailProvider({ ...base(), transporter: bad });
  assert.match((await p.sendScheduleAlert('a@b.com', 't')).errorMessage, /authentication failed/);
});

test('password reset rejects links with line breaks', async () => {
  const p = new EmailProvider({ ...base(), transporter: okTransport([]) });
  assert.equal((await p.sendPasswordResetLink('a@b.com', 'http://x\nBcc: y')).success, false);
  assert.equal((await p.sendPasswordResetLink('a@b.com', 'http://x/reset?token=abc')).success, true);
});
