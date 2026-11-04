import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_web_plugins/url_strategy.dart';

import 'config/router.dart';
import 'config/theme.dart';
import 'providers/theme_provider.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  // Web: real paths (/oauth2/callback?accessToken=...) instead of /#/..., so the
  // backend's sign-in redirects can reach the router. No-op on other platforms.
  usePathUrlStrategy();

  runApp(const ProviderScope(child: ECommerceApp()));
}

class ECommerceApp extends ConsumerWidget {
  const ECommerceApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);
    final themeStyle = ref.watch(themeStyleProvider);
    final isDark = themeStyle.isDark;
    final palette = AppPalette.fromStyle(themeStyle);

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness: isDark ? Brightness.light : Brightness.dark,
        systemNavigationBarColor: palette.surface,
        systemNavigationBarIconBrightness: isDark
            ? Brightness.light
            : Brightness.dark,
      ),
      child: MaterialApp.router(
        title: 'ShopOnline E-Commerce',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.getTheme(themeStyle),
        routerConfig: router,
      ),
    );
  }
}
