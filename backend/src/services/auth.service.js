'use strict';

/**
 * services/auth.service.js
 * Logik bisnes pengesahan: daftar, log masuk, JWT.
 */

const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const config = require('../config/env');
const ApiError = require('../utils/apiError');
const logger = require('../utils/logger');
const userRepository = require('../repositories/user.repository');

const log = logger.child('AuthService');
const BCRYPT_ROUNDS = 10;

class AuthService {
  /**
   * Mendaftar pengguna baharu.
   * @param {{username: string, displayName: string, password: string}} input
   * @returns {Promise<{token: string, user: object}>}
   * @throws {ApiError} 409 jika username sudah wujud
   */
  async register({ username, displayName, password }) {
    const passwordHash = await bcrypt.hash(password, BCRYPT_ROUNDS);
    const user = await userRepository.createUser({ username, displayName, passwordHash });
    log.info('Pengguna berdaftar', { userId: user.id, username: user.username });
    return { token: this.generateToken(user), user: user.toPublicJSON() };
  }

  /**
   * Log masuk pengguna sedia ada.
   * @param {{username: string, password: string}} input
   * @returns {Promise<{token: string, user: object}>}
   * @throws {ApiError} 401 jika kredensial tidak sah
   */
  async login({ username, password }) {
    const user = await userRepository.findByUsername(username);
    if (!user) {
      throw ApiError.unauthorized('Username atau kata laluan tidak sah');
    }
    const passwordMatches = await bcrypt.compare(password, user.passwordHash);
    if (!passwordMatches) {
      throw ApiError.unauthorized('Username atau kata laluan tidak sah');
    }
    await userRepository.updateLastSeen(user.id, new Date());
    log.info('Pengguna log masuk', { userId: user.id, username: user.username });
    return { token: this.generateToken(user), user: user.toPublicJSON() };
  }

  /**
   * Menjana JWT berclaim sub/username/displayName (displayName dipakai
   * oleh event typing tanpa perlu pertanyaan DB setiap kali).
   * @param {User} user
   * @returns {string}
   */
  generateToken(user) {
    return jwt.sign(
      { sub: user.id, username: user.username, displayName: user.displayName },
      config.jwtSecret,
      { expiresIn: config.jwtExpiresIn }
    );
  }

  /**
   * Mengesahkan token JWT.
   * @param {string} token
   * @returns {{userId: string, username: string, displayName: string}}
   * @throws {ApiError} 401 jika token tidak sah / luput
   */
  verifyAccessToken(token) {
    try {
      const decoded = jwt.verify(token, config.jwtSecret);
      return {
        userId: decoded.sub,
        username: decoded.username,
        displayName: decoded.displayName,
      };
    } catch (err) {
      if (err && err.name === 'TokenExpiredError') {
        throw ApiError.unauthorized('Token telah luput');
      }
      if (err && err.name === 'JsonWebTokenError') {
        throw ApiError.unauthorized('Token tidak sah');
      }
      throw err;
    }
  }
}

module.exports = new AuthService();
