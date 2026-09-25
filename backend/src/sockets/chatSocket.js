'use strict';

/**
 * sockets/chatSocket.js
 * PENGURUS SOCKET UTAMA FASA 1 - Modul Utama Sembang & Masa Nyata.
 *
 * Event dikendalikan (Client -> Server):
 *   - connection        : pendaftaran handler + bootstrap sesi
 *   - join_room         : masuk namespace bilik (semakan keahlian)
 *   - send_message      : simpan mesej + ack tick tunggal + broadcast
 *   - message_read      : read receipt => tick berganda penghantar
 *   - typing_status     : "Typing..." masa nyata (broadcast kepada rakan)
 *   - add_reaction      : toggle reaksi pantas + broadcast
 *   - disconnect        : pembersihan status typing + kemas kini lastSeen
 *
 * Event diterbitkan (Server -> Client):
 *   - connection_ready, room_joined, new_message, message_ack,
 *     read_receipt, typing_status, reaction_updated, error
 */

const { Server } = require('socket.io');
const config = require('../config/env');
const logger = require('../utils/logger');
const { SOCKET_EVENTS } = require('../utils/constants');
const socketAuth = require('./socketAuth');
const roomService = require('../services/room.service');
const messageService = require('../services/message.service');
const userRepository = require('../repositories/user.repository');
const ApiError = require('../utils/apiError');
const {
  validateJoinRoom,
  validateSendMessage,
  validateMessageRead,
  validateTypingStatus,
  validateAddReaction,
} = require('../validators/chat.validator');

const log = logger.child('ChatSocket');

/**
 * Mendaftarkan Socket.IO ke HTTP server.
 * @param {import('http').Server} httpServer
 * @returns {import('socket.io').Server}
 */
function registerChatSocket(httpServer) {
  const io = new Server(httpServer, {
    cors: {
      origin: config.corsOrigins,
      methods: ['GET', 'POST'],
      credentials: true,
    },
    pingInterval: config.socketPingIntervalMs,
    pingTimeout: config.socketPingTimeoutMs,
    maxHttpBufferSize: 1e6,
  });

  io.use(socketAuth);
  io.on('connection', (socket) => handleConnection(io, socket));

  log.info('Socket.IO chat server didaftarkan', {
    pingIntervalMs: config.socketPingIntervalMs,
    pingTimeoutMs: config.socketPingTimeoutMs,
  });
  return io;
}

/**
 * Mendaftarkan semua handler untuk satu sambungan.
 * @param {import('socket.io').Server} io
 * @param {import('socket.io').Socket} socket
 */
function handleConnection(io, socket) {
  const authUser = socket.data.user;
  log.info('Klien disambungkan', {
    socketId: socket.id,
    userId: authUser.userId,
    username: authUser.username,
  });

  // Set bilik di mana pengguna melaporkan dirinya sedang menaip -
  // digunakan untuk pembersihan automatik semasa disconnect.
  const typingRooms = new Set();

  socket.emit(SOCKET_EVENTS.CONNECTION_READY, {
    userId: authUser.userId,
    username: authUser.username,
    displayName: authUser.displayName,
    serverTime: new Date().toISOString(),
  });

  /**
   * Membalut handler async supaya sebarang ralat dihantar sebagai
   * event `error` yang berstruktur kepada klien (bukan crash).
   * @param {string} eventName
   * @param {Function} handler
   * @returns {Function}
   */
  const safeHandler = (eventName, handler) => async (payload) => {
    try {
      await handler(payload);
    } catch (err) {
      const known = err instanceof ApiError;
      log.error(`Handler "${eventName}" gagal`, {
        socketId: socket.id,
        userId: authUser.userId,
        message: err.message,
        ...(known ? {} : { stack: err.stack }),
      });
      socket.emit(SOCKET_EVENTS.ERROR, {
        event: eventName,
        code: known ? err.code : 'INTERNAL_ERROR',
        message: known ? err.message : 'Ralat pelayan dalaman',
        details: err.details ?? null,
      });
    }
  };

  // ------------------------------------------------------------------
  // join_room: sahkan keahlian -> masuk bilik -> pulangkan ahli + sejarah
  // ------------------------------------------------------------------
  socket.on(
    SOCKET_EVENTS.JOIN_ROOM,
    safeHandler(SOCKET_EVENTS.JOIN_ROOM, async (rawPayload) => {
      const payload = validateJoinRoom(rawPayload);
      await roomService.assertMembership(payload.roomId, authUser.userId);
      const members = await roomService.getRoomMembers(payload.roomId, authUser.userId);
      const history = await messageService.listHistory(payload.roomId, authUser.userId, {
        limit: config.historyPageSizeDefault,
      });
      await socket.join(payload.roomId);
      socket.emit(SOCKET_EVENTS.ROOM_JOINED, {
        roomId: payload.roomId,
        members,
        messages: history.messages,
        serverTime: new Date().toISOString(),
      });
      log.info('Klien menyertai bilik', {
        socketId: socket.id,
        userId: authUser.userId,
        roomId: payload.roomId,
      });
    })
  );

  // ------------------------------------------------------------------
  // send_message: simpan -> ack tick tunggal kepada penghantar ->
  //               broadcast `new_message` kepada ahli bilik lain
  // ------------------------------------------------------------------
  socket.on(
    SOCKET_EVENTS.SEND_MESSAGE,
    safeHandler(SOCKET_EVENTS.SEND_MESSAGE, async (rawPayload) => {
      const payload = validateSendMessage(rawPayload);
      const result = await messageService.sendMessage({
        roomId: payload.roomId,
        senderId: authUser.userId,
        text: payload.text,
        isSilent: payload.isSilent,
        replyToMessageId: payload.replyToMessageId,
        tempId: payload.tempId,
      });

      socket.emit(SOCKET_EVENTS.MESSAGE_ACK, {
        tempId: result.tempId,
        message: result.message,
        status: 'sent',
        serverTime: new Date().toISOString(),
      });
      socket.to(payload.roomId).emit(SOCKET_EVENTS.NEW_MESSAGE, {
        message: result.message,
      });
    })
  );

  // ------------------------------------------------------------------
  // message_read: tanda dibaca => tick berganda penghantar
  // ------------------------------------------------------------------
  socket.on(
    SOCKET_EVENTS.MESSAGE_READ,
    safeHandler(SOCKET_EVENTS.MESSAGE_READ, async (rawPayload) => {
      const payload = validateMessageRead(rawPayload);
      const result = await messageService.markRoomRead({
        roomId: payload.roomId,
        userId: authUser.userId,
        lastReadMessageId: payload.lastReadMessageId,
      });

      if (result.messageIds.length === 0) {
        return;
      }
      io.to(payload.roomId).emit(SOCKET_EVENTS.READ_RECEIPT, {
        roomId: payload.roomId,
        userId: authUser.userId,
        messageIds: result.messageIds,
        readAt: result.readAt,
      });
    })
  );

  // ------------------------------------------------------------------
  // typing_status: "Typing..." masa nyata (tidak disimpan ke DB)
  // ------------------------------------------------------------------
  socket.on(
    SOCKET_EVENTS.TYPING_STATUS,
    safeHandler(SOCKET_EVENTS.TYPING_STATUS, async (rawPayload) => {
      const payload = validateTypingStatus(rawPayload);
      await roomService.assertMembership(payload.roomId, authUser.userId);

      if (payload.isTyping) {
        typingRooms.add(payload.roomId);
      } else {
        typingRooms.delete(payload.roomId);
      }

      socket.to(payload.roomId).emit(SOCKET_EVENTS.TYPING_STATUS, {
        roomId: payload.roomId,
        userId: authUser.userId,
        displayName: authUser.displayName,
        isTyping: payload.isTyping,
        serverTime: new Date().toISOString(),
      });
    })
  );

  // ------------------------------------------------------------------
  // add_reaction: toggle reaksi -> broadcast keadaan terkini ke bilik
  // ------------------------------------------------------------------
  socket.on(
    SOCKET_EVENTS.ADD_REACTION,
    safeHandler(SOCKET_EVENTS.ADD_REACTION, async (rawPayload) => {
      const payload = validateAddReaction(rawPayload);
      const result = await messageService.toggleReaction({
        messageId: payload.messageId,
        userId: authUser.userId,
        emoji: payload.emoji,
      });

      io.to(result.roomId).emit(SOCKET_EVENTS.REACTION_UPDATED, {
        messageId: result.messageId,
        roomId: result.roomId,
        reactions: result.reactions,
        updatedBy: authUser.userId,
        serverTime: new Date().toISOString(),
      });
    })
  );

  // ------------------------------------------------------------------
  // disconnect: bersihkan status typing + kemas kini lastSeenAt
  // ------------------------------------------------------------------
  socket.on('disconnect', async (reason) => {
    try {
      for (const roomId of typingRooms) {
        socket.to(roomId).emit(SOCKET_EVENTS.TYPING_STATUS, {
          roomId,
          userId: authUser.userId,
          displayName: authUser.displayName,
          isTyping: false,
          serverTime: new Date().toISOString(),
        });
      }
      typingRooms.clear();
      await userRepository.updateLastSeen(authUser.userId, new Date());
    } catch (err) {
      log.error('Pembersihan disconnect gagal', {
        socketId: socket.id,
        userId: authUser.userId,
        message: err.message,
      });
    }
    log.info('Klien terputus', {
      socketId: socket.id,
      userId: authUser.userId,
      reason,
    });
  });
}

module.exports = { registerChatSocket };
