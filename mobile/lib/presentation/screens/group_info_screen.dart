import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../core/constants/app_constants.dart';
import '../../core/error/app_exception.dart';
import '../../core/utils/app_logger.dart';
import '../../data/datasources/media_store.dart';
import '../../domain/entities/room_entity.dart';
import '../../domain/repositories/chat_repository.dart';

/// Skrin info kumpulan (FASA 2):
///  - senarai ahli + peranan (admin/pencipta boleh buang/lantik)
///  - tambah ahli, nama semula, keluar, padam kumpulan
///  - galeri media + muat turun pukal ("download all")
class GroupInfoScreen extends StatefulWidget {
  const GroupInfoScreen({
    required this.roomId,
    required this.currentUserId,
    required this.repository,
    required this.mediaStore,
    super.key,
  });

  final String roomId;
  final String currentUserId;
  final ChatRepository repository;
  final MediaStore mediaStore;

  @override
  State<GroupInfoScreen> createState() => _GroupInfoScreenState();
}

class _GroupInfoScreenState extends State<GroupInfoScreen> {
  late final AppLogger _log;
  RoomDetailEntity? _detail;
  List<RoomMediaItem> _media = <RoomMediaItem>[];
  bool _isLoading = true;
  bool _isBulkDownloading = false;
  int _downloadedCount = 0;
  String? _error;

  @override
  void initState() {
    super.initState();
    _log = AppLogger('GroupInfo:${widget.roomId}');
    _reload();
  }

  Future<void> _reload() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final RoomDetailEntity detail = await widget.repository.getRoomDetail(widget.roomId);
      final List<RoomMediaItem> media = await widget.repository.getRoomMedia(widget.roomId);
      if (!mounted) return;
      setState(() {
        _detail = detail;
        _media = media;
        _isLoading = false;
      });
    } on ApiException catch (err) {
      _log.warn('Gagal memuatkan info kumpulan', err);
      if (!mounted) return;
      setState(() {
        _error = err.message;
        _isLoading = false;
      });
    } catch (err, stackTrace) {
      _log.error('Gagal memuatkan info kumpulan', err, stackTrace);
      if (!mounted) return;
      setState(() {
        _error = 'Gagal memuatkan maklumat kumpulan';
        _isLoading = false;
      });
    }
  }

  RoomMemberInfo? get _myMembership {
    final RoomDetailEntity? detail = _detail;
    if (detail == null) return null;
    for (final RoomMemberInfo member in detail.members) {
      if (member.userId == widget.currentUserId) {
        return member;
      }
    }
    return null;
  }

  bool get _amAdmin => _myMembership?.isAdmin ?? false;

  Future<void> _runAction(Future<void> Function() action, String successMessage) async {
    try {
      await action();
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(successMessage)));
      await _reload();
    } on ApiException catch (err) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(err.message)));
    } catch (err, stackTrace) {
      _log.error('Aksi kumpulan gagal', err, stackTrace);
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('Aksi gagal - cuba lagi')));
    }
  }

  Future<void> _addMember() async {
    final TextEditingController controller = TextEditingController();
    final String? username = await showDialog<String>(
      context: context,
      builder: (BuildContext dialogContext) {
        return AlertDialog(
          title: const Text('Tambah Ahli'),
          content: TextField(
            controller: controller,
            autofocus: true,
            decoration: const InputDecoration(
              labelText: 'Username',
              prefixIcon: Icon(Icons.alternate_email),
            ),
            onSubmitted: (String value) => Navigator.of(dialogContext).pop(value),
          ),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: const Text('Batal'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(controller.text),
              child: const Text('Tambah'),
            ),
          ],
        );
      },
    );
    controller.dispose();
    final String trimmed = username?.trim() ?? '';
    if (trimmed.isEmpty) return;
    await _runAction(
      () => widget.repository.addMember(roomId: widget.roomId, username: trimmed),
      'Ahli ditambah',
    );
  }

  Future<void> _renameGroup() async {
    final TextEditingController controller =
        TextEditingController(text: _detail?.room.title ?? '');
    final String? name = await showDialog<String>(
      context: context,
      builder: (BuildContext dialogContext) {
        return AlertDialog(
          title: const Text('Nama Semula Kumpulan'),
          content: TextField(
            controller: controller,
            autofocus: true,
            decoration: const InputDecoration(labelText: 'Nama kumpulan'),
            onSubmitted: (String value) => Navigator.of(dialogContext).pop(value),
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
    final String trimmed = name?.trim() ?? '';
    if (trimmed.isEmpty) return;
    await _runAction(
      () => widget.repository.renameRoom(roomId: widget.roomId, name: trimmed),
      'Kumpulan dinamakan semula',
    );
  }

  Future<void> _removeMember(RoomMemberInfo member) async {
    final bool? confirmed = await showDialog<bool>(
      context: context,
      builder: (BuildContext dialogContext) {
        return AlertDialog(
          title: const Text('Buang Ahli'),
          content: Text('Buang ${member.displayName} dari kumpulan?'),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(false),
              child: const Text('Batal'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(true),
              child: const Text('Buang'),
            ),
          ],
        );
      },
    );
    if (confirmed != true) return;
    await _runAction(
      () => widget.repository.removeMember(roomId: widget.roomId, userId: member.userId),
      '${member.displayName} dibuang',
    );
  }

  Future<void> _promoteMember(RoomMemberInfo member) async {
    await _runAction(
      () => widget.repository.updateMemberRole(
        roomId: widget.roomId,
        userId: member.userId,
        role: 'admin',
      ),
      '${member.displayName} kini admin',
    );
  }

  Future<void> _leaveGroup() async {
    final bool? confirmed = await showDialog<bool>(
      context: context,
      builder: (BuildContext dialogContext) {
        return AlertDialog(
          title: const Text('Keluar dari Kumpulan'),
          content: const Text('Anda tidak akan lagi menerima mesej kumpulan ini.'),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(false),
              child: const Text('Batal'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(true),
              child: const Text('Keluar'),
            ),
          ],
        );
      },
    );
    if (confirmed != true) return;
    try {
      await widget.repository.leaveRoom(widget.roomId);
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } on ApiException catch (err) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(err.message)));
    }
  }

  Future<void> _deleteGroup() async {
    final bool? confirmed = await showDialog<bool>(
      context: context,
      builder: (BuildContext dialogContext) {
        return AlertDialog(
          title: const Text('Padam Kumpulan'),
          content: const Text('Semua mesej kumpulan akan dipadam secara kekal.'),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(false),
              child: const Text('Batal'),
            ),
            FilledButton(
              style: FilledButton.styleFrom(backgroundColor: Colors.redAccent),
              onPressed: () => Navigator.of(dialogContext).pop(true),
              child: const Text('Padam'),
            ),
          ],
        );
      },
    );
    if (confirmed != true) return;
    try {
      await widget.repository.deleteGroup(widget.roomId);
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } on ApiException catch (err) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(err.message)));
    }
  }

  /// FASA 2: muat turun pukal semua media kumpulan ke storan peranti.
  Future<void> _downloadAllMedia() async {
    if (_media.isEmpty || _isBulkDownloading) return;
    setState(() {
      _isBulkDownloading = true;
      _downloadedCount = 0;
    });
    int failed = 0;
    for (final RoomMediaItem item in _media) {
      try {
        await widget.mediaStore.download(
          AppConstants.mediaUrl(item.media.url),
          item.media.name.isNotEmpty ? item.media.name : 'media_${item.messageId}',
        );
        _downloadedCount += 1;
        if (mounted) {
          setState(() {});
        }
      } catch (err, stackTrace) {
        failed += 1;
        _log.error('Muat turun media gagal (${item.media.url})', err, stackTrace);
      }
    }
    if (!mounted) return;
    setState(() => _isBulkDownloading = false);
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(
        content: Text('Muat turun selesai: $_downloadedCount berjaya'
            '${failed > 0 ? ', $failed gagal' : ''}'),
      ));
  }

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    final RoomDetailEntity? detail = _detail;

    return Scaffold(
      appBar: AppBar(
        backgroundColor: scheme.primary,
        foregroundColor: scheme.onPrimary,
        title: Text(detail?.room.title ?? 'Info Kumpulan'),
        actions: <Widget>[
          if (_amAdmin)
            IconButton(
              tooltip: 'Nama semula',
              icon: const Icon(Icons.edit),
              onPressed: _renameGroup,
            ),
        ],
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
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    children: <Widget>[
                      // ---------- Media ----------
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: Row(
                          children: <Widget>[
                            Icon(Icons.photo_library_outlined, size: 18, color: scheme.primary),
                            const SizedBox(width: 6),
                            Text(
                              'Media (${_media.length})',
                              style: const TextStyle(fontWeight: FontWeight.w600),
                            ),
                            const Spacer(),
                            if (_media.isNotEmpty)
                              TextButton.icon(
                                onPressed: _isBulkDownloading ? null : _downloadAllMedia,
                                icon: _isBulkDownloading
                                    ? const SizedBox(
                                        width: 14,
                                        height: 14,
                                        child: CircularProgressIndicator(strokeWidth: 2),
                                      )
                                    : const Icon(Icons.download, size: 18),
                                label: Text(
                                  _isBulkDownloading
                                      ? '$_downloadedCount/${_media.length}'
                                      : 'Muat turun semua',
                                ),
                              ),
                          ],
                        ),
                      ),
                      if (_media.isEmpty)
                        const Padding(
                          padding: EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                          child: Text('Belum ada media dikongsi'),
                        )
                      else
                        SizedBox(
                          height: 110,
                          child: ListView.separated(
                            scrollDirection: Axis.horizontal,
                            padding: const EdgeInsets.symmetric(horizontal: 16),
                            itemCount: _media.length,
                            separatorBuilder: (BuildContext context, int index) =>
                                const SizedBox(width: 8),
                            itemBuilder: (BuildContext context, int index) {
                              final RoomMediaItem item = _media[index];
                              return Container(
                                width: 110,
                                decoration: BoxDecoration(
                                  color: scheme.surfaceContainerHighest,
                                  borderRadius: BorderRadius.circular(10),
                                ),
                                child: Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: <Widget>[
                                    Icon(
                                      item.media.isImage
                                          ? Icons.image
                                          : item.media.isVideo
                                              ? Icons.movie
                                              : Icons.picture_as_pdf,
                                      size: 30,
                                      color: scheme.primary,
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      item.media.name.isNotEmpty
                                          ? item.media.name
                                          : 'media',
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: const TextStyle(fontSize: 11),
                                    ),
                                  ],
                                ),
                              );
                            },
                          ),
                        ),
                      const Divider(height: 24),
                      // ---------- Ahli ----------
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: Row(
                          children: <Widget>[
                            Icon(Icons.people_outline, size: 18, color: scheme.primary),
                            const SizedBox(width: 6),
                            Text(
                              'Ahli (${detail!.members.length})',
                              style: const TextStyle(fontWeight: FontWeight.w600),
                            ),
                            const Spacer(),
                            TextButton.icon(
                              onPressed: _addMember,
                              icon: const Icon(Icons.person_add_alt, size: 18),
                              label: const Text('Tambah'),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 4),
                      ...detail.members.map((RoomMemberInfo member) {
                        final bool isMe = member.userId == widget.currentUserId;
                        return ListTile(
                          leading: CircleAvatar(
                            backgroundColor: scheme.primaryContainer,
                            child: Text(
                              member.displayName.isNotEmpty
                                  ? member.displayName[0].toUpperCase()
                                  : '?',
                              style: TextStyle(color: scheme.primary, fontWeight: FontWeight.w700),
                            ),
                          ),
                          title: Text(
                            isMe ? '${member.displayName} (anda)' : member.displayName,
                            style: const TextStyle(fontWeight: FontWeight.w600),
                          ),
                          subtitle: Text('@${member.username}${member.isAdmin ? ' - admin' : ''}'),
                          trailing: (_amAdmin && !isMe && !member.isAdmin)
                              ? PopupMenuButton<String>(
                                  onSelected: (String value) {
                                    if (value == 'remove') {
                                      _removeMember(member);
                                    } else if (value == 'promote') {
                                      _promoteMember(member);
                                    }
                                  },
                                  itemBuilder: (BuildContext context) => <PopupMenuEntry<String>>[
                                    const PopupMenuItem<String>(
                                      value: 'promote',
                                      child: Text('Jadikan Admin'),
                                    ),
                                    const PopupMenuItem<String>(
                                      value: 'remove',
                                      child: Text('Buang dari Kumpulan'),
                                    ),
                                  ],
                                )
                              : (detail.room.createdBy == member.userId
                                  ? Icon(Icons.star, size: 18, color: scheme.primary)
                                  : null),
                        );
                      }),
                      const Divider(height: 24),
                      // ---------- Aksi ----------
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: Column(
                          children: <Widget>[
                            SizedBox(
                              width: double.infinity,
                              child: OutlinedButton.icon(
                                onPressed: _leaveGroup,
                                icon: const Icon(Icons.logout),
                                label: const Text('Keluar dari Kumpulan'),
                              ),
                            ),
                            const SizedBox(height: 8),
                            if (detail.room.createdBy == widget.currentUserId)
                              SizedBox(
                                width: double.infinity,
                                child: OutlinedButton.icon(
                                  style: OutlinedButton.styleFrom(
                                    foregroundColor: Colors.redAccent,
                                  ),
                                  onPressed: _deleteGroup,
                                  icon: const Icon(Icons.delete_outline),
                                  label: const Text('Padam Kumpulan'),
                                ),
                              ),
                          ],
                        ),
                      ),
                      if (detail.room.createdAt != null)
                        Padding(
                          padding: const EdgeInsets.only(top: 16),
                          child: Center(
                            child: Text(
                              'Dicipta ${DateFormat('dd/MM/yyyy').format(detail.room.createdAt!.toLocal())}',
                              style: TextStyle(fontSize: 12, color: scheme.onSurfaceVariant),
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
    );
  }
}
