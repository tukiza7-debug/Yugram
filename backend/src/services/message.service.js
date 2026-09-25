'use strict';

/**
 * services/message.service.js
 * Logik bisnes mesej: hantar (dengan silent + reply + rate limit),
 * read receipt (tick berganda), toggle reaksi, sejarah.
 */

const config = require('../config/env');
const ApiError = require('../utils/apiError');
const logger = require('../utils/logger');
const rateLimiter = require('../utils/rateLimiter');
const messageRepository = require('../repositories/message.repository');
const roomRepository = require('../repositories/room.repository');
const roomService = require('./room.service');
const notificationService = require('./notification.service');

const log = logger.child('MessageService');

class MessageService {
  /**
   * Fitur 10 + 64: Hantar mesej (1-ke-1) dengan bendera silent.
   * Aliran tick tunggal: mesej berjaya disimpan => ack kepada penghantar.
   * @param {{roomId: string, senderId: string, text: string,
   *          isSilent: boolean, replyToMessageId: string|null,
   *          tempId: string|null}} input
   * @returns {Promise<{message: object, tempId: string|null}>}
   * @throws {ApiError} 429 rate limit, 403 bukan ahli, 400 reply tidak sah
   */
  async sendMessage({ roomId, senderId, text, isSilent, replyToMessageId, tempId }) {
    const limitResult = rateLimiter.tryConsume(`send:${senderId}`);
    if (!limitResult.allowed) {
      throw ApiError.tooManyRequests(
        `Terlalu banyak mesej. Cuba lagi dalam ${Math.ceil(limitResult.retryAfterMs / 1000)} saat.`
      );
    }

    await roomService.assertMembership(roomId, senderId);

    if (replyToMessageId) {
      const parent = await messageRepository.findById(replyToMessageId);
      if (!parent || parent.roomId !== roomId) {
        throw ApiError.badRequest('replyToMessageId tidak tergolong dalam bilik ini');
      }
    }

    const message = await messageRepository.createMessage({
      roomId,
      senderId,
      text,
      isSilent: Boolean(isSilent),
      replyToMessageId: replyToMessageId || null,
    });
    const payload = message.toPayloadJSON();

    log.info('Mesej disimpan', {
      messageId: message.id,
      roomId,
      senderId,
      isSilent: payload.isSilent,
      replyToMessageId: payload.replyToMessageId,
    });

    // Push notification dihantar secara tidak segerak (tidak menyekat socket).
    setImmediate(() => {
      notificationService
        .notifyRoomMembers({ message: payload, excludeUserId: senderId })
        .catch((err) => log.error('Push notification gagal', { messageId: message.id, message: err.message }));
    });

    return { message: payload, tempId: tempId || null };
  }

  /**
   * Fitur 11: Menanda bilik dibaca sehingga `lastReadMessageId`.
   * Menjana data untuk tick berganda penghantar.
   * @param {{roomId: string, userId: string, lastReadMessageId: string}} input
   * @returns {Promise<{messageIds: string[], readAt: string}>}
   */
  async markRoomRead({ roomId, userId, lastReadMessageId }) {
    await roomService.assertMembership(roomId, userId);

    const anchor = await messageRepository.findById(lastReadMessageId);
    if (!anchor || anchor.roomId !== roomId) {
      throw ApiError.badRequest('lastReadMessageId tidak tergolong dalam bilik ini');
    }

    const readAt = new Date();
    const unreadMessageIds = await messageRepository.findUnreadMessageIds(
      roomId,
      userId,
      anchor.createdAt
    );

    if (unreadMessageIds.length > 0) {
      await messageRepository.markMessagesRead({
        roomId,
        userId,
        messageIds: unreadMessageIds,
        readAt,
      });
    }

    await roomRepository.setLastRead({
      roomId,
      userId,
      messageId: lastReadMessageId,
      readAt,
    });

    return { messageIds: unreadMessageIds, readAt: readAt.toISOString() };
  }

  /**
   * Fitur 67: Toggle reaksi pantas per pengguna (satu reaksi/pengguna).
   * Emoji sama = buang; emoji lain = ganti.
   * @param {{messageId: string, userId: string, emoji: string}} input
   * @returns {Promise<{messageId: string, roomId: string, reactions: object[]}>}
   */
  async toggleReaction({ messageId, userId, emoji }) {
    const message = await messageRepository.findById(messageId);
    if (!message) {
      throw ApiError.notFound('Mesej tidak dijumpai');
    }
    await roomService.assertMembership(message.roomId, userId);

    const current = Array.isArray(message.reactions) ? message.reactions : [];
    const reactions = current.map((reaction) => ({
      userId: reaction.userId,
      emoji: reaction.emoji,
      createdAt: reaction.createdAt instanceof Date ? reaction.createdAt.toISOString() : reaction.createdAt,
    }));

    const existingIndex = reactions.findIndex((reaction) => reaction.userId === userId);
    if (existingIndex >= 0 && reactions[existingIndex].emoji === emoji) {
      reactions.splice(existingIndex, 1);
    } else if (existingIndex >= 0) {
      reactions[existingIndex] = { userId, emoji, createdAt: new Date().toISOString() };
    } else {
      reactions.push({ userId, emoji, createdAt: new Date().toISOString() });
    }

    const updated = await messageRepository.updateReactions(message.id, reactions);
    log.info('Reaksi dikemas kini', {
      messageId: message.id,
      userId,
      emoji,
      total: reactions.length,
    });

    return {
      messageId: updated.id,
      roomId: updated.roomId,
      reactions: Array.isArray(updated.reactions) ? updated.reactions : [],
    };
  }

  /**
   * Sejarah mesej bilik (paginasi kursor `before`).
   * @param {string} roomId
   * @param {string} userId
   * @param {{limit?: unknown, before?: unknown}} options
   * @returns {Promise<{messages: object[], hasMore: boolean, nextBefore: string|null}>}
   */
  async listHistory(roomId, userId, options = {}) {
    await roomService.assertMembership(roomId, userId);

    const parsedLimit = Number.parseInt(options.limit, 10);
    const limit =
      Number.isFinite(parsedLimit) && parsedLimit >= 1 && parsedLimit <= 100
        ? parsedLimit
        : config.historyPageSizeDefault;

    let before = null;
    if (options.before) {
      before = new Date(options.before);
      if (Number.isNaN(before.getTime())) {
        throw ApiError.badRequest('Parameter "before" bukan tarikh ISO yang sah');
      }
    }

    const result = await messageRepository.listMessages(roomId, { limit, before });
    return {
      messages: result.messages.map((message) => message.toPayloadJSON()),
      hasMore: result.hasMore,
      nextBefore: result.nextBefore,
    };
  }
}

module.exports = new MessageService();
