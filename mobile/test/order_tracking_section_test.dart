import 'package:ecomerce_shop/config/theme.dart';
import 'package:ecomerce_shop/models/models.dart';
import 'package:ecomerce_shop/providers/providers.dart';
import 'package:ecomerce_shop/screens/orders/order_tracking_section.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';

Order _order(String status) => Order.fromJson({
  'id': 141,
  'orderNumber': 'ORD-141',
  'status': status,
  'deliveryOptionId': 2,
  'deliveryMethodName': 'Standard Delivery',
  'createdAt': '2026-11-01T18:05:00',
});

OrderTrackingEvent _event(String status, String note, String createdAt) =>
    OrderTrackingEvent(status: status, note: note, createdAt: createdAt);

// Oldest first, as the API sends them.
final _shippedHistory = [
  _event('PENDING', 'Order placed', '2026-11-01T18:05:00'),
  _event('CONFIRMED', 'Order status updated', '2026-11-02T09:10:00'),
  _event('PROCESSING', 'Packed by Sokha', '2026-11-02T15:00:00'),
  _event('SHIPPED', 'Order status updated', '2026-11-03T08:30:00'),
];

Future<void> _pump(
  WidgetTester tester, {
  required String status,
  required List<OrderTrackingEvent> events,
  AppThemeStyle style = AppThemeStyle.monochrome,
  double width = 390,
}) async {
  tester.view.devicePixelRatio = 3;
  tester.view.physicalSize = Size(width * 3, 1400 * 3);
  addTearDown(tester.view.reset);

  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        orderTrackingProvider(141).overrideWith(
          (ref) async => OrderTracking(
            orderId: 141,
            orderNumber: 'ORD-141',
            currentStatus: status,
            events: events,
          ),
        ),
        deliveryOptionsProvider.overrideWith(
          (ref) async => [
            DeliveryOption.fromJson({
              'id': 2,
              'name': 'Standard Delivery',
              'type': 'STANDARD',
              'estimatedTime': '2-3 days',
            }),
          ],
        ),
      ],
      child: MaterialApp(
        theme: AppTheme.getTheme(style),
        home: Scaffold(
          body: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: OrderTrackingSection(order: _order(status)),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(() {
    GoogleFonts.config.allowRuntimeFetching = false;
  });

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  testWidgets('summarises a shipped order with its progress and ETA', (
    tester,
  ) async {
    await _pump(tester, status: 'SHIPPED', events: _shippedHistory);

    // Summary title and the latest history entry.
    expect(find.text('On the way'), findsNWidgets(2));
    expect(find.text('Your package is on its way to you.'), findsOneWidget);
    expect(find.text('Standard Delivery · 2-3 days'), findsOneWidget);
    for (final step in [
      'Placed',
      'Confirmed',
      'Packing',
      'Shipped',
      'Delivered',
    ]) {
      expect(find.text(step), findsOneWidget);
    }
  });

  testWidgets('history is newest first and hides system notes', (tester) async {
    await _pump(tester, status: 'SHIPPED', events: _shippedHistory);

    expect(find.text('Latest'), findsOneWidget);
    expect(find.text('Packed by Sokha'), findsOneWidget);
    expect(find.text('Order status updated'), findsNothing);
    expect(find.text('3 Nov 2026 · 08:30'), findsOneWidget);
    expect(
      tester.getTopLeft(find.text('Order placed')).dy,
      greaterThan(tester.getTopLeft(find.text('Packed by Sokha')).dy),
    );
  });

  testWidgets('long histories collapse behind "Show all"', (tester) async {
    final events = [
      ..._shippedHistory,
      _event('SHIPPED', 'Left the Phnom Penh hub', '2026-11-03T12:00:00'),
      _event('SHIPPED', 'With the courier', '2026-11-03T15:00:00'),
    ];
    await _pump(tester, status: 'SHIPPED', events: events);

    expect(find.text('Show all updates (6)'), findsOneWidget);
    expect(find.text('Order placed'), findsNothing);

    await tester.tap(find.text('Show all updates (6)'));
    await tester.pumpAndSettle();

    expect(find.text('Order placed'), findsOneWidget);
    expect(find.text('Show fewer'), findsOneWidget);
  });

  testWidgets('a cancelled order has no delivery progress', (tester) async {
    await _pump(
      tester,
      status: 'CANCELLED',
      events: [
        _event('PENDING', 'Order placed', '2026-11-01T18:05:00'),
        _event('CANCELLED', 'Order cancelled', '2026-11-01T19:00:00'),
      ],
    );

    expect(find.text('Order cancelled'), findsNWidgets(2));
    expect(find.text('Packing'), findsNothing);
    expect(find.text('Standard Delivery · 2-3 days'), findsNothing);
  });

  for (final style in AppThemeStyle.values) {
    testWidgets('fits 320px in Khmer on ${style.name}', (tester) async {
      SharedPreferences.setMockInitialValues({'app_language': 'km'});
      await _pump(
        tester,
        status: 'PROCESSING',
        events: _shippedHistory.take(3).toList(),
        style: style,
        width: 320,
      );
      expect(tester.takeException(), isNull);
      // Really rendered in Khmer, not the English fallback.
      expect(find.text('ប្រវត្តិតាមដាន'), findsOneWidget);
    });
  }
}
