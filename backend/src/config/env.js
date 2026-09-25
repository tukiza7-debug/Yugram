'use strict';

/**
 * config/env.js
 * Memuatkan dan mengesahkan (validate) semua pembolehubah persekitaran
 * menggunakan Joi. Proses akan gagal-pantas (fail-fast) jika konfigurasi tidak sah.
 */

const path = require('path');
const Joi = require('joi');

// Muatkan .env milik backend ini secara eksplisit dan OVERRIDE nilai
// ambient supaya konfigurasi aplikasi sentiasa konsisten (fail .env di
// root backend ialah sumber kebenaran).
require('dotenv').config({
  path: path.join(__dirname, '..', '..', '.env'),
  override: true,
});

const envSchema = Joi.object({
  NODE_ENV: Joi.string().valid('development', 'test', 'production').default('development'),
  PORT: Joi.number().integer().port().default(4000),
  DATABASE_URL: Joi.string().required(),
  DB_SSL: Joi.boolean().default(false),
  JWT_SECRET: Joi.string().min(16).required(),
  JWT_EXPIRES_IN: Joi.string().default('7d'),
  CORS_ALLOWED_ORIGINS: Joi.string().default('*'),
  SOCKET_PING_INTERVAL_MS: Joi.number().integer().min(1000).default(25000),
  SOCKET_PING_TIMEOUT_MS: Joi.number().integer().min(1000).default(20000),
  MESSAGE_RATE_LIMIT_PER_MINUTE: Joi.number().integer().min(1).default(60),
  HISTORY_PAGE_SIZE_DEFAULT: Joi.number().integer().min(1).max(100).default(30),
  PUSH_ENDPOINT: Joi.string().uri({ scheme: ['http', 'https'] }).allow('').default(''),
  LOG_LEVEL: Joi.string().valid('debug', 'info', 'warn', 'error').default('info'),
}).unknown(true);

const { value, error } = envSchema.validate(process.env, { convert: true, abortEarly: false });

if (error) {
  console.error('[config] Pengesahan persekitaran GAGAL:');
  for (const detail of error.details) {
    console.error(`  - ${detail.message}`);
  }
  process.exit(1);
}

const env = value.NODE_ENV;
const jwtSecret = value.JWT_SECRET;

if (env === 'production' && jwtSecret === 'yugram_dev_secret_change_me') {
  console.error('[config] Menolak untuk berjalan di production dengan JWT_SECRET lalai. Tukar JWT_SECRET sekarang.');
  process.exit(1);
}

const corsOrigins =
  value.CORS_ALLOWED_ORIGINS === '*'
    ? true
    : value.CORS_ALLOWED_ORIGINS.split(',')
        .map((origin) => origin.trim())
        .filter(Boolean);

module.exports = {
  env,
  isProduction: env === 'production',
  port: value.PORT,
  databaseUrl: value.DATABASE_URL,
  dbSsl: value.DB_SSL,
  jwtSecret,
  jwtExpiresIn: value.JWT_EXPIRES_IN,
  corsOrigins,
  socketPingIntervalMs: value.SOCKET_PING_INTERVAL_MS,
  socketPingTimeoutMs: value.SOCKET_PING_TIMEOUT_MS,
  messageRateLimitPerMinute: value.MESSAGE_RATE_LIMIT_PER_MINUTE,
  historyPageSizeDefault: value.HISTORY_PAGE_SIZE_DEFAULT,
  pushEndpoint: value.PUSH_ENDPOINT,
  logLevel: value.LOG_LEVEL,
};
