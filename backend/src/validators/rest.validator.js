'use strict';

/**
 * validators/rest.validator.js
 * Skema Joi untuk endpoint REST (auth, rooms, messages).
 */

const Joi = require('joi');
const {
  USERNAME_MAX_LENGTH,
  DISPLAY_NAME_MAX_LENGTH,
  ROOM_NAME_MAX_LENGTH,
} = require('../utils/constants');

const uuidSchema = Joi.string().uuid({ version: ['uuidv4', 'uuidv1', 'uuidv5'] });

const registerSchema = Joi.object({
  username: Joi.string()
    .pattern(/^[a-zA-Z0-9_]+$/)
    .min(3)
    .max(USERNAME_MAX_LENGTH)
    .required()
    .messages({
      'string.pattern.base': 'Username hanya dibenarkan mengandungi huruf, nombor dan underscore',
    }),
  displayName: Joi.string().trim().min(1).max(DISPLAY_NAME_MAX_LENGTH).required(),
  password: Joi.string()
    .min(8)
    .max(72)
    .pattern(/^(?=.*[A-Za-z])(?=.*\d)/)
    .required()
    .messages({
      'string.pattern.base': 'Kata laluan mesti mengandungi sekurang-kurangnya satu huruf dan satu nombor',
    }),
}).required();

const loginSchema = Joi.object({
  username: Joi.string().trim().min(3).max(USERNAME_MAX_LENGTH).required(),
  password: Joi.string().min(1).max(72).required(),
}).required();

const createDirectRoomSchema = Joi.object({
  peerUsername: Joi.string()
    .pattern(/^[a-zA-Z0-9_]+$/)
    .min(3)
    .max(USERNAME_MAX_LENGTH)
    .required(),
}).required();

const listMessagesQuerySchema = Joi.object({
  limit: Joi.number().integer().min(1).max(100).default(30),
  before: Joi.date().iso().optional(),
}).unknown(false);

const roomIdParamSchema = Joi.object({
  roomId: uuidSchema.required(),
}).unknown(false);

module.exports = {
  registerSchema,
  loginSchema,
  createDirectRoomSchema,
  listMessagesQuerySchema,
  roomIdParamSchema,
};
