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
    await sequelize.sync();
    log.info('Skema database disegerakkan (mod development)');
    await sequelize.close();
    process.exit(0);
  } catch (err) {
    log.error('Gagal menyegerakkan skema database', { message: err.message, stack: err.stack });
    process.exit(1);
  }
})();
