'use strict';

/**
 * controllers/auth.controller.js
 * Handler REST untuk pendaftaran, log masuk dan profil semasa.
 */

const authService = require('../services/auth.service');

/**
 * POST /api/auth/register
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function register(req, res) {
  const result = await authService.register(req.body);
  res.status(201).json(result);
}

/**
 * POST /api/auth/login
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function login(req, res) {
  const result = await authService.login(req.body);
  res.status(200).json(result);
}

/**
 * GET /api/auth/me
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function me(req, res) {
  res.status(200).json({ user: req.user });
}

module.exports = { register, login, me };
