'use strict';

/**
 * services/room.service.js
 * Logik bisnes bilik: bilik direct 1-ke-1, keahlian, senarai bilik.
 */

const ApiError = require('../utils/apiError');
const logger = require('../utils/logger');
const { SOCKET_EVENTS } = require('../utils/constants');
const socketGateway = require('../sockets/socketGateway');
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

  // ==================================================================
  // FASA 2 - KUMPULAN (GROUP)
  // ==================================================================

  /**
   * Mencipta kumpulan baharu. Pencipta menjadi 'admin'. Semua ahli
   * mesti wujud; nama kumpulan divalidasi oleh Joi di lapisan routes.
   * @param {{creatorId: string, name: string, memberUsernames: string[]}} input
   * @returns {Promise<{room: Room, members: object[]}>}
   * @throws {ApiError} 404 jika mana-mana ahli tiada, 400 jika tiada ahli lain
   */
  async createGroup({ creatorId, name, memberUsernames }) {
    const usernames = [...new Set(
      (memberUsernames || []).map((username) => String(username).trim().toLowerCase()).filter(Boolean)
    )];
    if (usernames.length === 0) {
      throw ApiError.badRequest('Kumpulan perlu sekurang-kurangnya seorang ahli lain');
    }

    const memberIds = [creatorId];
    for (const username of usernames) {
      const user = await userRepository.findByUsername(username);
      if (!user) {
        throw ApiError.notFound(`Pengguna "@${username}" tidak dijumpai`);
      }
      if (user.id !== creatorId) {
        memberIds.push(user.id);
      }
    }

    const room = await roomRepository.createGroup({ name, createdById: creatorId, memberIds });
    const members = await roomRepository.getRoomMembers(room.id);
    log.info('Kumpulan dicipta', { roomId: room.id, name, members: members.length });
    return { room, members };
  }

  /**
   * Menambah ahli kumpulan. Ahli sedia ada boleh menambah orang baharu
   * (kelakuan Telegram). Broadcast `room_updated` kepada bilik.
   * @param {{roomId: string, requesterId: string, username: string}} input
   * @returns {Promise<{room: Room, members: object[], addedUser: object}>}
   */
  async addMember({ roomId, requesterId, username }) {
    await this.assertMembership(roomId, requesterId);
    const user = await userRepository.findByUsername(username);
    if (!user) {
      throw ApiError.notFound(`Pengguna "@${username}" tidak dijumpai`);
    }
    await roomRepository.addMember(roomId, user.id, 'member');
    const members = await roomRepository.getRoomMembers(roomId);
    const room = await roomRepository.findRoomOrThrow(roomId);
    socketGateway.publishToRoom(roomId, SOCKET_EVENTS.ROOM_UPDATED, {
      room: room.toRoomJSON(),
      members,
      updatedBy: requesterId,
    });
    log.info('Ahli ditambah', { roomId, requesterId, addedUserId: user.id });
    return { room, members, addedUser: user.toPublicJSON() };
  }

  /**
   * Membuang ahli. Hanya admin yang boleh membuang; admin tidak boleh
   * membuang admin lain. Broadcast `room_updated`.
   * @param {{roomId: string, requesterId: string, targetUserId: string}} input
   * @returns {Promise<{removedUserId: string, members: object[]}>}
   */
  async removeMember({ roomId, requesterId, targetUserId }) {
    await this.assertMembership(roomId, requesterId);
    const room = await roomRepository.findRoomOrThrow(roomId);
    if (room.type !== 'group') {
      throw ApiError.badRequest('Ahli hanya boleh dibuang daripada kumpulan');
    }
    const requesterRole = await roomRepository.getMemberRole(roomId, requesterId);
    if (requesterRole !== 'admin') {
      throw ApiError.forbidden('Hanya admin boleh membuang ahli');
    }
    const targetRole = await roomRepository.getMemberRole(roomId, targetUserId);
    if (!targetRole) {
      throw ApiError.notFound('Pengguna bukan ahli kumpulan ini');
    }
    if (targetRole === 'admin') {
      throw ApiError.forbidden('Admin tidak boleh dibuang oleh admin lain');
    }
    await roomRepository.removeMember(roomId, targetUserId);
    const members = await roomRepository.getRoomMembers(roomId);
    socketGateway.publishToRoom(roomId, SOCKET_EVENTS.ROOM_UPDATED, {
      room: room.toRoomJSON(),
      members,
      updatedBy: requesterId,
    });
    log.info('Ahli dibuang', { roomId, requesterId, targetUserId });
    return { removedUserId: targetUserId, members };
  }

  /**
   * Mengemas kini peranan ahli (member <-> admin). Hanya pencipta bilik
   * (room.createdBy) yang boleh menukar peranan.
   * @param {{roomId: string, requesterId: string, targetUserId: string, role: string}} input
   * @returns {Promise<{members: object[]}>}
   */
  async updateMemberRole({ roomId, requesterId, targetUserId, role }) {
    await this.assertMembership(roomId, requesterId);
    const room = await roomRepository.findRoomOrThrow(roomId);
    if (room.type !== 'group') {
      throw ApiError.badRequest('Peranan hanya terpakai untuk kumpulan');
    }
    if (room.createdBy !== requesterId) {
      throw ApiError.forbidden('Hanya pencipta kumpulan boleh menukar peranan');
    }
    if (targetUserId === requesterId) {
      throw ApiError.badRequest('Tidak boleh menukar peranan diri sendiri');
    }
    await roomRepository.updateMemberRole(roomId, targetUserId, role);
    const members = await roomRepository.getRoomMembers(roomId);
    socketGateway.publishToRoom(roomId, SOCKET_EVENTS.ROOM_UPDATED, {
      room: room.toRoomJSON(),
      members,
      updatedBy: requesterId,
    });
    log.info('Peranan ahli dikemas kini', { roomId, targetUserId, role });
    return { members };
  }

  /**
   * Ahli meninggalkan kumpulan. Pencipta hanya boleh keluar jika terdapat
   * admin lain (untuk memastikan kumpulan sentiasa ada pentadbir) atau jika
   * beliau ahli tunggal (kumpulan dipadam). Broadcast `room_updated`.
   * @param {{roomId: string, userId: string}} input
   * @returns {Promise<{left: boolean, roomDeleted: boolean}>}
   */
  async leaveRoom({ roomId, userId }) {
    await this.assertMembership(roomId, userId);
    const room = await roomRepository.findRoomOrThrow(roomId);
    if (room.type !== 'group') {
      throw ApiError.badRequest('Sembang peribadi tidak boleh ditinggalkan');
    }

    const memberCount = await roomRepository.countMembers(roomId);
    if (room.createdBy === userId && memberCount > 1) {
      const members = await roomRepository.getRoomMembers(roomId);
      const hasOtherAdmin = members.some(
        (member) => member.userId !== userId && member.role === 'admin'
      );
      if (!hasOtherAdmin) {
        throw ApiError.badRequest(
          'Lantik admin lain dahulu sebelum keluar (Menu Kumpulan > Ahli > Jadikan Admin)'
        );
      }
    }

    await roomRepository.removeMember(roomId, userId);
    const remainingCount = await roomRepository.countMembers(roomId);
    if (remainingCount === 0) {
      await roomRepository.deleteRoom(roomId);
      socketGateway.publishToRoom(roomId, SOCKET_EVENTS.ROOM_DELETED, {
        roomId,
        reason: 'empty',
      });
      log.info('Kumpulan dipadam (tiada ahli)', { roomId });
      return { left: true, roomDeleted: true };
    }

    const members = await roomRepository.getRoomMembers(roomId);
    socketGateway.publishToRoom(roomId, SOCKET_EVENTS.ROOM_UPDATED, {
      room: room.toRoomJSON(),
      members,
      updatedBy: userId,
    });
    log.info('Ahli keluar dari kumpulan', { roomId, userId });
    return { left: true, roomDeleted: false };
  }

  /**
   * Memadamkan kumpulan. Hanya pencipta yang boleh memadam.
   * Broadcast `room_deleted` kepada semua ahli.
   * @param {{roomId: string, requesterId: string}} input
   * @returns {Promise<{deleted: boolean}>}
   */
  async deleteGroup({ roomId, requesterId }) {
    await this.assertMembership(roomId, requesterId);
    const room = await roomRepository.findRoomOrThrow(roomId);
    if (room.type !== 'group') {
      throw ApiError.badRequest('Hanya kumpulan boleh dipadam');
    }
    if (room.createdBy !== requesterId) {
      throw ApiError.forbidden('Hanya pencipta kumpulan boleh memadamkannya');
    }
    await roomRepository.deleteRoom(roomId);
    socketGateway.publishToRoom(roomId, SOCKET_EVENTS.ROOM_DELETED, {
      roomId,
      reason: 'deleted_by_creator',
      deletedBy: requesterId,
    });
    log.info('Kumpulan dipadam', { roomId, requesterId });
    return { deleted: true };
  }

  /**
   * Menamakan semula kumpulan. Admin dibenarkan. Broadcast `room_updated`.
   * @param {{roomId: string, requesterId: string, name: string}} input
   * @returns {Promise<{room: Room, members: object[]}>}
   */
  async renameGroup({ roomId, requesterId, name }) {
    await this.assertMembership(roomId, requesterId);
    const room = await roomRepository.findRoomOrThrow(roomId);
    if (room.type !== 'group') {
      throw ApiError.badRequest('Hanya kumpulan boleh dinamakan semula');
    }
    const role = await roomRepository.getMemberRole(roomId, requesterId);
    if (role !== 'admin') {
      throw ApiError.forbidden('Hanya admin boleh menamakan semula kumpulan');
    }
    room.set('name', name);
    await room.save();
    const members = await roomRepository.getRoomMembers(roomId);
    socketGateway.publishToRoom(roomId, SOCKET_EVENTS.ROOM_UPDATED, {
      room: room.toRoomJSON(),
      members,
      updatedBy: requesterId,
    });
    log.info('Kumpulan dinamakan semula', { roomId, name });
    return { room, members };
  }
}

module.exports = new RoomService();
