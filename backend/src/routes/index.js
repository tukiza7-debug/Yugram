'use strict';

/**
 * routes/index.js
 * Pendaftaran semua laluan REST di bawah /api.
 */

const { Router } = require('express');
const { authMiddleware } = require('../middlewares/auth.middleware');
const { validate } = require('../middlewares/validate.middleware');
const { asyncHandler } = require('../utils/asyncHandler');
const authController = require('../controllers/auth.controller');
const roomController = require('../controllers/room.controller');
const messageController = require('../controllers/message.controller');
const {
  registerSchema,
  loginSchema,
  createDirectRoomSchema,
} = require('../validators/rest.validator');

const router = Router();

// ------------------------------------------------------------------
// Auth
// ------------------------------------------------------------------
router.post('/auth/register', validate(registerSchema), asyncHandler(authController.register));
router.post('/auth/login', validate(loginSchema), asyncHandler(authController.login));
router.get('/auth/me', authMiddleware, asyncHandler(authController.me));

// ------------------------------------------------------------------
// Rooms
// ------------------------------------------------------------------
router.get('/rooms', authMiddleware, asyncHandler(roomController.listRooms));
router.post(
  '/rooms/direct',
  authMiddleware,
  validate(createDirectRoomSchema),
  asyncHandler(roomController.createDirectRoom)
);
router.get('/rooms/:roomId/members', authMiddleware, asyncHandler(roomController.getRoomMembers));

// ------------------------------------------------------------------
// Messages
// ------------------------------------------------------------------
router.get(
  '/rooms/:roomId/messages',
  authMiddleware,
  asyncHandler(messageController.listMessages)
);

module.exports = router;
