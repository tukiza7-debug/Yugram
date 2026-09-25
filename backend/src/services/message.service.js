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
const { SOCKET_EVENTS } = require('../utils/constants');
const socketGateway = require('../sockets/socketGateway');
const messageRepository = require('../repositories/message.repository');
const roomRepository = require('../repositories/room.repository');
const roomService = require('./room.service');
const notificationService = require('./notification.service');

const log = logger.child('MessageService');

class MessageService {
  /**
   * Fitur 10 + 64 + FASA 2/3: Hantar mesej (direct @ kumpulan) dengan
   * bendera silent, media, reply dan label terusan (forwardedFromName).
   * Peraturan kandungan: teks ATAU media wajib ada.
   * @param {{roomId: string, senderId: string, text: string,
   *          isSilent: boolean, replyToMessageId: string|null,
   *          media: object|null, forwardedFromName: string|null,
   *          tempId: string|null}} input
   * @returns {Promise<{message: object, tempId: string|null}>}
   * @throws {ApiError} 429 rate limit, 403 bukan ahli, 400 reply/kandungan tidak sah
   */
  async sendMessage({
    roomId,
    senderId,
    text,
    isSilent,
    replyToMessageId,
    media,
    forwardedFromName,
    tempId,
  }) {
    const limitResult = rateLimiter.tryConsume(`send:${senderId}`);
    if (!limitResult.allowed) {
      throw ApiError.tooManyRequests(
        `Terlalu banyak mesej. Cuba lagi dalam ${Math.ceil(limitResult.retryAfterMs / 1000)} saat.`
      );
    }

    await roomService.assertMembership(roomId, senderId);

    const trimmedText = typeof text === 'string' ? text.trim() : '';
    const hasMedia = media !== null && media !== undefined;
    if (trimmedText.length === 0 && !hasMedia) {
      throw ApiError.badRequest('Mesej mesti mengandungi teks atau media');
    }

    if (replyToMessageId) {
      const parent = await messageRepository.findById(replyToMessageId);
      if (!parent || parent.roomId !== roomId) {
        throw ApiError.badRequest('replyToMessageId tidak tergolong dalam bilik ini');
      }
    }

    const message = await messageRepository.createMessage({
      roomId,
      senderId,
      text: trimmedText,
      isSilent: Boolean(isSilent),
      replyToMessageId: replyToMessageId || null,
      media: hasMedia ? media : null,
      forwardedFromName: forwardedFromName || null,
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

  /**
   * FASA 3: Edit mesej sendiri (isEdited=true + editedAt). Broadcast
   * `message_edited` dihantar kepada seluruh bilik termasuk penghantar
   * supaya peranti lain turut dikemas kini.
   * @param {{messageId: string, userId: string, text: string}} input
   * @returns {Promise<object>} payload mesej selepas edit
   */
  async editMessage({ messageId, userId, text }) {
    const message = await messageRepository.findById(messageId);
    if (!message) {
      throw ApiError.notFound('Mesej tidak dijumpai');
    }
    if (message.senderId !== userId) {
      throw ApiError.forbidden('Hanya penghantar mesej boleh mengeditnya');
    }
    const updated = await messageRepository.updateText(messageId, text);
    const payload = updated.toPayloadJSON();
    socketGateway.publishToRoom(payload.roomId, SOCKET_EVENTS.MESSAGE_EDITED, {
      roomId: payload.roomId,
      message: payload,
    });
    log.info('Mesej diedit', { messageId, userId, roomId: payload.roomId });
    return payload;
  }

  /**
   * FASA 3: Padam mesej sendiri; admin kumpulan boleh memadam mesej
   * ahli lain. Broadcast `message_deleted` kepada seluruh bilik.
   * @param {{messageId: string, userId: string}} input
   * @returns {Promise<{messageId: string, roomId: string}>}
   */
  async deleteMessage({ messageId, userId }) {
    const message = await messageRepository.findById(messageId);
    if (!message) {
      throw ApiError.notFound('Mesej tidak dijumpai');
    }
    await roomService.assertMembership(message.roomId, userId);

    if (message.senderId !== userId) {
      const role = await roomRepository.getMemberRole(message.roomId, userId);
      if (role !== 'admin') {
        throw ApiError.forbidden('Hanya penghantar atau admin boleh memadam mesej ini');
      }
    }

    const roomId = await messageRepository.deleteById(messageId);
    socketGateway.publishToRoom(roomId, SOCKET_EVENTS.MESSAGE_DELETED, {
      roomId,
      messageId,
      deletedBy: userId,
    });
    log.info('Mesej dipadam', { messageId, userId, roomId });
    return { messageId, roomId };
  }

  /**
   * FASA 3: Carian mesej dalam bilik mengikut kata kunci.
   * @param {{roomId: string, userId: string, query: string, limit?: number}} input
   * @returns {Promise<object[]>}
   */
  async searchMessages({ roomId, userId, query, limit = 30 }) {
    await roomService.assertMembership(roomId, userId);
    const rows = await messageRepository.searchMessages(roomId, query, {
      limit: Math.min(Math.max(Number.parseInt(limit, 10) || 30, 1), 100),
    });
    return rows.map((message) => message.toPayloadJSON());
  }

  /**
   * FASA 2: Senarai media dalam bilik (untuk muat turun pukal).
   * @param {{roomId: string, userId: string}} input
   * @returns {Promise<Array<object>>}
   */
  async listRoomMedia({ roomId, userId }) {
    await roomService.assertMembership(roomId, userId);
    return messageRepository.listMedia(roomId);
  }
}

module.exports = new MessageService();
