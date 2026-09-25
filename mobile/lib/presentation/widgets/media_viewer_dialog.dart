import 'dart:io';

import 'package:flutter/material.dart';

import '../../core/constants/app_constants.dart';
import '../../core/error/app_exception.dart';
import '../../core/utils/app_logger.dart';
import '../../domain/entities/message_entity.dart';
import '../../data/datasources/media_store.dart';

/// Dialog penuh untuk melihat/memuat turun lampiran media (FASA 2).
/// Imej dipaparkan dengan pinch-zoom; video/pdf menunjukkan maklumat fail.
Future<void> showMediaViewer(
  BuildContext context, {
  required MediaEntity media,
  required MediaStore mediaStore,
}) {
  return showDialog<void>(
    context: context,
    barrierColor: Colors.black87,
    builder: (BuildContext dialogContext) {
      final AppLogger log = AppLogger('MediaViewer');
      return _MediaViewerDialog(
        media: media,
        mediaStore: mediaStore,
        log: log,
      );
    },
  );
}

class _MediaViewerDialog extends StatefulWidget {
  const _MediaViewerDialog({required this.media, required this.mediaStore, required this.log});

  final MediaEntity media;
  final MediaStore mediaStore;
  final AppLogger log;

  @override
  State<_MediaViewerDialog> createState() => _MediaViewerDialogState();
}

class _MediaViewerDialogState extends State<_MediaViewerDialog> {
  bool _downloading = false;
  String? _error;

  Future<void> _download() async {
    setState(() {
      _downloading = true;
      _error = null;
    });
    try {
      final File file = await widget.mediaStore.download(
        AppConstants.mediaUrl(widget.media.url),
        widget.media.name.isNotEmpty ? widget.media.name : 'media',
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text('Disimpan: ${file.uri.pathSegments.last}')));
      Navigator.of(context).pop();
    } on ApiException catch (err) {
      if (!mounted) return;
      setState(() {
        _downloading = false;
        _error = err.message;
      });
    } catch (err, stackTrace) {
      widget.log.error('Muat turun media gagal', err, stackTrace);
      if (!mounted) return;
      setState(() {
        _downloading = false;
        _error = 'Muat turun gagal';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final MediaEntity media = widget.media;
    return Dialog(
      backgroundColor: Colors.transparent,
      insetPadding: const EdgeInsets.all(16),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: <Widget>[
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.only(left: 8),
                  child: Text(
                    media.name.isNotEmpty ? media.name : 'Media',
                    style: const TextStyle(color: Colors.white, fontSize: 14),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ),
              IconButton(
                icon: _downloading
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : const Icon(Icons.download, color: Colors.white),
                tooltip: 'Muat turun',
                onPressed: _downloading ? null : _download,
              ),
              IconButton(
                icon: const Icon(Icons.close, color: Colors.white),
                onPressed: () => Navigator.of(context).pop(),
              ),
            ],
          ),
          Flexible(
            child: Padding(
              padding: const EdgeInsets.all(8),
              child: media.isImage
                  ? InteractiveViewer(
                      maxScale: 5,
                      child: Image.network(
                        AppConstants.mediaUrl(media.url),
                        fit: BoxFit.contain,
                        errorBuilder: (BuildContext context, Object error, StackTrace? stack) =>
                            const Center(
                          child: Icon(Icons.broken_image, color: Colors.white54, size: 48),
                        ),
                      ),
                    )
                  : Container(
                      padding: const EdgeInsets.all(24),
                      decoration: BoxDecoration(
                        color: Colors.white10,
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: <Widget>[
                          Icon(
                            media.isVideo ? Icons.movie : Icons.picture_as_pdf,
                            color: Colors.white70,
                            size: 48,
                          ),
                          const SizedBox(height: 12),
                          Text(
                            media.name.isNotEmpty ? media.name : 'Fail',
                            style: const TextStyle(color: Colors.white),
                            textAlign: TextAlign.center,
                          ),
                          if (media.readableSize.isNotEmpty) ...<Widget>[
                            const SizedBox(height: 4),
                            Text(
                              media.readableSize,
                              style: const TextStyle(color: Colors.white54, fontSize: 12),
                            ),
                          ],
                          const SizedBox(height: 8),
                          const Text(
                            'Gunakan butang muat turun untuk menyimpan fail ini.',
                            style: TextStyle(color: Colors.white54, fontSize: 12),
                            textAlign: TextAlign.center,
                          ),
                        ],
                      ),
                    ),
            ),
          ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Text(
                _error!,
                style: const TextStyle(color: Colors.redAccent, fontSize: 12),
              ),
            ),
        ],
      ),
    );
  }
}
