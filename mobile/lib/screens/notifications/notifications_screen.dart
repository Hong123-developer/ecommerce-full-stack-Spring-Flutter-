import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../config/api_config.dart';
import '../../config/theme.dart';
import '../../models/models.dart';
import '../../providers/providers.dart';
import '../../services/api_client.dart';
import '../../widgets/common_widgets.dart';

class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final palette = context.palette;
    final notificationsAsync = ref.watch(notificationsProvider);
    final language = ref.watch(appLanguageProvider);

    return Scaffold(
      backgroundColor: palette.background,
      appBar: AppBar(
        leading: const AppBackButton(fallbackRoute: '/home'),
        title: Text(appText(language, 'notifications')),
        actions: [
          TextButton(
            onPressed: () async {
              final dio = ref.read(dioProvider);
              try {
                await dio.patch('${ApiConfig.notifications}/read-all');
                ref.invalidate(notificationsProvider);
                ref.invalidate(unreadCountProvider);
              } catch (_) {}
            },
            child: Text(appText(language, 'markAllRead')),
          ),
        ],
      ),
      body: notificationsAsync.when(
        data: (notifications) {
          if (notifications.isEmpty) {
            return RefreshIndicator(
              onRefresh: () async {
                ref.refresh(notificationsProvider);
                ref.refresh(unreadCountProvider);
              },
              child: SingleChildScrollView(
                physics: const AlwaysScrollableScrollPhysics(),
                child: Padding(
                  padding: const EdgeInsets.only(top: 100),
                  child: EmptyState(
                    icon: Icons.notifications_off_outlined,
                    title: appText(language, 'noNotifications'),
                    subtitle: appText(language, 'allCaughtUp'),
                  ),
                ),
              ),
            );
          }

          return RefreshIndicator(
            onRefresh: () async {
              ref.refresh(notificationsProvider);
              ref.refresh(unreadCountProvider);
            },
            child: ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: notifications.length,
              separatorBuilder: (_, __) => const SizedBox(height: 10),
              itemBuilder: (context, index) {
                final item = notifications[index];
                return _buildNotificationItem(context, ref, item);
              },
            ),
          );
        },
        loading: () => Center(
          child: CircularProgressIndicator(color: palette.primary),
        ),
        error: (err, _) => Center(
          child: EmptyState(
            icon: Icons.error_outline_rounded,
            title: appText(language, 'failedLoadNotifications'),
            subtitle: err.toString(),
            actionLabel: appText(language, 'retry'),
            onAction: () => ref.refresh(notificationsProvider),
          ),
        ),
      ),
    );
  }

  Widget _buildNotificationItem(BuildContext context, WidgetRef ref, AppNotification notif) {
    final palette = context.palette;
    final typeColor = _getNotificationColor(notif.type, palette);
    return GestureDetector(
      onTap: () async {
        if (!notif.read) {
          final dio = ref.read(dioProvider);
          try {
            await dio.patch('${ApiConfig.notifications}/${notif.id}/read');
            ref.invalidate(notificationsProvider);
            ref.invalidate(unreadCountProvider);
          } catch (_) {}
        }
        if (notif.orderId != null && context.mounted) {
          context.push('/orders/${notif.orderId}');
        }
      },
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: notif.read ? palette.surface : palette.surfaceVariant,
          borderRadius: BorderRadius.circular(16),
          border: notif.read ? null : Border.all(color: palette.primary.withValues(alpha: 0.2)),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.02),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: typeColor.withValues(alpha: 0.12),
                shape: BoxShape.circle,
              ),
              child: Icon(
                _getNotificationIcon(notif.type),
                size: 20,
                color: typeColor,
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          notif.title,
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: notif.read ? FontWeight.w600 : FontWeight.w700,
                            color: palette.textPrimary,
                          ),
                        ),
                      ),
                      if (!notif.read)
                        Container(
                          width: 8,
                          height: 8,
                          decoration: BoxDecoration(
                            color: palette.primary,
                            shape: BoxShape.circle,
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    notif.message,
                    style: TextStyle(
                      fontSize: 13,
                      color: palette.textSecondary,
                      height: 1.3,
                    ),
                  ),
                  if (notif.createdAt != null) ...[
                    const SizedBox(height: 6),
                    Text(
                      _formatDate(notif.createdAt!),
                      style: TextStyle(fontSize: 11, color: palette.textHint),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  IconData _getNotificationIcon(String type) {
    switch (type.toUpperCase()) {
      case 'ORDER_STATUS':
      case 'ORDER':
        return Icons.local_shipping_outlined;
      case 'PROMOTION':
      case 'DISCOUNT':
        return Icons.local_offer_outlined;
      default:
        return Icons.notifications_none_rounded;
    }
  }

  Color _getNotificationColor(String type, AppPalette palette) {
    switch (type.toUpperCase()) {
      case 'ORDER_STATUS':
      case 'ORDER':
        return palette.primary;
      case 'PROMOTION':
      case 'DISCOUNT':
        return palette.accent;
      default:
        return AppColors.info;
    }
  }

  String _formatDate(String isoString) {
    try {
      final dt = DateTime.parse(isoString);
      return '${dt.day}/${dt.month}/${dt.year} ${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
    } catch (_) {
      return isoString;
    }
  }
}
