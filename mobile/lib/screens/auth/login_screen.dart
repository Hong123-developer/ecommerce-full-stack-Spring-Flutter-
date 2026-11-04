import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../config/theme.dart';
import '../../providers/providers.dart';
import '../../widgets/auth_widgets.dart';
import '../../widgets/common_widgets.dart';

const _rememberedEmailKey = 'remembered_email';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailCtrl = TextEditingController();
  final _passwordCtrl = TextEditingController();
  bool _obscure = true;
  bool _loading = false;
  bool _rememberMe = false;
  AutovalidateMode _autovalidate = AutovalidateMode.disabled;

  @override
  void initState() {
    super.initState();
    _loadRememberedEmail();
  }

  @override
  void dispose() {
    _emailCtrl.dispose();
    _passwordCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadRememberedEmail() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final email = prefs.getString(_rememberedEmailKey);
      if (email == null || !mounted) return;
      setState(() {
        _emailCtrl.text = email;
        _rememberMe = true;
      });
    } catch (_) {}
  }

  // "Remember me" only remembers the email; sessions persist either way.
  Future<void> _saveRememberedEmail(String email) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      if (_rememberMe) {
        await prefs.setString(_rememberedEmailKey, email);
      } else {
        await prefs.remove(_rememberedEmailKey);
      }
    } catch (_) {}
  }

  Future<void> _login() async {
    FocusScope.of(context).unfocus();
    if (!(_formKey.currentState?.validate() ?? false)) {
      setState(() => _autovalidate = AutovalidateMode.onUserInteraction);
      return;
    }
    final email = _emailCtrl.text.trim();
    final notifier = ref.read(authProvider.notifier);
    setState(() => _loading = true);
    final success = await notifier.login(email, _passwordCtrl.text);
    if (success) await _saveRememberedEmail(email);
    if (!mounted) return;

    if (success) {
      setState(() => _loading = false);
      context.go('/home');
      return;
    }

    final error = ref.read(authProvider).error;
    // An account that never finished sign-up (403 "Please verify your email ...")
    // goes back to the code screen instead of being stuck here
    if (notifier.lastLoginErrorStatus == 403 && (error ?? '').toLowerCase().contains('verify')) {
      // A cooldown error just means the previous code is still valid
      await notifier.resendVerificationCode(email);
      if (!mounted) return;
      setState(() => _loading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please verify your email first. Enter the code we sent you.')),
      );
      context.push('/verify-email', extra: email);
      return;
    }

    setState(() => _loading = false);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(error ?? 'Login failed'), backgroundColor: AppColors.error),
    );
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
                  const AuthIllustration(
                    icon: Icons.person_rounded,
                    badgeIcon: Icons.lock_open_rounded,
                  ),
                  const SizedBox(height: 20),
                  Text(
                    appText(language, 'login'),
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.w700,
                          color: palette.textPrimary,
                        ),
                  ),
                  const SizedBox(height: 20),

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
                    controller: _passwordCtrl,
                    hint: appText(language, 'password'),
                    icon: Icons.lock_outline_rounded,
                    textInputAction: TextInputAction.done,
                    autofillHints: const [AutofillHints.password],
                    obscureText: _obscure,
                    onToggleObscure: () => setState(() => _obscure = !_obscure),
                    validator: (value) =>
                        (value ?? '').isEmpty ? 'Please enter your password' : null,
                  ),
                  const SizedBox(height: 8),

                  // ─── Remember me / Forgot password ───
                  Row(
                    children: [
                      SizedBox(
                        width: 24,
                        height: 24,
                        child: Checkbox(
                          value: _rememberMe,
                          activeColor: palette.primary,
                          side: BorderSide(color: palette.textHint, width: 1.2),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                          onChanged: (v) => setState(() => _rememberMe = v ?? false),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Flexible(
                        child: GestureDetector(
                          onTap: () => setState(() => _rememberMe = !_rememberMe),
                          child: Text(
                            'Remember me',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(fontSize: 13, color: palette.textSecondary),
                          ),
                        ),
                      ),
                      const Spacer(),
                      TextButton(
                        onPressed: () =>
                            context.push('/forgot-password', extra: _emailCtrl.text.trim()),
                        // No padding, so the text lines up with the fields' right edge
                        style: TextButton.styleFrom(
                          padding: EdgeInsets.zero,
                          minimumSize: const Size(0, 36),
                          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                        ),
                        child: Text(
                          'Forgot password?',
                          style: TextStyle(
                            fontSize: 13,
                            color: palette.primary,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),

                  GradientButton(
                    label: appText(language, 'login'),
                    isLoading: _loading,
                    onPressed: _login,
                  ),
                  const SizedBox(height: 24),

                  const AuthDivider(label: 'Or login with'),
                  const SizedBox(height: 20),

                  // ─── Social Login Options ───
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
                        "Don't have an account? ",
                        style: TextStyle(color: palette.textSecondary, fontSize: 13),
                      ),
                      GestureDetector(
                        onTap: () => context.push('/register'),
                        child: Text(
                          'Sign up',
                          style: TextStyle(
                            color: palette.primary,
                            fontWeight: FontWeight.w700,
                            fontSize: 13,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Center(
                    child: TextButton(
                      onPressed: () => context.go('/home'),
                      child: Text(
                        'Continue as guest',
                        style: TextStyle(fontSize: 13, color: palette.textHint),
                      ),
                    ),
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
