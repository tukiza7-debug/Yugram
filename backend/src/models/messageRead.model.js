'use strict';

/**
 * models/messageRead.model.js
 * Read receipt per-mesej: satu baris unik (messageId, userId).
 * Menjana "tick berganda" (dibaca penerima).
 */

const { DataTypes, Model } = require('sequelize');
const { sequelize } = require('../config/database');

class MessageRead extends Model {}

MessageRead.init(
  {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true,
    },
    messageId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: { model: 'messages', key: 'id' },
      onDelete: 'CASCADE',
    },
    // Denormalisasi roomId untuk pertanyaan pantas per-bilik.
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
    readAt: {
      type: DataTypes.DATE,
      allowNull: false,
      defaultValue: DataTypes.NOW,
    },
  },
  {
    sequelize,
    modelName: 'MessageRead',
    tableName: 'message_reads',
    underscored: true,
    indexes: [
      { unique: true, fields: ['message_id', 'user_id'] },
      { fields: ['room_id', 'user_id'] },
    ],
  }
);

module.exports = MessageRead;
