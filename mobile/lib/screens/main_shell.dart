import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../providers/providers.dart';
import '../widgets/app_drawer.dart';

class MainShell extends ConsumerWidget {
  final StatefulNavigationShell navigationShell;

  const MainShell({super.key, required this.navigationShell});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cart = ref.watch(cartProvider);
    final themeStyle = ref.watch(themeStyleProvider);
    final language = ref.watch(appLanguageProvider);

    final currentIndex = navigationShell.currentIndex;

    final destinations = [
      const _NavDestination(
        icon: Icons.home_rounded,
        activeIcon: Icons.home_rounded,
        labelKey: 'home',
      ),
      const _NavDestination(
        icon: Icons.grid_view_rounded,
        activeIcon: Icons.grid_view_rounded,
        labelKey: 'shop',
      ),
      const _NavDestination(
        icon: Icons.favorite_border_rounded,
        activeIcon: Icons.favorite_rounded,
        labelKey: 'wishlist',
      ),
      _NavDestination(
        icon: Icons.shopping_cart_outlined,
        activeIcon: Icons.shopping_cart_rounded,
        labelKey: 'cart',
        badgeCount: cart.totalQuantity,
      ),
      const _NavDestination(
        icon: Icons.person_outline_rounded,
        activeIcon: Icons.person_rounded,
        labelKey: 'profile',
      ),
    ];

    return Scaffold(
      drawer: const AppDrawer(),
      extendBody: true,
      body: navigationShell,
      bottomNavigationBar: Container(
        decoration: BoxDecoration(
          color: themeStyle.isDark ? const Color(0xFF1E293B) : Colors.white,
          borderRadius: const BorderRadius.only(
            topLeft: Radius.circular(30),
            topRight: Radius.circular(30),
          ),
          boxShadow: [
            BoxShadow(
              color: (themeStyle.isDark ? Colors.black : themeStyle.primary)
                  .withValues(alpha: themeStyle.isDark ? 0.35 : 0.09),
              blurRadius: 20,
              offset: const Offset(0, -6),
              spreadRadius: 0,
            ),
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.02),
              blurRadius: 6,
              offset: const Offset(0, -1),
            ),
          ],
        ),
        child: SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: List.generate(destinations.length, (index) {
                final dest = destinations[index];
                final isActive = currentIndex == index;

                return _NavItem(
                  icon: dest.icon,
                  activeIcon: dest.activeIcon,
                  label: appText(language, dest.labelKey),
                  isActive: isActive,
                  badgeCount: dest.badgeCount,
                  activeColor: themeStyle.navActiveColor,
                  inactiveColor: themeStyle.isDark
                      ? const Color(0xFF94A3B8)
                      : const Color(0xFF8C93A4),
                  onTap: () {
                    if (currentIndex != index) {
                      navigationShell.goBranch(index, initialLocation: false);
                    }
                  },
                );
              }),
            ),
          ),
        ),
      ),
    );
  }
}

class _NavDestination {
  final IconData icon;
  final IconData activeIcon;
  final String labelKey;
  final int badgeCount;

  const _NavDestination({
    required this.icon,
    required this.activeIcon,
    required this.labelKey,
    this.badgeCount = 0,
  });
}

class _NavItem extends StatelessWidget {
  final IconData icon;
  final IconData activeIcon;
  final String label;
  final bool isActive;
  final int badgeCount;
  final Color activeColor;
  final Color inactiveColor;
  final VoidCallback onTap;

  const _NavItem({
    required this.icon,
    required this.activeIcon,
    required this.label,
    required this.isActive,
    required this.badgeCount,
    required this.activeColor,
    required this.inactiveColor,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 260),
        curve: Curves.easeOutCubic,
        padding: isActive
            ? const EdgeInsets.symmetric(horizontal: 18, vertical: 10)
            : const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: isActive ? activeColor : Colors.transparent,
          borderRadius: BorderRadius.circular(26),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Stack(
              clipBehavior: Clip.none,
              children: [
                Icon(
                  isActive ? activeIcon : icon,
                  color: isActive ? Colors.white : inactiveColor,
                  size: 22,
                ),
                if (!isActive && badgeCount > 0)
                  Positioned(
                    top: -2,
                    right: -4,
                    child: Container(
                      width: 7,
                      height: 7,
                      decoration: const BoxDecoration(
                        color: Color(0xFFFF3B30),
                        shape: BoxShape.circle,
                      ),
                    ),
                  ),
              ],
            ),
            if (isActive) ...[
              const SizedBox(width: 8),
              Text(
                label,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  letterSpacing: -0.2,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
