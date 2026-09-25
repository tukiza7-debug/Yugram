'use strict';

/**
 * utils/constants.js
 * Kontrak tetap sistem: nama event Socket.io, had had (limits),
 * senarai reaksi lalai dan atribut pengguna awam.
 */

/** Nama event Socket.io (kontrak backend <-> frontend) */
const SOCKET_EVENTS = {
  // Client -> Server
  JOIN_ROOM: 'join_room',
  SEND_MESSAGE: 'send_message',
  MESSAGE_READ: 'message_read',
  TYPING_STATUS: 'typing_status',
  ADD_REACTION: 'add_reaction',

  // Server -> Client
  CONNECTION_READY: 'connection_ready',
  ROOM_JOINED: 'room_joined',
  NEW_MESSAGE: 'new_message',
  MESSAGE_ACK: 'message_ack',
  READ_RECEIPT: 'read_receipt',
  REACTION_UPDATED: 'reaction_updated',
  MESSAGE_EDITED: 'message_edited',
  MESSAGE_DELETED: 'message_deleted',
  ROOM_UPDATED: 'room_updated',
  ROOM_DELETED: 'room_deleted',
  ERROR: 'error',
};

/** Status penghantaran mesej (dari perspektif penghantar) */
const MESSAGE_STATUS = {
  SENDING: 'sending', // belum diterima server
  SENT: 'sent', // tick tunggal - diterima server
  READ: 'read', // tick berganda - dibaca penerima
  FAILED: 'failed', // gagal dihantar
};

/** Senarai reaksi pantas (selari dengan pilihan UI Flutter) */
const DEFAULT_REACTIONS = ['👍', '❤️', '😂', '😮', '😢', '🔥', '🙏', '🎉'];

const MESSAGE_MAX_LENGTH = 4096;
const REACTION_MAX_LENGTH = 32;
const USERNAME_MAX_LENGTH = 32;
const DISPLAY_NAME_MAX_LENGTH = 64;
const USER_BIO_MAX_LENGTH = 280;
const ROOM_NAME_MAX_LENGTH = 64;
const FORWARDED_FROM_MAX_LENGTH = 64;
const SEARCH_QUERY_MAX_LENGTH = 64;
const USER_SEARCH_LIMIT = 20;
const MEDIA_MAX_SIZE_MB = 10;
const MEDIA_MAX_SIZE_BYTES = MEDIA_MAX_SIZE_MB * 1024 * 1024;

/** Jenis media yang dibenarkan untuk muat naik (mime types) */
const ALLOWED_MEDIA_MIME_TYPES = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/gif',
  'video/mp4',
  'application/pdf',
];
const ROOM_TYPE = {
  DIRECT: 'direct',
  GROUP: 'group',
};
const MEMBER_ROLE = {
  MEMBER: 'member',
  ADMIN: 'admin',
};

/** Atribut pengguna yang selamat diterbitkan kepada klien (tanpa passwordHash) */
const PUBLIC_USER_ATTRS = ['id', 'username', 'displayName', 'avatarUrl', 'lastSeenAt'];

module.exports = {
  SOCKET_EVENTS,
  MESSAGE_STATUS,
  DEFAULT_REACTIONS,
  MESSAGE_MAX_LENGTH,
  REACTION_MAX_LENGTH,
  USERNAME_MAX_LENGTH,
  DISPLAY_NAME_MAX_LENGTH,
  USER_BIO_MAX_LENGTH,
  ROOM_NAME_MAX_LENGTH,
  FORWARDED_FROM_MAX_LENGTH,
  SEARCH_QUERY_MAX_LENGTH,
  USER_SEARCH_LIMIT,
  MEDIA_MAX_SIZE_MB,
  MEDIA_MAX_SIZE_BYTES,
  ALLOWED_MEDIA_MIME_TYPES,
  ROOM_TYPE,
  MEMBER_ROLE,
  PUBLIC_USER_ATTRS,
};
