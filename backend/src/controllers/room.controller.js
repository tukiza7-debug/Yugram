'use strict';

/**
 * controllers/room.controller.js
 * Handler REST untuk senarai bilik + penciptaan bilik direct.
 */

const roomService = require('../services/room.service');

/**
 * GET /api/rooms
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function listRooms(req, res) {
  const rooms = await roomService.listRooms(req.user.id);
  res.status(200).json({ rooms });
}

/**
 * POST /api/rooms/direct
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function createDirectRoom(req, res) {
  const { room, members } = await roomService.createDirectRoomFromUsername({
    requesterId: req.user.id,
    peerUsername: req.body.peerUsername,
  });
  res.status(201).json({ room: room.toRoomJSON(), members });
}

/**
 * GET /api/rooms/:roomId/members
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function getRoomMembers(req, res) {
  const { members } = await roomService.getRoomWithMembers(req.params.roomId, req.user.id);
  res.status(200).json({ members });
}

/**
 * GET /api/rooms/:roomId - butiran bilik + ahli (FASA 2, skrin info kumpulan)
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function getRoomDetail(req, res) {
  const { room, members } = await roomService.getRoomWithMembers(req.params.roomId, req.user.id);
  res.status(200).json({ room: room.toRoomJSON(), members });
}

// ==================================================================
// FASA 2 - KUMPULAN
// ==================================================================

/**
 * POST /api/rooms/group { name, memberUsernames[] }
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function createGroup(req, res) {
  const { room, members } = await roomService.createGroup({
    creatorId: req.user.id,
    name: req.body.name,
    memberUsernames: req.body.memberUsernames,
  });
  res.status(201).json({ room: room.toRoomJSON(), members });
}

/**
 * POST /api/rooms/:roomId/members { username }
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function addMember(req, res) {
  const { members, addedUser } = await roomService.addMember({
    roomId: req.params.roomId,
    requesterId: req.user.id,
    username: req.body.username,
  });
  res.status(201).json({ members, addedUser });
}

/**
 * DELETE /api/rooms/:roomId/members/:userId
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function removeMember(req, res) {
  const { removedUserId, members } = await roomService.removeMember({
    roomId: req.params.roomId,
    requesterId: req.user.id,
    targetUserId: req.params.userId,
  });
  res.status(200).json({ removedUserId, members });
}

/**
 * PATCH /api/rooms/:roomId/members/:userId { role }
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function updateMemberRole(req, res) {
  const { members } = await roomService.updateMemberRole({
    roomId: req.params.roomId,
    requesterId: req.user.id,
    targetUserId: req.params.userId,
    role: req.body.role,
  });
  res.status(200).json({ members });
}

/**
 * POST /api/rooms/:roomId/leave
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function leaveRoom(req, res) {
  const result = await roomService.leaveRoom({
    roomId: req.params.roomId,
    userId: req.user.id,
  });
  res.status(200).json(result);
}

/**
 * DELETE /api/rooms/:roomId (padam kumpulan - pencipta sahaja)
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function deleteGroup(req, res) {
  const result = await roomService.deleteGroup({
    roomId: req.params.roomId,
    requesterId: req.user.id,
  });
  res.status(200).json(result);
}

/**
 * PATCH /api/rooms/:roomId { name } (nama semula kumpulan)
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 */
async function renameGroup(req, res) {
  const { room, members } = await roomService.renameGroup({
    roomId: req.params.roomId,
    requesterId: req.user.id,
    name: req.body.name,
  });
  res.status(200).json({ room: room.toRoomJSON(), members });
}

module.exports = {
  listRooms,
  createDirectRoom,
  getRoomMembers,
  getRoomDetail,
  // FASA 2
  createGroup,
  addMember,
  removeMember,
  updateMemberRole,
  leaveRoom,
  deleteGroup,
  renameGroup,
};
