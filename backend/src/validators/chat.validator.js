'use strict';

/**
 * validators/chat.validator.js
 * Skema Joi untuk semua event Socket.io + helper pengesahan.
 * Payload tidak sah akan membuang ApiError dengan butiran medan.
 */

const Joi = require('joi');
const ApiError = require('../utils/apiError');
const { MESSAGE_MAX_LENGTH, REACTION_MAX_LENGTH } = require('../utils/constants');

const uuidSchema = Joi.string().uuid({ version: ['uuidv4', 'uuidv1', 'uuidv5'] });

const joinRoomSchema = Joi.object({
  roomId: uuidSchema.required(),
}).required();

const sendMessageSchema = Joi.object({
  roomId: uuidSchema.required(),
  text: Joi.string().trim().min(1).max(MESSAGE_MAX_LENGTH).required(),
  isSilent: Joi.boolean().default(false),
  replyToMessageId: uuidSchema.allow(null).default(null),
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
