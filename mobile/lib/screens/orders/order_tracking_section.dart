import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../config/theme.dart';
import '../../models/models.dart';
import '../../providers/providers.dart';
import '../../widgets/common_widgets.dart';

/// The delivery journey, in order. CANCELLED and REFUNDED end it early.
const _journey = ['PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED'];

/// Icon and translation-key suffix (track*, track*Msg, step*) per status.
const _statusInfo = <String, ({IconData icon, String key})>{
  'PENDING': (icon: Icons.receipt_long_rounded, key: 'Placed'),
  'CONFIRMED': (icon: Icons.verified_outlined, key: 'Confirmed'),
  'PROCESSING': (icon: Icons.inventory_2_outlined, key: 'Processing'),
  'SHIPPED': (icon: Icons.local_shipping_outlined, key: 'Shipped'),
  'DELIVERED': (icon: Icons.home_outlined, key: 'Delivered'),
  'CANCELLED': (icon: Icons.cancel_outlined, key: 'Cancelled'),
  'REFUNDED': (icon: Icons.currency_exchange_rounded, key: 'Refunded'),
};

/// Notes the backend writes on every status change. They only repeat the
/// status, so the history hides them and shows real (admin-written) notes.
const _systemNotes = {
  'Order placed',
  'Order status updated',
  'Order cancelled',
  'Current order status',
};

/// Order tracking: a status summary with the delivery progress, followed by
/// the history of status updates (newest first).
class OrderTrackingSection extends ConsumerStatefulWidget {
  final Order order;

  const OrderTrackingSection({super.key, required this.order});

  @override
  ConsumerState<OrderTrackingSection> createState() =>
      _OrderTrackingSectionState();
}

class _OrderTrackingSectionState extends ConsumerState<OrderTrackingSection> {
  // Longer histories collapse to this many updates behind "Show all".
  static const _collapsedCount = 4;

  bool _showAllEvents = false;

  // build() watches appLanguageProvider, so reading it here stays current.
  String _t(String key) => appText(ref.read(appLanguageProvider), key);

  @override
  Widget build(BuildContext context) {
    ref.watch(appLanguageProvider);
    final trackingAsync = ref.watch(orderTrackingProvider(widget.order.id));
    final tracking = trackingAsync.value;
    final status =
        (tracking != null && tracking.currentStatus.isNotEmpty
                ? tracking.currentStatus
                : widget.order.status)
            .toUpperCase();
    final events = tracking?.events ?? const <OrderTrackingEvent>[];
    final lastUpdate =
        _parse(events.isNotEmpty ? events.last.createdAt : null) ??
        _parse(widget.order.updatedAt) ??
        _parse(widget.order.createdAt);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildSummaryCard(status, lastUpdate),
        const SizedBox(height: 16),
        _buildHistoryCard(trackingAsync),
      ],
    );
  }

  // ──────────────────────────────────────────────
  // Summary
  // ──────────────────────────────────────────────

  Widget _buildSummaryCard(String status, DateTime? lastUpdate) {
    final palette = context.palette;
    final info = _statusInfo[status];
    final accent = _accentFor(status);
    final journeyIndex = _journey.indexOf(status);
    final isDelivered = status == 'DELIVERED';
    final estimate = journeyIndex >= 0 && !isDelivered
        ? _deliveryEstimate()
        : null;

    return Container(
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ColoredBox(
            color: accent.withValues(alpha: palette.isDark ? 0.16 : 0.08),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 48,
                    height: 48,
                    decoration: BoxDecoration(
                      color: accent,
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      info?.icon ?? Icons.info_outline_rounded,
                      color: _onColor(accent),
                      size: 24,
                    ),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _statusTitle(status),
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: palette.textPrimary,
                          ),
                        ),
                        if (info != null) ...[
                          const SizedBox(height: 4),
                          Text(
                            _t('track${info.key}Msg'),
                            style: TextStyle(
                              fontSize: 13.5,
                              height: 1.4,
                              color: palette.textSecondary,
                            ),
                          ),
                        ],
                        if (lastUpdate != null) ...[
                          const SizedBox(height: 8),
                          Row(
                            children: [
                              Icon(
                                Icons.schedule_rounded,
                                size: 14,
                                color: palette.textHint,
                              ),
                              const SizedBox(width: 4),
                              Flexible(
                                child: Text(
                                  '${_t('lastUpdated')} ${_ago(lastUpdate)}',
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: palette.textHint,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          if (journeyIndex >= 0)
            Padding(
              padding: const EdgeInsets.fromLTRB(8, 18, 8, 16),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  for (var i = 0; i < _journey.length; i++)
                    Expanded(
                      child: _buildJourneyStep(i, journeyIndex, isDelivered),
                    ),
                ],
              ),
            ),
          if (estimate != null) ...[
            const Divider(height: 1),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 14),
              child: Row(
                children: [
                  Icon(
                    Icons.local_shipping_outlined,
                    size: 18,
                    color: palette.textSecondary,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      estimate,
                      style: TextStyle(
                        fontSize: 13,
                        color: palette.textSecondary,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildJourneyStep(int index, int activeIndex, bool isDelivered) {
    final palette = context.palette;
    final info = _statusInfo[_journey[index]]!;
    final reached = index <= activeIndex;
    final completed = index < activeIndex || isDelivered;
    final current = index == activeIndex && !isDelivered;

    // Half-lines on each side of the dot join up with the neighbouring steps.
    Widget connector({required bool visible, required bool filled}) {
      return Expanded(
        child: Container(
          height: 3,
          color: !visible
              ? Colors.transparent
              : filled
              ? palette.primary
              : palette.divider,
        ),
      );
    }

    return Semantics(
      container: true,
      selected: current,
      label: _t('step${info.key}'),
      excludeSemantics: true,
      child: Column(
        children: [
          SizedBox(
            height: 36,
            child: Row(
              children: [
                connector(visible: index > 0, filled: reached),
                Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: reached ? palette.primary : palette.surfaceVariant,
                    shape: BoxShape.circle,
                    boxShadow: current
                        ? [
                            BoxShadow(
                              color: palette.primary.withValues(alpha: 0.22),
                              spreadRadius: 4,
                            ),
                          ]
                        : null,
                  ),
                  child: Icon(
                    completed ? Icons.check_rounded : info.icon,
                    size: completed ? 18 : 16,
                    color: reached ? palette.onPrimary : palette.textHint,
                  ),
                ),
                connector(
                  visible: index < _journey.length - 1,
                  filled: index < activeIndex,
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 2),
            child: Text(
              _t('step${info.key}'),
              textAlign: TextAlign.center,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 11.5,
                height: 1.25,
                fontWeight: current
                    ? FontWeight.w700
                    : reached
                    ? FontWeight.w600
                    : FontWeight.w500,
                color: reached ? palette.textPrimary : palette.textHint,
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// "Standard Delivery · 2-3 days", from the order and its delivery option.
  String? _deliveryEstimate() {
    final order = widget.order;
    String? estimate;
    final optionId = order.deliveryOptionId;
    if (optionId != null) {
      final options = ref.watch(deliveryOptionsProvider).value;
      for (final option in options ?? const <DeliveryOption>[]) {
        if (option.id == optionId) {
          estimate = option.estimatedTime;
          break;
        }
      }
    }
    final parts = [
      if (order.deliveryMethodName?.trim().isNotEmpty == true)
        order.deliveryMethodName!.trim(),
      if (estimate?.trim().isNotEmpty == true) estimate!.trim(),
    ];
    return parts.isEmpty ? null : parts.join(' · ');
  }

  // ──────────────────────────────────────────────
  // History
  // ──────────────────────────────────────────────

  Widget _buildHistoryCard(AsyncValue<OrderTracking> trackingAsync) {
    final palette = context.palette;
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.timeline_rounded, size: 20, color: palette.primary),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  _t('trackingHistory'),
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: palette.textPrimary,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          trackingAsync.when(
            loading: () => const Padding(
              padding: EdgeInsets.only(bottom: 8),
              child: Column(
                children: [
                  ShimmerLoading(height: 44),
                  SizedBox(height: 12),
                  ShimmerLoading(height: 44),
                ],
              ),
            ),
            error: (_, _) => Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Row(
                children: [
                  Icon(
                    Icons.cloud_off_rounded,
                    size: 18,
                    color: palette.textHint,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      _t('trackingUnavailable'),
                      style: TextStyle(
                        fontSize: 13,
                        color: palette.textSecondary,
                      ),
                    ),
                  ),
                  TextButton(
                    onPressed: () =>
                        ref.invalidate(orderTrackingProvider(widget.order.id)),
                    child: Text(_t('retry')),
                  ),
                ],
              ),
            ),
            data: (tracking) => _buildEvents(tracking.events),
          ),
        ],
      ),
    );
  }

  Widget _buildEvents(List<OrderTrackingEvent> events) {
    if (events.isEmpty) return const SizedBox(height: 8);

    // The API lists oldest first; the latest update matters most.
    final newestFirst = events.reversed.toList();
    final collapsible = newestFirst.length > _collapsedCount + 1;
    final collapsed = collapsible && !_showAllEvents;
    final visible = collapsed
        ? newestFirst.take(_collapsedCount).toList()
        : newestFirst;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var i = 0; i < visible.length; i++)
          _buildEvent(
            visible[i],
            isLatest: i == 0,
            // Keep the rail running when older updates are hidden below.
            isLast: i == visible.length - 1 && !collapsed,
          ),
        if (collapsible)
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton(
              onPressed: () => setState(() => _showAllEvents = !_showAllEvents),
              child: Text(
                _showAllEvents
                    ? _t('showFewerUpdates')
                    : _t('showAllUpdates')
                          .replaceAll('{n}', '${newestFirst.length}'),
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildEvent(
    OrderTrackingEvent event, {
    required bool isLatest,
    required bool isLast,
  }) {
    final palette = context.palette;
    final status = event.status.toUpperCase();
    final accent = _accentFor(status);
    final note = event.note?.trim() ?? '';
    final showNote = note.isNotEmpty && !_systemNotes.contains(note);
    final time = _parse(event.createdAt);

    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            width: 24,
            child: Column(
              children: [
                if (isLatest)
                  Container(
                    width: 22,
                    height: 22,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: accent.withValues(alpha: 0.18),
                      shape: BoxShape.circle,
                    ),
                    child: Container(
                      width: 10,
                      height: 10,
                      decoration: BoxDecoration(
                        color: accent,
                        shape: BoxShape.circle,
                      ),
                    ),
                  )
                else
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 6),
                    child: Container(
                      width: 10,
                      height: 10,
                      decoration: BoxDecoration(
                        color: palette.surface,
                        shape: BoxShape.circle,
                        border: Border.all(color: palette.textHint, width: 2),
                      ),
                    ),
                  ),
                if (!isLast)
                  Expanded(
                    child: Container(
                      width: 2,
                      margin: const EdgeInsets.symmetric(vertical: 4),
                      color: palette.divider,
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Padding(
              padding: EdgeInsets.only(bottom: isLast ? 12 : 20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: Text(
                          _statusTitle(status),
                          style: TextStyle(
                            fontSize: 14.5,
                            fontWeight: isLatest
                                ? FontWeight.w700
                                : FontWeight.w600,
                            color: isLatest
                                ? palette.textPrimary
                                : palette.textSecondary,
                          ),
                        ),
                      ),
                      if (isLatest) ...[
                        const SizedBox(width: 8),
                        DecoratedBox(
                          decoration: BoxDecoration(
                            color: accent.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 6,
                              vertical: 2,
                            ),
                            child: Text(
                              _t('latest'),
                              style: TextStyle(
                                fontSize: 10.5,
                                fontWeight: FontWeight.w700,
                                color: palette.isDark
                                    ? palette.textPrimary
                                    : accent,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                  if (showNote) ...[
                    const SizedBox(height: 3),
                    Text(
                      note,
                      style: TextStyle(
                        fontSize: 13,
                        height: 1.4,
                        color: palette.textSecondary,
                      ),
                    ),
                  ],
                  if (time != null) ...[
                    const SizedBox(height: 3),
                    Text(
                      _formatTime(time),
                      style: TextStyle(fontSize: 12, color: palette.textHint),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ──────────────────────────────────────────────
  // Helpers
  // ──────────────────────────────────────────────

  String _statusTitle(String status) {
    final info = _statusInfo[status];
    if (info != null) return _t('track${info.key}');
    // Unknown status from a newer backend: "ON_HOLD" -> "On hold".
    final words = status.toLowerCase().replaceAll('_', ' ');
    return words.isEmpty ? '' : words[0].toUpperCase() + words.substring(1);
  }

  /// Delivered, cancelled and refunded keep their meaning colors; the
  /// in-progress statuses follow the theme.
  Color _accentFor(String status) {
    final palette = context.palette;
    return switch (status) {
      'DELIVERED' =>
        palette.isDark ? AppColors.success : const Color(0xFF15803D),
      'CANCELLED' => AppColors.error,
      'REFUNDED' => AppColors.shipped,
      _ => palette.primary,
    };
  }

  Color _onColor(Color background) =>
      ThemeData.estimateBrightnessForColor(background) == Brightness.dark
      ? Colors.white
      : const Color(0xFF111111);

  // The API sends local times without a zone, e.g. 2026-11-03T14:32:10.
  DateTime? _parse(String? value) =>
      value == null ? null : DateTime.tryParse(value);

  String _formatTime(DateTime time) =>
      DateFormat('d MMM yyyy · HH:mm').format(time);

  String _ago(DateTime time) {
    final diff = DateTime.now().difference(time);
    if (diff.inMinutes < 1) return _t('justNow');
    if (diff.inHours < 1) {
      return _t('minutesAgo').replaceAll('{n}', '${diff.inMinutes}');
    }
    if (diff.inDays < 1) {
      return _t('hoursAgo').replaceAll('{n}', '${diff.inHours}');
    }
    if (diff.inDays < 7) {
      return _t('daysAgo').replaceAll('{n}', '${diff.inDays}');
    }
    return _formatTime(time);
  }
}
