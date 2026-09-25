'use strict';

/**
 * models/room.model.js
 * Model bilik (Room/Namespace). Bilik 'direct' 1-ke-1 mempunyai directKey
 * unik (pasangan ID diisih) supaya bilik tidak didaftarkan dua kali.
 */

const { DataTypes, Model } = require('sequelize');
const { sequelize } = require('../config/database');
const { ROOM_TYPE, ROOM_NAME_MAX_LENGTH } = require('../utils/constants');

class Room extends Model {
  /**
   * Payload bilik untuk klien.
   * @returns {object}
   */
  toRoomJSON() {
    return {
      id: this.id,
      type: this.type,
      name: this.name ?? null,
      directKey: this.directKey ?? null,
      createdBy: this.createdBy,
      createdAt: this.createdAt instanceof Date ? this.createdAt.toISOString() : this.createdAt,
      updatedAt: this.updatedAt instanceof Date ? this.updatedAt.toISOString() : this.updatedAt,
    };
  }
}

Room.init(
  {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true,
    },
    type: {
      type: DataTypes.ENUM(ROOM_TYPE.DIRECT, ROOM_TYPE.GROUP),
      allowNull: false,
      defaultValue: ROOM_TYPE.DIRECT,
    },
    name: {
      type: DataTypes.STRING(ROOM_NAME_MAX_LENGTH),
      allowNull: true,
    },
    directKey: {
      type: DataTypes.STRING(80),
      allowNull: true,
      unique: true,
    },
    createdBy: {
      type: DataTypes.UUID,
      allowNull: false,
      references: { model: 'users', key: 'id' },
      onDelete: 'RESTRICT',
    },
  },
  {
    sequelize,
    modelName: 'Room',
    tableName: 'rooms',
    underscored: true,
    indexes: [{ fields: ['type'] }],
  }
);

module.exports = Room;
