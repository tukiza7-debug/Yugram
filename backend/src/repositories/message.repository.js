'use strict';

/**
 * repositories/message.repository.js
 * Akses data mesej + read receipt + reactions.
 */

const { Op } = require('sequelize');
const { Message, MessageRead, User } = require('../models');
const { PUBLIC_USER_ATTRS } = require('../utils/constants');
const ApiError = require('../utils/apiError');

const MESSAGE_INCLUDES = [
  { model: User, as: 'sender', attributes: PUBLIC_USER_ATTRS },
  { model: Message, as: 'replyTo', attributes: ['id', 'senderId', 'text', 'createdAt'] },
];

/**
 * Melarikan aksara wildcard LIKE dalam pertanyaan carian.
 * @param {string} query
 * @returns {string}
 */
function escapeLike(query) {
  return query.replace(/[%_\\]/g, '\\$&');
}

class MessageRepository {
  /**
   * Mencipta mesej dan memulangkannya berserta sender + replyTo.
   * @param {{roomId: string, senderId: string, text: string, isSilent: boolean,
   *          replyToMessageId: string|null, media: object|null,
   *          forwardedFromName: string|null}} input
   * @returns {Promise<Message>}
   */
  async createMessage({ roomId, senderId, text, isSilent, replyToMessageId, media, forwardedFromName }) {
    const created = await Message.create({
      roomId,
      senderId,
      text,
      isSilent,
      replyToMessageId: replyToMessageId || null,
      media: media || null,
      forwardedFromName: forwardedFromName || null,
    });
    return this.findByIdWithSender(created.id);
  }

  /**
   * @param {string} id
   * @returns {Promise<Message|null>}
   */
  async findById(id) {
    return Message.findByPk(id);
  }

  /**
   * @param {string} id
   * @returns {Promise<Message|null>}
   */
  async findByIdWithSender(id) {
    return Message.findByPk(id, { include: MESSAGE_INCLUDES });
  }

  /**
   * Sejarah mesej bilik, tertib menaik (lama -> baharu), paginasi kursor.
   * @param {string} roomId
   * @param {{limit: number, before: Date|null}} options
   * @returns {Promise<{messages: Message[], hasMore: boolean, nextBefore: string|null}>}
   */
  async listMessages(roomId, { limit, before }) {
    const where = { roomId };
    if (before) {
      where.createdAt = { [Op.lt]: before };
    }
    const rows = await Message.findAll({
      where,
      include: MESSAGE_INCLUDES,
      order: [['createdAt', 'DESC']],
      limit: limit + 1,
    });

    const hasMore = rows.length > limit;
    const page = rows.slice(0, limit).reverse();

    return {
      messages: page,
      hasMore,
      nextBefore:
        hasMore && page.length > 0
          ? page[0].createdAt instanceof Date
            ? page[0].createdAt.toISOString()
            : String(page[0].createdAt)
          : null,
    };
  }

  /**
   * ID mesej dalam bilik yang BELUM dibaca oleh `userId`
   * (mesej daripada pengguna lain, sehingga `upToCreatedAt`).
   * @param {string} roomId
   * @param {string} userId
   * @param {Date} upToCreatedAt
   * @returns {Promise<string[]>}
   */
  async findUnreadMessageIds(roomId, userId, upToCreatedAt) {
    const rows = await Message.findAll({
      attributes: ['id'],
      where: {
        roomId,
        senderId: { [Op.ne]: userId },
        createdAt: { [Op.lte]: upToCreatedAt },
      },
      include: [
        { model: MessageRead, as: 'reads', required: false, where: { userId }, attributes: ['id'] },
      ],
    });
    return rows
      .filter((row) => !row.reads || row.reads.length === 0)
      .map((row) => row.id);
  }

  /**
   * Menanda mesej sebagai dibaca (idempoten melalui unique constraint).
   * @param {{roomId: string, userId: string, messageIds: string[], readAt: Date}} input
   * @returns {Promise<number>} bilangan mesej diproses
   */
  async markMessagesRead({ roomId, userId, messageIds, readAt }) {
    if (!messageIds.length) {
      return 0;
    }
    await MessageRead.bulkCreate(
      messageIds.map((messageId) => ({ messageId, roomId, userId, readAt })),
      { ignoreDuplicates: true }
    );
    return messageIds.length;
  }

  /**
   * Menggantikan array reactions mesej (toggle dilakukan di service).
   * @param {string} messageId
   * @param {Array<{userId: string, emoji: string, createdAt: string}>} reactions
   * @returns {Promise<Message>}
   */
  async updateReactions(messageId, reactions) {
    const message = await Message.findByPk(messageId);
    if (!message) {
      throw ApiError.notFound('Mesej tidak dijumpai');
    }
    message.set('reactions', reactions);
    message.changed('reactions', true);
    await message.save();
    return message;
  }

  /**
   * Bilangan mesej dalam bilik (digunakan ujian/monitoring).
   * @param {string} roomId
   * @returns {Promise<number>}
   */
  async countMessages(roomId) {
    return Message.count({ where: { roomId } });
  }

  // ==================================================================
  // FASA 2 + 3 - EDIT, PADAM, CARIAN, SENARAI MEDIA
  // ==================================================================

  /**
   * FASA 3: Mengemas kini teks mesej (edit). editedAt/isEdited diset di sini
   * supaya semua laluan edit konsisten.
   * @param {string} messageId
   * @param {string} text
   * @returns {Promise<Message>} mesej terkini berserta sender + replyTo
   */
  async updateText(messageId, text) {
    const message = await Message.findByPk(messageId);
    if (!message) {
      throw ApiError.notFound('Mesej tidak dijumpai');
    }
    message.set('text', text);
    message.set('isEdited', true);
    message.set('editedAt', new Date());
    await message.save();
    return this.findByIdWithSender(messageId);
  }

  /**
   * FASA 3: Memadamkan mesej secara kekal.
   * @param {string} messageId
   * @returns {Promise<string>} roomId mesej yang dipadam (sebelum dipadam)
   * @throws {ApiError} 404 jika mesej tiada
   */
  async deleteById(messageId) {
    const message = await Message.findByPk(messageId, { attributes: ['id', 'roomId'] });
    if (!message) {
      throw ApiError.notFound('Mesej tidak dijumpai');
    }
    const roomId = message.roomId;
    await message.destroy();
    return roomId;
  }

  /**
   * FASA 3: Carian mesej dalam bilik (ILIKE, tidak kes sensitif huruf).
   * @param {string} roomId
   * @param {string} query
   * @param {{limit: number}} options
   * @returns {Promise<Message[]>}
   */
  async searchMessages(roomId, query, { limit }) {
    return Message.findAll({
      where: {
        roomId,
        text: { [Op.iLike]: `%${escapeLike(query)}%` },
      },
      include: MESSAGE_INCLUDES,
      order: [['createdAt', 'DESC']],
      limit,
    });
  }

  /**
   * FASA 2: Senarai semua mesej bermedia dalam bilik (untuk muat turun
   * pukal "download all"). Susunan lama -> baharu.
   * @param {string} roomId
   * @param {number} [limit=200]
   * @returns {Promise<Array<{messageId: string, senderName: string|null, media: object, createdAt: string}>>}
   */
  async listMedia(roomId, limit = 200) {
    const rows = await Message.findAll({
      attributes: ['id', 'media', 'createdAt'],
      where: {
        roomId,
        media: { [Op.ne]: null },
      },
      include: [{ model: User, as: 'sender', attributes: PUBLIC_USER_ATTRS }],
      order: [['createdAt', 'ASC']],
      limit,
    });
    return rows.map((row) => ({
      messageId: row.id,
      senderName: row.sender ? row.sender.displayName : null,
      media: row.media,
      createdAt: row.createdAt instanceof Date ? row.createdAt.toISOString() : row.createdAt,
    }));
  }
}

module.exports = new MessageRepository();
