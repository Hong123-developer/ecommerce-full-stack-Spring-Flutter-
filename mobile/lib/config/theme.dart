import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';

// ──────────────────────────────────────────────
// Color Palette
// ──────────────────────────────────────────────
// Static light-mode defaults. Brand and neutral colors here do NOT follow the
// selected theme style or dark mode — in widgets, read them from
// `context.palette` instead. Status, order-status and star colors are fine to
// use directly because they read well on every theme.
class AppColors {
  // Brand palette
  static const Color primary = Color(0xFF5146D8);
  static const Color primaryLight = Color(0xFF8B83F3);
  static const Color primaryDark = Color(0xFF3930A8);

  static const Color accent = Color(0xFFF05D5E);
  static const Color accentLight = Color(0xFFF58282);

  // Surfaces
  static const Color background = Color(0xFFF7F8FC);
  static const Color surface = Colors.white;
  static const Color surfaceVariant = Color(0xFFEEF0F6);

  // Text
  static const Color textPrimary = Color(0xFF1F2937);
  static const Color textSecondary = Color(0xFF667085);
  static const Color textHint = Color(0xFF98A2B3);

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
  static const Color divider = Color(0xFFE4E7EC);
  static const Color shimmerBase = Color(0xFFE7E9F0);
  static const Color shimmerHighlight = Color(0xFFF8F9FC);
  // Deeper amber than pure gold so stars keep contrast on white surfaces.
  static const Color starFilled = Color(0xFFFFB400);
  static const Color starEmpty = Color(0xFFDFE6E9);

  // Gradients
  static const LinearGradient primaryGradient = LinearGradient(
    colors: [primary, Color(0xFF746BED)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const LinearGradient accentGradient = LinearGradient(
    colors: [accent, Color(0xFFFF8A72)],
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
  monochrome(
    id: 'mono',
    name: 'Minimal Black',
    primary: Color(0xFF111111),
    primaryLight: Color(0xFF52525B),
    primaryDark: Color(0xFF000000),
    accent: Color(0xFFE5484D),
    drawerBackground: Color(0xFFF7F7F7),
    navActiveColor: Color(0xFF111111),
    headerGradient: LinearGradient(
      colors: [Color(0xFF111111), Color(0xFF3F3F46)],
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
    ),
  ),
  purple(
    id: 'purple',
    name: 'Royal Purple',
    primary: Color(0xFF5146D8),
    primaryLight: Color(0xFF8B83F3),
    primaryDark: Color(0xFF3930A8),
    accent: Color(0xFFF05D5E),
    drawerBackground: Color(0xFFF7F6FF),
    navActiveColor: Color(0xFF6C5CE7),
    headerGradient: LinearGradient(
      colors: [Color(0xFF5146D8), Color(0xFF746BED)],
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
    accent: Color(0xFF0891B2),
    drawerBackground: Color(0xFFF2F6FF),
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
    accent: Color(0xFFD97706),
    drawerBackground: Color(0xFFF0FDF4),
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
    accent: Color(0xFFEA580C),
    drawerBackground: Color(0xFFFFF1F2),
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
    required this.navActiveColor,
    required this.headerGradient,
    this.isDark = false,
  });
}

// ──────────────────────────────────────────────
// Theme-aware palette
// ──────────────────────────────────────────────
// Resolved from the active AppThemeStyle and attached to ThemeData, so widgets
// pick up the user's chosen style and dark mode:
//   color: context.palette.textPrimary
@immutable
class AppPalette extends ThemeExtension<AppPalette> {
  final bool isDark;

  // Brand
  final Color primary;
  final Color primaryLight;
  final Color primaryDark;
  final Color onPrimary;
  final Color accent;
  final Color onAccent; // text/icons drawn on an accent fill
  final LinearGradient primaryGradient;
  final LinearGradient headerGradient;

  // Surfaces
  final Color background;
  final Color surface;
  final Color surfaceVariant;

  // Text
  final Color textPrimary;
  final Color textSecondary;
  final Color textHint;

  // Misc
  final Color divider;
  final Color shimmerBase;
  final Color shimmerHighlight;
  final Color starEmpty;

  const AppPalette({
    required this.isDark,
    required this.primary,
    required this.primaryLight,
    required this.primaryDark,
    required this.onPrimary,
    required this.accent,
    required this.onAccent,
    required this.primaryGradient,
    required this.headerGradient,
    required this.background,
    required this.surface,
    required this.surfaceVariant,
    required this.textPrimary,
    required this.textSecondary,
    required this.textHint,
    required this.divider,
    required this.shimmerBase,
    required this.shimmerHighlight,
    required this.starEmpty,
  });

  factory AppPalette.fromStyle(AppThemeStyle style) {
    final brand = (
      primary: style.primary,
      primaryLight: style.primaryLight,
      primaryDark: style.primaryDark,
      accent: style.accent,
      // Light accents (e.g. Midnight Dark's sky blue) need dark ink on top.
      onAccent:
          ThemeData.estimateBrightnessForColor(style.accent) == Brightness.dark
          ? Colors.white
          : const Color(0xFF111111),
      // The dark style's header gradient is near-black navy, too low-contrast
      // for buttons, so it gets a gradient built from its brand colors.
      primaryGradient: style.isDark
          ? LinearGradient(
              colors: [style.primary, style.primaryLight],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            )
          : style.headerGradient,
      headerGradient: style.headerGradient,
    );

    if (style.isDark) {
      return AppPalette(
        isDark: true,
        primary: brand.primary,
        primaryLight: brand.primaryLight,
        primaryDark: brand.primaryDark,
        onPrimary: Colors.white,
        accent: brand.accent,
        onAccent: brand.onAccent,
        primaryGradient: brand.primaryGradient,
        headerGradient: brand.headerGradient,
        background: const Color(0xFF0F172A),
        surface: const Color(0xFF182235),
        surfaceVariant: const Color(0xFF27344A),
        textPrimary: const Color(0xFFF1F5F9),
        textSecondary: const Color(0xFF94A3B8),
        textHint: const Color(0xFF64748B),
        divider: const Color(0xFF334155),
        shimmerBase: const Color(0xFF1E293B),
        shimmerHighlight: const Color(0xFF334155),
        starEmpty: const Color(0xFF475569),
      );
    }

    // Minimal Black uses true neutral greys; the colored styles keep the
    // slightly cool greys the app was designed with.
    if (style == AppThemeStyle.monochrome) {
      return AppPalette(
        isDark: false,
        primary: brand.primary,
        primaryLight: brand.primaryLight,
        primaryDark: brand.primaryDark,
        onPrimary: Colors.white,
        accent: brand.accent,
        onAccent: brand.onAccent,
        primaryGradient: brand.primaryGradient,
        headerGradient: brand.headerGradient,
        background: const Color(0xFFF5F5F5),
        surface: Colors.white,
        surfaceVariant: const Color(0xFFF1F1F2),
        textPrimary: const Color(0xFF111111),
        textSecondary: const Color(0xFF6B6B70),
        textHint: const Color(0xFFA1A1AA),
        divider: const Color(0xFFE6E6E8),
        shimmerBase: const Color(0xFFEBEBEB),
        shimmerHighlight: const Color(0xFFF7F7F7),
        starEmpty: const Color(0xFFDDDDDD),
      );
    }

    return AppPalette(
      isDark: false,
      primary: brand.primary,
      primaryLight: brand.primaryLight,
      primaryDark: brand.primaryDark,
      onPrimary: Colors.white,
      accent: brand.accent,
      onAccent: brand.onAccent,
      primaryGradient: brand.primaryGradient,
      headerGradient: brand.headerGradient,
      background: AppColors.background,
      surface: AppColors.surface,
      surfaceVariant: AppColors.surfaceVariant,
      textPrimary: AppColors.textPrimary,
      textSecondary: AppColors.textSecondary,
      textHint: AppColors.textHint,
      divider: AppColors.divider,
      shimmerBase: AppColors.shimmerBase,
      shimmerHighlight: AppColors.shimmerHighlight,
      starEmpty: AppColors.starEmpty,
    );
  }

  @override
  AppPalette copyWith({
    bool? isDark,
    Color? primary,
    Color? primaryLight,
    Color? primaryDark,
    Color? onPrimary,
    Color? accent,
    Color? onAccent,
    LinearGradient? primaryGradient,
    LinearGradient? headerGradient,
    Color? background,
    Color? surface,
    Color? surfaceVariant,
    Color? textPrimary,
    Color? textSecondary,
    Color? textHint,
    Color? divider,
    Color? shimmerBase,
    Color? shimmerHighlight,
    Color? starEmpty,
  }) {
    return AppPalette(
      isDark: isDark ?? this.isDark,
      primary: primary ?? this.primary,
      primaryLight: primaryLight ?? this.primaryLight,
      primaryDark: primaryDark ?? this.primaryDark,
      onPrimary: onPrimary ?? this.onPrimary,
      accent: accent ?? this.accent,
      onAccent: onAccent ?? this.onAccent,
      primaryGradient: primaryGradient ?? this.primaryGradient,
      headerGradient: headerGradient ?? this.headerGradient,
      background: background ?? this.background,
      surface: surface ?? this.surface,
      surfaceVariant: surfaceVariant ?? this.surfaceVariant,
      textPrimary: textPrimary ?? this.textPrimary,
      textSecondary: textSecondary ?? this.textSecondary,
      textHint: textHint ?? this.textHint,
      divider: divider ?? this.divider,
      shimmerBase: shimmerBase ?? this.shimmerBase,
      shimmerHighlight: shimmerHighlight ?? this.shimmerHighlight,
      starEmpty: starEmpty ?? this.starEmpty,
    );
  }

  @override
  AppPalette lerp(covariant AppPalette? other, double t) {
    if (other == null) return this;
    Color c(Color a, Color b) => Color.lerp(a, b, t)!;
    return AppPalette(
      isDark: t < 0.5 ? isDark : other.isDark,
      primary: c(primary, other.primary),
      primaryLight: c(primaryLight, other.primaryLight),
      primaryDark: c(primaryDark, other.primaryDark),
      onPrimary: c(onPrimary, other.onPrimary),
      accent: c(accent, other.accent),
      onAccent: c(onAccent, other.onAccent),
      primaryGradient: LinearGradient.lerp(
        primaryGradient,
        other.primaryGradient,
        t,
      )!,
      headerGradient: LinearGradient.lerp(
        headerGradient,
        other.headerGradient,
        t,
      )!,
      background: c(background, other.background),
      surface: c(surface, other.surface),
      surfaceVariant: c(surfaceVariant, other.surfaceVariant),
      textPrimary: c(textPrimary, other.textPrimary),
      textSecondary: c(textSecondary, other.textSecondary),
      textHint: c(textHint, other.textHint),
      divider: c(divider, other.divider),
      shimmerBase: c(shimmerBase, other.shimmerBase),
      shimmerHighlight: c(shimmerHighlight, other.shimmerHighlight),
      starEmpty: c(starEmpty, other.starEmpty),
    );
  }
}

extension AppPaletteContext on BuildContext {
  /// Theme-aware colors for the active theme style (see [AppPalette]).
  AppPalette get palette =>
      Theme.of(this).extension<AppPalette>() ??
      AppPalette.fromStyle(AppThemeStyle.monochrome);
}

// ──────────────────────────────────────────────
// Theme
// ──────────────────────────────────────────────
class AppTheme {
  static ThemeData get lightTheme => getTheme(AppThemeStyle.monochrome);

  static ThemeData getTheme(AppThemeStyle style) {
    final palette = AppPalette.fromStyle(style);
    final surfaceColor = palette.surface;
    final surfaceVariant = palette.surfaceVariant;
    final textPrimary = palette.textPrimary;
    final textSecondary = palette.textSecondary;
    final textHint = palette.textHint;
    final scaffoldBg = palette.background;
    final colorScheme = ColorScheme.fromSeed(
      seedColor: style.primary,
      brightness: style.isDark ? Brightness.dark : Brightness.light,
      primary: style.primary,
      onPrimary: palette.onPrimary,
      secondary: style.accent,
      onSecondary: Colors.white,
      surface: surfaceColor,
      onSurface: textPrimary,
      onSurfaceVariant: textSecondary,
      surfaceContainerHighest: surfaceVariant,
      outline: textHint,
      outlineVariant: palette.divider,
      error: AppColors.error,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: style.isDark ? Brightness.dark : Brightness.light,
      primaryColor: style.primary,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: scaffoldBg,
      canvasColor: scaffoldBg,
      dividerColor: palette.divider,
      fontFamily: GoogleFonts.inter().fontFamily,
      extensions: [palette],
      textTheme: TextTheme(
        headlineSmall: GoogleFonts.inter(
          color: textPrimary,
          fontSize: 24,
          fontWeight: FontWeight.w700,
          letterSpacing: -0.3,
        ),
        bodyLarge: GoogleFonts.inter(color: textPrimary, fontSize: 16),
        bodyMedium: GoogleFonts.inter(color: textSecondary, fontSize: 14),
        bodySmall: GoogleFonts.inter(color: textHint, fontSize: 12),
        titleLarge: GoogleFonts.inter(
          color: textPrimary,
          fontSize: 22,
          fontWeight: FontWeight.w700,
          letterSpacing: -0.2,
        ),
        titleMedium: GoogleFonts.inter(
          color: textPrimary,
          fontSize: 16,
          fontWeight: FontWeight.w600,
        ),
        titleSmall: GoogleFonts.inter(
          color: textPrimary,
          fontSize: 14,
          fontWeight: FontWeight.w600,
        ),
        labelLarge: GoogleFonts.inter(
          fontSize: 14,
          fontWeight: FontWeight.w600,
        ),
      ),
      iconTheme: IconThemeData(color: textPrimary),
      dividerTheme: DividerThemeData(
        color: palette.divider,
        thickness: 1,
        space: 1,
      ),
      progressIndicatorTheme: ProgressIndicatorThemeData(
        color: style.primary,
        linearTrackColor: palette.divider,
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: style.isDark
            ? const Color(0xFF27344A)
            : const Color(0xFF1F1F23),
        contentTextStyle: GoogleFonts.inter(
          color: Colors.white,
          fontSize: 14,
          fontWeight: FontWeight.w500,
        ),
        actionTextColor: Colors.white,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: surfaceColor,
        modalBackgroundColor: surfaceColor,
        surfaceTintColor: Colors.transparent,
        dragHandleColor: palette.divider,
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: surfaceColor,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        titleTextStyle: GoogleFonts.inter(
          color: textPrimary,
          fontSize: 18,
          fontWeight: FontWeight.w700,
        ),
        contentTextStyle: GoogleFonts.inter(
          color: textSecondary,
          fontSize: 14,
          height: 1.4,
        ),
      ),
      // Clean surface app bars on every style (the brand color lives in
      // accents). A soft shadow appears only once content scrolls under.
      appBarTheme: AppBarTheme(
        backgroundColor: surfaceColor,
        foregroundColor: textPrimary,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 2,
        shadowColor: Colors.black.withValues(alpha: style.isDark ? 0.5 : 0.12),
        // Hairline that separates the bar from the page at rest.
        shape: Border(bottom: BorderSide(color: palette.divider, width: 0.8)),
        centerTitle: true,
        systemOverlayStyle: SystemUiOverlayStyle(
          statusBarColor: Colors.transparent,
          statusBarIconBrightness: style.isDark
              ? Brightness.light
              : Brightness.dark,
          statusBarBrightness: style.isDark
              ? Brightness.dark
              : Brightness.light,
        ),
        titleTextStyle: GoogleFonts.inter(
          color: textPrimary,
          fontSize: 17,
          fontWeight: FontWeight.w600,
          letterSpacing: -0.2,
        ),
        iconTheme: IconThemeData(color: textPrimary, size: 22),
        actionsIconTheme: IconThemeData(color: textPrimary, size: 22),
      ),
      // The automatic back button (screens without a custom leading) matches
      // AppBackButton.
      actionIconTheme: ActionIconThemeData(
        backButtonIconBuilder: (context) =>
            const Icon(Icons.arrow_back_ios_new_rounded, size: 20),
      ),
      tabBarTheme: TabBarThemeData(
        labelColor: textPrimary,
        unselectedLabelColor: textSecondary,
        indicatorColor: style.primary,
        indicatorSize: TabBarIndicatorSize.label,
        dividerColor: palette.divider,
        labelStyle: GoogleFonts.inter(
          fontSize: 13.5,
          fontWeight: FontWeight.w700,
        ),
        unselectedLabelStyle: GoogleFonts.inter(
          fontSize: 13.5,
          fontWeight: FontWeight.w500,
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: style.primary,
          foregroundColor: palette.onPrimary,
          disabledBackgroundColor: palette.divider,
          disabledForegroundColor: textHint,
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
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: style.primary,
          foregroundColor: palette.onPrimary,
          disabledBackgroundColor: palette.divider,
          disabledForegroundColor: textHint,
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
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: style.primary,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          textStyle: GoogleFonts.inter(
            fontSize: 14,
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
          borderSide: BorderSide(color: style.primary, width: 2),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: AppColors.error),
        ),
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 14,
        ),
        hintStyle: GoogleFonts.inter(color: textHint, fontSize: 14),
        labelStyle: GoogleFonts.inter(color: textSecondary, fontSize: 14),
        floatingLabelStyle: GoogleFonts.inter(
          color: style.primary,
          fontSize: 14,
          fontWeight: FontWeight.w600,
        ),
      ),
      cardTheme: CardThemeData(
        color: surfaceColor,
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        margin: EdgeInsets.zero,
      ),
      bottomNavigationBarTheme: BottomNavigationBarThemeData(
        backgroundColor: surfaceColor,
        selectedItemColor: style.primary,
        unselectedItemColor: textHint,
        type: BottomNavigationBarType.fixed,
        elevation: 0,
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: surfaceColor,
        indicatorColor: style.primary.withValues(alpha: 0.14),
        labelTextStyle: WidgetStatePropertyAll(
          GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600),
        ),
        elevation: 0,
      ),
      chipTheme: ChipThemeData(
        backgroundColor: surfaceVariant,
        selectedColor: style.primary.withValues(alpha: 0.15),
        labelStyle: GoogleFonts.inter(fontSize: 13, color: textPrimary),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
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
