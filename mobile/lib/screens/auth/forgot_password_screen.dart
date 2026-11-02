import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../config/theme.dart';
import '../../providers/providers.dart';
import '../../widgets/auth_widgets.dart';

class ForgotPasswordScreen extends ConsumerStatefulWidget {
  /// Prefills the email field, e.g. with what was typed on the login screen.
  final String initialEmail;

  const ForgotPasswordScreen({super.key, this.initialEmail = ''});

  @override
  ConsumerState<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends ConsumerState<ForgotPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _emailCtrl;
  bool _loading = false;
  AutovalidateMode _autovalidate = AutovalidateMode.disabled;

  @override
  void initState() {
    super.initState();
    _emailCtrl = TextEditingController(text: widget.initialEmail);
  }

  @override
  void dispose() {
    _emailCtrl.dispose();
    super.dispose();
  }

  Future<void> _sendCode() async {
    FocusScope.of(context).unfocus();
    if (!(_formKey.currentState?.validate() ?? false)) {
      setState(() => _autovalidate = AutovalidateMode.onUserInteraction);
      return;
    }
    final email = _emailCtrl.text.trim();
    setState(() => _loading = true);
    final success = await ref.read(authProvider.notifier).forgotPassword(email);
    if (!mounted) return;
    setState(() => _loading = false);

    if (success) {
      context.push('/verify-reset-code', extra: email);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(ref.read(authProvider).error ?? 'Could not send the code. Please try again.'),
          backgroundColor: AppColors.error,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final language = ref.watch(appLanguageProvider);
    return AuthStepScaffold(
      title: appText(language, 'forgotPassword'),
      buttonLabel: 'Get OTP',
      isLoading: _loading,
      onPressed: _sendCode,
      child: Form(
        key: _formKey,
        autovalidateMode: _autovalidate,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const AuthIllustration(
              icon: Icons.lock_reset_rounded,
              badgeIcon: Icons.mail_rounded,
            ),
            const SizedBox(height: 24),
            const AuthStepMessage(
              'Please enter your email address. We will send a code '
              'to your email to reset your password.',
            ),
            const SizedBox(height: 24),
            AuthTextField(
              controller: _emailCtrl,
              hint: appText(language, 'email'),
              icon: Icons.email_outlined,
              keyboardType: TextInputType.emailAddress,
              textInputAction: TextInputAction.done,
              autofillHints: const [AutofillHints.email],
              validator: AuthValidators.email,
            ),
          ],
        ),
      ),
    );
  }
}
