'use strict';

/**
 * utils/apiError.js
 * Kelas ralat aplikasi berstruktur yang membawa status HTTP, kod ralat
 * dan butiran pilihan. Digunakan merentas service/socket/controller.
 */

class ApiError extends Error {
  /**
   * @param {number} statusCode - Status HTTP (cth: 400, 401, 404)
   * @param {string} code - Kod ralat stabil (cth: 'VALIDATION_ERROR')
   * @param {string} message - Mesej ralat mesra manusia
   * @param {Array<{field: string, message: string}>|undefined} [details]
   */
  constructor(statusCode, code, message, details) {
    super(message);
    this.name = 'ApiError';
    this.statusCode = statusCode;
    this.code = code;
    this.details = details;
    Error.captureStackTrace(this, this.constructor);
  }

  static badRequest(message, details) {
    return new ApiError(400, 'VALIDATION_ERROR', message, details);
  }

  static unauthorized(message) {
    return new ApiError(401, 'UNAUTHORIZED', message);
  }

  static forbidden(message) {
    return new ApiError(403, 'FORBIDDEN', message);
  }

  static notFound(message) {
    return new ApiError(404, 'NOT_FOUND', message);
  }

  static conflict(message) {
    return new ApiError(409, 'CONFLICT', message);
  }

  static tooManyRequests(message) {
    return new ApiError(429, 'RATE_LIMITED', message);
  }

  static payloadTooLarge(message, details) {
    return new ApiError(413, 'PAYLOAD_TOO_LARGE', message, details);
  }

  static internal(message) {
    return new ApiError(500, 'INTERNAL_ERROR', message);
  }
}

module.exports = ApiError;
