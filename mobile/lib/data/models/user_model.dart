import '../../domain/entities/user_entity.dart';

class UserModel extends UserEntity {
  const UserModel({
    required super.id,
    required super.username,
    required super.displayName,
    super.bio,
    super.avatarUrl,
    super.lastSeenAt,
  });

  factory UserModel.fromJson(Map<String, dynamic> json) => UserModel(
        id: json['id']?.toString() ?? '',
        username: json['username']?.toString() ?? '',
        displayName: json['displayName']?.toString() ?? '',
        bio: json['bio']?.toString(),
        avatarUrl: json['avatarUrl']?.toString(),
        lastSeenAt: json['lastSeenAt'] == null
            ? null
            : DateTime.tryParse(json['lastSeenAt'].toString()),
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'username': username,
        'displayName': displayName,
        'avatarUrl': avatarUrl,
        'lastSeenAt': lastSeenAt?.toIso8601String(),
      };
}
