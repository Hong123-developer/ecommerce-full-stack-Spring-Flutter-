import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../config/theme.dart';
import '../../providers/providers.dart';
import '../../services/api_client.dart';

class OAuth2CallbackScreen extends ConsumerStatefulWidget {
  final String? accessToken;
  final String? refreshToken;
  final String? error;

  const OAuth2CallbackScreen({
    super.key,
    this.accessToken,
    this.refreshToken,
    this.error,
  });

  @override
  ConsumerState<OAuth2CallbackScreen> createState() => _OAuth2CallbackScreenState();
}

class _OAuth2CallbackScreenState extends ConsumerState<OAuth2CallbackScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _handleCallback());
  }

  Future<void> _handleCallback() async {
    if (widget.error != null && widget.error!.isNotEmpty) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(widget.error!),
            backgroundColor: AppColors.error,
          ),
        );
        context.go('/login');
      }
      return;
    }

    if (widget.accessToken != null && widget.accessToken!.isNotEmpty) {
      await saveTokens(
        accessToken: widget.accessToken!,
        refreshToken: widget.refreshToken ?? '',
      );
      await ref.read(authProvider.notifier).checkAuth();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Successfully signed in with Google!'),
            backgroundColor: AppColors.success,
          ),
        );
        context.go('/home');
      }
    } else {
      if (mounted) {
        context.go('/login');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      backgroundColor: AppColors.background,
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircularProgressIndicator(color: AppColors.primary),
            SizedBox(height: 20),
            Text(
              'Signing in with Google...',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
                color: AppColors.textPrimary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
