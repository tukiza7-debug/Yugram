'use strict';

/**
 * middlewares/error.middleware.js
 * Pengendali ralat pusat Express - semua ralat menjadi JSON berstruktur.
 */

const ApiError = require('../utils/apiError');
const config = require('../config/env');
const logger = require('../utils/logger');

const log = logger.child('ErrorHandler');

/**
 * 404 untuk laluan yang tidak dijumpai.
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 * @param {import('express').NextFunction} next
 */
function notFoundHandler(req, res, next) {
  next(ApiError.notFound(`Laluan tidak dijumpai: ${req.method} ${req.originalUrl}`));
}

/**
 * Pengendali ralat terakhir.
 * @param {unknown} err
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 * @param {import('express').NextFunction} next
 */
function errorHandler(err, req, res, next) {
  if (res.headersSent) {
    next(err);
    return;
  }

  const isApiError = err instanceof ApiError;
  const statusCode = isApiError ? err.statusCode : 500;
  const code = isApiError ? err.code : 'INTERNAL_ERROR';
  const message = isApiError ? err.message : 'Ralat pelayan dalaman';

  if (!isApiError) {
    log.error('Ralat tidak dikendalikan', {
      method: req.method,
      path: req.originalUrl,
      message: err && err.message ? err.message : String(err),
      stack: err && err.stack ? err.stack : undefined,
    });
  }

  res.status(statusCode).json({
    error: {
      code,
      message,
      details: err && err.details ? err.details : null,
    },
  });
}

module.exports = { notFoundHandler, errorHandler };
