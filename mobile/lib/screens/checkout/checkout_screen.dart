import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../config/api_config.dart';
import '../../config/theme.dart';
import '../../models/models.dart';
import '../../providers/providers.dart';
import '../../services/api_client.dart';
import '../../widgets/common_widgets.dart';

class CheckoutScreen extends ConsumerStatefulWidget {
  const CheckoutScreen({super.key});

  @override
  ConsumerState<CheckoutScreen> createState() => _CheckoutScreenState();
}

class _CheckoutScreenState extends ConsumerState<CheckoutScreen> {
  Address? _selectedAddress;
  DeliveryOption? _selectedDeliveryOption;
  String _selectedPaymentMethod =
      'KHQR'; // KHQR, CASH_ON_DELIVERY, CARD, BANK_TRANSFER
  final _noteController = TextEditingController();
  final _couponController = TextEditingController();
  double _discount = 0;
  String? _couponCode;
  bool _isApplyingCoupon = false;
  bool _isSubmitting = false;

  @override
  void dispose() {
    _noteController.dispose();
    _couponController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cart = ref.watch(cartProvider);
    final language = ref.watch(appLanguageProvider);
    final addressesAsync = ref.watch(addressesProvider);
    final deliveryOptionsAsync = ref.watch(deliveryOptionsProvider);

    // Auto-select default address if none chosen
    addressesAsync.whenData((addresses) {
      if (_selectedAddress == null && addresses.isNotEmpty) {
        _selectedAddress = addresses.firstWhere(
          (a) => a.isDefault,
          orElse: () => addresses.first,
        );
      }
    });

    // Auto-select first delivery option if none chosen
    deliveryOptionsAsync.whenData((options) {
      if (_selectedDeliveryOption == null && options.isNotEmpty) {
        _selectedDeliveryOption = options.first;
      }
    });

    final deliveryFee = _selectedDeliveryOption?.fee ?? 0.0;
    final totalAmount = cart.subtotal + deliveryFee - _discount;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new_rounded),
          onPressed: () {
            if (context.canPop()) {
              context.pop();
            } else {
              context.go('/cart');
            }
          },
        ),
        title: Text(appText(language, 'checkout')),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // ─── 1. Delivery Address ───
            _buildSectionHeader('Delivery Address', Icons.location_on_rounded),
            const SizedBox(height: 10),
            _buildAddressCard(addressesAsync),
            const SizedBox(height: 20),

            // ─── 2. Delivery Option ───
            _buildSectionHeader(
              'Delivery Method',
              Icons.local_shipping_rounded,
            ),
            const SizedBox(height: 10),
            _buildDeliveryOptions(deliveryOptionsAsync),
            const SizedBox(height: 20),

            // ─── 3. Payment Method ───
            _buildSectionHeader('Payment Method', Icons.payment_rounded),
            const SizedBox(height: 10),
            _buildPaymentMethods(),
            const SizedBox(height: 20),

            // ─── 4. Order Items ───
            _buildSectionHeader(
              'Order Summary (${cart.totalQuantity} items)',
              Icons.shopping_bag_rounded,
            ),
            const SizedBox(height: 10),
            _buildItemsSummary(cart),
            const SizedBox(height: 20),

            TextField(
              controller: _couponController,
              textCapitalization: TextCapitalization.characters,
              decoration: InputDecoration(
                labelText: 'Coupon code',
                suffixIcon: IconButton(
                  icon: _isApplyingCoupon
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.check_circle_outline),
                  onPressed: _isApplyingCoupon ? null : _applyCoupon,
                ),
                filled: true,
                fillColor: AppColors.surface,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
            if (_couponCode != null)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text(
                  'Applied $_couponCode • Discount \$${_discount.toStringAsFixed(2)}',
                  style: const TextStyle(
                    color: AppColors.success,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            const SizedBox(height: 20),

            // ─── 5. Special Instructions ───
            _buildSectionHeader(
              'Delivery Instructions',
              Icons.edit_note_rounded,
            ),
            const SizedBox(height: 10),
            TextField(
              controller: _noteController,
              maxLines: 2,
              decoration: InputDecoration(
                hintText:
                    'e.g. Leave package at front door, call before arriving...',
                filled: true,
                fillColor: AppColors.surface,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
            const SizedBox(height: 24),

            // ─── 6. Price Calculation Card ───
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(16),
              ),
              child: Column(
                children: [
                  _buildCostRow(
                    'Subtotal',
                    '\$${cart.subtotal.toStringAsFixed(2)}',
                  ),
                  const SizedBox(height: 8),
                  _buildCostRow(
                    'Delivery Fee',
                    deliveryFee > 0
                        ? '\$${deliveryFee.toStringAsFixed(2)}'
                        : 'Free',
                  ),
                  if (_discount > 0) ...[
                    const SizedBox(height: 8),
                    _buildCostRow(
                      'Discount',
                      '-\$${_discount.toStringAsFixed(2)}',
                    ),
                  ],
                  const Divider(height: 24, color: AppColors.divider),
                  _buildCostRow(
                    'Total Amount',
                    '\$${totalAmount.toStringAsFixed(2)}',
                    isTotal: true,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Place Order Button
            GradientButton(
              label:
                  '${appText(language, 'placeOrder')} • \$${totalAmount.toStringAsFixed(2)}',
              isLoading: _isSubmitting,
              onPressed: cart.items.isEmpty
                  ? null
                  : () => _handlePlaceOrder(cart),
            ),
            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionHeader(String title, IconData icon) {
    return Row(
      children: [
        Icon(icon, size: 20, color: AppColors.primary),
        const SizedBox(width: 8),
        Text(
          title,
          style: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w700,
            color: AppColors.textPrimary,
          ),
        ),
      ],
    );
  }

  Widget _buildAddressCard(AsyncValue<List<Address>> addressesAsync) {
    return addressesAsync.when(
      data: (addresses) {
        if (addresses.isEmpty) {
          return Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Column(
              children: [
                const Text(
                  'No saved addresses found. Please add a shipping address.',
                  style: TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 13,
                  ),
                ),
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  onPressed: () => _showAddAddressDialog(),
                  icon: const Icon(Icons.add_location_alt_rounded),
                  label: const Text('Add Shipping Address'),
                ),
              ],
            ),
          );
        }

        final address = _selectedAddress ?? addresses.first;
        return Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppColors.primary.withValues(alpha: 0.3)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Text(
                        address.recipientName,
                        style: const TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
                          color: AppColors.textPrimary,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        address.phone,
                        style: const TextStyle(
                          fontSize: 13,
                          color: AppColors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                  TextButton(
                    onPressed: () => _showAddressSelectionModal(addresses),
                    child: const Text('Change'),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                address.fullAddress ??
                    '${address.addressDetail ?? ''}, ${address.commune ?? ''}, ${address.district ?? ''}, ${address.province ?? ''}',
                style: const TextStyle(
                  fontSize: 13,
                  color: AppColors.textSecondary,
                  height: 1.3,
                ),
              ),
            ],
          ),
        );
      },
      loading: () => const ShimmerLoading(height: 90),
      error: (_, __) => const Text('Error loading address'),
    );
  }

  Widget _buildDeliveryOptions(AsyncValue<List<DeliveryOption>> optionsAsync) {
    return optionsAsync.when(
      data: (options) {
        if (options.isEmpty) {
          return const SizedBox.shrink();
        }
        return Column(
          children: options.map((option) {
            final isSelected = _selectedDeliveryOption?.id == option.id;
            return GestureDetector(
              onTap: () => setState(() => _selectedDeliveryOption = option),
              child: Container(
                margin: const EdgeInsets.only(bottom: 8),
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(
                    color: isSelected ? AppColors.primary : AppColors.divider,
                    width: isSelected ? 1.5 : 1,
                  ),
                ),
                child: Row(
                  children: [
                    Icon(
                      isSelected
                          ? Icons.radio_button_checked
                          : Icons.radio_button_off,
                      color: isSelected
                          ? AppColors.primary
                          : AppColors.textHint,
                      size: 20,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            option.name,
                            style: const TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                              color: AppColors.textPrimary,
                            ),
                          ),
                          if (option.estimatedTime != null)
                            Text(
                              option.estimatedTime!,
                              style: const TextStyle(
                                fontSize: 12,
                                color: AppColors.textSecondary,
                              ),
                            ),
                        ],
                      ),
                    ),
                    Text(
                      option.fee > 0
                          ? '\$${option.fee.toStringAsFixed(2)}'
                          : 'Free',
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: option.fee > 0
                            ? AppColors.textPrimary
                            : AppColors.success,
                      ),
                    ),
                  ],
                ),
              ),
            );
          }).toList(),
        );
      },
      loading: () => const ShimmerLoading(height: 60),
      error: (_, __) => const SizedBox.shrink(),
    );
  }

  Widget _buildPaymentMethods() {
    final methods = [
      {
        'id': 'KHQR',
        'name': 'KHQR (Bakong / Scan QR)',
        'icon': Icons.qr_code_scanner_rounded,
      },
      {
        'id': 'CASH_ON_DELIVERY',
        'name': 'Cash on Delivery (COD)',
        'icon': Icons.payments_outlined,
      },
      {
        'id': 'CARD',
        'name': 'Credit / Debit Card (ABA PayWay)',
        'icon': Icons.credit_card_rounded,
      },
      {
        'id': 'BANK_TRANSFER',
        'name': 'Bank Transfer',
        'icon': Icons.account_balance_rounded,
      },
    ];

    return Column(
      children: methods.map((m) {
        final isSelected = _selectedPaymentMethod == m['id'];
        return GestureDetector(
          onTap: () =>
              setState(() => _selectedPaymentMethod = m['id'] as String),
          child: Container(
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color: isSelected ? AppColors.primary : AppColors.divider,
                width: isSelected ? 1.5 : 1,
              ),
            ),
            child: Row(
              children: [
                Icon(
                  isSelected
                      ? Icons.radio_button_checked
                      : Icons.radio_button_off,
                  color: isSelected ? AppColors.primary : AppColors.textHint,
                  size: 20,
                ),
                const SizedBox(width: 12),
                Icon(m['icon'] as IconData, size: 22, color: AppColors.primary),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    m['name'] as String,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: AppColors.textPrimary,
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildItemsSummary(Cart cart) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        children: cart.items.map((item) {
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: Row(
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: SizedBox(
                    width: 44,
                    height: 44,
                    child: item.imageUrl != null
                        ? CachedNetworkImage(
                            imageUrl: item.imageUrl!,
                            fit: BoxFit.cover,
                            errorWidget: (_, __, ___) =>
                                Container(color: AppColors.surfaceVariant),
                          )
                        : Container(color: AppColors.surfaceVariant),
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
                      Text(
                        'Qty: ${item.quantity}',
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppColors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
                Text(
                  '\$${item.lineTotal.toStringAsFixed(2)}',
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
          );
        }).toList(),
      ),
    );
  }

  Widget _buildCostRow(String title, String amount, {bool isTotal = false}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          title,
          style: TextStyle(
            fontSize: isTotal ? 16 : 14,
            fontWeight: isTotal ? FontWeight.w700 : FontWeight.w400,
            color: isTotal ? AppColors.textPrimary : AppColors.textSecondary,
          ),
        ),
        Text(
          amount,
          style: TextStyle(
            fontSize: isTotal ? 20 : 14,
            fontWeight: isTotal ? FontWeight.w800 : FontWeight.w600,
            color: isTotal ? AppColors.primary : AppColors.textPrimary,
          ),
        ),
      ],
    );
  }

  Future<void> _handlePlaceOrder(Cart cart) async {
    if (_selectedAddress == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please select or add a shipping address'),
          backgroundColor: AppColors.error,
        ),
      );
      return;
    }

    setState(() => _isSubmitting = true);
    final dio = ref.read(dioProvider);

    try {
      final payload = {
        'addressId': _selectedAddress!.id,
        'paymentMethod': _selectedPaymentMethod,
        'deliveryOptionId': _selectedDeliveryOption?.id,
        'contactName': _selectedAddress!.recipientName,
        'contactPhone': _selectedAddress!.phone,
        'note': _noteController.text.trim().isNotEmpty
            ? _noteController.text.trim()
            : null,
        'couponCode': _couponCode,
      };

      final response = await dio.post(ApiConfig.checkout, data: payload);
      final orderData = response.data['data'];
      final order = Order.fromJson(orderData);

      // Refresh cart and orders
      await ref.read(cartProvider.notifier).fetchCart();
      ref.invalidate(ordersProvider);

      if (!mounted) return;

      if (_selectedPaymentMethod == 'KHQR') {
        // Start payment to get KHQR
        _startKhqrPayment(order.id, order.totalAmount);
      } else {
        // Navigate directly to Order Detail
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Order placed successfully!'),
            backgroundColor: AppColors.success,
          ),
        );
        context.go('/orders/${order.id}');
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to place order: $e'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  Future<void> _applyCoupon() async {
    final code = _couponController.text.trim();
    if (code.isEmpty) return;
    setState(() => _isApplyingCoupon = true);
    try {
      final response = await ref
          .read(dioProvider)
          .post(ApiConfig.couponValidate, data: {'code': code});
      final data = response.data['data'] as Map<String, dynamic>;
      if (!mounted) return;
      setState(() {
        _couponCode = data['code'] as String?;
        _discount = (data['discount'] as num?)?.toDouble() ?? 0;
      });
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Coupon applied')));
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Coupon could not be applied: $error'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isApplyingCoupon = false);
    }
  }

  Future<void> _startKhqrPayment(int orderId, double amount) async {
    final dio = ref.read(dioProvider);
    try {
      final payResponse = await dio.post(
        '${ApiConfig.payments}/orders/$orderId',
      );
      final payment = PaymentData.fromJson(payResponse.data['data']);

      if (mounted) {
        _showKhqrDialog(orderId, payment);
      }
    } catch (e) {
      if (mounted) {
        context.go('/orders/$orderId');
      }
    }
  }

  void _showKhqrDialog(int orderId, PaymentData payment) {
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
          if (context.mounted) {
            Navigator.of(context).pop();
            context.go('/orders/$orderId');
          }
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

              // QR display
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
                'Scan with any Cambodian banking app that supports KHQR (ABA, ACLEDA, Wing, etc.)',
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
                label: appText(ref.watch(appLanguageProvider), 'orderDetails'),
                onPressed: () {
                  Navigator.pop(ctx);
                  context.go('/orders/$orderId');
                },
              ),
            ],
          ),
        );
      },
    ).whenComplete(() => pollTimer?.cancel());
  }

  void _showAddressSelectionModal(List<Address> addresses) {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        return Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Select Shipping Address',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                  ),
                  TextButton.icon(
                    onPressed: () {
                      Navigator.pop(ctx);
                      _showAddAddressDialog();
                    },
                    icon: const Icon(Icons.add, size: 16),
                    label: const Text('Add New'),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              ...addresses.map((addr) {
                final isSelected = _selectedAddress?.id == addr.id;
                return ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(
                    isSelected
                        ? Icons.check_circle_rounded
                        : Icons.radio_button_off,
                    color: isSelected ? AppColors.primary : AppColors.textHint,
                  ),
                  title: Text(
                    '${addr.recipientName} (${addr.phone})',
                    style: const TextStyle(
                      fontWeight: FontWeight.w600,
                      fontSize: 14,
                    ),
                  ),
                  subtitle: Text(
                    addr.fullAddress ??
                        '${addr.addressDetail}, ${addr.province}',
                    style: const TextStyle(fontSize: 12),
                  ),
                  onTap: () {
                    setState(() => _selectedAddress = addr);
                    Navigator.pop(ctx);
                  },
                );
              }),
            ],
          ),
        );
      },
    );
  }

  void _showAddAddressDialog() {
    final nameCtrl = TextEditingController();
    final phoneCtrl = TextEditingController();
    final provinceCtrl = TextEditingController();
    final districtCtrl = TextEditingController();
    final detailCtrl = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('New Shipping Address'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: nameCtrl,
                decoration: const InputDecoration(
                  labelText: 'Recipient Name *',
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: phoneCtrl,
                keyboardType: TextInputType.phone,
                decoration: const InputDecoration(labelText: 'Phone Number *'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: provinceCtrl,
                decoration: const InputDecoration(
                  labelText: 'Province / City *',
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: districtCtrl,
                decoration: const InputDecoration(labelText: 'District / Khan'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: detailCtrl,
                decoration: const InputDecoration(
                  labelText: 'Street & House Number',
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () async {
              if (nameCtrl.text.isEmpty ||
                  phoneCtrl.text.isEmpty ||
                  provinceCtrl.text.isEmpty) {
                return;
              }
              final dio = ref.read(dioProvider);
              try {
                final res = await dio.post(
                  ApiConfig.addresses,
                  data: {
                    'recipientName': nameCtrl.text.trim(),
                    'phone': phoneCtrl.text.trim(),
                    'province': provinceCtrl.text.trim(),
                    'district': districtCtrl.text.trim(),
                    'addressDetail': detailCtrl.text.trim(),
                    'isDefault': true,
                  },
                );
                final newAddr = Address.fromJson(res.data['data']);
                ref.invalidate(addressesProvider);
                setState(() => _selectedAddress = newAddr);
                if (ctx.mounted) Navigator.pop(ctx);
              } catch (_) {}
            },
            child: const Text('Save Address'),
          ),
        ],
      ),
    );
  }
}
