import '../../domain/entities/user_entity.dart';

/// Model data bagi payload reaksi (dari JSONB `reactions` pelayan).
class ReactionModel extends ReactionEntity {
  const ReactionModel({
    required super.userId,
    required super.emoji,
    required super.createdAt,
  });

  factory ReactionModel.fromJson(Map<String, dynamic> json) => ReactionModel(
        userId: json['userId']?.toString() ?? '',
        emoji: json['emoji']?.toString() ?? '',
        createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ??
            DateTime.fromMillisecondsSinceEpoch(0),
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
        'userId': userId,
        'emoji': emoji,
        'createdAt': createdAt.toIso8601String(),
      };
}

/// Kumpulan utiliti untuk menukar senarai reaksi.
class ReactionList {
  const ReactionList._();

  static List<ReactionModel> fromJsonList(List<dynamic>? list) => (list ?? <dynamic>[])
      .whereType<Map>()
      .map((Map raw) => ReactionModel.fromJson(Map<String, dynamic>.from(raw)))
      .toList();

  static List<Map<String, dynamic>> toJsonList(List<ReactionEntity> reactions) =>
      reactions.map((ReactionEntity r) => <String, dynamic>{
            'userId': r.userId,
            'emoji': r.emoji,
            'createdAt': r.createdAt.toIso8601String(),
          }).toList();
}
