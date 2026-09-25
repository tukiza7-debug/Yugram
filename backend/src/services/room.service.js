'use strict';

/**
 * services/room.service.js
 * Logik bisnes bilik: bilik direct 1-ke-1, keahlian, senarai bilik.
 */

const ApiError = require('../utils/apiError');
const logger = require('../utils/logger');
const userRepository = require('../repositories/user.repository');
const roomRepository = require('../repositories/room.repository');

const log = logger.child('RoomService');

class RoomService {
  /**
   * Mencari atau mencipta bilik direct antara dua pengguna (idempoten).
   * directKey = pasangan ID diisih supaya susunan tidak penting.
   * @param {string} userIdA
   * @param {string} userIdB
   * @returns {Promise<{room: Room, members: object[], created: boolean}>}
   * @throws {ApiError} 400 jika berkawan dengan diri sendiri, 404 jika peer tiada
   */
  async ensureDirectRoom(userIdA, userIdB) {
    if (userIdA === userIdB) {
      throw ApiError.badRequest('Tidak boleh mencipta sembang peribadi dengan diri sendiri');
    }
    const peer = await userRepository.findById(userIdB);
    if (!peer) {
      throw ApiError.notFound('Pengguna rakan sembang tidak dijumpai');
    }
    const directKey = [userIdA, userIdB].sort().join(':');
    const { room, created } = await roomRepository.ensureDirectRoom({
      directKey,
      memberIds: [userIdA, userIdB],
      createdById: userIdA,
    });
    const members = await roomRepository.getRoomMembers(room.id);
    if (created) {
      log.info('Bilik direct dicipta', { roomId: room.id, directKey });
    }
    return { room, members, created };
  }

  /**
   * Bilik direct melalui username rakan sembang (dipakai REST).
   * @param {{requesterId: string, peerUsername: string}} input
   * @returns {Promise<{room: Room, members: object[]}>}
   */
  async createDirectRoomFromUsername({ requesterId, peerUsername }) {
    const peer = await userRepository.findByUsername(peerUsername);
    if (!peer) {
      throw ApiError.notFound(`Pengguna "@${peerUsername}" tidak dijumpai`);
    }
    const { room, members } = await this.ensureDirectRoom(requesterId, peer.id);
    return { room, members };
  }

  /**
   * Menguji keahlian - ApiError 403 jika bukan ahli.
   * @param {string} roomId
   * @param {string} userId
   * @returns {Promise<void>}
   */
  async assertMembership(roomId, userId) {
    const isMember = await roomRepository.isMember(roomId, userId);
    if (!isMember) {
      throw ApiError.forbidden('Anda bukan ahli bilik ini');
    }
  }

  /**
   * Senarai ahli bilik (memerlukan keahlian).
   * @param {string} roomId
   * @param {string} requesterId
   * @returns {Promise<object[]>}
   */
  async getRoomMembers(roomId, requesterId) {
    await this.assertMembership(roomId, requesterId);
    return roomRepository.getRoomMembers(roomId);
  }

  /**
   * Senarai bilik untuk skrin senarai sembang.
   * @param {string} userId
   * @returns {Promise<object[]>}
   */
  async listRooms(userId) {
    return roomRepository.findRoomsWithLastMessage(userId);
  }

  /**
   * Mendapatkan bilik + ahli setelah disahkan keahlian peminta.
   * @param {string} roomId
   * @param {string} requesterId
   * @returns {Promise<{room: Room, members: object[]}>}
   */
  async getRoomWithMembers(roomId, requesterId) {
    await this.assertMembership(roomId, requesterId);
    const room = await roomRepository.findRoomOrThrow(roomId);
    const members = await roomRepository.getRoomMembers(roomId);
    return { room, members };
  }
}

module.exports = new RoomService();
