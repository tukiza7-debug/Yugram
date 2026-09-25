'use strict';

/**
 * controllers/media.controller.js
 * FASA 2 - Handler REST muat naik media. Fail disimpan pada cakera
 * (uploads/) dan URL awamnya /uploads/<nama-fail> dihidang statik
 * oleh Express (app.js).
 */

const logger = require('../utils/logger');
const ApiError = require('../utils/apiError');

const log = logger.child('MediaController');

/**
 * POST /api/media (multipart/form-data, medan "file")
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function uploadMedia(req, res) {
  if (!req.file) {
    throw ApiError.badRequest('Fail "file" tiada dalam permintaan');
  }
  const media = {
    url: `/uploads/${req.file.filename}`,
    name: typeof req.body.name === 'string' && req.body.name.trim() !== ''
      ? req.body.name.trim().slice(0, 255)
      : req.file.originalname,
    mimeType: req.file.mimetype,
    size: req.file.size,
  };
  log.info('Media dimuat naik', { url: media.url, size: media.size, mimeType: media.mimeType });
  res.status(201).json({ media });
}

module.exports = { uploadMedia };
