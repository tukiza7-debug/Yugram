'use strict';

/**
 * repositories/user.repository.js
 * Akses data pengguna - TIADA logik bisnes di sini.
 */

const { User } = require('../models');
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
}

module.exports = new UserRepository();
