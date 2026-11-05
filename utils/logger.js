const LEVELS = { debug: 10, info: 20, warn: 30, error: 40 };
const threshold = LEVELS[(process.env.LOG_LEVEL || 'info').toLowerCase()] ?? LEVELS.info;

function emit(level, message, meta) {
  if (LEVELS[level] < threshold) return;
  let line = `${new Date().toISOString()} ${level.toUpperCase().padEnd(5)} ${message}`;
  if (meta instanceof Error) line += `\n${meta.stack || meta.message}`;
  else if (meta !== undefined) line += ` ${JSON.stringify(meta)}`;
  (level === 'error' || level === 'warn' ? console.error : console.log)(line);
}

module.exports = {
  debug: (m, x) => emit('debug', m, x),
  info: (m, x) => emit('info', m, x),
  warn: (m, x) => emit('warn', m, x),
  error: (m, x) => emit('error', m, x),
};
