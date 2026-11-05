/**
 * SMTP (Gmail) delivery for schedule alerts, password resets and the admin test email.
 * Ambiguous SMTP outcomes (e.g. timeouts) are reported as failures but never auto-retried
 * here - the message may already have been accepted.
 */
const { ok, failure } = require('./sendResult');

const blank = (v) => v === null || v === undefined || String(v).trim() === '';
const hasLineBreak = (v) => /[\r\n]/.test(v);
const ADDRESS_RE = /^[^\s@<>,;:"()[\]\\]+@[^\s@<>,;:"()[\]\\]+\.[^\s@<>,;:"()[\]\\]+$/;
const singleAddress = (v) => !blank(v) && !hasLineBreak(v) && ADDRESS_RE.test(v);

class EmailProvider {
  constructor({ email, smtp, transporter, now = () => Date.now() } = {}) {
    const config = email && smtp ? null : require('../config');
    this.email = email || config.email;
    this.smtp = smtp || config.mail;
    this.now = now;
    this.transporter = transporter;
    this.nextTestAttempt = 0;
  }

  getTransporter() {
    if (!this.transporter) {
      this.transporter = require('nodemailer').createTransport({
        host: this.smtp.host,
        port: this.smtp.port,
        secure: false,
        requireTLS: true, // STARTTLS mandatory
        auth: { user: this.smtp.username, pass: this.smtp.password },
        connectionTimeout: 5000,
        greetingTimeout: 5000,
        socketTimeout: 5000,
      });
    }
    return this.transporter;
  }

  smtpConfigured() {
    return !blank(this.smtp.host) && !blank(this.smtp.username) && !blank(this.smtp.password);
  }

  sender() {
    return blank(this.email.from) ? this.smtp.username : this.email.from;
  }

  senderNameValid() {
    return !blank(this.email.senderName) && !hasLineBreak(this.email.senderName);
  }

  async deliver(to, subject, text, messages) {
    if (!this.senderNameValid()) return failure('Email sender configuration is invalid.');
    try {
      await this.getTransporter().sendMail({
        from: { name: this.email.senderName, address: this.sender() },
        to,
        subject,
        text,
      });
      // SMTP acceptance is not proof of inbox delivery and yields no receipt id.
      return ok(null);
    } catch (err) {
      return failure(err && err.code === 'EAUTH' ? messages.auth : messages.other);
    }
  }

  async sendTestEmail() {
    if (!this.email.enabled) return failure('Test email is disabled. Set AQUAALERT_MAIL_ENABLED=true.');
    if (!this.smtpConfigured()) {
      return failure('SMTP configuration is missing. Set AQUAALERT_MAIL_USERNAME and AQUAALERT_MAIL_PASSWORD.');
    }
    if (!singleAddress(this.sender()) || !singleAddress(this.email.testRecipient)) {
      return failure('Configure a valid sender and exactly one AQUAALERT_TEST_EMAIL address.');
    }
    if (this.now() < this.nextTestAttempt) return failure('Please wait 60 seconds between test email attempts.');
    // Throttle even failed sends (a timeout can happen after acceptance). Set before awaiting
    // so concurrent calls can't slip through.
    this.nextTestAttempt = this.now() + 60_000;
    return this.deliver(
      this.email.testRecipient,
      'AquaAlert test email',
      'This is a development test email from AquaAlert.\nYour SMTP configuration is working. This is not a scheduled water-release alert.',
      {
        auth: 'SMTP authentication failed. Check the configured Gmail account and app password.',
        other: 'SMTP send could not be confirmed. Check your inbox and SMTP settings before retrying.',
      }
    );
  }

  async sendPasswordResetLink(recipient, resetLink) {
    if (!this.email.enabled) return failure('Email delivery is not configured.');
    if (
      !this.smtpConfigured() || !singleAddress(this.sender()) || !singleAddress(recipient) ||
      blank(resetLink) || hasLineBreak(resetLink)
    ) {
      return failure('Email delivery is not configured with a valid address.');
    }
    return this.deliver(
      recipient,
      'Reset your AquaAlert password',
      'We received a request to reset your AquaAlert password.\n\n' +
        'Use this link within 30 minutes to choose a new password:\n' +
        `${resetLink}\n\n` +
        'If you did not request this, you can ignore this email.',
      { auth: 'SMTP authentication failed.', other: 'SMTP could not confirm delivery.' }
    );
  }

  async sendScheduleAlert(recipient, text) {
    if (!this.email.enabled) return failure('Email is disabled. Set AQUAALERT_MAIL_ENABLED=true.');
    if (!this.smtpConfigured() || !singleAddress(this.sender())) {
      return failure('SMTP configuration is missing or the sender address is invalid.');
    }
    if (!singleAddress(recipient)) {
      return failure('Resident email address is missing or invalid. Update the resident before retrying.');
    }
    return this.deliver(recipient, 'AquaAlert water supply alert', text, {
      auth: 'SMTP authentication failed. Use the Gmail account and its app password without spaces.',
      other: 'SMTP delivery could not be confirmed. Check the inbox before retrying.',
    });
  }
}

let shared;
/** Process-wide provider built from app config (lazy, so importing this module has no side effects). */
const getEmailProvider = () => (shared ||= new EmailProvider());

module.exports = { EmailProvider, singleAddress, getEmailProvider };
