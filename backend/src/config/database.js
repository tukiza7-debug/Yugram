'use strict';

/**
 * config/database.js
 * Sambungan Sequelize ke PostgreSQL (connection pool + SSL boleh dikonfigur).
 */

const { Sequelize } = require('sequelize');
const config = require('./env');
const logger = require('../utils/logger');

const log = logger.child('Database');

const sequelize = new Sequelize(config.databaseUrl, {
  dialect: 'postgres',
  logging: (sql) => log.debug(sql),
  ssl: config.dbSsl
    ? { require: true, rejectUnauthorized: false }
    : undefined,
  pool: {
    max: 10,
    min: 0,
    acquire: 30000,
    idle: 10000,
  },
  define: {
    underscored: true,
    timestamps: true,
  },
});

/**
 * Menyambung ke PostgreSQL dengan error handling yang jelas.
 * @returns {Promise<void>}
 * @throws {Error} jika sambungan gagal - pemanggil (server.js) akan terminate.
 */
async function connectDatabase() {
  try {
    await sequelize.authenticate();
    log.info('PostgreSQL berjaya disambungkan', {
      dialect: sequelize.getDialect(),
      database: sequelize.getDatabaseName(),
    });
  } catch (err) {
    log.error('Gagal menyambung ke PostgreSQL', {
      message: err.message,
      host: maskHost(config.databaseUrl),
    });
    throw err;
  }
}

/**
 * Menyembunyi kredensial dalam URL untuk logging yang selamat.
 * @param {string} url
 * @returns {string}
 */
function maskHost(url) {
  try {
    const parsed = new URL(url);
    return `${parsed.protocol}//${parsed.hostname}:${parsed.port || 5432}${parsed.pathname}`;
  } catch (err) {
    return '<url-tidak-sah>';
  }
}

module.exports = { sequelize, connectDatabase };
