'use strict';

/**
 * models/user.model.js
 * Model pengguna - kata laluan disimpan sebagai hash bcrypt.
 */

const { DataTypes, Model } = require('sequelize');
const { sequelize } = require('../config/database');
const {
  USERNAME_MAX_LENGTH,
  DISPLAY_NAME_MAX_LENGTH,
  USER_BIO_MAX_LENGTH,
} = require('../utils/constants');

class User extends Model {
  /**
   * Payload awam yang selamat (tiada passwordHash).
   * @returns {object}
   */
  toPublicJSON() {
    return {
      id: this.id,
      username: this.username,
      displayName: this.displayName,
      bio: this.bio ?? null,
      avatarUrl: this.avatarUrl ?? null,
      lastSeenAt: this.lastSeenAt instanceof Date ? this.lastSeenAt.toISOString() : this.lastSeenAt ?? null,
    };
  }
}

User.init(
  {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true,
    },
    username: {
      type: DataTypes.STRING(USERNAME_MAX_LENGTH),
      allowNull: false,
      unique: true,
      validate: {
        is: /^[a-z0-9_]{3,32}$/i,
      },
    },
    displayName: {
      type: DataTypes.STRING(DISPLAY_NAME_MAX_LENGTH),
      allowNull: false,
      validate: {
        len: [1, DISPLAY_NAME_MAX_LENGTH],
      },
    },
    passwordHash: {
      type: DataTypes.STRING(128),
      allowNull: false,
    },
    bio: {
      type: DataTypes.STRING(USER_BIO_MAX_LENGTH),
      allowNull: true,
      validate: {
        len: [0, USER_BIO_MAX_LENGTH],
      },
    },
    avatarUrl: {
      type: DataTypes.STRING(512),
      allowNull: true,
    },
    lastSeenAt: {
      type: DataTypes.DATE,
      allowNull: true,
    },
  },
  {
    sequelize,
    modelName: 'User',
    tableName: 'users',
    underscored: true,
  }
);

module.exports = User;
