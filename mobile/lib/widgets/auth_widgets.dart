import 'dart:math' as math;
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import '../config/api_config.dart';
import '../config/theme.dart';
import 'common_widgets.dart';

// ──────────────────────────────────────────────
// Validators (mirror the backend request rules)
// ──────────────────────────────────────────────
class AuthValidators {
  static String? fullName(String? value) {
    final name = value?.trim() ?? '';
    if (name.isEmpty) return 'Please enter your full name';
    if (name.length > 150) return 'Full name must be at most 150 characters';
    return null;
  }

  static String? email(String? value) {
    final email = value?.trim() ?? '';
    if (email.isEmpty) return 'Please enter your email';
    if (email.length > 180) return 'Email must be at most 180 characters';
    // Close to the server's @Email: no empty or dot-edged parts, a dotted domain
    if (!RegExp(r"^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
            r'@[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)+$')
        .hasMatch(email)) {
      return 'Please enter a valid email';
    }
    return null;
  }

  /// Optional: empty is allowed.
  static String? phone(String? value) {
    final phone = value?.trim() ?? '';
    if (phone.isEmpty) return null;
    if (!RegExp(r'^[0-9+\-\s]{8,30}$').hasMatch(phone)) {
      return 'Phone must be 8-30 digits';
    }
    return null;
  }

  static String? newPassword(String? value) {
    final password = value ?? '';
    if (password.length < 8) return 'Use at least 8 characters';
    if (password.length > 72) return 'Password must be at most 72 characters';
    if (!RegExp(r'[A-Za-z]').hasMatch(password)) return 'Include at least one letter';
    if (!RegExp(r'\d').hasMatch(password)) return 'Include at least one number';
    return null;
  }

  static String? Function(String?) confirmPassword(TextEditingController password) {
    return (value) {
      if ((value ?? '').isEmpty) return 'Please re-enter your password';
      if (value != password.text) return 'Passwords do not match';
      return null;
    };
  }
}

// ──────────────────────────────────────────────
// Social Sign-In (Google / Facebook via backend OAuth2)
// ──────────────────────────────────────────────
Future<void> launchSocialAuth(BuildContext context, String provider, String label) async {
  var url = Uri.parse('${ApiConfig.baseUrl}/oauth2/authorization/$provider');
  if (kIsWeb) {
    // Tell the backend where this web app lives so the tokens come back here
    url = url.replace(queryParameters: {
      'app_redirect_uri': '${Uri.base.origin}/oauth2/callback',
    });
  }
  try {
    if (kIsWeb) {
      await launchUrl(url, webOnlyWindowName: '_self');
    } else {
      await launchUrl(url, mode: LaunchMode.externalApplication);
    }
  } catch (e) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not open $label sign-in: $e')),
      );
    }
  }
}

// ──────────────────────────────────────────────
// Auth Step Scaffold (centered title, pinned bottom button)
// ──────────────────────────────────────────────
class AuthStepScaffold extends StatelessWidget {
  final String title;
  final Widget child;
  final String buttonLabel;
  final VoidCallback? onPressed;
  final bool isLoading;
  /// Where the back arrow goes when there is nothing to pop.
  final String fallbackRoute;

  const AuthStepScaffold({
    super.key,
    required this.title,
    required this.child,
    required this.buttonLabel,
    this.onPressed,
    this.isLoading = false,
    this.fallbackRoute = '/login',
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        centerTitle: true,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_rounded),
          onPressed: () => context.canPop() ? context.pop() : context.go(fallbackRoute),
        ),
        title: Text(
          title,
          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
        ),
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
                child: child,
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 16),
              child: GradientButton(
                label: buttonLabel,
                isLoading: isLoading,
                onPressed: onPressed,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Centered grey helper text used under the auth illustrations.
class AuthStepMessage extends StatelessWidget {
  final String text;

  const AuthStepMessage(this.text, {super.key});

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      textAlign: TextAlign.center,
      style: const TextStyle(color: AppColors.textSecondary, fontSize: 13, height: 1.5),
    );
  }
}

// ──────────────────────────────────────────────
// Auth Illustration (header art for auth screens)
// ──────────────────────────────────────────────
class AuthIllustration extends StatelessWidget {
  final IconData icon;
  final IconData badgeIcon;

  const AuthIllustration({
    super.key,
    required this.icon,
    this.badgeIcon = Icons.check_rounded,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: SizedBox(
        width: 240,
        height: 180,
        child: Stack(
          alignment: Alignment.center,
          children: [
            // Soft backdrop
            Container(
              width: 168,
              height: 168,
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.08),
                shape: BoxShape.circle,
              ),
            ),
            // Decorative shapes
            Positioned(left: 28, top: 36, child: _dot(10, AppColors.primaryLight)),
            Positioned(right: 26, bottom: 30, child: _dot(14, AppColors.accent.withValues(alpha: 0.6))),
            Positioned(
              left: 40,
              bottom: 22,
              child: Container(
                width: 18,
                height: 18,
                decoration: BoxDecoration(
                  color: AppColors.primary.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(5),
                ),
              ),
            ),
            // Form card
            Container(
              width: 104,
              height: 140,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.divider, width: 1.2),
                boxShadow: [
                  BoxShadow(
                    color: AppColors.primary.withValues(alpha: 0.12),
                    blurRadius: 18,
                    offset: const Offset(0, 8),
                  ),
                ],
              ),
              child: Column(
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: const BoxDecoration(
                      gradient: AppColors.primaryGradient,
                      shape: BoxShape.circle,
                    ),
                    child: Icon(icon, size: 22, color: Colors.white),
                  ),
                  const SizedBox(height: 12),
                  _bar(60, 6, AppColors.surfaceVariant),
                  const SizedBox(height: 6),
                  _bar(44, 6, AppColors.surfaceVariant),
                  const Spacer(),
                  _bar(64, 14, AppColors.primary),
                ],
              ),
            ),
            // Floating badge
            Positioned(
              top: 12,
              right: 54,
              child: Container(
                width: 34,
                height: 34,
                decoration: BoxDecoration(
                  color: AppColors.success,
                  shape: BoxShape.circle,
                  border: Border.all(color: Colors.white, width: 3),
                ),
                child: Icon(badgeIcon, size: 16, color: Colors.white),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _dot(double size, Color color) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(color: color, shape: BoxShape.circle),
    );
  }

  Widget _bar(double width, double height, Color color) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(4),
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Auth Text Field (white, outlined)
// ──────────────────────────────────────────────
class AuthTextField extends StatelessWidget {
  final TextEditingController controller;
  final String hint;
  final IconData icon;
  final TextInputType? keyboardType;
  final TextInputAction? textInputAction;
  final Iterable<String>? autofillHints;
  final bool obscureText;
  final VoidCallback? onToggleObscure;
  final String? Function(String?)? validator;

  const AuthTextField({
    super.key,
    required this.controller,
    required this.hint,
    required this.icon,
    this.keyboardType,
    this.textInputAction,
    this.autofillHints,
    this.obscureText = false,
    this.onToggleObscure,
    this.validator,
  });

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      keyboardType: keyboardType,
      textInputAction: textInputAction,
      autofillHints: autofillHints,
      obscureText: obscureText,
      validator: validator,
      style: const TextStyle(fontSize: 14, color: AppColors.textPrimary),
      decoration: InputDecoration(
        hintText: hint,
        filled: true,
        fillColor: AppColors.surface,
        prefixIcon: Icon(icon, size: 20, color: AppColors.textHint),
        // Only password fields get the show/hide toggle
        suffixIcon: onToggleObscure == null
            ? null
            : IconButton(
                icon: Icon(
                  obscureText ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                  size: 20,
                  color: AppColors.textHint,
                ),
                onPressed: onToggleObscure,
              ),
        border: _border(AppColors.divider),
        enabledBorder: _border(AppColors.divider),
        focusedBorder: _border(AppColors.primary, width: 1.5),
        errorBorder: _border(AppColors.error),
        focusedErrorBorder: _border(AppColors.error, width: 1.5),
      ),
    );
  }

  OutlineInputBorder _border(Color color, {double width = 1.2}) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: BorderSide(color: color, width: width),
    );
  }
}

// ──────────────────────────────────────────────
// "Or ... with" Divider
// ──────────────────────────────────────────────
class AuthDivider extends StatelessWidget {
  final String label;

  const AuthDivider({super.key, required this.label});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const Expanded(child: Divider(color: AppColors.divider)),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Text(
            label,
            style: const TextStyle(fontSize: 12, color: AppColors.textHint),
          ),
        ),
        const Expanded(child: Divider(color: AppColors.divider)),
      ],
    );
  }
}

// ──────────────────────────────────────────────
// Social Auth Button (Google / Facebook)
// ──────────────────────────────────────────────
class SocialAuthButton extends StatelessWidget {
  final String label;
  final Widget logo;
  final VoidCallback? onPressed;

  const SocialAuthButton({
    super.key,
    required this.label,
    required this.logo,
    this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 48,
      child: OutlinedButton(
        onPressed: onPressed,
        style: OutlinedButton.styleFrom(
          backgroundColor: AppColors.surface,
          foregroundColor: AppColors.textPrimary,
          side: const BorderSide(color: AppColors.divider, width: 1.2),
          padding: const EdgeInsets.symmetric(horizontal: 12),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            SizedBox(width: 20, height: 20, child: logo),
            const SizedBox(width: 10),
            Flexible(
              child: Text(
                label,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: AppColors.textPrimary,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class GoogleLogo extends StatelessWidget {
  const GoogleLogo({super.key});

  @override
  Widget build(BuildContext context) => CustomPaint(painter: GoogleLogoPainter());
}

class FacebookLogo extends StatelessWidget {
  const FacebookLogo({super.key});

  @override
  Widget build(BuildContext context) {
    return const FittedBox(
      child: Icon(Icons.facebook, color: Color(0xFF1877F2)),
    );
  }
}

class GoogleLogoPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final double w = size.width;
    final double h = size.height;

    final paintBlue = Paint()
      ..color = const Color(0xFF4285F4)
      ..style = PaintingStyle.fill;

    final center = Offset(w / 2, h / 2);
    final radius = w / 2;

    // Blue horizontal bar
    final barRect = Rect.fromLTWH(w * 0.45, h * 0.38, w * 0.55, h * 0.24);
    canvas.drawRRect(RRect.fromRectAndRadius(barRect, const Radius.circular(2)), paintBlue);

    // Arcs
    final arcRect = Rect.fromCircle(center: center, radius: radius * 0.85);
    final strokePaint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = radius * 0.38;

    // Red (top)
    strokePaint.color = const Color(0xFFEA4335);
    canvas.drawArc(arcRect, -math.pi * 0.75, math.pi * 0.55, false, strokePaint);

    // Yellow (left)
    strokePaint.color = const Color(0xFFFBBC05);
    canvas.drawArc(arcRect, -math.pi * 1.25, math.pi * 0.55, false, strokePaint);

    // Green (bottom)
    strokePaint.color = const Color(0xFF34A853);
    canvas.drawArc(arcRect, math.pi * 0.25, math.pi * 0.55, false, strokePaint);

    // Blue arc (bottom right)
    strokePaint.color = const Color(0xFF4285F4);
    canvas.drawArc(arcRect, 0, math.pi * 0.35, false, strokePaint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

// ──────────────────────────────────────────────
// Success Dialog
// ──────────────────────────────────────────────
/// Shows a non-dismissible success dialog. Both the close icon and the action
/// button pop the dialog and then run [onAction], so the flow always continues.
Future<void> showAuthSuccessDialog(
  BuildContext context, {
  required String title,
  required String message,
  required String actionLabel,
  required VoidCallback onAction,
}) {
  return showDialog<void>(
    context: context,
    barrierDismissible: false,
    builder: (dialogContext) {
      void finish() {
        Navigator.of(dialogContext).pop();
        onAction();
      }

      return PopScope(
        canPop: false,
        child: Dialog(
          backgroundColor: AppColors.surface,
          insetPadding: const EdgeInsets.symmetric(horizontal: 40),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          child: Stack(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(24, 32, 24, 24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 72,
                      height: 72,
                      decoration: BoxDecoration(
                        gradient: AppColors.primaryGradient,
                        shape: BoxShape.circle,
                        boxShadow: [
                          BoxShadow(
                            color: AppColors.primary.withValues(alpha: 0.25),
                            blurRadius: 16,
                            offset: const Offset(0, 6),
                          ),
                        ],
                      ),
                      child: const Icon(Icons.check_rounded, size: 40, color: Colors.white),
                    ),
                    const SizedBox(height: 20),
                    Text(
                      title,
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w700,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      message,
                      textAlign: TextAlign.center,
                      style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
                    ),
                    const SizedBox(height: 24),
                    GradientButton(label: actionLabel, onPressed: finish),
                  ],
                ),
              ),
              Positioned(
                top: 4,
                right: 4,
                child: IconButton(
                  icon: const Icon(Icons.close_rounded, size: 20, color: AppColors.textSecondary),
                  onPressed: finish,
                ),
              ),
            ],
          ),
        ),
      );
    },
  );
}
