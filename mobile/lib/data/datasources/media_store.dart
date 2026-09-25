import 'dart:io';

import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';

import '../../core/constants/app_constants.dart';
import '../../core/error/app_exception.dart';

/// Penyimpan media tempatan (FASA 2): memuat turun fail dari pelayan
/// (endpoint statik /uploads) ke direktori dokumen aplikasi.
/// Digunakan oleh muat turun tunggal (viewer) dan pukal (info kumpulan).
class MediaStore {
  MediaStore();

  final http.Client _client = http.Client();

  /// Memuat turun [absoluteUrl] dan menyimpannya dengan nama mesra.
  /// @returns File tempatan yang telah disimpan.
  Future<File> download(String absoluteUrl, String fileName) async {
    try {
      final http.Response response = await _client
          .get(Uri.parse(absoluteUrl))
          .timeout(AppConstants.apiTimeout * 2);
      if (response.statusCode >= 400) {
        throw ApiException(
          statusCode: response.statusCode,
          code: 'DOWNLOAD_FAILED',
          message: 'Muat turun gagal (${response.statusCode})',
        );
      }
      final Directory docs = await getApplicationDocumentsDirectory();
      final Directory mediaDir = Directory('${docs.path}/yugram_media');
      if (!await mediaDir.exists()) {
        await mediaDir.create(recursive: true);
      }
      final String safeBase =
          fileName.replaceAll(RegExp(r'[^A-Za-z0-9_.-]'), '_').replaceAll(RegExp(r'_+'), '_');
      final String prefix = DateTime.now().millisecondsSinceEpoch.toString();
      final File file = File('${mediaDir.path}/${prefix}_$safeBase');
      await file.writeAsBytes(response.bodyBytes);
      return file;
    } on ApiException {
      rethrow;
    } catch (err) {
      throw ApiException(
        statusCode: 0,
        code: 'DOWNLOAD_FAILED',
        message: 'Muat turun gagal: $err',
      );
    }
  }

  /// Senarai fail media yang telah dimuat turun (terbaru dahulu).
  Future<List<FileSystemEntity>> savedFiles() async {
    try {
      final Directory docs = await getApplicationDocumentsDirectory();
      final Directory mediaDir = Directory('${docs.path}/yugram_media');
      if (!await mediaDir.exists()) {
        return <FileSystemEntity>[];
      }
      final List<FileSystemEntity> files = await mediaDir.list().toList();
      files.sort((FileSystemEntity a, FileSystemEntity b) =>
          b.path.compareTo(a.path));
      return files;
    } catch (err) {
      return <FileSystemEntity>[];
    }
  }
}
