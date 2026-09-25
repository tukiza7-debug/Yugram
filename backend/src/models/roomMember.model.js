'use strict';

/**
 * models/roomMember.model.js
 * Keahlian bilik + jejak bacaan terakhir (untuk read receipt).
 */

const { DataTypes, Model } = require('sequelize');
const { sequelize } = require('../config/database');
const { MEMBER_ROLE } = require('../utils/constants');

class RoomMember extends Model {}

RoomMember.init(
  {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true,
    },
    roomId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: { model: 'rooms', key: 'id' },
      onDelete: 'CASCADE',
    },
    userId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: { model: 'users', key: 'id' },
      onDelete: 'CASCADE',
    },
    role: {
      type: DataTypes.ENUM(MEMBER_ROLE.MEMBER, MEMBER_ROLE.ADMIN),
      allowNull: false,
      defaultValue: MEMBER_ROLE.MEMBER,
    },
    lastReadMessageId: {
      type: DataTypes.UUID,
      allowNull: true,
    },
    lastReadAt: {
      type: DataTypes.DATE,
      allowNull: true,
    },
  },
  {
    sequelize,
    modelName: 'RoomMember',
    tableName: 'room_members',
    underscored: true,
    indexes: [
      { unique: true, fields: ['room_id', 'user_id'] },
      { fields: ['user_id'] },
    ],
  }
);

module.exports = RoomMember;
