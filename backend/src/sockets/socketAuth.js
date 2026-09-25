'use strict';

/**
 * sockets/socketAuth.js
 * Middleware pengesahan JWT untuk handshake Socket.io.
 * Token dihantar melalui handshake.auth.token (atau query ?token=).
 */

const authService = require('../services/auth.service');
const logger = require('../utils/logger');

const log = logger.child('SocketAuth');

/**
 * @param {import('socket.io').Socket} socket
 * @param {Function} next
 */
function socketAuthMiddleware(socket, next) {
  try {
    const handshakeAuth = socket.handshake.auth || {};
    const handshakeQuery = socket.handshake.query || {};
    const token =
      (typeof handshakeAuth.token === 'string' && handshakeAuth.token) ||
      (typeof handshakeQuery.token === 'string' && handshakeQuery.token) ||
      null;

    if (!token) {
      next(new Error('AUTH_REQUIRED: Token pengesahan tiada'));
      return;
    }

    const payload = authService.verifyAccessToken(token);
    socket.data.user = {
      userId: payload.userId,
      username: payload.username,
      displayName: payload.displayName,
    };
    next();
  } catch (err) {
    log.warn('Handshake ditolak', {
      socketId: socket.id,
      message: err.message,
    });
    next(new Error(`AUTH_FAILED: ${err.message}`));
  }
}

module.exports = socketAuthMiddleware;
