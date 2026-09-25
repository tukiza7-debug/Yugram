'use strict';

/**
 * utils/logger.js
 * Logger berstruktur (JSON) peringkat aplikasi - sifar dependency.
 * Format: {"time","level","scope","message","meta"}
 */

const LEVELS = { debug: 10, info: 20, warn: 30, error: 40 };

const rawLevel = String(process.env.LOG_LEVEL || 'info').toLowerCase();
const currentLevel = LEVELS.hasOwnProperty(rawLevel) ? rawLevel : 'info';

/**
 * Menulis satu entri log berstruktur ke stdout/stderr.
 * @param {string} level
 * @param {string} scope
 * @param {string} message
 * @param {object|undefined} [meta]
 */
function write(level, scope, message, meta) {
  if (LEVELS[level] < LEVELS[currentLevel]) return;
  const entry = {
    time: new Date().toISOString(),
    level,
    scope,
    message,
  };
  if (meta !== undefined) {
    entry.meta = meta;
  }
  const line = JSON.stringify(entry);
  if (level === 'error') {
    console.error(line);
  } else if (level === 'warn') {
    console.warn(line);
  } else {
    console.log(line);
  }
}

/**
 * Mencipta logger dengan scope tetap (cth: 'ChatSocket').
 * @param {string} scope
 * @returns {{debug: Function, info: Function, warn: Function, error: Function}}
 */
function child(scope) {
  return {
    debug: (message, meta) => write('debug', scope, message, meta),
    info: (message, meta) => write('info', scope, message, meta),
    warn: (message, meta) => write('warn', scope, message, meta),
    error: (message, meta) => write('error', scope, message, meta),
  };
}

module.exports = {
  debug: (message, meta) => write('debug', 'app', message, meta),
  info: (message, meta) => write('info', 'app', message, meta),
  warn: (message, meta) => write('warn', 'app', message, meta),
  error: (message, meta) => write('error', 'app', message, meta),
  child,
};
