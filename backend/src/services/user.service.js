'use strict';

/**
 * services/user.service.js
 * FASA 2 - Logik bisnes profil pengguna: lihat, kemas kini, carian.
 */

const ApiError = require('../utils/apiError');
const logger = require('../utils/logger');
const userRepository = require('../repositories/user.repository');

const log = logger.child('UserService');

class UserService {
  /**
   * Profil awam pengguna.
   * @param {string} userId
   * @returns {Promise<object>}
   */
  async getProfile(userId) {
    const user = await userRepository.findById(userId);
    if (!user) {
      throw ApiError.notFound('Pengguna tidak dijumpai');
    }
    return user.toPublicJSON();
  }

  /**
   * Mengemas kini profil diri. Sekurang-kurangnya satu medan wajib
   * dibekalkan (divalidasi oleh Joi); username mesti unik (409 automatik).
   * @param {{userId: string, displayName?: string, bio?: string|null,
   *          username?: string, avatarUrl?: string|null}} input
   * @returns {Promise<object>} profil selepas kemas kini
   */
  async updateProfile({ userId, displayName, bio, username, avatarUrl }) {
    const fields = {};
    if (displayName !== undefined) {
      fields.displayName = displayName;
    }
    if (bio !== undefined) {
      fields.bio = bio === null || String(bio).trim() === '' ? null : String(bio).trim();
    }
    if (username !== undefined) {
      fields.username = String(username).toLowerCase();
    }
    if (avatarUrl !== undefined) {
      fields.avatarUrl = avatarUrl === null || String(avatarUrl).trim() === '' ? null : String(avatarUrl).trim();
    }
    if (Object.keys(fields).length === 0) {
      throw ApiError.badRequest('Tiada medan untuk dikemas kini');
    }

    if (fields.username) {
      const existing = await userRepository.findByUsername(fields.username);
      if (existing && existing.id !== userId) {
        throw ApiError.conflict('Username sudah diambil');
      }
    }

    const updated = await userRepository.updateProfile(userId, fields);
    log.info('Profil dikemas kini', { userId, fields: Object.keys(fields) });
    return updated.toPublicJSON();
  }

  /**
   * Carian pengguna untuk mula sembah baharu / tambah ahli kumpulan.
   * @param {{requesterId: string, query: string}} input
   * @returns {Promise<object[]>}
   */
  async searchUsers({ requesterId, query }) {
    const users = await userRepository.searchUsers(query, requesterId);
    return users.map((user) => user.toPublicJSON());
  }
}

module.exports = new UserService();
