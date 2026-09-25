'use strict';

/**
 * scripts/db-sync.js
 * Helper development: segerakkan model Sequelize ke PostgreSQL
 * (sequelize.sync). Untuk production, gunakan database/schema.sql.
 */

const logger = require('../src/utils/logger');
const { sequelize } = require('../src/config/database');
require('../src/models');

const log = logger.child('DbSync');

(async () => {
  try {
    await sequelize.authenticate();
    // alter: true - lajur/jadual baharu FASA 2+3 ditambah pada skema sedia
    // ada tanpa membuang data pembangunan. Untuk production gunakan
    // database/schema.sql (DDL penuh).
    await sequelize.sync({ alter: true });
    log.info('Skema database disegerakkan (mod development, alter:true)');
    await sequelize.close();
    process.exit(0);
  } catch (err) {
    log.error('Gagal menyegerakkan skema database', { message: err.message, stack: err.stack });
    process.exit(1);
  }
})();
