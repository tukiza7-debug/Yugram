'use strict';

/**
 * controllers/user.controller.js
 * FASA 2 - Handler REST untuk profil & carian pengguna.
 */

const userService = require('../services/user.service');

/**
 * GET /api/auth/me - dipindahkan logiknya supaya konsisten (profil penuh).
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function getProfile(req, res) {
  const user = await userService.getProfile(req.user.id);
  res.status(200).json({ user });
}

/**
 * PATCH /api/users/me
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function updateProfile(req, res) {
  const user = await userService.updateProfile({
    userId: req.user.id,
    displayName: req.body.displayName,
    bio: req.body.bio,
    username: req.body.username,
    avatarUrl: req.body.avatarUrl,
  });
  res.status(200).json({ user });
}

/**
 * GET /api/users/search?q=...
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function searchUsers(req, res) {
  const users = await userService.searchUsers({
    requesterId: req.user.id,
    query: (req.validatedQuery && req.validatedQuery.q) || req.query.q,
  });
  res.status(200).json({ users });
}

module.exports = { getProfile, updateProfile, searchUsers };
