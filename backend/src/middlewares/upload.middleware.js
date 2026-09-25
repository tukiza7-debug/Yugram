'use strict';

/**
 * middlewares/upload.middleware.js
 * FASA 2 - Konfigurasi multer untuk muat naik media:
 *  - storan cakera: uploads/<uuid><ext> (nama asal tidak dipakai - selamat)
 *  - had saiz: MEDIA_MAX_SIZE_MB (config)
 *  - penapis mime: imej / video mp4 / pdf sahaja
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const multer = require('multer');
const config = require('../config/env');
const { ALLOWED_MEDIA_MIME_TYPES, MEDIA_MAX_SIZE_BYTES } = require('../utils/constants');
const ApiError = require('../utils/apiError');

const UPLOAD_DIR = path.join(__dirname, '..', '..', 'uploads');

// Pastikan direktori wujud semasa but (recursive: tiada ralat jika sedia ada).
fs.mkdirSync(UPLOAD_DIR, { recursive: true });

const storage = multer.diskStorage({
  destination: (req, file, callback) => {
    callback(null, UPLOAD_DIR);
  },
  filename: (req, file, callback) => {
    // Nama cakera sentiasa rawak; sambungan diambil dari mime type yang
    // disahkan supaya fail jahat tidak menyamar sebagai sambungan lain.
    const extensionByMime = {
      'image/jpeg': '.jpg',
      'image/png': '.png',
      'image/webp': '.webp',
      'image/gif': '.gif',
      'video/mp4': '.mp4',
      'application/pdf': '.pdf',
    };
    const extension = extensionByMime[file.mimetype] || '';
    callback(null, `${crypto.randomUUID()}${extension}`);
  },
});

const fileFilter = (req, file, callback) => {
  if (!ALLOWED_MEDIA_MIME_TYPES.includes(file.mimetype)) {
    callback(
      ApiError.badRequest(`Jenis fail tidak dibenarkan: ${file.mimetype}`, [
        { field: 'file', message: `Jenis dibenarkan: ${ALLOWED_MEDIA_MIME_TYPES.join(', ')}` },
      ])
    );
    return;
  }
  callback(null, true);
};

const upload = multer({
  storage,
  fileFilter,
  limits: {
    fileSize: MEDIA_MAX_SIZE_BYTES,
    files: 1,
  },
});

module.exports = { upload, UPLOAD_DIR };
