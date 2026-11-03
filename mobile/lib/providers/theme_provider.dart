import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../config/theme.dart';

class ThemeStyleNotifier extends Notifier<AppThemeStyle> {
  static const _storageKey = 'app_theme_style_id';

  @override
  AppThemeStyle build() {
    // Avoid notifying listeners while Riverpod is still mounting the provider.
    Future<void>.microtask(_loadSavedTheme);
    return AppThemeStyle.purple;
  }

  Future<void> _loadSavedTheme() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final savedId = prefs.getString(_storageKey);
      if (savedId != null) {
        final found = AppThemeStyle.values.firstWhere(
          (s) => s.id == savedId,
          orElse: () => AppThemeStyle.purple,
        );
        if (ref.mounted) {
          state = found;
        }
      }
    } catch (_) {}
  }

  Future<void> setTheme(AppThemeStyle style) async {
    state = style;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_storageKey, style.id);
    } catch (_) {}
  }
}

final themeStyleProvider = NotifierProvider<ThemeStyleNotifier, AppThemeStyle>(
  ThemeStyleNotifier.new,
);
