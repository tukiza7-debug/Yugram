'use strict';

/**
 * controllers/message.controller.js
 * Handler REST untuk sejarah mesej (paginasi kursor).
 */

const Joi = require('joi');
const ApiError = require('../utils/apiError');
const messageService = require('../services/message.service');
const { listMessagesQuerySchema, roomIdParamSchema } = require('../validators/rest.validator');

/**
 * GET /api/rooms/:roomId/messages?limit=&before=
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function listMessages(req, res) {
  const paramCheck = roomIdParamSchema.validate(req.params, { stripUnknown: true });
  if (paramCheck.error) {
    throw ApiError.badRequest('roomId bukan UUID yang sah', paramCheck.error.details.map((d) => ({
      field: d.path.join('.'),
      message: d.message,
    })));
  }

  const queryCheck = listMessagesQuerySchema.validate(req.query, { convert: true, stripUnknown: true });
  if (queryCheck.error) {
    throw ApiError.badRequest('Parameter pertanyaan tidak sah', queryCheck.error.details.map((d) => ({
      field: d.path.join('.'),
      message: d.message,
    })));
  }

  const result = await messageService.listHistory(paramCheck.value.roomId, req.user.id, {
    limit: queryCheck.value.limit,
    before: queryCheck.value.before,
  });
  res.status(200).json(result);
}

module.exports = { listMessages };
