import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../config/theme.dart';
import '../../providers/providers.dart';
import '../../widgets/auth_widgets.dart';

enum OtpPurpose {
  /// Confirms a new account's email (sign-up flow).
  emailVerification,

  /// Collects a password-reset code and hands it to the Reset Password screen.
  /// The backend only checks this code when the new password is submitted.
  passwordReset,
}

class VerifyEmailScreen extends ConsumerStatefulWidget {
  final String email;
  final OtpPurpose purpose;

  const VerifyEmailScreen({
    super.key,
    required this.email,
    this.purpose = OtpPurpose.emailVerification,
  });

  @override
  ConsumerState<VerifyEmailScreen> createState() => _VerifyEmailScreenState();
}

class _VerifyEmailScreenState extends ConsumerState<VerifyEmailScreen> {
  static const _codeLength = 6;

  // One hidden field holds the code and the six cells just display it, so
  // typing, backspace, paste and SMS/email autofill all behave like a normal input.
  final _codeCtrl = TextEditingController();
  final _codeFocus = FocusNode();
  bool _wasComplete = false;

  bool _loading = false;
  bool _isResending = false;
  int _resendCountdown = 60;
  Timer? _timer;

  String get _email => widget.email.trim();
  bool get _isReset => widget.purpose == OtpPurpose.passwordReset;
  String get _code => _codeCtrl.text;

  @override
  void initState() {
    super.initState();
    _codeFocus.addListener(() => setState(() {}));
    _startCountdown();
  }

  void _startCountdown() {
    _resendCountdown = 60;
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) {
        timer.cancel();
        return;
      }
      if (_resendCountdown > 0) {
        setState(() => _resendCountdown--);
      } else {
        timer.cancel();
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _codeCtrl.dispose();
    _codeFocus.dispose();
    super.dispose();
  }

  void _onCodeChanged(String value) {
    setState(() {});
    // Submit only when the code becomes complete, not on every edit of a complete code
    final complete = value.length == _codeLength;
    if (complete && !_wasComplete) _verify();
    _wasComplete = complete;
  }

  void _setCode(String code) {
    final clean = code.replaceAll(RegExp(r'\D'), '');
    final value = clean.length > _codeLength ? clean.substring(0, _codeLength) : clean;
    _codeCtrl.value = TextEditingValue(
      text: value,
      selection: TextSelection.collapsed(offset: value.length),
    );
    _codeFocus.requestFocus();
    _onCodeChanged(value);
  }

  void _clearCode() {
    _codeCtrl.clear();
    _wasComplete = false;
    setState(() {});
    _codeFocus.requestFocus();
  }

  Future<void> _loadCodeFromClipboard() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text ?? '';
    final match = RegExp(r'\b\d{6}\b').firstMatch(text);
    final digits = match != null ? match.group(0)! : text.replaceAll(RegExp(r'\D'), '');
    if (!mounted) return;

    if (digits.length >= _codeLength) {
      _wasComplete = false; // a pasted code is always submitted
      _setCode(digits);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('No 6-digit code found in clipboard. Please check your email.'),
          backgroundColor: AppColors.warning,
        ),
      );
    }
  }

  Future<void> _verify() async {
    if (_loading) return;
    if (_email.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Missing email address. Please start again.')),
      );
      return;
    }
    if (_code.length != _codeLength) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter the complete 6-digit code')),
      );
      return;
    }

    if (_isReset) {
      FocusScope.of(context).unfocus();
      context.push('/reset-password', extra: {'email': _email, 'code': _code});
      return;
    }

    setState(() => _loading = true);
    final success = await ref.read(authProvider.notifier).verifyEmail(_email, _code);
    if (!mounted) return;
    setState(() => _loading = false);

    if (success) {
      _timer?.cancel();
      FocusScope.of(context).unfocus();
      await showAuthSuccessDialog(
        context,
        title: 'Successful',
        message: 'Your account has been created successfully.\nPlease log in to continue.',
        actionLabel: 'Go to login',
        onAction: () => context.go('/login'),
      );
    } else {
      _clearCode();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(ref.read(authProvider).error ?? 'Verification failed. Code may be invalid or expired.'),
          backgroundColor: AppColors.error,
        ),
      );
    }
  }

  Future<void> _resendCode() async {
    if (_email.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Missing email address. Please start again.')),
      );
      return;
    }

    setState(() => _isResending = true);
    final notifier = ref.read(authProvider.notifier);
    // A reset code is re-issued by asking for a new one
    final success = _isReset
        ? await notifier.forgotPassword(_email)
        : await notifier.resendVerificationCode(_email);
    if (!mounted) return;
    setState(() => _isResending = false);

    if (success) {
      _startCountdown();
      _clearCode();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('A new 6-digit code has been sent to your email!'),
          backgroundColor: AppColors.success,
        ),
      );
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(ref.read(authProvider).error ?? 'Could not resend the code'),
          backgroundColor: AppColors.error,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final emailLabel = _email.isNotEmpty ? _email : 'your email';

    return AuthStepScaffold(
      title: 'Verify OTP',
      buttonLabel: 'Verify',
      isLoading: _loading,
      onPressed: _code.length == _codeLength ? _verify : null,
      fallbackRoute: _isReset ? '/forgot-password' : '/register',
      child: Column(
        children: [
          const AuthIllustration(
            icon: Icons.mark_email_read_rounded,
            badgeIcon: Icons.shield_rounded,
          ),
          const SizedBox(height: 24),
          Text.rich(
            TextSpan(
              style: const TextStyle(
                color: AppColors.textSecondary,
                fontSize: 13,
                height: 1.5,
              ),
              children: [
                // The backend never reveals whether a reset email exists
                TextSpan(
                  text: _isReset
                      ? 'If an account exists for\n'
                      : 'We have just sent a 6 digit code to\n',
                ),
                TextSpan(
                  text: emailLabel,
                  style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                TextSpan(
                  text: _isReset
                      ? ', we have sent it a 6 digit code. Enter the code below.'
                      : '. Enter the code below.',
                ),
              ],
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 28),

          // ─── 6 OTP Cells ───
          _buildCodeInput(),
          const SizedBox(height: 24),

          // ─── Resend Code Option ───
          Wrap(
            alignment: WrapAlignment.center,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              const Text(
                "Didn't receive any code? ",
                style: TextStyle(color: AppColors.textSecondary, fontSize: 13),
              ),
              if (_resendCountdown > 0)
                Text(
                  'Resend in ${_resendCountdown}s',
                  style: const TextStyle(
                    color: AppColors.textHint,
                    fontWeight: FontWeight.w600,
                    fontSize: 13,
                  ),
                )
              else
                GestureDetector(
                  onTap: _isResending ? null : _resendCode,
                  child: Text(
                    _isResending ? 'Sending...' : 'Resend',
                    style: const TextStyle(
                      color: AppColors.primary,
                      fontWeight: FontWeight.w700,
                      fontSize: 13,
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 8),
          TextButton.icon(
            onPressed: _loadCodeFromClipboard,
            icon: const Icon(Icons.content_paste_rounded, size: 16),
            label: const Text('Paste code', style: TextStyle(fontSize: 13)),
          ),
        ],
      ),
    );
  }

  Widget _buildCodeInput() {
    const gap = 8.0;
    return LayoutBuilder(
      builder: (context, constraints) {
        // Shrinks to fit narrow phones (6 x 46 + gaps needs 316px)
        final cellWidth = ((constraints.maxWidth - gap * (_codeLength - 1)) / _codeLength).clamp(36.0, 46.0);
        final activeIndex = _code.length.clamp(0, _codeLength - 1);

        return SizedBox(
          height: 54,
          child: Stack(
            alignment: Alignment.center,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(_codeLength, (i) {
                  final digit = i < _code.length ? _code[i] : '';
                  final isActive = _codeFocus.hasFocus && i == activeIndex;
                  return Container(
                    width: cellWidth,
                    height: 54,
                    margin: EdgeInsets.only(right: i < _codeLength - 1 ? gap : 0),
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: AppColors.surface,
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                        color: isActive || digit.isNotEmpty ? AppColors.primary : AppColors.divider,
                        width: isActive ? 1.5 : 1.2,
                      ),
                    ),
                    child: digit.isNotEmpty
                        ? Text(
                            digit,
                            style: const TextStyle(
                              fontSize: 20,
                              fontWeight: FontWeight.w700,
                              color: AppColors.textPrimary,
                            ),
                          )
                        : (isActive
                            ? Container(width: 2, height: 22, color: AppColors.primary)
                            : null),
                  );
                }),
              ),
              // The real input: invisible, covering the cells so a tap focuses it
              Positioned.fill(
                child: Opacity(
                  opacity: 0,
                  child: TextField(
                    controller: _codeCtrl,
                    focusNode: _codeFocus,
                    autofocus: true,
                    keyboardType: TextInputType.number,
                    autofillHints: const [AutofillHints.oneTimeCode],
                    inputFormatters: const [_CodeInputFormatter(_codeLength)],
                    showCursor: false,
                    // Always edit at the end, whichever cell was tapped
                    onTap: () => _codeCtrl.selection = TextSelection.collapsed(offset: _codeCtrl.text.length),
                    decoration: const InputDecoration(
                      border: InputBorder.none,
                      enabledBorder: InputBorder.none,
                      focusedBorder: InputBorder.none,
                      filled: false,
                      counterText: '',
                    ),
                    onChanged: _onCodeChanged,
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

/// Digits only, at most [length]. Pasting a whole code over digits already
/// typed keeps the pasted code instead of truncating the combined text.
class _CodeInputFormatter extends TextInputFormatter {
  final int length;

  const _CodeInputFormatter(this.length);

  @override
  TextEditingValue formatEditUpdate(TextEditingValue oldValue, TextEditingValue newValue) {
    String digitsOf(String s) => s.replaceAll(RegExp(r'\D'), '');

    var code = digitsOf(newValue.text);
    if (code.length > length) {
      final insertedLength = newValue.text.length - oldValue.text.length;
      final end = newValue.selection.baseOffset;
      final inserted = insertedLength > 0 && end >= insertedLength && end <= newValue.text.length
          ? digitsOf(newValue.text.substring(end - insertedLength, end))
          : '';
      code = (inserted.length >= length ? inserted : code).substring(0, length);
    }
    return TextEditingValue(text: code, selection: TextSelection.collapsed(offset: code.length));
  }
}
