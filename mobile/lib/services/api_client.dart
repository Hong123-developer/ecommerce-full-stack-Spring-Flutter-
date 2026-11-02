import 'package:flutter/foundation.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../config/api_config.dart';

// ──────────────────────────────────────────────
// Secure Token Storage
// ──────────────────────────────────────────────
const _storage = FlutterSecureStorage();

Future<String?> getAccessToken() => _storage.read(key: 'accessToken');
Future<String?> getRefreshToken() => _storage.read(key: 'refreshToken');

Future<void> saveTokens({
  required String accessToken,
  required String refreshToken,
}) async {
  await _storage.write(key: 'accessToken', value: accessToken);
  await _storage.write(key: 'refreshToken', value: refreshToken);
}

Future<void> clearTokens() async {
  await _storage.delete(key: 'accessToken');
  await _storage.delete(key: 'refreshToken');
}

// ──────────────────────────────────────────────
// Dio Provider with Interceptors
// ──────────────────────────────────────────────
final dioProvider = Provider<Dio>((ref) {
  final dio = Dio(BaseOptions(
    baseUrl: ApiConfig.baseUrl,
    connectTimeout: const Duration(seconds: 15),
    receiveTimeout: const Duration(seconds: 15),
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
  ));

  dio.interceptors.add(AuthInterceptor(dio));
  dio.interceptors.add(LogInterceptor(
    requestBody: true,
    responseBody: true,
    logPrint: (o) => debugPrint('🌐 $o'),
  ));

  return dio;
});

class AuthInterceptor extends Interceptor {
  final Dio _dio;
  bool _isRefreshing = false;

  AuthInterceptor(this._dio);

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    final token = await getAccessToken();
    if (token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    if (err.response?.statusCode == 401 && !_isRefreshing) {
      _isRefreshing = true;
      String? newAccessToken;
      try {
        final refreshTk = await getRefreshToken();
        if (refreshTk != null) {
          final response = await Dio(BaseOptions(baseUrl: ApiConfig.baseUrl)).post(
            ApiConfig.refreshToken,
            data: {'refreshToken': refreshTk},
          );
          final data = response.data['data'];
          await saveTokens(
            accessToken: data['accessToken'],
            refreshToken: data['refreshToken'],
          );
          newAccessToken = data['accessToken'];
        }
      } catch (_) {
        // Only a failed refresh ends the session
        await clearTokens();
      } finally {
        _isRefreshing = false;
      }

      if (newAccessToken != null) {
        // Retry original request. A FormData body can only be sent once, so send a copy.
        final opts = err.requestOptions;
        if (opts.data is FormData) opts.data = (opts.data as FormData).clone();
        opts.headers['Authorization'] = 'Bearer $newAccessToken';
        try {
          handler.resolve(await _dio.fetch(opts));
        } on DioException catch (retryError) {
          handler.next(retryError);
        }
        return;
      }
    }
    handler.next(err);
  }
}

// ──────────────────────────────────────────────
// API Error Handling
// ──────────────────────────────────────────────
class ApiException implements Exception {
  final String message;
  final int? statusCode;

  ApiException(this.message, {this.statusCode});

  factory ApiException.fromDioError(DioException error) {
    if (error.response?.data is Map) {
      final data = error.response!.data as Map;
      // Validation failures carry the useful text per field; the top-level
      // message is only "One or more fields are invalid".
      final fieldErrors = data['fieldErrors'];
      final firstFieldError =
          fieldErrors is Map && fieldErrors.isNotEmpty ? fieldErrors.values.first?.toString() : null;
      return ApiException(
        firstFieldError ?? data['message']?.toString() ?? 'Something went wrong',
        statusCode: error.response?.statusCode,
      );
    }
    switch (error.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
        return ApiException('Connection timed out');
      case DioExceptionType.connectionError:
        return ApiException('No internet connection');
      default:
        return ApiException('Something went wrong');
    }
  }

  @override
  String toString() => message;
}
