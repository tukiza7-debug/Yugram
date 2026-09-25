'use strict';

/**
 * validators/chat.validator.js
 * Skema Joi untuk semua event Socket.io + helper pengesahan.
 * Payload tidak sah akan membuang ApiError dengan butiran medan.
 */

const Joi = require('joi');
const ApiError = require('../utils/apiError');
const {
  MESSAGE_MAX_LENGTH,
  REACTION_MAX_LENGTH,
  FORWARDED_FROM_MAX_LENGTH,
  MEDIA_MAX_SIZE_BYTES,
} = require('../utils/constants');

const uuidSchema = Joi.string().uuid({ version: ['uuidv4', 'uuidv1', 'uuidv5'] });

const joinRoomSchema = Joi.object({
  roomId: uuidSchema.required(),
}).required();

/** Skema lampiran media dalam send_message (hasil /api/media) */
const mediaSchema = Joi.object({
  url: Joi.string().trim().min(1).max(512).required(),
  name: Joi.string().trim().max(255).allow('').default(''),
  mimeType: Joi.string().trim().max(128).allow('').default(''),
  size: Joi.number().integer().min(0).max(MEDIA_MAX_SIZE_BYTES).default(0),
});

const sendMessageSchema = Joi.object({
  roomId: uuidSchema.required(),
  // Teks dibenarkan kosong APABILA media dilampirkan; peraturan
  // "teks ATAU media" dikuatkuasakan dalam message.service.
  text: Joi.string().trim().allow('').max(MESSAGE_MAX_LENGTH).default(''),
  isSilent: Joi.boolean().default(false),
  replyToMessageId: uuidSchema.allow(null).default(null),
  media: mediaSchema.allow(null).default(null),
  forwardedFromName: Joi.string().trim().allow(null, '').max(FORWARDED_FROM_MAX_LENGTH).default(null),
  tempId: Joi.string().trim().max(64).allow(null, '').default(null),
}).required();

const messageReadSchema = Joi.object({
  roomId: uuidSchema.required(),
  lastReadMessageId: uuidSchema.required(),
}).required();

const typingStatusSchema = Joi.object({
  roomId: uuidSchema.required(),
  isTyping: Joi.boolean().required(),
}).required();

const addReactionSchema = Joi.object({
  messageId: uuidSchema.required(),
  emoji: Joi.string().trim().min(1).max(REACTION_MAX_LENGTH).required(),
}).required();

/**
 * Mengesahkan payload dengan skema Joi.
 * @param {Joi.ObjectSchema} schema
 * @param {unknown} payload
 * @returns {object} nilai yang telah dinormalisasi
 * @throws {ApiError} 400 VALIDATION_ERROR dengan butiran medan
 */
function validatePayload(schema, payload) {
  const { value, error } = schema.validate(payload ?? {}, {
    abortEarly: false,
    stripUnknown: true,
    convert: true,
  });
  if (error) {
    throw ApiError.badRequest(
      'Payload tidak sah',
      error.details.map((detail) => ({
        field: detail.path.join('.'),
        message: detail.message,
      }))
    );
  }
  return value;
}

const validateJoinRoom = (payload) => validatePayload(joinRoomSchema, payload);
const validateSendMessage = (payload) => validatePayload(sendMessageSchema, payload);
const validateMessageRead = (payload) => validatePayload(messageReadSchema, payload);
const validateTypingStatus = (payload) => validatePayload(typingStatusSchema, payload);
const validateAddReaction = (payload) => validatePayload(addReactionSchema, payload);

module.exports = {
  validateJoinRoom,
  validateSendMessage,
  validateMessageRead,
  validateTypingStatus,
  validateAddReaction,
  validatePayload,
  schemas: {
    joinRoomSchema,
    sendMessageSchema,
    messageReadSchema,
    typingStatusSchema,
    addReactionSchema,
  },
};
