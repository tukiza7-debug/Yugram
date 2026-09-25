'use strict';

/**
 * routes/index.js
 * Pendaftaran semua laluan REST di bawah /api.
 * FASA 1: auth, rooms (direct), mesej, reaksi (socket).
 * FASA 2: profil, carian pengguna, kumpulan, media.
 * FASA 3: edit/padam mesej, carian dalam bilik.
 */

const { Router } = require('express');
const { authMiddleware } = require('../middlewares/auth.middleware');
const { validate } = require('../middlewares/validate.middleware');
const { uploadSingleSafe } = require('../middlewares/upload.middleware');
const { asyncHandler } = require('../utils/asyncHandler');
const authController = require('../controllers/auth.controller');
const userController = require('../controllers/user.controller');
const roomController = require('../controllers/room.controller');
const messageController = require('../controllers/message.controller');
const mediaController = require('../controllers/media.controller');
const {
  registerSchema,
  loginSchema,
  createDirectRoomSchema,
  // FASA 2
  updateProfileSchema,
  userSearchQuerySchema,
  createGroupSchema,
  addMemberSchema,
  updateMemberRoleSchema,
  renameRoomSchema,
  roomAndUserParamSchema,
  // FASA 3
  messageIdParamSchema,
  editMessageSchema,
} = require('../validators/rest.validator');

const router = Router();

// ------------------------------------------------------------------
// Auth (FASA 1)
// ------------------------------------------------------------------
router.post('/auth/register', validate(registerSchema), asyncHandler(authController.register));
router.post('/auth/login', validate(loginSchema), asyncHandler(authController.login));
router.get('/auth/me', authMiddleware, asyncHandler(userController.getProfile));

// ------------------------------------------------------------------
// Pengguna (FASA 2)
// ------------------------------------------------------------------
router.patch('/users/me', authMiddleware, validate(updateProfileSchema), asyncHandler(userController.updateProfile));
router.get(
  '/users/search',
  authMiddleware,
  validate(userSearchQuerySchema, 'query'),
  asyncHandler(userController.searchUsers)
);

// ------------------------------------------------------------------
// Media (FASA 2) - multipart upload
// ------------------------------------------------------------------
router.post('/media', authMiddleware, uploadSingleSafe('file'), asyncHandler(mediaController.uploadMedia));

// ------------------------------------------------------------------
// Bilik (FASA 1 + 2)
// ------------------------------------------------------------------
router.get('/rooms', authMiddleware, asyncHandler(roomController.listRooms));
router.post(
  '/rooms/direct',
  authMiddleware,
  validate(createDirectRoomSchema),
  asyncHandler(roomController.createDirectRoom)
);
router.post(
  '/rooms/group',
  authMiddleware,
  validate(createGroupSchema),
  asyncHandler(roomController.createGroup)
);
router.post(
  '/rooms/:roomId/leave',
  authMiddleware,
  asyncHandler(roomController.leaveRoom)
);
router.patch(
  '/rooms/:roomId',
  authMiddleware,
  validate(renameRoomSchema),
  asyncHandler(roomController.renameGroup)
);
router.delete(
  '/rooms/:roomId',
  authMiddleware,
  asyncHandler(roomController.deleteGroup)
);
router.get('/rooms/:roomId/members', authMiddleware, asyncHandler(roomController.getRoomMembers));
router.get('/rooms/:roomId', authMiddleware, asyncHandler(roomController.getRoomDetail));
router.post(
  '/rooms/:roomId/members',
  authMiddleware,
  validate(addMemberSchema),
  asyncHandler(roomController.addMember)
);
router.delete(
  '/rooms/:roomId/members/:userId',
  authMiddleware,
  validate(roomAndUserParamSchema, 'params'),
  asyncHandler(roomController.removeMember)
);
router.patch(
  '/rooms/:roomId/members/:userId',
  authMiddleware,
  validate(roomAndUserParamSchema, 'params'),
  validate(updateMemberRoleSchema),
  asyncHandler(roomController.updateMemberRole)
);

// ------------------------------------------------------------------
// Mesej (FASA 1 + 2 + 3)
// ------------------------------------------------------------------
router.get(
  '/rooms/:roomId/messages',
  authMiddleware,
  asyncHandler(messageController.listMessages)
);
router.get(
  '/rooms/:roomId/media',
  authMiddleware,
  asyncHandler(messageController.listRoomMedia)
);
router.patch(
  '/messages/:messageId',
  authMiddleware,
  validate(messageIdParamSchema, 'params'),
  validate(editMessageSchema),
  asyncHandler(messageController.editMessage)
);
router.delete(
  '/messages/:messageId',
  authMiddleware,
  validate(messageIdParamSchema, 'params'),
  asyncHandler(messageController.deleteMessage)
);

module.exports = router;
