import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';

// ──────────────────────────────────────────────
// Color Palette
// ──────────────────────────────────────────────
class AppColors {
  // Primary gradient colors
  static const Color primary = Color(0xFF6C5CE7);
  static const Color primaryLight = Color(0xFFA29BFE);
  static const Color primaryDark = Color(0xFF4A3FC2);

  // Accent
  static const Color accent = Color(0xFFFF6B6B);
  static const Color accentLight = Color(0xFFFF8E8E);

  // Background
  static const Color background = Color(0xFFF8F9FE);
  static const Color surface = Colors.white;
  static const Color surfaceVariant = Color(0xFFF0F0F8);

  // Text
  static const Color textPrimary = Color(0xFF2D3436);
  static const Color textSecondary = Color(0xFF636E72);
  static const Color textHint = Color(0xFFB2BEC3);

  // Status
  static const Color success = Color(0xFF00B894);
  static const Color warning = Color(0xFFFDAA5D);
  static const Color error = Color(0xFFE74C3C);
  static const Color info = Color(0xFF74B9FF);

  // Order Status Colors
  static const Color pending = Color(0xFFFDAA5D);
  static const Color confirmed = Color(0xFF74B9FF);
  static const Color processing = Color(0xFF6C5CE7);
  static const Color shipped = Color(0xFF0984E3);
  static const Color delivered = Color(0xFF00B894);
  static const Color cancelled = Color(0xFFE74C3C);

  // Misc
  static const Color divider = Color(0xFFEEF0F5);
  static const Color shimmerBase = Color(0xFFE8E8F0);
  static const Color shimmerHighlight = Color(0xFFF5F5FA);
  static const Color starFilled = Color(0xFFFFD700);
  static const Color starEmpty = Color(0xFFDFE6E9);

  // Gradients
  static const LinearGradient primaryGradient = LinearGradient(
    colors: [primary, Color(0xFF8B7BF7)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const LinearGradient accentGradient = LinearGradient(
    colors: [accent, Color(0xFFFF9A76)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const LinearGradient darkGradient = LinearGradient(
    colors: [Color(0xFF2D3436), Color(0xFF636E72)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );
}

// ──────────────────────────────────────────────
// Theme Styles / Presets
// ──────────────────────────────────────────────
enum AppThemeStyle {
  purple(
    id: 'purple',
    name: 'Royal Purple',
    primary: Color(0xFF6C5CE7),
    primaryLight: Color(0xFFA29BFE),
    primaryDark: Color(0xFF4A3FC2),
    accent: Color(0xFFFF6B6B),
    drawerBackground: Color(0xFFF8F7FF),
    appBarColor: Color(0xFF6C5CE7),
    appBarTextColor: Colors.white,
    navActiveColor: Color(0xFF6C5CE7),
    headerGradient: LinearGradient(
      colors: [Color(0xFF6C5CE7), Color(0xFF8B7BF7)],
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
    ),
  ),
  blue(
    id: 'blue',
    name: 'Ocean Blue',
    primary: Color(0xFF1E50FF),
    primaryLight: Color(0xFF6889FF),
    primaryDark: Color(0xFF0F3ED6),
    accent: Color(0xFF00CEC9),
    drawerBackground: Color(0xFFF2F6FF),
    appBarColor: Color(0xFF1E50FF),
    appBarTextColor: Colors.white,
    navActiveColor: Color(0xFF1E50FF),
    headerGradient: LinearGradient(
      colors: [Color(0xFF1E50FF), Color(0xFF4D77FF)],
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
    ),
  ),
  emerald(
    id: 'emerald',
    name: 'Emerald Green',
    primary: Color(0xFF059669),
    primaryLight: Color(0xFF34D399),
    primaryDark: Color(0xFF047857),
    accent: Color(0xFFF59E0B),
    drawerBackground: Color(0xFFF0FDF4),
    appBarColor: Color(0xFF059669),
    appBarTextColor: Colors.white,
    navActiveColor: Color(0xFF059669),
    headerGradient: LinearGradient(
      colors: [Color(0xFF059669), Color(0xFF10B981)],
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
    ),
  ),
  sunset(
    id: 'sunset',
    name: 'Sunset Coral',
    primary: Color(0xFFE11D48),
    primaryLight: Color(0xFFFB7185),
    primaryDark: Color(0xFFBE123C),
    accent: Color(0xFFFB923C),
    drawerBackground: Color(0xFFFFF1F2),
    appBarColor: Color(0xFFE11D48),
    appBarTextColor: Colors.white,
    navActiveColor: Color(0xFFE11D48),
    headerGradient: LinearGradient(
      colors: [Color(0xFFE11D48), Color(0xFFF43F5E)],
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
    ),
  ),
  amber(
    id: 'amber',
    name: 'Amber Gold',
    primary: Color(0xFFD97706),
    primaryLight: Color(0xFFFBBF24),
    primaryDark: Color(0xFFB45309),
    accent: Color(0xFF6366F1),
    drawerBackground: Color(0xFFFFFBEB),
    appBarColor: Color(0xFFD97706),
    appBarTextColor: Colors.white,
    navActiveColor: Color(0xFFD97706),
    headerGradient: LinearGradient(
      colors: [Color(0xFFD97706), Color(0xFFF59E0B)],
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
    ),
  ),
  dark(
    id: 'dark',
    name: 'Midnight Dark',
    primary: Color(0xFF6366F1),
    primaryLight: Color(0xFF818CF8),
    primaryDark: Color(0xFF4F46E5),
    accent: Color(0xFF38BDF8),
    drawerBackground: Color(0xFF1E293B),
    appBarColor: Color(0xFF0F172A),
    appBarTextColor: Colors.white,
    navActiveColor: Color(0xFF6366F1),
    headerGradient: LinearGradient(
      colors: [Color(0xFF0F172A), Color(0xFF1E293B)],
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
    ),
    isDark: true,
  );

  final String id;
  final String name;
  final Color primary;
  final Color primaryLight;
  final Color primaryDark;
  final Color accent;
  final Color drawerBackground;
  final Color appBarColor;
  final Color appBarTextColor;
  final Color navActiveColor;
  final LinearGradient headerGradient;
  final bool isDark;

  const AppThemeStyle({
    required this.id,
    required this.name,
    required this.primary,
    required this.primaryLight,
    required this.primaryDark,
    required this.accent,
    required this.drawerBackground,
    required this.appBarColor,
    required this.appBarTextColor,
    required this.navActiveColor,
    required this.headerGradient,
    this.isDark = false,
  });
}

// ──────────────────────────────────────────────
// Theme
// ──────────────────────────────────────────────
class AppTheme {
  static ThemeData get lightTheme => getTheme(AppThemeStyle.purple);

  static ThemeData getTheme(AppThemeStyle style) {
    final surfaceColor = style.isDark ? const Color(0xFF1E293B) : AppColors.surface;
    final surfaceVariant = style.isDark ? const Color(0xFF334155) : AppColors.surfaceVariant;
    final textPrimary = style.isDark ? Colors.white : AppColors.textPrimary;
    final textSecondary = style.isDark ? const Color(0xFF94A3B8) : AppColors.textSecondary;
    final textHint = style.isDark ? const Color(0xFF64748B) : AppColors.textHint;
    final scaffoldBg = style.isDark ? const Color(0xFF0F172A) : AppColors.background;

    return ThemeData(
      useMaterial3: true,
      brightness: style.isDark ? Brightness.dark : Brightness.light,
      primaryColor: style.primary,
      colorScheme: ColorScheme.fromSeed(
        seedColor: style.primary,
        brightness: style.isDark ? Brightness.dark : Brightness.light,
        primary: style.primary,
        secondary: style.accent,
        surface: surfaceColor,
        error: AppColors.error,
      ),
      scaffoldBackgroundColor: scaffoldBg,
      fontFamily: GoogleFonts.inter().fontFamily,
      textTheme: TextTheme(
        bodyLarge: TextStyle(color: textPrimary),
        bodyMedium: TextStyle(color: textSecondary),
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: style.appBarColor,
        foregroundColor: style.appBarTextColor,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        systemOverlayStyle: style.isDark || style.appBarTextColor == Colors.white
            ? SystemUiOverlayStyle.light
            : SystemUiOverlayStyle.dark,
        titleTextStyle: GoogleFonts.inter(
          color: style.appBarTextColor,
          fontSize: 19,
          fontWeight: FontWeight.w700,
        ),
        iconTheme: IconThemeData(color: style.appBarTextColor),
        actionsIconTheme: IconThemeData(color: style.appBarTextColor),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: style.primary,
          foregroundColor: Colors.white,
          elevation: 0,
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
          textStyle: GoogleFonts.inter(
            fontSize: 16,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: style.primary,
          side: BorderSide(color: style.primary),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
          textStyle: GoogleFonts.inter(
            fontSize: 16,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surfaceVariant,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide.none,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide(color: style.primary, width: 1.5),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: AppColors.error),
        ),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        hintStyle: GoogleFonts.inter(
          color: textHint,
          fontSize: 14,
        ),
      ),
      cardTheme: CardThemeData(
        color: surfaceColor,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
        ),
        margin: EdgeInsets.zero,
      ),
      bottomNavigationBarTheme: BottomNavigationBarThemeData(
        backgroundColor: surfaceColor,
        selectedItemColor: style.primary,
        unselectedItemColor: textHint,
        type: BottomNavigationBarType.fixed,
        elevation: 0,
      ),
      chipTheme: ChipThemeData(
        backgroundColor: surfaceVariant,
        selectedColor: style.primary.withValues(alpha: 0.15),
        labelStyle: GoogleFonts.inter(fontSize: 13, color: textPrimary),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(10),
        ),
        side: BorderSide.none,
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Spacing & Sizing
// ──────────────────────────────────────────────
class AppSpacing {
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 16;
  static const double lg = 24;
  static const double xl = 32;
  static const double xxl = 48;
}

class AppRadius {
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 24;
  static const double full = 100;
}
