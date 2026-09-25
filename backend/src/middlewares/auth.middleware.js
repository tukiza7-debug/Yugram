'use strict';

/**
 * middlewares/auth.middleware.js
 * Middleware JWT untuk laluan REST (Authorization: Bearer <token>).
 */

const authService = require('../services/auth.service');
const userRepository = require('../repositories/user.repository');
const ApiError = require('../utils/apiError');

/**
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 * @param {import('express').NextFunction} next
 */
async function authMiddleware(req, res, next) {
  try {
    const header = req.headers.authorization || '';
    const token = header.startsWith('Bearer ') ? header.slice(7) : null;
    if (!token) {
      throw ApiError.unauthorized('Token pembawa (bearer) tiada');
    }

    const payload = authService.verifyAccessToken(token);
    const user = await userRepository.findById(payload.userId);
    if (!user) {
      throw ApiError.unauthorized('Pengguna tidak lagi wujud');
    }

    req.user = user.toPublicJSON();
    next();
  } catch (err) {
    next(err);
  }
}

module.exports = { authMiddleware };
