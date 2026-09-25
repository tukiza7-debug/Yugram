'use strict';

/**
 * sockets/socketGateway.js
 * Jambatan ringkas antara lapisan servis (REST/logik bisnes) dan Socket.IO.
 * chatSocket.js mendaftarkan instance `io` semasa but; servis lain
 * mengguna modul ini untuk menyiarkan event kepada ahli bilik tanpa
 * mewujudkan ketergantungan pusingan (circular dependency).
 */

const logger = require('../utils/logger');

const log = logger.child('SocketGateway');

/** @type {import('socket.io').Server|null} */
let ioInstance = null;

/**
 * Mendaftarkan instance Socket.IO (dipanggil sekali oleh chatSocket).
 * @param {import('socket.io').Server} io
 */
function setIO(io) {
  ioInstance = io;
  log.info('Socket.IO didaftarkan ke gateway');
}

/**
 * @returns {import('socket.io').Server|null}
 */
function getIO() {
  return ioInstance;
}

/**
 * Menyiarkan event kepada semua socket dalam satu bilik.
 * Gagal menyiarkan TIDAK pernah membuang exception - dilog sahaja,
 * kerana broadcast bersifat "best effort" selepas operasi DB berjaya.
 * @param {string} roomId
 * @param {string} event
 * @param {object} payload
 */
function publishToRoom(roomId, event, payload) {
  if (!ioInstance) {
    log.warn('Broadcast diabaikan - Socket.IO belum berdaftar', { roomId, event });
    return;
  }
  try {
    ioInstance.to(roomId).emit(event, payload);
    log.debug('Broadcast dihantar', { roomId, event });
  } catch (err) {
    log.error('Broadcast gagal', { roomId, event, message: err.message });
  }
}

module.exports = { setIO, getIO, publishToRoom };
