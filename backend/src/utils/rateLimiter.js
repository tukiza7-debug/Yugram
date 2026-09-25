'use strict';

/**
 * utils/rateLimiter.js
 * Rate limiter sliding-window dalam ingatan (in-memory), tanpa dependency.
 * Digunakan untuk mengehadkan kadar hantar mesej per pengguna.
 */

const config = require('../config/env');

class SlidingWindowRateLimiter {
  /**
   * @param {object} options
   * @param {number} options.limit - Bilangan maksimum kebenaran dalam satu window
   * @param {number} options.windowMs - Saiz window dalam milisaat
   */
  constructor({ limit, windowMs }) {
    if (!Number.isInteger(limit) || limit < 1) {
      throw new Error('SlidingWindowRateLimiter: limit mesti integer >= 1');
    }
    if (!Number.isInteger(windowMs) || windowMs < 100) {
      throw new Error('SlidingWindowRateLimiter: windowMs mesti integer >= 100');
    }
    this.limit = limit;
    this.windowMs = windowMs;
    this.hits = new Map();
  }

  /**
   * Cuba gunakan satu slot untuk kunci yang diberi.
   * @param {string} key - Pengecam unik (cth: `send:<userId>`)
   * @returns {{allowed: boolean, remaining: number, retryAfterMs: number}}
   */
  tryConsume(key) {
    const now = Date.now();
    let timestamps = this.hits.get(key);
    if (!timestamps) {
      timestamps = [];
      this.hits.set(key, timestamps);
    }

    while (timestamps.length > 0 && now - timestamps[0] >= this.windowMs) {
      timestamps.shift();
    }

    if (timestamps.length >= this.limit) {
      return {
        allowed: false,
        remaining: 0,
        retryAfterMs: Math.max(this.windowMs - (now - timestamps[0]), 0),
      };
    }

    timestamps.push(now);

    // Pembersihan oportunistik supaya Map tidak membesar tanpa had.
    if (this.hits.size > 10000) {
      for (const [existingKey, entries] of this.hits) {
        const last = entries[entries.length - 1];
        if (entries.length === 0 || now - last >= this.windowMs) {
          this.hits.delete(existingKey);
        }
      }
    }

    return { allowed: true, remaining: this.limit - timestamps.length, retryAfterMs: 0 };
  }

  /** Reset penuh (untuk ujian). */
  reset() {
    this.hits.clear();
  }
}

const messageRateLimiter = new SlidingWindowRateLimiter({
  limit: config.messageRateLimitPerMinute,
  windowMs: 60 * 1000,
});

module.exports = messageRateLimiter;
module.exports.SlidingWindowRateLimiter = SlidingWindowRateLimiter;
