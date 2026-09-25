'use strict';

/**
 * server.js
 * Titik masuk: sambung PostgreSQL => daftar Socket.io => dengar port.
 * Merangkumi graceful shutdown + pengendalian uncaught/unhandled.
 */

const http = require('http');
const app = require('./app');
const config = require('./config/env');
const logger = require('./utils/logger');
const { connectDatabase, sequelize } = require('./config/database');
const { registerChatSocket } = require('./sockets/chatSocket');

const log = logger.child('Server');

let httpServer = null;
let io = null;

async function main() {
  await connectDatabase();

  httpServer = http.createServer(app);
  io = registerChatSocket(httpServer);

  httpServer.listen(config.port, () => {
    log.info(`Yugram chat backend mendengar pada port :${config.port}`, {
      env: config.env,
    });
  });
}

main().catch((err) => {
  log.error('Permulaan pelayan gagal', { message: err.message, stack: err.stack });
  process.exit(1);
});

process.on('unhandledRejection', (reason) => {
  log.error('Unhandled promise rejection', {
    reason: reason instanceof Error ? reason.message : String(reason),
  });
});

process.on('uncaughtException', (err) => {
  log.error('Uncaught exception - mematikan pelayan', { message: err.message, stack: err.stack });
  process.exit(1);
});

/**
 * Graceful shutdown: tutup socket, HTTP server dan pool DB.
 * @param {string} signal
 */
async function shutdown(signal) {
  log.warn('Isyarat shutdown diterima', { signal });
  try {
    if (io) {
      await new Promise((resolve) => io.close(() => resolve()));
    }
    if (httpServer) {
      await new Promise((resolve) => httpServer.close(() => resolve()));
    }
    await sequelize.close();
    log.info('Shutdown lengkap - semua sambungan ditutup dengan selamat');
    process.exit(0);
  } catch (err) {
    log.error('Ralat semasa shutdown', { message: err.message });
    process.exit(1);
  }
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
