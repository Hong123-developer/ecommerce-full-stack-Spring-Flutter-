import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';

import '../config/api_config.dart';
import '../config/theme.dart';
import '../providers/providers.dart';

class AppDrawer extends ConsumerWidget {
  const AppDrawer({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authProvider);
    final user = auth.user;
    final isAuth = auth.status == AuthStatus.authenticated;
    final cart = ref.watch(cartProvider);
    final unreadAsync = ref.watch(unreadCountProvider);
    final themeStyle = ref.watch(themeStyleProvider);
    final language = ref.watch(appLanguageProvider);
    final palette = context.palette;
    String text(String key) => drawerText(language, key);

    return Drawer(
      backgroundColor: themeStyle.drawerBackground,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.only(
          topRight: Radius.circular(24),
          bottomRight: Radius.circular(24),
        ),
      ),
      child: Column(
        children: [
          // ─── Header ───
          _DrawerHeader(
            user: user,
            isAuth: isAuth,
            headerGradient: themeStyle.headerGradient,
            onAvatarTap: () {
              Navigator.of(context).pop();
              if (isAuth) {
                context.push('/personal-details');
              } else {
                context.push('/login');
              }
            },
          ),

          // ─── Menu Items ───
          Expanded(
            child: ListView(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
              children: [
                // ── Shopping section ──
                _DrawerSectionTitle(title: text('shopping')),
                const SizedBox(height: 4),
                _DrawerMenuItem(
                  icon: Icons.home_rounded,
                  label: text('home'),
                  iconBgColor: palette.primary.withValues(alpha: 0.08),
                  iconColor: palette.primary,
                  onTap: () {
                    Navigator.of(context).pop();
                    context.go('/home');
                  },
                ),
                _DrawerMenuItem(
                  icon: Icons.store_rounded,
                  label: text('shop'),
                  iconBgColor: AppColors.info.withValues(alpha: 0.12),
                  iconColor: AppColors.info,
                  onTap: () {
                    Navigator.of(context).pop();
                    context.go('/shop');
                  },
                ),
                _DrawerMenuItem(
                  icon: Icons.shopping_cart_rounded,
                  label: text('cart'),
                  iconBgColor: palette.accent.withValues(alpha: 0.10),
                  iconColor: palette.accent,
                  badgeCount: cart.totalQuantity,
                  onTap: () {
                    Navigator.of(context).pop();
                    context.go('/cart');
                  },
                ),
                _DrawerMenuItem(
                  icon: Icons.receipt_long_rounded,
                  label: text('orders'),
                  iconBgColor: AppColors.warning.withValues(alpha: 0.10),
                  iconColor: AppColors.warning,
                  onTap: () {
                    Navigator.of(context).pop();
                    context.go('/orders');
                  },
                ),
                _DrawerMenuItem(
                  icon: Icons.notifications_rounded,
                  label: text('notifications'),
                  iconBgColor: AppColors.processing.withValues(alpha: 0.10),
                  iconColor: AppColors.processing,
                  badgeCount: unreadAsync.value ?? 0,
                  onTap: () {
                    Navigator.of(context).pop();
                    context.push('/notifications');
                  },
                ),

                const _DrawerDivider(),

                // ── Settings section ──
                _DrawerSectionTitle(title: text('settings')),
                const SizedBox(height: 4),
                _DrawerMenuItem(
                  icon: Icons.person_rounded,
                  label: text('profile'),
                  iconBgColor: palette.primary.withValues(alpha: 0.08),
                  iconColor: palette.primary,
                  onTap: () {
                    Navigator.of(context).pop();
                    context.go('/profile');
                  },
                ),
                if (isAuth) ...[
                  _DrawerMenuItem(
                    icon: Icons.badge_outlined,
                    label: text('personalDetails'),
                    subtitle: text('personalDetailsSubtitle'),
                    iconBgColor: AppColors.success.withValues(alpha: 0.10),
                    iconColor: AppColors.success,
                    onTap: () {
                      Navigator.of(context).pop();
                      context.push('/personal-details');
                    },
                  ),
                  _DrawerMenuItem(
                    icon: Icons.location_on_rounded,
                    label: text('addresses'),
                    subtitle: text('addressesSubtitle'),
                    iconBgColor: AppColors.shipped.withValues(alpha: 0.10),
                    iconColor: AppColors.shipped,
                    onTap: () {
                      Navigator.of(context).pop();
                      context.go('/profile');
                    },
                  ),
                  _DrawerMenuItem(
                    icon: Icons.lock_outline_rounded,
                    label: text('password'),
                    subtitle: text('passwordSubtitle'),
                    iconBgColor: AppColors.cancelled.withValues(alpha: 0.10),
                    iconColor: AppColors.cancelled,
                    onTap: () {
                      Navigator.of(context).pop();
                      context.go('/profile');
                    },
                  ),
                ],

                const _DrawerDivider(),

                _DrawerMenuItem(
                  icon: Icons.palette_rounded,
                  label: text('theme'),
                  subtitle: themeStyle.name,
                  iconBgColor: themeStyle.primary.withValues(alpha: 0.12),
                  iconColor: themeStyle.primary,
                  onTap: () => _showThemePicker(context, ref, themeStyle),
                ),
                const SizedBox(height: 10),

                const _DrawerDivider(),

                // ── Other section ──
                _DrawerSectionTitle(title: text('other')),
                const SizedBox(height: 4),
                _DrawerMenuItem(
                  icon: Icons.language_rounded,
                  label: text('language'),
                  subtitle: language == AppLanguage.khmer ? text('khmer') : text('english'),
                  iconBgColor: AppColors.info.withValues(alpha: 0.10),
                  iconColor: AppColors.info,
                  onTap: () {
                    _showLanguagePicker(context, ref, language);
                  },
                ),
                _DrawerMenuItem(
                  icon: Icons.help_outline_rounded,
                  label: text('help'),
                  iconBgColor: AppColors.success.withValues(alpha: 0.10),
                  iconColor: AppColors.success,
                  onTap: () {
                    Navigator.of(context).pop();
                    _showComingSoon(context, 'Help & Support');
                  },
                ),
                _DrawerMenuItem(
                  icon: Icons.privacy_tip_outlined,
                  label: text('privacy'),
                  iconBgColor: AppColors.warning.withValues(alpha: 0.10),
                  iconColor: AppColors.warning,
                  onTap: () {
                    Navigator.of(context).pop();
                    _showComingSoon(context, 'Privacy Policy');
                  },
                ),
                _DrawerMenuItem(
                  icon: Icons.info_outline_rounded,
                  label: text('about'),
                  subtitle: 'Version 1.0.0',
                  iconBgColor: palette.primaryLight.withValues(alpha: 0.15),
                  iconColor: palette.primaryLight,
                  onTap: () {
                    Navigator.of(context).pop();
                    _showAboutDialog(context);
                  },
                ),
                const SizedBox(height: 8),
              ],
            ),
          ),

          // ─── Footer ───
          _DrawerFooter(
            isAuth: isAuth,
            loginLabel: text('login'),
            logoutLabel: text('logout'),
            primaryColor: themeStyle.primary,
            onLogout: () async {
              final confirm = await showDialog<bool>(
                context: context,
                builder: (ctx) => AlertDialog(
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(20),
                  ),
                  title: Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: AppColors.error.withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: const Icon(
                          Icons.logout_rounded,
                          color: AppColors.error,
                          size: 22,
                        ),
                      ),
                      const SizedBox(width: 12),
                      const Text('Log Out'),
                    ],
                  ),
                  content: const Text(
                    'Are you sure you want to sign out of your account?',
                  ),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(ctx, false),
                      child: Text(
                        'Cancel',
                        style: TextStyle(color: ctx.palette.textSecondary),
                      ),
                    ),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.error,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      onPressed: () => Navigator.pop(ctx, true),
                      child: const Text('Log Out'),
                    ),
                  ],
                ),
              );
              if (confirm == true && context.mounted) {
                Navigator.of(context).pop();
                await ref.read(authProvider.notifier).logout();
              }
            },
            onLogin: () {
              Navigator.of(context).pop();
              context.push('/login');
            },
          ),
        ],
      ),
    );
  }

  void _showThemePicker(
    BuildContext context,
    WidgetRef ref,
    AppThemeStyle currentStyle,
  ) {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: context.palette.surface,
      showDragHandle: true,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      builder: (sheetContext) {
        final palette = sheetContext.palette;
        return SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(20, 4, 20, 20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Theme Style & Color',
                  style: GoogleFonts.inter(
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                    color: palette.textPrimary,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  'Choose a style for the whole app.',
                  style: GoogleFonts.inter(
                    fontSize: 13,
                    color: palette.textSecondary,
                  ),
                ),
                const SizedBox(height: 18),
                _ThemeStyleSelector(
                  currentStyle: currentStyle,
                  onSelect: (style) {
                    ref.read(themeStyleProvider.notifier).setTheme(style);
                    Navigator.of(sheetContext).pop();
                  },
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _showLanguagePicker(
    BuildContext context,
    WidgetRef ref,
    AppLanguage currentLanguage,
  ) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      backgroundColor: context.palette.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      builder: (sheetContext) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 4, 20, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                drawerText(currentLanguage, 'chooseLanguage'),
                style: GoogleFonts.inter(
                  fontSize: 20,
                  fontWeight: FontWeight.w800,
                  color: sheetContext.palette.textPrimary,
                ),
              ),
              const SizedBox(height: 14),
              _LanguageOption(
                image: 'assets/flags/khmer.png',
                label: drawerText(currentLanguage, 'khmer'),
                selected: currentLanguage == AppLanguage.khmer,
                onTap: () {
                  ref.read(appLanguageProvider.notifier).setLanguage(AppLanguage.khmer);
                  Navigator.of(sheetContext).pop();
                  Navigator.of(context).pop();
                },
              ),
              _LanguageOption(
                image: 'assets/flags/english.png',
                label: drawerText(currentLanguage, 'english'),
                selected: currentLanguage == AppLanguage.english,
                onTap: () {
                  ref.read(appLanguageProvider.notifier).setLanguage(AppLanguage.english);
                  Navigator.of(sheetContext).pop();
                  Navigator.of(context).pop();
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showComingSoon(BuildContext context, String feature) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Row(
          children: [
            const Icon(
              Icons.info_outline_rounded,
              color: Colors.white,
              size: 20,
            ),
            const SizedBox(width: 10),
            Text('$feature is coming soon!'),
          ],
        ),
        backgroundColor: context.palette.primary,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        margin: const EdgeInsets.all(16),
        duration: const Duration(seconds: 2),
      ),
    );
  }

  void _showAboutDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) {
        final palette = ctx.palette;
        return Dialog(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
          child: Padding(
            padding: const EdgeInsets.all(28),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    gradient: palette.primaryGradient,
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: const Icon(
                    Icons.shopping_bag_rounded,
                    color: Colors.white,
                    size: 40,
                  ),
                ),
                const SizedBox(height: 20),
                Text(
                  'ShopOnline',
                  style: GoogleFonts.inter(
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                    color: palette.textPrimary,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  'Version 1.0.0',
                  style: GoogleFonts.inter(
                    fontSize: 13,
                    color: palette.textSecondary,
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  'Your premium online shopping experience.\nShop smart. Shop easy.',
                  textAlign: TextAlign.center,
                  style: GoogleFonts.inter(
                    fontSize: 13,
                    color: palette.textSecondary,
                    height: 1.5,
                  ),
                ),
                const SizedBox(height: 24),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    onPressed: () => Navigator.pop(ctx),
                    child: const Text('Got it'),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

// ──────────────────────────────────────────────
// Drawer Header
// ──────────────────────────────────────────────
class _DrawerHeader extends StatelessWidget {
  final dynamic user;
  final bool isAuth;
  final LinearGradient headerGradient;
  final VoidCallback onAvatarTap;

  const _DrawerHeader({
    required this.user,
    required this.isAuth,
    required this.headerGradient,
    required this.onAvatarTap,
  });

  String _avatarUrl(dynamic user) {
    final img = user?.profileImage ?? '';
    if (img.isEmpty) return '';
    if (img.startsWith('http')) return img;
    return '${ApiConfig.baseUrl}/uploads/$img';
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: EdgeInsets.only(
        top: MediaQuery.of(context).padding.top + 24,
        left: 20,
        right: 20,
        bottom: 24,
      ),
      decoration: BoxDecoration(
        gradient: headerGradient,
        borderRadius: const BorderRadius.only(bottomRight: Radius.circular(32)),
      ),
      child: GestureDetector(
        onTap: onAvatarTap,
        child: Row(
          children: [
            // Avatar
            Container(
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(
                  color: Colors.white.withValues(alpha: 0.4),
                  width: 3,
                ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.15),
                    blurRadius: 12,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: CircleAvatar(
                radius: 30,
                backgroundColor: Colors.white.withValues(alpha: 0.2),
                backgroundImage: isAuth && _avatarUrl(user).isNotEmpty
                    ? CachedNetworkImageProvider(
                        _avatarUrl(user),
                        headers: const {'Accept': 'image/*'},
                      )
                    : null,
                child: !isAuth || _avatarUrl(user).isEmpty
                    ? Icon(
                        Icons.person_rounded,
                        size: 30,
                        color: Colors.white.withValues(alpha: 0.8),
                      )
                    : null,
              ),
            ),
            const SizedBox(width: 14),

            // Name & email
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    isAuth ? (user?.fullName ?? 'User') : 'Guest',
                    style: GoogleFonts.inter(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                      letterSpacing: -0.3,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 3),
                  Text(
                    isAuth ? (user?.email ?? '') : 'Tap to sign in',
                    style: GoogleFonts.inter(
                      color: Colors.white.withValues(alpha: 0.75),
                      fontSize: 12,
                      fontWeight: FontWeight.w400,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),

            // Edit icon
            Container(
              padding: const EdgeInsets.all(6),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(
                isAuth ? Icons.edit_rounded : Icons.login_rounded,
                color: Colors.white.withValues(alpha: 0.9),
                size: 18,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Drawer Menu Item
// ──────────────────────────────────────────────


class _DrawerMenuItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final String? subtitle;
  final VoidCallback onTap;
  final int badgeCount;
  final Color iconBgColor;
  final Color iconColor;

  const _DrawerMenuItem({
    required this.icon,
    required this.label,
    this.subtitle,
    required this.onTap,
    this.badgeCount = 0,
    required this.iconBgColor,
    required this.iconColor,
  });

  @override
  Widget build(BuildContext context) {
    final palette = context.palette;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 1.5),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(14),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(14),
          splashColor: iconColor.withValues(alpha: 0.08),
          highlightColor: iconColor.withValues(alpha: 0.04),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
            child: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: iconBgColor,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(icon, color: iconColor, size: 20),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        label,
                        style: GoogleFonts.inter(
                          color: palette.textPrimary,
                          fontSize: 14,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      if (subtitle != null) ...[
                        const SizedBox(height: 2),
                        Text(
                          subtitle!,
                          style: GoogleFonts.inter(
                            color: palette.isDark
                                ? palette.textSecondary
                                : palette.textHint,
                            fontSize: 11.5,
                            fontWeight: FontWeight.w400,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                if (badgeCount > 0) ...[
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 3,
                    ),
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: [
                          palette.accent,
                          palette.accent.withValues(alpha: 0.8),
                        ],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      badgeCount > 99 ? '99+' : '$badgeCount',
                      style: GoogleFonts.inter(
                        color: palette.onAccent,
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ],
                const SizedBox(width: 4),
                Icon(
                  Icons.chevron_right_rounded,
                  color: palette.textHint.withValues(alpha: 0.4),
                  size: 20,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Section Title
// ──────────────────────────────────────────────
class _DrawerSectionTitle extends StatelessWidget {
  final String title;
  const _DrawerSectionTitle({required this.title});

  @override
  Widget build(BuildContext context) {
    final palette = context.palette;
    return Padding(
      padding: const EdgeInsets.only(left: 14, top: 6, bottom: 2),
      child: Text(
        title.toUpperCase(),
        style: GoogleFonts.inter(
          // textHint is too faint on the dark drawer, so step up one shade.
          color: palette.isDark ? palette.textSecondary : palette.textHint,
          fontSize: 11,
          fontWeight: FontWeight.w700,
          letterSpacing: 1.2,
        ),
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Divider
// ──────────────────────────────────────────────
class _DrawerDivider extends StatelessWidget {
  const _DrawerDivider();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: Divider(
        color: context.palette.divider,
        height: 1,
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Drawer Footer
// ──────────────────────────────────────────────
class _LanguageOption extends StatelessWidget {
  final String image;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _LanguageOption({
    required this.image,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final palette = context.palette;
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 4),
      leading: ClipRRect(
        borderRadius: BorderRadius.circular(4),
        child: Image.asset(image, width: 42, height: 28, fit: BoxFit.cover),
      ),
      title: Text(label, style: GoogleFonts.inter(fontWeight: FontWeight.w600)),
      trailing: selected
          ? Icon(Icons.check_circle_rounded, color: palette.primary)
          : Icon(Icons.radio_button_unchecked_rounded, color: palette.textSecondary),
      onTap: onTap,
    );
  }
}

class _DrawerFooter extends StatelessWidget {
  final bool isAuth;
  final String loginLabel;
  final String logoutLabel;
  final Color primaryColor;
  final VoidCallback onLogout;
  final VoidCallback onLogin;

  const _DrawerFooter({
    required this.isAuth,
    required this.loginLabel,
    required this.logoutLabel,
    required this.primaryColor,
    required this.onLogout,
    required this.onLogin,
  });

  @override
  Widget build(BuildContext context) {
    final palette = context.palette;
    return Container(
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 12,
        bottom: MediaQuery.of(context).padding.bottom + 16,
      ),
      decoration: BoxDecoration(
        color: palette.surface,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: palette.isDark ? 0.25 : 0.04),
            blurRadius: 10,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: isAuth
          ? _FooterButton(
              icon: Icons.logout_rounded,
              label: logoutLabel,
              color: AppColors.error,
              onTap: onLogout,
            )
          : _FooterButton(
              icon: Icons.login_rounded,
              label: loginLabel,
              color: primaryColor,
              onTap: onLogin,
            ),
    );
  }
}

class _FooterButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  const _FooterButton({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: color.withValues(alpha: 0.08),
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 14),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, color: color, size: 20),
              const SizedBox(width: 10),
              Text(
                label,
                style: GoogleFonts.inter(
                  color: color,
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Theme Style Selector Widget
// ──────────────────────────────────────────────
class _ThemeStyleSelector extends StatelessWidget {
  final AppThemeStyle currentStyle;
  final ValueChanged<AppThemeStyle> onSelect;

  const _ThemeStyleSelector({
    required this.currentStyle,
    required this.onSelect,
  });

  static const double _spacing = 10;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        // Three per row on phones, four once the sheet is wide (tablets).
        final columns = constraints.maxWidth >= 520 ? 4 : 3;
        final tileWidth =
            ((constraints.maxWidth - _spacing * (columns - 1)) / columns)
                .floorToDouble();

        return Wrap(
          spacing: _spacing,
          runSpacing: _spacing,
          children: [
            for (final style in AppThemeStyle.values)
              SizedBox(
                width: tileWidth,
                child: _ThemeStyleTile(
                  style: style,
                  selected: style == currentStyle,
                  onTap: () => onSelect(style),
                ),
              ),
          ],
        );
      },
    );
  }
}

class _ThemeStyleTile extends StatelessWidget {
  final AppThemeStyle style;
  final bool selected;
  final VoidCallback onTap;

  const _ThemeStyleTile({
    required this.style,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final palette = context.palette;

    return Semantics(
      selected: selected,
      child: Material(
        color: selected
            ? style.primary.withValues(alpha: palette.isDark ? 0.16 : 0.06)
            : palette.surface,
        clipBehavior: Clip.antiAlias,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: BorderSide(
            color: selected ? style.primary : palette.divider,
            width: selected ? 2 : 1,
          ),
        ),
        child: InkWell(
          onTap: onTap,
          splashColor: style.primary.withValues(alpha: 0.12),
          highlightColor: style.primary.withValues(alpha: 0.06),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(8, 8, 8, 10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _ThemeSwatch(style: style, selected: selected),
                const SizedBox(height: 8),
                Center(
                  child: FittedBox(
                    fit: BoxFit.scaleDown,
                    child: Text(
                      style.name,
                      maxLines: 1,
                      style: GoogleFonts.inter(
                        fontSize: 12,
                        fontWeight:
                            selected ? FontWeight.w700 : FontWeight.w600,
                        color: selected
                            ? palette.textPrimary
                            : palette.textSecondary,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Mini preview of a theme style: its header gradient with the brand and
/// accent colors as dots, so even near-black styles stay recognizable.
class _ThemeSwatch extends StatelessWidget {
  final AppThemeStyle style;
  final bool selected;

  const _ThemeSwatch({required this.style, required this.selected});

  @override
  Widget build(BuildContext context) {
    final palette = context.palette;

    return Container(
      height: 54,
      decoration: BoxDecoration(
        gradient: style.headerGradient,
        borderRadius: BorderRadius.circular(11),
        // Hairline so dark swatches don't melt into a dark sheet.
        border: Border.all(
          color: palette.isDark
              ? Colors.white.withValues(alpha: 0.14)
              : Colors.black.withValues(alpha: 0.04),
        ),
      ),
      child: Stack(
        children: [
          Positioned(
            left: 8,
            bottom: 8,
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                _SwatchDot(color: style.primary),
                const SizedBox(width: 4),
                _SwatchDot(color: style.accent),
              ],
            ),
          ),
          if (style.isDark)
            Positioned(
              left: 7,
              top: 6,
              child: Icon(
                Icons.dark_mode_rounded,
                size: 14,
                color: Colors.white.withValues(alpha: 0.85),
              ),
            ),
          if (selected)
            Positioned(
              right: 6,
              top: 6,
              child: Container(
                width: 20,
                height: 20,
                decoration: BoxDecoration(
                  color: Colors.white,
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.18),
                      blurRadius: 4,
                      offset: const Offset(0, 1),
                    ),
                  ],
                ),
                child: Icon(
                  Icons.check_rounded,
                  size: 14,
                  color: style.primary,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _SwatchDot extends StatelessWidget {
  final Color color;

  const _SwatchDot({required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 12,
      height: 12,
      decoration: BoxDecoration(
        color: color,
        shape: BoxShape.circle,
        border: Border.all(
          color: Colors.white.withValues(alpha: 0.9),
          width: 1.5,
        ),
      ),
    );
  }
}
