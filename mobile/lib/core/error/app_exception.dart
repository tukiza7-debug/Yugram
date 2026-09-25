/// Exception lapisan data - dibina oleh ApiClient dan repositori.
class ApiException implements Exception {
  ApiException({
    required this.statusCode,
    required this.code,
    required this.message,
    this.details,
  });

  /// Kod HTTP (0 = ralat rangkaian/sebelum-respons).
  final int statusCode;
  final String code;
  final String message;
  final List<FieldError>? details;

  bool get isNetworkError => statusCode == 0;

  @override
  String toString() => 'ApiException($statusCode, $code): $message';
}

/// Ralat pengesahan medan daripada pelayan (Joi).
class FieldError {
  FieldError({required this.field, required this.message});

  final String field;
  final String message;

  factory FieldError.fromJson(Map<String, dynamic> json) => FieldError(
        field: json['field']?.toString() ?? '',
        message: json['message']?.toString() ?? '',
      );
}
