'use strict';

/**
 * models/index.js
 * Pendaftar semua model + hubungan (associations).
 */

const { sequelize } = require('../config/database');
const User = require('./user.model');
const Room = require('./room.model');
const RoomMember = require('./roomMember.model');
const Message = require('./message.model');
const MessageRead = require('./messageRead.model');

// Keahlian bilik
Room.hasMany(RoomMember, { as: 'memberRows', foreignKey: 'roomId' });
RoomMember.belongsTo(Room, { as: 'room', foreignKey: 'roomId' });
User.hasMany(RoomMember, { as: 'memberships', foreignKey: 'userId' });
RoomMember.belongsTo(User, { as: 'user', foreignKey: 'userId' });

// Mesej
Message.belongsTo(User, { as: 'sender', foreignKey: 'senderId' });
Message.belongsTo(Room, { as: 'room', foreignKey: 'roomId' });
Message.belongsTo(Message, { as: 'replyTo', foreignKey: 'replyToMessageId' });
Message.hasMany(MessageRead, { as: 'reads', foreignKey: 'messageId' });
MessageRead.belongsTo(Message, { as: 'message', foreignKey: 'messageId' });
MessageRead.belongsTo(User, { as: 'reader', foreignKey: 'userId' });

module.exports = { sequelize, User, Room, RoomMember, Message, MessageRead };
