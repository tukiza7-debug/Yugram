import 'package:flutter/material.dart';

import '../../core/error/app_exception.dart';
import '../../core/utils/app_logger.dart';
import '../../domain/entities/user_entity.dart';
import '../../domain/repositories/user_repository.dart';

/// Skrin profil (FASA 2): lihat & sunting displayName/bio/username.
class ProfileScreen extends StatefulWidget {
  const ProfileScreen({required this.userRepository, super.key});

  final UserRepository userRepository;

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  late final AppLogger _log;
  UserEntity? _profile;
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _log = AppLogger('ProfileScreen');
    _reload();
  }

  Future<void> _reload() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final UserEntity profile = await widget.userRepository.getProfile();
      if (!mounted) return;
      setState(() {
        _profile = profile;
        _isLoading = false;
      });
    } on ApiException catch (err) {
      if (!mounted) return;
      setState(() {
        _error = err.message;
        _isLoading = false;
      });
    } catch (err, stackTrace) {
      _log.error('Gagal memuatkan profil', err, stackTrace);
      if (!mounted) return;
      setState(() {
        _error = 'Gagal memuatkan profil';
        _isLoading = false;
      });
    }
  }

  Future<void> _editField({
    required String title,
    required String label,
    required String initialValue,
    required int maxLines,
    required Future<void> Function(String value) onSave,
  }) async {
    final TextEditingController controller = TextEditingController(text: initialValue);
    final String? value = await showDialog<String>(
      context: context,
      builder: (BuildContext dialogContext) {
        return AlertDialog(
          title: Text(title),
          content: TextField(
            controller: controller,
            autofocus: true,
            maxLines: maxLines,
            decoration: InputDecoration(labelText: label),
            onSubmitted: (String _) => Navigator.of(dialogContext).pop(controller.text),
          ),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: const Text('Batal'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(controller.text),
              child: const Text('Simpan'),
            ),
          ],
        );
      },
    );
    controller.dispose();
    if (value == null) return;
    final String trimmed = value.trim();
    if (trimmed == initialValue.trim() || trimmed.isEmpty) return;
    await onSave(trimmed);
  }

  Future<void> _save({String? displayName, String? bio, String? username}) async {
    try {
      final UserEntity updated = await widget.userRepository.updateProfile(
        displayName: displayName,
        bio: bio,
        username: username,
      );
      if (!mounted) return;
      setState(() => _profile = updated);
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('Profil dikemas kini')));
    } on ApiException catch (err) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(err.message)));
    } catch (err, stackTrace) {
      _log.error('Kemas kini profil gagal', err, stackTrace);
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('Kemas kini gagal - cuba lagi')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    final UserEntity? profile = _profile;

    return Scaffold(
      appBar: AppBar(
        backgroundColor: scheme.primary,
        foregroundColor: scheme.onPrimary,
        title: const Text('Profil Saya'),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: <Widget>[
                      Text(_error!),
                      const SizedBox(height: 12),
                      FilledButton(
                        onPressed: _reload,
                        child: const Text('Cuba Semula'),
                      ),
                    ],
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _reload,
                  child: ListView(
                    padding: const EdgeInsets.all(24),
                    children: <Widget>[
                      const SizedBox(height: 8),
                      Center(
                        child: CircleAvatar(
                          radius: 48,
                          backgroundColor: scheme.primaryContainer,
                          child: Text(
                            profile!.initials,
                            style: TextStyle(
                              fontSize: 34,
                              fontWeight: FontWeight.w700,
                              color: scheme.primary,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                      Center(
                        child: Text(
                          profile.displayName,
                          style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
                        ),
                      ),
                      Center(
                        child: Text(
                          '@${profile.username}',
                          style: TextStyle(color: scheme.onSurfaceVariant),
                        ),
                      ),
                      const SizedBox(height: 24),
                      Card(
                        child: Column(
                          children: <Widget>[
                            ListTile(
                              leading: const Icon(Icons.person_outline),
                              title: const Text('Nama Paparan'),
                              subtitle: Text(profile.displayName),
                              trailing: const Icon(Icons.edit, size: 18),
                              onTap: () => _editField(
                                title: 'Nama Paparan',
                                label: 'Nama paparan',
                                initialValue: profile.displayName,
                                maxLines: 1,
                                onSave: (String value) => _save(displayName: value),
                              ),
                            ),
                            const Divider(height: 1),
                            ListTile(
                              leading: const Icon(Icons.info_outline),
                              title: const Text('Bio'),
                              subtitle: Text(
                                (profile.bio == null || profile.bio!.isEmpty)
                                    ? 'Tambah bio ringkas'
                                    : profile.bio!,
                              ),
                              trailing: const Icon(Icons.edit, size: 18),
                              onTap: () => _editField(
                                title: 'Bio',
                                label: 'Bio (maks 280 aksara)',
                                initialValue: profile.bio ?? '',
                                maxLines: 3,
                                onSave: (String value) => _save(bio: value),
                              ),
                            ),
                            const Divider(height: 1),
                            ListTile(
                              leading: const Icon(Icons.alternate_email),
                              title: const Text('Username'),
                              subtitle: Text('@${profile.username}'),
                              trailing: const Icon(Icons.edit, size: 18),
                              onTap: () => _editField(
                                title: 'Username',
                                label: 'Username (a-z, 0-9, _)',
                                initialValue: profile.username,
                                maxLines: 1,
                                onSave: (String value) => _save(username: value),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
    );
  }
}
