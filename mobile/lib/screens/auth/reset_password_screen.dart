import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../config/theme.dart';
import '../../providers/providers.dart';
import '../../widgets/auth_widgets.dart';

class ResetPasswordScreen extends ConsumerStatefulWidget {
  final String email;
  final String code;

  const ResetPasswordScreen({super.key, required this.email, required this.code});

  @override
  ConsumerState<ResetPasswordScreen> createState() => _ResetPasswordScreenState();
}

class _ResetPasswordScreenState extends ConsumerState<ResetPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _passwordCtrl = TextEditingController();
  final _confirmCtrl = TextEditingController();
  bool _obscure = true;
  bool _obscureConfirm = true;
  bool _loading = false;
  AutovalidateMode _autovalidate = AutovalidateMode.disabled;

  @override
  void dispose() {
    _passwordCtrl.dispose();
    _confirmCtrl.dispose();
    super.dispose();
  }

  Future<void> _reset() async {
    FocusScope.of(context).unfocus();
    if (!(_formKey.currentState?.validate() ?? false)) {
      setState(() => _autovalidate = AutovalidateMode.onUserInteraction);
      return;
    }
    setState(() => _loading = true);
    final success = await ref.read(authProvider.notifier).resetPassword(
          widget.email,
          widget.code,
          _passwordCtrl.text,
        );
    if (!mounted) return;
    setState(() => _loading = false);

    if (success) {
      await showAuthSuccessDialog(
        context,
        title: 'Reset Successful',
        message: 'Your password has been successfully reset.',
        actionLabel: 'Back to login',
        onAction: () => context.go('/login'),
      );
    } else {
      // A wrong or expired code only surfaces here; the user can go back and re-enter it
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(ref.read(authProvider).error ?? 'Could not reset password. Please try again.'),
          backgroundColor: AppColors.error,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return AuthStepScaffold(
      title: 'Reset Password',
      buttonLabel: 'Reset',
      isLoading: _loading,
      onPressed: _reset,
      fallbackRoute: '/forgot-password',
      child: Form(
        key: _formKey,
        autovalidateMode: _autovalidate,
        child: AutofillGroup(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const AuthIllustration(
                icon: Icons.password_rounded,
                badgeIcon: Icons.lock_rounded,
              ),
              const SizedBox(height: 24),
              const AuthStepMessage(
                'Use at least 8 characters with a letter and a number',
              ),
              const SizedBox(height: 24),
              AuthTextField(
                controller: _passwordCtrl,
                hint: 'Enter new password',
                icon: Icons.lock_outline_rounded,
                textInputAction: TextInputAction.next,
                autofillHints: const [AutofillHints.newPassword],
                obscureText: _obscure,
                onToggleObscure: () => setState(() => _obscure = !_obscure),
                validator: AuthValidators.newPassword,
              ),
              const SizedBox(height: 12),
              AuthTextField(
                controller: _confirmCtrl,
                hint: 'Re-enter password',
                icon: Icons.lock_outline_rounded,
                textInputAction: TextInputAction.done,
                obscureText: _obscureConfirm,
                onToggleObscure: () => setState(() => _obscureConfirm = !_obscureConfirm),
                validator: AuthValidators.confirmPassword(_passwordCtrl),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
