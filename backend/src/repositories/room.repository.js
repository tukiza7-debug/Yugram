'use strict';

/**
 * repositories/room.repository.js
 * Akses data bilik + keahlian + jejak bacaan.
 */

const { Op, QueryTypes } = require('sequelize');
const { sequelize, Room, RoomMember, User } = require('../models');
const { PUBLIC_USER_ATTRS } = require('../utils/constants');
const ApiError = require('../utils/apiError');

class RoomRepository {
  /**
   * Mencari bilik direct sedia ada melalui directKey.
   * @param {string} directKey
   * @returns {Promise<Room|null>}
   */
  async findDirectByKey(directKey) {
    return Room.findOne({ where: { directKey } });
  }

  /**
   * Mencipta bilik direct + ahli dalam satu transaksi.
   * Race-safe: jika directKey didaftarkan serentak oleh proses lain,
   * bilik sedia ada dipulangkan.
   * @param {{directKey: string, memberIds: string[], createdById: string}} input
   * @returns {Promise<{room: Room, created: boolean}>}
   */
  async ensureDirectRoom({ directKey, memberIds, createdById }) {
    const existing = await this.findDirectByKey(directKey);
    if (existing) {
      return { room: existing, created: false };
    }

    try {
      const room = await sequelize.transaction(async (transaction) => {
        const created = await Room.create(
          { type: 'direct', directKey, createdBy: createdById },
          { transaction }
        );
        await RoomMember.bulkCreate(
          memberIds.map((userId) => ({ roomId: created.id, userId, role: 'member' })),
          { transaction }
        );
        return created;
      });
      return { room, created: true };
    } catch (err) {
      if (err && err.name === 'SequelizeUniqueConstraintError') {
        const room = await this.findDirectByKey(directKey);
        if (room) {
          return { room, created: false };
        }
      }
      throw err;
    }
  }

  /**
   * @param {string} roomId
   * @param {string} userId
   * @returns {Promise<boolean>}
   */
  async isMember(roomId, userId) {
    const count = await RoomMember.count({ where: { roomId, userId } });
    return count > 0;
  }

  /**
   * Senarai ahli bilik berserta maklumat awam pengguna.
   * @param {string} roomId
   * @returns {Promise<Array<{userId: string, role: string, joinedAt: string|null, user: object}>>}
   */
  async getRoomMembers(roomId) {
    const rows = await RoomMember.findAll({
      where: { roomId },
      include: [{ model: User, as: 'user', attributes: PUBLIC_USER_ATTRS }],
      order: [['createdAt', 'ASC']],
    });
    return rows.map((row) => ({
      userId: row.userId,
      role: row.role,
      joinedAt: row.createdAt instanceof Date ? row.createdAt.toISOString() : row.createdAt,
      user: row.user ? row.user.toPublicJSON() : null,
    }));
  }

  /**
   * Menanda bacaan terakhir ahli.
   * @param {{roomId: string, userId: string, messageId: string, readAt: Date}} input
   * @returns {Promise<number>}
   */
  async setLastRead({ roomId, userId, messageId, readAt }) {
    const [affectedRows] = await RoomMember.update(
      { lastReadMessageId: messageId, lastReadAt: readAt },
      { where: { roomId, userId } }
    );
    return affectedRows;
  }

  /**
   * Senarai bilik pengguna + mesej terakhir + ahli (untuk skrin senarai sembang).
   * Menggunakan DISTINCT ON (PostgreSQL) untuk mesej terakhir setiap bilik.
   * @param {string} userId
   * @returns {Promise<Array<object>>}
   */
  async findRoomsWithLastMessage(userId) {
    const memberships = await RoomMember.findAll({
      where: { userId },
      include: [{ model: Room, as: 'room' }],
    });
    if (memberships.length === 0) {
      return [];
    }

    const rooms = memberships.map((membership) => membership.room);
    const roomIds = rooms.map((room) => room.id);

    const lastMessageRows = await sequelize.query(
      `SELECT DISTINCT ON (room_id)
         room_id, id, sender_id, text, is_silent, media, forwarded_from_name, created_at
       FROM messages
       WHERE room_id IN (:roomIds)
       ORDER BY room_id, created_at DESC`,
      { replacements: { roomIds }, type: QueryTypes.SELECT }
    );

    const lastMessageByRoom = new Map();
    for (const row of lastMessageRows) {
      lastMessageByRoom.set(row.room_id, {
        id: row.id,
        roomId: row.room_id,
        senderId: row.sender_id,
        text: row.text,
        isSilent: row.is_silent,
        media: row.media ?? null,
        forwardedFromName: row.forwarded_from_name ?? null,
        createdAt: row.created_at instanceof Date ? row.created_at.toISOString() : row.created_at,
      });
    }

    const memberRows = await RoomMember.findAll({
      where: { roomId: { [Op.in]: roomIds } },
      include: [{ model: User, as: 'user', attributes: PUBLIC_USER_ATTRS }],
    });
    const membersByRoom = new Map();
    for (const row of memberRows) {
      const list = membersByRoom.get(row.roomId) || [];
      list.push({
        userId: row.userId,
        role: row.role,
        joinedAt: row.createdAt instanceof Date ? row.createdAt.toISOString() : row.createdAt,
        user: row.user ? row.user.toPublicJSON() : null,
      });
      membersByRoom.set(row.roomId, list);
    }

    // FASA 2: bilangan belum dibaca per bilik (badge senarai sembang).
    const unreadByRoom = await this.unreadCountsForUser(userId);

    return rooms
      .map((room) => ({
        ...room.toRoomJSON(),
        lastMessage: lastMessageByRoom.get(room.id) || null,
        members: membersByRoom.get(room.id) || [],
        unreadCount: unreadByRoom.get(room.id) || 0,
      }))
      .sort((a, b) => {
        const aTime = a.lastMessage ? Date.parse(a.lastMessage.createdAt) : 0;
        const bTime = b.lastMessage ? Date.parse(b.lastMessage.createdAt) : 0;
        return bTime - aTime;
      });
  }

  /**
   * Mencari bilik mengikut ID - ApiError jika tidak wujud.
   * @param {string} roomId
   * @returns {Promise<Room>}
   */
  async findRoomOrThrow(roomId) {
    const room = await Room.findByPk(roomId);
    if (!room) {
      throw ApiError.notFound('Bilik tidak dijumpai');
    }
    return room;
  }

  // ==================================================================
  // FASA 2 - KUMPULAN (GROUP)
  // ==================================================================

  /**
   * Mencipta bilik kumpulan + keahlian dalam satu transaksi.
   * @param {{name: string, createdById: string, memberIds: string[]}} input
   * @returns {Promise<Room>}
   */
  async createGroup({ name, createdById, memberIds }) {
    return sequelize.transaction(async (transaction) => {
      const room = await Room.create(
        { type: 'group', name, createdBy: createdById },
        { transaction }
      );
      const uniqueMemberIds = [...new Set(memberIds)];
      await RoomMember.bulkCreate(
        uniqueMemberIds.map((userId) => ({
          roomId: room.id,
          userId,
          role: userId === createdById ? 'admin' : 'member',
        })),
        { transaction }
      );
      return room;
    });
  }

  /**
   * Menambah ahli kumpulan. Konflik keahlian sedia ada -> 409.
   * @param {string} roomId
   * @param {string} userId
   * @param {string} [role='member']
   * @returns {Promise<void>}
   */
  async addMember(roomId, userId, role = 'member') {
    try {
      await RoomMember.create({ roomId, userId, role });
    } catch (err) {
      if (err && err.name === 'SequelizeUniqueConstraintError') {
        throw ApiError.conflict('Pengguna sudah menjadi ahli bilik ini');
      }
      throw err;
    }
  }

  /**
   * Membuang ahli kumpulan.
   * @param {string} roomId
   * @param {string} userId
   * @returns {Promise<number>} bilangan baris dipadam
   */
  async removeMember(roomId, userId) {
    return RoomMember.destroy({ where: { roomId, userId } });
  }

  /**
   * Mengemas kini peranan ahli (member <-> admin).
   * @param {string} roomId
   * @param {string} userId
   * @param {string} role
   * @returns {Promise<void>}
   */
  async updateMemberRole(roomId, userId, role) {
    const [affectedRows] = await RoomMember.update({ role }, { where: { roomId, userId } });
    if (affectedRows === 0) {
      throw ApiError.notFound('Keahlian tidak dijumpai');
    }
  }

  /**
   * Peranan peminta dalam bilik ('member' | 'admin') atau null jika bukan ahli.
   * @param {string} roomId
   * @param {string} userId
   * @returns {Promise<string|null>}
   */
  async getMemberRole(roomId, userId) {
    const row = await RoomMember.findOne({
      where: { roomId, userId },
      attributes: ['role'],
    });
    return row ? row.role : null;
  }

  /**
   * Bilangan ahli bilik.
   * @param {string} roomId
   * @returns {Promise<number>}
   */
  async countMembers(roomId) {
    return RoomMember.count({ where: { roomId } });
  }

  /**
   * Memadamkan bilik (ahli + mesej + bacaan terpadam melalui CASCADE).
   * @param {string} roomId
   * @returns {Promise<number>}
   */
  async deleteRoom(roomId) {
    return Room.destroy({ where: { id: roomId } });
  }

  /**
   * FASA 2: Bilangan mesej belum dibaca per bilik untuk seorang pengguna.
   * @param {string} userId
   * @returns {Promise<Map<string, number>>} roomId -> unreadCount
   */
  async unreadCountsForUser(userId) {
    const rows = await sequelize.query(
      `SELECT m.room_id, COUNT(*)::int AS unread
         FROM messages m
         JOIN room_members rm
           ON rm.room_id = m.room_id AND rm.user_id = :userId
        WHERE m.sender_id <> :userId
          AND NOT EXISTS (
            SELECT 1 FROM message_reads r
             WHERE r.message_id = m.id AND r.user_id = :userId
          )
        GROUP BY m.room_id`,
      { replacements: { userId }, type: QueryTypes.SELECT }
    );
    const counts = new Map();
    for (const row of rows) {
      counts.set(row.room_id, Number(row.unread));
    }
    return counts;
  }
}

module.exports = new RoomRepository();
