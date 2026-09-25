'use strict';

/**
 * repositories/user.repository.js
 * Akses data pengguna - TIADA logik bisnes di sini.
 */

const { Op } = require('sequelize');
const { User } = require('../models');
const { PUBLIC_USER_ATTRS, USER_SEARCH_LIMIT } = require('../utils/constants');
const ApiError = require('../utils/apiError');

class UserRepository {
  /**
   * @param {{username: string, displayName: string, passwordHash: string}} input
   * @returns {Promise<User>}
   */
  async createUser({ username, displayName, passwordHash }) {
    try {
      return await User.create({ username: username.toLowerCase(), displayName, passwordHash });
    } catch (err) {
      if (err && err.name === 'SequelizeUniqueConstraintError') {
        throw ApiError.conflict('Username sudah diambil');
      }
      throw err;
    }
  }

  /**
   * @param {string} username
   * @returns {Promise<User|null>}
   */
  async findByUsername(username) {
    return User.findOne({ where: { username: String(username).toLowerCase() } });
  }

  /**
   * @param {string} id
   * @returns {Promise<User|null>}
   */
  async findById(id) {
    return User.findByPk(id);
  }

  /**
   * @param {string} id
   * @param {Date} date
   * @returns {Promise<number>} bilangan baris dikemas kini
   */
  async updateLastSeen(id, date) {
    const [affectedRows] = await User.update({ lastSeenAt: date }, { where: { id } });
    return affectedRows;
  }

  /**
   * FASA 2: Mengemas kini profil pengguna (displayName, bio, username,
   * avatarUrl). Konflik username unik dipetakan kepada 409.
   * @param {string} id
   * @param {{displayName?: string, bio?: string|null, username?: string, avatarUrl?: string|null}} fields
   * @returns {Promise<User>}
   */
  async updateProfile(id, fields) {
    try {
      const [, [updated]] = await User.update(fields, {
        where: { id },
        returning: true,
      });
      if (!updated) {
        throw ApiError.notFound('Pengguna tidak dijumpai');
      }
      return updated;
    } catch (err) {
      if (err && err.name === 'SequelizeUniqueConstraintError') {
        throw ApiError.conflict('Username sudah diambil');
      }
      throw err;
    }
  }

  /**
   * FASA 2: Carian pengguna mengikut username / displayName (ILIKE).
   * @param {string} query
   * @param {string} excludeUserId
   * @returns {Promise<User[]>}
   */
  async searchUsers(query, excludeUserId) {
    const pattern = `%${query.replace(/[%_\\]/g, '\\$&')}%`;
    return User.findAll({
      where: {
        id: { [Op.ne]: excludeUserId },
        [Op.or]: [
          { username: { [Op.iLike]: pattern } },
          { displayName: { [Op.iLike]: pattern } },
        ],
      },
      attributes: PUBLIC_USER_ATTRS.concat(['bio']),
      order: [['username', 'ASC']],
      limit: USER_SEARCH_LIMIT,
    });
  }
}

module.exports = new UserRepository();
