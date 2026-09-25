'use strict';

/**
 * validators/rest.validator.js
 * Skema Joi untuk endpoint REST (auth, rooms, messages).
 */

const Joi = require('joi');
const {
  USERNAME_MAX_LENGTH,
  DISPLAY_NAME_MAX_LENGTH,
  USER_BIO_MAX_LENGTH,
  ROOM_NAME_MAX_LENGTH,
  FORWARDED_FROM_MAX_LENGTH,
  SEARCH_QUERY_MAX_LENGTH,
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
  // FASA 3: carian mesej dalam bilik (pilihan).
  q: Joi.string().trim().min(1).max(SEARCH_QUERY_MAX_LENGTH).optional(),
}).unknown(false);

const roomIdParamSchema = Joi.object({
  roomId: uuidSchema.required(),
}).unknown(false);

// ------------------------------------------------------------------
// FASA 2 - PROFIL, PENGGUNA & KUMPULAN
// ------------------------------------------------------------------

const updateProfileSchema = Joi.object({
  displayName: Joi.string().trim().min(1).max(DISPLAY_NAME_MAX_LENGTH).optional(),
  bio: Joi.string().trim().allow('').max(USER_BIO_MAX_LENGTH).optional(),
  username: Joi.string()
    .pattern(/^[a-zA-Z0-9_]+$/)
    .min(3)
    .max(USERNAME_MAX_LENGTH)
    .optional()
    .messages({
      'string.pattern.base': 'Username hanya dibenarkan mengandungi huruf, nombor dan underscore',
    }),
  avatarUrl: Joi.string().trim().allow('').max(512).optional(),
}).min(1).required();

const userSearchQuerySchema = Joi.object({
  q: Joi.string().trim().min(1).max(SEARCH_QUERY_MAX_LENGTH).required(),
}).unknown(false);

const createGroupSchema = Joi.object({
  name: Joi.string().trim().min(1).max(ROOM_NAME_MAX_LENGTH).required(),
  memberUsernames: Joi.array()
    .items(
      Joi.string()
        .pattern(/^[a-zA-Z0-9_]+$/)
        .min(3)
        .max(USERNAME_MAX_LENGTH)
    )
    .min(1)
    .max(100)
    .required(),
}).required();

const addMemberSchema = Joi.object({
  username: Joi.string()
    .pattern(/^[a-zA-Z0-9_]+$/)
    .min(3)
    .max(USERNAME_MAX_LENGTH)
    .required(),
}).required();

const updateMemberRoleSchema = Joi.object({
  role: Joi.string().valid('member', 'admin').required(),
}).required();

const renameRoomSchema = Joi.object({
  name: Joi.string().trim().min(1).max(ROOM_NAME_MAX_LENGTH).required(),
}).required();

const roomAndUserParamSchema = Joi.object({
  roomId: uuidSchema.required(),
  userId: uuidSchema.required(),
}).unknown(false);

// ------------------------------------------------------------------
// FASA 3 - EDIT / PADAM MESEJ
// ------------------------------------------------------------------

const messageIdParamSchema = Joi.object({
  messageId: uuidSchema.required(),
}).unknown(false);

const editMessageSchema = Joi.object({
  text: Joi.string().trim().min(1).max(4096).required(),
}).required();

module.exports = {
  registerSchema,
  loginSchema,
  createDirectRoomSchema,
  listMessagesQuerySchema,
  roomIdParamSchema,
  // FASA 2
  updateProfileSchema,
  userSearchQuerySchema,
  createGroupSchema,
  addMemberSchema,
  updateMemberRoleSchema,
  renameRoomSchema,
  roomAndUserParamSchema,
  // FASA 3
  messageIdParamSchema,
  editMessageSchema,
};
