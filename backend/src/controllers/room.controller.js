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

module.exports = { listRooms, createDirectRoom, getRoomMembers };
