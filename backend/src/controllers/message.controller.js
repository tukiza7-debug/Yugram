'use strict';

/**
 * controllers/message.controller.js
 * Handler REST mesej: sejarah + carian (FASA 3), edit, padam (FASA 3)
 * dan senarai media untuk muat turun pukal (FASA 2).
 */

const ApiError = require('../utils/apiError');
const messageService = require('../services/message.service');
const {
  listMessagesQuerySchema,
  roomIdParamSchema,
  messageIdParamSchema,
  editMessageSchema,
} = require('../validators/rest.validator');

/**
 * Menyah struktur param/query dengan Joi dan membuang ApiError 400.
 * @param {Joi.ObjectSchema} schema
 * @param {unknown} payload
 * @param {string} label
 * @returns {object}
 */
function validated(schema, payload, label) {
  const check = schema.validate(payload, { convert: true, stripUnknown: true });
  if (check.error) {
    throw ApiError.badRequest(`${label} tidak sah`, check.error.details.map((detail) => ({
      field: detail.path.join('.'),
      message: detail.message,
    })));
  }
  return check.value;
}

/**
 * GET /api/rooms/:roomId/messages?limit=&before=&q=
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function listMessages(req, res) {
  const params = validated(roomIdParamSchema, req.params, 'roomId');
  const query = validated(listMessagesQuerySchema, req.query, 'Parameter pertanyaan');

  // FASA 3: jika parameter carian "q" diberi, pindahkan ke carian mesej.
  if (query.q) {
    const results = await messageService.searchMessages({
      roomId: params.roomId,
      userId: req.user.id,
      query: query.q,
      limit: query.limit,
    });
    res.status(200).json({ messages: results, hasMore: false, nextBefore: null });
    return;
  }

  const result = await messageService.listHistory(params.roomId, req.user.id, {
    limit: query.limit,
    before: query.before,
  });
  res.status(200).json(result);
}

/**
 * PATCH /api/messages/:messageId { text } (FASA 3 - edit mesej sendiri)
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function editMessage(req, res) {
  const params = validated(messageIdParamSchema, req.params, 'messageId');
  const body = validated(editMessageSchema, req.body, 'Badan permintaan');
  const message = await messageService.editMessage({
    messageId: params.messageId,
    userId: req.user.id,
    text: body.text,
  });
  res.status(200).json({ message });
}

/**
 * DELETE /api/messages/:messageId (FASA 3 - padam sendiri / oleh admin)
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function deleteMessage(req, res) {
  const params = validated(messageIdParamSchema, req.params, 'messageId');
  const result = await messageService.deleteMessage({
    messageId: params.messageId,
    userId: req.user.id,
  });
  res.status(200).json(result);
}

/**
 * GET /api/rooms/:roomId/media (FASA 2 - senarai media untuk muat turun pukal)
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function listRoomMedia(req, res) {
  const params = validated(roomIdParamSchema, req.params, 'roomId');
  const mediaItems = await messageService.listRoomMedia({
    roomId: params.roomId,
    userId: req.user.id,
  });
  res.status(200).json({ media: mediaItems });
}

module.exports = { listMessages, editMessage, deleteMessage, listRoomMedia };
