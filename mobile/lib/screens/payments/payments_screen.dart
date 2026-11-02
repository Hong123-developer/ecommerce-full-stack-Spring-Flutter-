import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../config/theme.dart';
import '../../models/models.dart';
import '../../providers/providers.dart';
import '../../widgets/common_widgets.dart';

class PaymentsScreen extends ConsumerWidget {
  const PaymentsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final paymentsAsync = ref.watch(paymentsProvider);
    final language = ref.watch(appLanguageProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Payments'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            onPressed: () => ref.refresh(paymentsProvider),
          ),
        ],
      ),
      body: paymentsAsync.when(
        loading: () => const Center(
          child: CircularProgressIndicator(color: AppColors.primary),
        ),
        error: (error, _) => EmptyState(
          icon: Icons.error_outline_rounded,
          title: 'Failed to load payments',
          subtitle: error.toString(),
          actionLabel: appText(language, 'retry'),
          onAction: () => ref.refresh(paymentsProvider),
        ),
        data: (payments) => payments.isEmpty
            ? const EmptyState(
                icon: Icons.account_balance_wallet_outlined,
                title: 'No payments yet',
                subtitle: 'Your payment history will appear here.',
              )
            : RefreshIndicator(
                onRefresh: () async => ref.refresh(paymentsProvider),
                child: ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: payments.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 12),
                  itemBuilder: (context, index) =>
                      _PaymentCard(payment: payments[index]),
                ),
              ),
      ),
    );
  }
}

class _PaymentCard extends StatelessWidget {
  final PaymentData payment;

  const _PaymentCard({required this.payment});

  @override
  Widget build(BuildContext context) {
    final paid = payment.paymentStatus.toUpperCase() == 'PAID';
    return InkWell(
      onTap: payment.orderId == null
          ? null
          : () => context.push('/orders/${payment.orderId}'),
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(16),
        ),
        child: Row(
          children: [
            Icon(
              paid
                  ? Icons.check_circle_outline_rounded
                  : Icons.schedule_rounded,
              color: paid ? AppColors.success : AppColors.warning,
              size: 30,
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    payment.orderNumber ?? 'Payment #${payment.id}',
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    payment.paymentMethod.replaceAll('_', ' '),
                    style: const TextStyle(
                      color: AppColors.textSecondary,
                      fontSize: 13,
                    ),
                  ),
                ],
              ),
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  '${payment.currency} ${payment.amount.toStringAsFixed(2)}',
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 4),
                Text(
                  payment.paymentStatus,
                  style: TextStyle(
                    color: paid ? AppColors.success : AppColors.warning,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
