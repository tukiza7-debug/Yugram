'use strict';

/**
 * models/message.model.js
 * Model mesej FASA 1 + 2:
 *   id, senderId, roomId, text, isSilent, isEdited, replyToMessageId,
 *   reactions (array JSONB), media (JSONB), forwardedFromName, editedAt,
 *   timestamps (createdAt/updatedAt).
 */

const { DataTypes, Model } = require('sequelize');
const { sequelize } = require('../config/database');
const {
  MESSAGE_MAX_LENGTH,
  REACTION_MAX_LENGTH,
  FORWARDED_FROM_MAX_LENGTH,
} = require('../utils/constants');

class Message extends Model {
  /**
   * Payload penuh untuk penghantaran melalui Socket.io / REST.
   * Tarikh diserialisasi ke format ISO 8601.
   * @returns {object}
   */
  toPayloadJSON() {
    const iso = (value) => (value instanceof Date ? value.toISOString() : value);
    const sender = this.sender
      ? {
          id: this.sender.id,
          username: this.sender.username,
          displayName: this.sender.displayName,
          avatarUrl: this.sender.avatarUrl ?? null,
        }
      : null;
    const replyTo = this.replyTo
      ? {
          id: this.replyTo.id,
          senderId: this.replyTo.senderId,
          text: this.replyTo.text,
          createdAt: iso(this.replyTo.createdAt),
        }
      : null;

    return {
      id: this.id,
      roomId: this.roomId,
      senderId: this.senderId,
      sender,
      text: this.text,
      isSilent: this.isSilent,
      isEdited: this.isEdited,
      editedAt: iso(this.editedAt) ?? null,
      replyToMessageId: this.replyToMessageId ?? null,
      replyTo,
      reactions: Array.isArray(this.reactions) ? this.reactions : [],
      media: this.media ?? null,
      forwardedFromName: this.forwardedFromName ?? null,
      createdAt: iso(this.createdAt),
      updatedAt: iso(this.updatedAt),
    };
  }
}

Message.init(
  {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true,
    },
    senderId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: { model: 'users', key: 'id' },
      onDelete: 'CASCADE',
    },
    roomId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: { model: 'rooms', key: 'id' },
      onDelete: 'CASCADE',
    },
    text: {
      type: DataTypes.TEXT,
      allowNull: false,
      // Rentetan kosong dibenarkan APABILA mesej mengandungi media;
      // peraturan "teks ATAU media wajib" dikuatkuasakan di message.service.
      validate: {
        len: {
          args: [0, MESSAGE_MAX_LENGTH],
          msg: `Panjang mesej mesti antara 0 hingga ${MESSAGE_MAX_LENGTH} aksara`,
        },
      },
    },
    isSilent: {
      type: DataTypes.BOOLEAN,
      allowNull: false,
      defaultValue: false,
    },
    isEdited: {
      type: DataTypes.BOOLEAN,
      allowNull: false,
      defaultValue: false,
    },
    replyToMessageId: {
      type: DataTypes.UUID,
      allowNull: true,
      references: { model: 'messages', key: 'id' },
      onDelete: 'SET NULL',
    },
    reactions: {
      type: DataTypes.JSONB,
      allowNull: false,
      defaultValue: [],
      validate: {
        isValidReactionArray(value) {
          if (!Array.isArray(value)) {
            throw new Error('reactions mesti array');
          }
          for (const reaction of value) {
            if (
              !reaction ||
              typeof reaction.userId !== 'string' ||
              typeof reaction.emoji !== 'string' ||
              reaction.emoji.length === 0 ||
              reaction.emoji.length > REACTION_MAX_LENGTH
            ) {
              throw new Error('Entri reaction tidak sah');
            }
          }
        },
      },
    },
    media: {
      type: DataTypes.JSONB,
      allowNull: true,
      validate: {
        isValidMedia(value) {
          if (value === null || value === undefined) {
            return;
          }
          if (typeof value !== 'object' || Array.isArray(value)) {
            throw new Error('media mesti objek');
          }
          if (typeof value.url !== 'string' || value.url.length === 0 || value.url.length > 512) {
            throw new Error('media.url tidak sah');
          }
        },
      },
    },
    forwardedFromName: {
      type: DataTypes.STRING(FORWARDED_FROM_MAX_LENGTH),
      allowNull: true,
    },
    editedAt: {
      type: DataTypes.DATE,
      allowNull: true,
    },
  },
  {
    sequelize,
    modelName: 'Message',
    tableName: 'messages',
    underscored: true,
    indexes: [
      { fields: ['room_id', 'created_at'] },
      { fields: ['sender_id'] },
    ],
  }
);

module.exports = Message;
