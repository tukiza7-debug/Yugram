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

class MessageRepository {
  /**
   * Mencipta mesej dan memulangkannya berserta sender + replyTo.
   * @param {{roomId: string, senderId: string, text: string, isSilent: boolean, replyToMessageId: string|null}} input
   * @returns {Promise<Message>}
   */
  async createMessage({ roomId, senderId, text, isSilent, replyToMessageId }) {
    const created = await Message.create({
      roomId,
      senderId,
      text,
      isSilent,
      replyToMessageId: replyToMessageId || null,
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
}

module.exports = new MessageRepository();
