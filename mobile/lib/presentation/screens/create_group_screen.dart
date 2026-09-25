import 'package:flutter/material.dart';

import '../../core/error/app_exception.dart';
import '../../core/utils/app_logger.dart';
import '../../domain/entities/room_entity.dart';
import '../../domain/repositories/chat_repository.dart';

/// Skrin cipta kumpulan (FASA 2): nama kumpulan + senarai username ahli.
class CreateGroupScreen extends StatefulWidget {
  const CreateGroupScreen({required this.repository, super.key});

  final ChatRepository repository;

  @override
  State<CreateGroupScreen> createState() => _CreateGroupScreenState();
}

class _CreateGroupScreenState extends State<CreateGroupScreen> {
  late final AppLogger _log;
  final TextEditingController _nameController = TextEditingController();
  final List<TextEditingController> _memberControllers = <TextEditingController>[
    TextEditingController(),
  ];
  bool _isCreating = false;

  @override
  void initState() {
    super.initState();
    _log = AppLogger('CreateGroupScreen');
  }

  @override
  void dispose() {
    _nameController.dispose();
    for (final TextEditingController controller in _memberControllers) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _createGroup() async {
    final String name = _nameController.text.trim();
    if (name.isEmpty) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('Masukkan nama kumpulan')));
      return;
    }
    final List<String> usernames = _memberControllers
        .map((TextEditingController controller) => controller.text.trim())
        .where((String value) => value.isNotEmpty)
        .toList();
    if (usernames.isEmpty) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('Tambah sekurang-kurangnya seorang ahli')));
      return;
    }

    setState(() => _isCreating = true);
    try {
      final RoomEntity room = await widget.repository.createGroup(
        name: name,
        memberUsernames: usernames,
      );
      if (!mounted) return;
      Navigator.of(context).pop(room);
    } on ApiException catch (err) {
      if (!mounted) return;
      setState(() => _isCreating = false);
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(err.message)));
    } catch (err, stackTrace) {
      _log.error('Cipta kumpulan gagal', err, stackTrace);
      if (!mounted) return;
      setState(() => _isCreating = false);
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('Cipta kumpulan gagal - cuba lagi')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(
        backgroundColor: scheme.primary,
        foregroundColor: scheme.onPrimary,
        title: const Text('Kumpulan Baharu'),
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: <Widget>[
            TextField(
              controller: _nameController,
              decoration: const InputDecoration(
                labelText: 'Nama kumpulan',
                prefixIcon: Icon(Icons.group_outlined),
              ),
            ),
            const SizedBox(height: 20),
            Text(
              'Ahli (username)',
              style: TextStyle(
                fontWeight: FontWeight.w600,
                color: scheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 8),
            ...List<Widget>.generate(_memberControllers.length, (int index) {
              return Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Row(
                  children: <Widget>[
                    Expanded(
                      child: TextField(
                        controller: _memberControllers[index],
                        decoration: InputDecoration(
                          labelText: 'Username ahli ${index + 1}',
                          prefixIcon: const Icon(Icons.alternate_email),
                        ),
                      ),
                    ),
                    if (_memberControllers.length > 1)
                      IconButton(
                        icon: const Icon(Icons.remove_circle_outline),
                        onPressed: () {
                          setState(() {
                            final TextEditingController removed =
                                _memberControllers.removeAt(index);
                            removed.dispose();
                          });
                        },
                      ),
                  ],
                ),
              );
            }),
            TextButton.icon(
              onPressed: () {
                setState(() {
                  _memberControllers.add(TextEditingController());
                });
              },
              icon: const Icon(Icons.add),
              label: const Text('Tambah medan ahli'),
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: _isCreating ? null : _createGroup,
              icon: _isCreating
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.group_add),
              label: Text(_isCreating ? 'Mencipta...' : 'Cipta Kumpulan'),
            ),
          ],
        ),
      ),
    );
  }
}
