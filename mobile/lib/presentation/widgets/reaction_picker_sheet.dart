import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/constants/app_constants.dart';
import '../../domain/entities/message_entity.dart';

/// Kelas hasil - digunakan untuk pop sheet.
class ReactionSheetAction {
  const ReactionSheetAction.reaction(this.emoji)
      : type = ReactionSheetActionType.react;

  const ReactionSheetAction.reply()
      : type = ReactionSheetActionType.reply,
        emoji = null;

  const ReactionSheetAction.copy()
      : type = ReactionSheetActionType.copy,
        emoji = null;

  final ReactionSheetActionType type;
  final String? emoji;
}

enum ReactionSheetActionType { react, reply, copy }

/// Bottom sheet reaksi pantas (Fitur 67) + aksi balas/salin.
Future<ReactionSheetAction?> showReactionPickerSheet(
  BuildContext context, {
  required MessageEntity message,
  required String myUserId,
}) {
  final String? myCurrentReaction = message.reactions
      .where((ReactionEntity r) => r.userId == myUserId)
      .map((ReactionEntity r) => r.emoji)
      .fold<String?>(null, (String? acc, String e) => acc ?? e);

  return showModalBottomSheet<ReactionSheetAction>(
    context: context,
    isScrollControlled: false,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
    ),
    builder: (BuildContext sheetContext) {
      return SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Center(
                child: Container(
                  width: 36,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 12),
                  decoration: BoxDecoration(
                    color: Theme.of(sheetContext).colorScheme.outlineVariant,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const Padding(
                padding: EdgeInsets.only(left: 4, bottom: 8),
                child: Text('Reaksi', style: TextStyle(fontWeight: FontWeight.w600)),
              ),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: AppConstants.defaultReactions.map((String emoji) {
                  final bool selected = myCurrentReaction == emoji;
                  return InkWell(
                    onTap: () => Navigator.of(sheetContext).pop(ReactionSheetAction.reaction(emoji)),
                    borderRadius: BorderRadius.circular(24),
                    child: Container(
                      width: 46,
                      height: 46,
                      alignment: Alignment.center,
                      decoration: BoxDecoration(
                        color: selected
                            ? Theme.of(sheetContext).colorScheme.secondaryContainer
                            : Theme.of(sheetContext).colorScheme.surfaceContainerHighest,
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: selected
                              ? Theme.of(sheetContext).colorScheme.secondary
                              : Colors.transparent,
                          width: 2,
                        ),
                      ),
                      child: Text(emoji, style: const TextStyle(fontSize: 22)),
                    ),
                  );
                }).toList(),
              ),
              const SizedBox(height: 12),
              Row(
                children: <Widget>[
                  Expanded(
                    child: TextButton.icon(
                      onPressed: () => Navigator.of(sheetContext).pop(const ReactionSheetAction.reply()),
                      icon: const Icon(Icons.reply),
                      label: const Text('Balas'),
                    ),
                  ),
                  Expanded(
                    child: TextButton.icon(
                      onPressed: () {
                        Clipboard.setData(ClipboardData(text: message.text));
                        Navigator.of(sheetContext).pop(const ReactionSheetAction.copy());
                      },
                      icon: const Icon(Icons.copy),
                      label: const Text('Salin'),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      );
    },
  );
}
