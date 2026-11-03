import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:path_provider/path_provider.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:printing/printing.dart';
import 'package:share_plus/share_plus.dart';

import '../../config/api_config.dart';
import '../../config/theme.dart';
import '../../models/models.dart';
import '../../providers/providers.dart';
import '../../services/api_client.dart';
import '../../widgets/common_widgets.dart';

class OrderDetailScreen extends ConsumerStatefulWidget {
  final int orderId;

  const OrderDetailScreen({super.key, required this.orderId});

  @override
  ConsumerState<OrderDetailScreen> createState() => _OrderDetailScreenState();
}

class _InvoiceSheet extends StatefulWidget {
  final Invoice invoice;

  const _InvoiceSheet({required this.invoice});

  @override
  State<_InvoiceSheet> createState() => _InvoiceSheetState();
}

class _InvoiceSheetState extends State<_InvoiceSheet> {
  bool _busy = false;

  Future<Uint8List> _buildPdf() async {
    final invoice = widget.invoice;
    final document = pw.Document();
    final regularFont = pw.Font.ttf(
      await rootBundle.load('assets/fonts/NotoSansKhmer-Regular.ttf'),
    );
    final boldFont = pw.Font.ttf(
      await rootBundle.load('assets/fonts/NotoSansKhmer-Bold.ttf'),
    );
    final regular = pw.TextStyle(
      font: regularFont,
      fontSize: 10,
      color: PdfColors.grey800,
    );
    final bold = pw.TextStyle(
      font: boldFont,
      fontSize: 10,
      fontWeight: pw.FontWeight.bold,
    );

    document.addPage(
      pw.Page(
        pageFormat: PdfPageFormat.a4,
        margin: const pw.EdgeInsets.all(36),
        build: (context) => pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.start,
          children: [
            pw.Text(
              'វិក្កយបត្រ / INVOICE',
              style: pw.TextStyle(
                font: boldFont,
                fontSize: 24,
                fontWeight: pw.FontWeight.bold,
              ),
            ),
            pw.SizedBox(height: 8),
            pw.Text(invoice.invoiceNumber, style: regular),
            pw.Text('ការបញ្ជាទិញ ${invoice.orderNumber}', style: regular),
            pw.Divider(height: 24),
            pw.Table(
              columnWidths: {
                0: const pw.FlexColumnWidth(3),
                1: const pw.FlexColumnWidth(1),
              },
              children: [
                for (final item in invoice.items)
                  pw.TableRow(
                    children: [
                      pw.Padding(
                        padding: const pw.EdgeInsets.only(bottom: 8),
                        child: pw.Text(
                          '${item.productName} x ${item.quantity}',
                          style: regular,
                        ),
                      ),
                      pw.Align(
                        alignment: pw.Alignment.centerRight,
                        child: pw.Text(
                          '\$${item.subtotal.toStringAsFixed(2)}',
                          style: regular,
                        ),
                      ),
                    ],
                  ),
              ],
            ),
            pw.Divider(height: 20),
            _pdfRow('សរុបរង / Subtotal', invoice.subtotal, regular),
            _pdfRow('ដឹកជញ្ជូន / Shipping', invoice.deliveryFee, regular),
            _pdfRow('បញ្ចុះតម្លៃ / Discount', -invoice.discount, regular),
            pw.Divider(height: 20),
            _pdfRow('សរុប / Total', invoice.totalAmount, bold),
            if (invoice.shippingAddress != null) ...[
              pw.SizedBox(height: 20),
              pw.Text('អាសយដ្ឋានដឹកជញ្ជូន / Shipping address', style: bold),
              pw.SizedBox(height: 4),
              pw.Text(invoice.shippingAddress!, style: regular),
            ],
          ],
        ),
      ),
    );
    return document.save();
  }

  pw.Widget _pdfRow(String label, double value, pw.TextStyle style) {
    return pw.Padding(
      padding: const pw.EdgeInsets.symmetric(vertical: 4),
      child: pw.Row(
        mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
        children: [
          pw.Text(label, style: style),
          pw.Text('\$${value.toStringAsFixed(2)}', style: style),
        ],
      ),
    );
  }

  Future<void> _printInvoice() async {
    setState(() => _busy = true);
    try {
      final bytes = await _buildPdf();
      await Printing.layoutPdf(onLayout: (_) async => bytes);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Unable to open print dialog: $error')),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _shareInvoice() async {
    setState(() => _busy = true);
    try {
      final bytes = await _buildPdf();
      final directory = await getApplicationDocumentsDirectory();
      final file = File(
        '${directory.path}/invoice-${widget.invoice.invoiceNumber}.pdf',
      );
      await file.writeAsBytes(bytes, flush: true);
      await SharePlus.instance.share(
        ShareParams(
          files: [XFile(file.path, mimeType: 'application/pdf')],
          subject: 'Invoice ${widget.invoice.invoiceNumber}',
        ),
      );
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Unable to save or share invoice PDF: $error'),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 24),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Expanded(
                    child: Text(
                      'Invoice',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  IconButton(
                    tooltip: 'Print invoice',
                    onPressed: _busy ? null : _printInvoice,
                    icon: const Icon(Icons.print_outlined),
                  ),
                  IconButton(
                    tooltip: 'Save or share PDF',
                    onPressed: _busy ? null : _shareInvoice,
                    icon: const Icon(Icons.picture_as_pdf_outlined),
                  ),
                ],
              ),
              Text(
                widget.invoice.invoiceNumber,
                style: const TextStyle(color: AppColors.textSecondary),
              ),
              if (_busy) const LinearProgressIndicator(),
              const SizedBox(height: 8),
              Text(
                'Order ${widget.invoice.orderNumber}',
                style: const TextStyle(color: AppColors.textSecondary),
              ),
              const Divider(height: 24),
              ...widget.invoice.items.map(
                (item) => Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text('${item.productName} × ${item.quantity}'),
                      ),
                      Text('\$${item.subtotal.toStringAsFixed(2)}'),
                    ],
                  ),
                ),
              ),
              const Divider(height: 20),
              _InvoiceRow(label: 'Subtotal', value: widget.invoice.subtotal),
              _InvoiceRow(label: 'Shipping', value: widget.invoice.deliveryFee),
              _InvoiceRow(label: 'Discount', value: -widget.invoice.discount),
              const Divider(height: 20),
              _InvoiceRow(
                label: 'Total',
                value: widget.invoice.totalAmount,
                bold: true,
              ),
              if (widget.invoice.shippingAddress != null) ...[
                const SizedBox(height: 16),
                Text(
                  widget.invoice.shippingAddress!,
                  style: const TextStyle(color: AppColors.textSecondary),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _InvoiceRow extends StatelessWidget {
  final String label;
  final double value;
  final bool bold;

  const _InvoiceRow({
    required this.label,
    required this.value,
    this.bold = false,
  });

  @override
  Widget build(BuildContext context) {
    final style = TextStyle(
      fontWeight: bold ? FontWeight.w700 : FontWeight.w400,
    );
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: style),
          Text('\$${value.toStringAsFixed(2)}', style: style),
        ],
      ),
    );
  }
}

class _OrderDetailScreenState extends ConsumerState<OrderDetailScreen> {
  bool _isCancelling = false;

  @override
  Widget build(BuildContext context) {
    final orderAsync = ref.watch(orderDetailProvider(widget.orderId));
    final trackingAsync = ref.watch(orderTrackingProvider(widget.orderId));
    final language = ref.watch(appLanguageProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new_rounded),
          onPressed: () {
            if (context.canPop()) {
              context.pop();
            } else {
              context.go('/orders');
            }
          },
        ),
        title: Text(appText(language, 'orderDetails')),
      ),
      body: orderAsync.when(
        data: (order) => _buildBody(order, trackingAsync),
        loading: () => const Center(
          child: CircularProgressIndicator(color: AppColors.primary),
        ),
        error: (err, _) => Center(
          child: EmptyState(
            icon: Icons.error_outline_rounded,
            title: 'Failed to load order',
            subtitle: err.toString(),
            actionLabel: 'Retry',
            onAction: () => ref.refresh(orderDetailProvider(widget.orderId)),
          ),
        ),
      ),
    );
  }

  Widget _buildBody(Order order, AsyncValue<OrderTracking> trackingAsync) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // ─── Status Stepper ───
          _buildStatusTracker(order.status),
          const SizedBox(height: 16),
          _buildTrackingCard(trackingAsync),
          const SizedBox(height: 16),
          _buildShipmentMap(order),
          const SizedBox(height: 16),

          // ─── Order Overview Card ───
          _buildOverviewCard(order),
          const SizedBox(height: 16),

          // ─── Items Card ───
          _buildItemsCard(order),
          const SizedBox(height: 16),

          // ─── Delivery Info Card ───
          _buildDeliveryInfoCard(order),
          const SizedBox(height: 16),

          // ─── Price Breakdown Card ───
          _buildPriceBreakdownCard(order),
          const SizedBox(height: 24),

          SizedBox(
            width: double.infinity,
            height: 50,
            child: OutlinedButton.icon(
              onPressed: () => _showInvoice(order.id),
              icon: const Icon(Icons.receipt_long_rounded),
              label: Text(
                appText(ref.read(appLanguageProvider), 'viewInvoice'),
              ),
              style: OutlinedButton.styleFrom(
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
              ),
            ),
          ),
          const SizedBox(height: 12),

          // ─── Action Buttons (Cancel / Pay) ───
          if (order.cancellable) ...[
            SizedBox(
              width: double.infinity,
              height: 50,
              child: OutlinedButton.icon(
                onPressed: _isCancelling
                    ? null
                    : () => _confirmCancelOrder(order),
                icon: _isCancelling
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: AppColors.error,
                        ),
                      )
                    : const Icon(Icons.cancel_outlined, color: AppColors.error),
                label: const Text(
                  'Cancel Order',
                  style: TextStyle(color: AppColors.error),
                ),
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: AppColors.error),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 12),
          ],

          if (order.paymentStatus.toUpperCase() == 'PENDING' &&
              order.paymentMethod.toUpperCase() == 'KHQR' &&
              order.status.toUpperCase() != 'CANCELLED')
            GradientButton(
              label: 'Pay Now with KHQR',
              onPressed: () => _startPayment(order),
            ),

          const SizedBox(height: 20),
        ],
      ),
    );
  }

  Widget _buildTrackingCard(AsyncValue<OrderTracking> trackingAsync) {
    return trackingAsync.when(
      loading: () => const Card(
        child: Padding(
          padding: EdgeInsets.all(16),
          child: Center(child: CircularProgressIndicator()),
        ),
      ),
      error: (_, __) => const SizedBox.shrink(),
      data: (tracking) => Card(
        elevation: 0,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(
                    Icons.local_shipping_outlined,
                    color: AppColors.primary,
                  ),
                  const SizedBox(width: 8),
                  Text(
                    appText(ref.read(appLanguageProvider), 'orderTracking'),
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              ...tracking.events.asMap().entries.map((entry) {
                final event = entry.value;
                final last = entry.key == tracking.events.length - 1;
                return Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Column(
                      children: [
                        Icon(
                          last
                              ? Icons.radio_button_checked
                              : Icons.check_circle,
                          size: 18,
                          color: last ? AppColors.primary : AppColors.success,
                        ),
                        if (!last)
                          Container(
                            width: 2,
                            height: 32,
                            color: AppColors.divider,
                          ),
                      ],
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              event.status,
                              style: const TextStyle(
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            if (event.note != null)
                              Text(
                                event.note!,
                                style: const TextStyle(fontSize: 12),
                              ),
                            if (event.createdAt != null)
                              Text(
                                _formatDate(event.createdAt!),
                                style: const TextStyle(
                                  fontSize: 11,
                                  color: AppColors.textHint,
                                ),
                              ),
                          ],
                        ),
                      ),
                    ),
                  ],
                );
              }),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildShipmentMap(Order order) {
    final hasPin = order.latitude != null && order.longitude != null;
    final isDelivered = order.status.toUpperCase() == 'DELIVERED';
    final isCancelled = order.status.toUpperCase() == 'CANCELLED';
    final mapBackground = Theme.of(context).brightness == Brightness.dark
        ? const Color(0xFF18283A)
        : const Color(0xFFEAF3F2);

    return Card(
      elevation: 0,
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            height: 164,
            width: double.infinity,
            color: mapBackground,
            child: Stack(
              alignment: Alignment.center,
              children: [
                CustomPaint(
                  size: const Size(double.infinity, 164),
                  painter: _RoutePainter(
                    routeColor: Theme.of(context).colorScheme.primary,
                    mutedColor: Theme.of(context).colorScheme.outlineVariant,
                  ),
                ),
                if (!isCancelled)
                  Positioned(
                    left: 26,
                    bottom: 24,
                    child: _mapMarker(
                      icon: Icons.storefront_rounded,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                  ),
                if (hasPin)
                  Positioned(
                    right: 26,
                    top: 24,
                    child: _mapMarker(
                      icon: Icons.location_on_rounded,
                      color: isDelivered
                          ? AppColors.success
                          : Theme.of(context).colorScheme.secondary,
                    ),
                  ),
                if (!hasPin)
                  Icon(
                    Icons.location_searching_rounded,
                    size: 36,
                    color: Theme.of(context).colorScheme.outline,
                  ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.map_outlined,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                    const SizedBox(width: 8),
                    const Expanded(
                      child: Text(
                        'Shipment location',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    if (hasPin)
                      TextButton(
                        onPressed: () => _openShipmentMap(order),
                        child: const Text('Open map'),
                      ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  hasPin
                      ? (isDelivered
                            ? 'Delivered to the saved destination'
                            : 'Delivery destination from your order')
                      : 'No map location was saved for this order',
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                    fontSize: 13,
                  ),
                ),
                if (order.shippingAddress?.trim().isNotEmpty == true) ...[
                  const SizedBox(height: 10),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(
                        Icons.pin_drop_outlined,
                        size: 18,
                        color: Theme.of(context).colorScheme.outline,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          order.shippingAddress!,
                          style: const TextStyle(fontSize: 13),
                        ),
                      ),
                    ],
                  ),
                ],
                if (hasPin) ...[
                  const SizedBox(height: 8),
                  Text(
                    '${order.latitude!.toStringAsFixed(5)}, ${order.longitude!.toStringAsFixed(5)}',
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.outline,
                      fontSize: 11,
                      fontFeatures: const [FontFeature.tabularFigures()],
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _mapMarker({required IconData icon, required Color color}) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: color,
        shape: BoxShape.circle,
        boxShadow: [
          BoxShadow(
            color: color.withValues(alpha: 0.28),
            blurRadius: 12,
            spreadRadius: 3,
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: Icon(icon, color: Colors.white, size: 20),
      ),
    );
  }

  Future<void> _openShipmentMap(Order order) async {
    final latitude = order.latitude;
    final longitude = order.longitude;
    if (latitude == null || longitude == null) return;

    final uri = Uri.parse(
      'https://www.google.com/maps/search/?api=1&query=$latitude,$longitude',
    );
    final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!opened && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Unable to open the map application')),
      );
    }
  }

  Future<void> _showInvoice(int orderId) async {
    final invoice = await ref.read(orderInvoiceProvider(orderId).future);
    if (!mounted) return;
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (_) => _InvoiceSheet(invoice: invoice),
    );
  }

  Widget _buildStatusTracker(String status) {
    final steps = [
      'PENDING',
      'CONFIRMED',
      'PROCESSING',
      'SHIPPED',
      'DELIVERED',
    ];
    final upperStatus = status.toUpperCase();

    if (upperStatus == 'CANCELLED') {
      return Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppColors.cancelled.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppColors.cancelled.withValues(alpha: 0.3)),
        ),
        child: Row(
          children: const [
            Icon(Icons.cancel_rounded, color: AppColors.cancelled, size: 28),
            SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Order Cancelled',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: AppColors.cancelled,
                    ),
                  ),
                  SizedBox(height: 2),
                  Text(
                    'This order has been cancelled and is no longer being processed.',
                    style: TextStyle(
                      fontSize: 12,
                      color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      );
    }

    final currentIndex = steps.indexOf(upperStatus);
    final activeIndex = currentIndex >= 0 ? currentIndex : 0;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 18),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: List.generate(steps.length, (index) {
          final isCompleted = index <= activeIndex;
          final isCurrent = index == activeIndex;
          final isLast = index == steps.length - 1;

          return Expanded(
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    children: [
                      Container(
                        width: 28,
                        height: 28,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: isCompleted
                              ? AppColors.primary
                              : AppColors.surfaceVariant,
                          border: isCurrent
                              ? Border.all(
                                  color: AppColors.primaryLight,
                                  width: 2,
                                )
                              : null,
                        ),
                        child: Center(
                          child: isCompleted
                              ? const Icon(
                                  Icons.check,
                                  size: 16,
                                  color: Colors.white,
                                )
                              : Text(
                                  '${index + 1}',
                                  style: const TextStyle(
                                    fontSize: 12,
                                    fontWeight: FontWeight.w600,
                                    color: AppColors.textHint,
                                  ),
                                ),
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        _stepLabel(steps[index]),
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 10,
                          fontWeight: isCompleted
                              ? FontWeight.w700
                              : FontWeight.w500,
                          color: isCompleted
                              ? AppColors.primary
                              : AppColors.textHint,
                        ),
                      ),
                    ],
                  ),
                ),
                if (!isLast)
                  Container(
                    width: 14,
                    height: 2,
                    color: index < activeIndex
                        ? AppColors.primary
                        : AppColors.divider,
                  ),
              ],
            ),
          );
        }),
      ),
    );
  }

  String _stepLabel(String step) {
    switch (step) {
      case 'PENDING':
        return 'Placed';
      case 'CONFIRMED':
        return 'Confirmed';
      case 'PROCESSING':
        return 'Processing';
      case 'SHIPPED':
        return 'Shipped';
      case 'DELIVERED':
        return 'Delivered';
      default:
        return step;
    }
  }

  Widget _buildOverviewCard(Order order) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Text(
                    order.orderNumber,
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: AppColors.textPrimary,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(
                      Icons.copy_rounded,
                      size: 16,
                      color: AppColors.textHint,
                    ),
                    tooltip: 'Copy Order Number',
                    onPressed: () {
                      Clipboard.setData(ClipboardData(text: order.orderNumber));
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text('Order number copied to clipboard'),
                        ),
                      );
                    },
                  ),
                ],
              ),
              OrderStatusBadge(status: order.status),
            ],
          ),
          const Divider(height: 16, color: AppColors.divider),
          _buildInfoRow('Placed On', _formatDate(order.createdAt ?? '')),
          const SizedBox(height: 6),
          _buildInfoRow(
            'Payment Method',
            order.paymentMethod.replaceAll('_', ' '),
          ),
          const SizedBox(height: 6),
          _buildInfoRow(
            'Payment Status',
            order.paymentStatus,
            valueColor: order.paymentStatus.toUpperCase() == 'PAID'
                ? AppColors.success
                : AppColors.warning,
          ),
        ],
      ),
    );
  }

  Widget _buildItemsCard(Order order) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Items (${order.items.length})',
            style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 12),
          ...order.items.map((item) {
            return Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Row(
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(10),
                    child: SizedBox(
                      width: 52,
                      height: 52,
                      child: item.imageUrl != null && item.imageUrl!.isNotEmpty
                          ? CachedNetworkImage(
                              imageUrl: item.imageUrl!,
                              fit: BoxFit.cover,
                              errorWidget: (_, __, ___) => _placeholder(),
                            )
                          : _placeholder(),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          item.productName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        if (item.variantLabel != null)
                          Text(
                            item.variantLabel!,
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppColors.textSecondary,
                            ),
                          ),
                        Text(
                          '${item.quantity} × \$${item.unitPrice.toStringAsFixed(2)}',
                          style: const TextStyle(
                            fontSize: 12,
                            color: AppColors.textSecondary,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Text(
                    '\$${item.subtotal.toStringAsFixed(2)}',
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
            );
          }),
        ],
      ),
    );
  }

  Widget _buildDeliveryInfoCard(Order order) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Shipping Details',
            style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 12),
          if (order.contactName != null || order.contactPhone != null)
            _buildInfoRow(
              'Recipient',
              '${order.contactName ?? ''} (${order.contactPhone ?? ''})',
            ),
          const SizedBox(height: 6),
          if (order.shippingAddress != null)
            _buildInfoRow('Address', order.shippingAddress!),
          if (order.deliveryMethodName != null) ...[
            const SizedBox(height: 6),
            _buildInfoRow('Method', order.deliveryMethodName!),
          ],
          if (order.note != null && order.note!.isNotEmpty) ...[
            const SizedBox(height: 6),
            _buildInfoRow('Note', order.note!),
          ],
        ],
      ),
    );
  }

  Widget _buildPriceBreakdownCard(Order order) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        children: [
          _buildInfoRow('Subtotal', '\$${order.subtotal.toStringAsFixed(2)}'),
          const SizedBox(height: 6),
          _buildInfoRow(
            'Delivery Fee',
            order.deliveryFee != null && order.deliveryFee! > 0
                ? '\$${order.deliveryFee!.toStringAsFixed(2)}'
                : 'Free',
          ),
          if (order.discount != null && order.discount! > 0) ...[
            const SizedBox(height: 6),
            _buildInfoRow(
              'Discount',
              '-\$${order.discount!.toStringAsFixed(2)}',
              valueColor: AppColors.success,
            ),
          ],
          const Divider(height: 20, color: AppColors.divider),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Total Amount',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
              ),
              Text(
                '\$${order.totalAmount.toStringAsFixed(2)}',
                style: const TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w800,
                  color: AppColors.primary,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, String value, {Color? valueColor}) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
        ),
        const SizedBox(width: 12),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.end,
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: valueColor ?? AppColors.textPrimary,
            ),
          ),
        ),
      ],
    );
  }

  Widget _placeholder() {
    return Container(
      color: AppColors.surfaceVariant,
      child: const Center(
        child: Icon(
          Icons.shopping_bag_outlined,
          size: 24,
          color: AppColors.textHint,
        ),
      ),
    );
  }

  void _confirmCancelOrder(Order order) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Cancel Order'),
        content: Text(
          'Are you sure you want to cancel order ${order.orderNumber}?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('No, Keep Order'),
          ),
          TextButton(
            onPressed: () async {
              Navigator.pop(ctx);
              setState(() => _isCancelling = true);
              final dio = ref.read(dioProvider);
              try {
                await dio.patch('${ApiConfig.orders}/${order.id}/cancel');
                ref.refresh(orderDetailProvider(order.id));
                ref.invalidate(ordersProvider);
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Order has been cancelled'),
                      backgroundColor: AppColors.warning,
                    ),
                  );
                }
              } catch (e) {
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('Failed to cancel order: $e')),
                  );
                }
              } finally {
                if (mounted) setState(() => _isCancelling = false);
              }
            },
            style: TextButton.styleFrom(foregroundColor: AppColors.error),
            child: const Text('Yes, Cancel'),
          ),
        ],
      ),
    );
  }

  Future<void> _startPayment(Order order) async {
    final dio = ref.read(dioProvider);
    try {
      final res = await dio.post('${ApiConfig.payments}/orders/${order.id}');
      final payment = PaymentData.fromJson(res.data['data']);
      if (mounted) {
        _showKhqrDialog(payment);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to initialize payment: $e')),
        );
      }
    }
  }

  void _showKhqrDialog(PaymentData payment) {
    Timer? pollTimer;
    var checking = false;

    Future<void> checkPayment() async {
      if (checking) return;
      checking = true;
      try {
        final response = await ref
            .read(dioProvider)
            .post('${ApiConfig.payments}/${payment.id}/check');
        final status = response.data['data']['paymentStatus'];
        if (status == 'PAID' && mounted) {
          pollTimer?.cancel();
          ref.invalidate(orderDetailProvider(widget.orderId));
          if (context.mounted) Navigator.of(context).pop();
        }
      } catch (_) {
        // A temporary polling failure should not close the payment dialog.
      } finally {
        checking = false;
      }
    }

    pollTimer = Timer.periodic(
      const Duration(seconds: 3),
      (_) => checkPayment(),
    );
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) {
        return Padding(
          padding: const EdgeInsets.fromLTRB(24, 20, 24, 36),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: AppColors.divider,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 16),
              const Text(
                'Scan to Pay with Bakong / KHQR',
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                  color: AppColors.textPrimary,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                'Amount: \$${payment.amount.toStringAsFixed(2)}',
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w800,
                  color: AppColors.primary,
                ),
              ),
              const SizedBox(height: 20),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(16),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.08),
                      blurRadius: 16,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child:
                    payment.khqr?.payload != null &&
                        payment.khqr!.payload!.isNotEmpty
                    ? QrImageView(
                        data: payment.khqr!.payload!,
                        version: QrVersions.auto,
                        size: 220.0,
                      )
                    : (payment.khqr?.qrImage != null
                          ? Image.memory(
                              base64Decode(
                                payment.khqr!.qrImage!.split(',').last,
                              ),
                              width: 220,
                              height: 220,
                            )
                          : const SizedBox(
                              width: 220,
                              height: 220,
                              child: Center(child: Text('QR not available')),
                            )),
              ),
              const SizedBox(height: 20),
              const Text(
                'Scan with ABA, ACLEDA, Wing, or any Bakong-compatible app',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
              ),
              const SizedBox(height: 8),
              const Text(
                'Payment status is checked automatically after you scan.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
              ),
              const SizedBox(height: 24),
              GradientButton(
                label: 'Check Payment Status',
                onPressed: checkPayment,
              ),
            ],
          ),
        );
      },
    ).whenComplete(() => pollTimer?.cancel());
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

class _RoutePainter extends CustomPainter {
  final Color routeColor;
  final Color mutedColor;

  const _RoutePainter({required this.routeColor, required this.mutedColor});

  @override
  void paint(Canvas canvas, Size size) {
    final basePaint = Paint()
      ..color = mutedColor.withValues(alpha: 0.55)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5;
    final routePaint = Paint()
      ..color = routeColor.withValues(alpha: 0.78)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3.5
      ..strokeCap = StrokeCap.round;
    final path = Path()
      ..moveTo(26, size.height - 26)
      ..quadraticBezierTo(size.width * 0.28, 26, size.width * 0.56, 76)
      ..quadraticBezierTo(size.width * 0.75, 110, size.width - 30, 30);
    canvas.drawPath(path, routePaint);

    for (var y = 18.0; y < size.height; y += 30) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y + 18), basePaint);
    }
  }

  @override
  bool shouldRepaint(covariant _RoutePainter oldDelegate) {
    return oldDelegate.routeColor != routeColor ||
        oldDelegate.mutedColor != mutedColor;
  }
}
