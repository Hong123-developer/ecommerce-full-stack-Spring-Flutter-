import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../config/theme.dart';
import '../../providers/providers.dart';
import '../../widgets/auth_widgets.dart';
import '../../widgets/common_widgets.dart';

class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameCtrl = TextEditingController();
  final _emailCtrl = TextEditingController();
  final _phoneCtrl = TextEditingController();
  final _passwordCtrl = TextEditingController();
  final _confirmCtrl = TextEditingController();
  bool _obscure = true;
  bool _obscureConfirm = true;
  bool _loading = false;
  // Errors only show live after the first submit attempt
  AutovalidateMode _autovalidate = AutovalidateMode.disabled;

  @override
  void dispose() {
    _nameCtrl.dispose();
    _emailCtrl.dispose();
    _phoneCtrl.dispose();
    _passwordCtrl.dispose();
    _confirmCtrl.dispose();
    super.dispose();
  }

  Future<void> _register() async {
    FocusScope.of(context).unfocus();
    if (!(_formKey.currentState?.validate() ?? false)) {
      setState(() => _autovalidate = AutovalidateMode.onUserInteraction);
      return;
    }
    final email = _emailCtrl.text.trim();
    setState(() => _loading = true);
    final success = await ref.read(authProvider.notifier).register(
          _nameCtrl.text.trim(),
          email,
          _phoneCtrl.text.trim(),
          _passwordCtrl.text,
        );
    if (!mounted) return;
    setState(() => _loading = false);
    if (success) {
      context.push('/verify-email', extra: email);
    } else {
      final error = ref.read(authProvider).error;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error ?? 'Registration failed'), backgroundColor: AppColors.error),
      );
    }
  }

  void _goToLogin() {
    if (context.canPop()) {
      context.pop();
    } else {
      context.go('/login');
    }
  }

  @override
  Widget build(BuildContext context) {
    final language = ref.watch(appLanguageProvider);
    final palette = context.palette;
    return Scaffold(
      backgroundColor: palette.surface,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 16, 24, 24),
          child: Form(
            key: _formKey,
            autovalidateMode: _autovalidate,
            child: AutofillGroup(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const AuthIllustration(icon: Icons.person_add_alt_1_rounded),
                  const SizedBox(height: 20),
                  Text(
                    appText(language, 'register'),
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.w700,
                          color: palette.textPrimary,
                        ),
                  ),
                  const SizedBox(height: 20),

                  AuthTextField(
                    controller: _nameCtrl,
                    hint: 'Full name',
                    icon: Icons.person_outline_rounded,
                    keyboardType: TextInputType.name,
                    textInputAction: TextInputAction.next,
                    autofillHints: const [AutofillHints.name],
                    validator: AuthValidators.fullName,
                  ),
                  const SizedBox(height: 12),
                  AuthTextField(
                    controller: _emailCtrl,
                    hint: appText(language, 'email'),
                    icon: Icons.email_outlined,
                    keyboardType: TextInputType.emailAddress,
                    textInputAction: TextInputAction.next,
                    autofillHints: const [AutofillHints.email],
                    validator: AuthValidators.email,
                  ),
                  const SizedBox(height: 12),
                  AuthTextField(
                    controller: _phoneCtrl,
                    hint: '${appText(language, 'phone')} (optional)',
                    icon: Icons.phone_outlined,
                    keyboardType: TextInputType.phone,
                    textInputAction: TextInputAction.next,
                    autofillHints: const [AutofillHints.telephoneNumber],
                    validator: AuthValidators.phone,
                  ),
                  const SizedBox(height: 12),
                  AuthTextField(
                    controller: _passwordCtrl,
                    hint: appText(language, 'password'),
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
                    hint: appText(language, 'confirmPassword'),
                    icon: Icons.lock_outline_rounded,
                    textInputAction: TextInputAction.done,
                    obscureText: _obscureConfirm,
                    onToggleObscure: () => setState(() => _obscureConfirm = !_obscureConfirm),
                    validator: AuthValidators.confirmPassword(_passwordCtrl),
                  ),
                  const SizedBox(height: 24),

                  GradientButton(
                    label: appText(language, 'register'),
                    isLoading: _loading,
                    onPressed: _register,
                  ),
                  const SizedBox(height: 24),

                  const AuthDivider(label: 'Or sign up with'),
                  const SizedBox(height: 20),

                  // ─── Social Sign-Up Options ───
                  Row(
                    children: [
                      Expanded(
                        child: SocialAuthButton(
                          label: 'Google',
                          logo: const GoogleLogo(),
                          onPressed: () => launchSocialAuth(context, 'google', 'Google'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: SocialAuthButton(
                          label: 'Facebook',
                          logo: const FacebookLogo(),
                          onPressed: () => launchSocialAuth(context, 'facebook', 'Facebook'),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 28),

                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        'Already have an account? ',
                        style: TextStyle(color: palette.textSecondary, fontSize: 13),
                      ),
                      GestureDetector(
                        onTap: _goToLogin,
                        child: Text(
                          'Log in',
                          style: TextStyle(
                            color: palette.primary,
                            fontWeight: FontWeight.w700,
                            fontSize: 13,
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
