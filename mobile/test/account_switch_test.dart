import 'package:dio/dio.dart';
import 'package:ecomerce_shop/config/api_config.dart';
import 'package:ecomerce_shop/models/models.dart';
import 'package:ecomerce_shop/providers/providers.dart';
import 'package:ecomerce_shop/services/api_client.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// Auth without secure storage or network: tests flip the session directly.
class _FakeAuth extends AuthNotifier {
  @override
  AuthState build() => const AuthState(status: AuthStatus.unauthenticated);

  void signInAs(int id) {
    state = AuthState(
      status: AuthStatus.authenticated,
      user: User(
        id: id,
        fullName: 'Customer $id',
        email: 'customer$id@example.com',
        provider: 'LOCAL',
        emailVerified: true,
        enabled: true,
        roles: const ['CUSTOMER'],
      ),
    );
  }

  void signOutLocally() {
    state = const AuthState(status: AuthStatus.unauthenticated);
  }
}

/// A backend that answers with data belonging to whoever is signed in.
Dio _fakeBackend(int? Function() signedInCustomer) {
  final dio = Dio();
  dio.interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) {
        final customer = signedInCustomer();
        final Object? data = switch (options.path) {
          ApiConfig.orders => {
            'content': [
              {'id': customer, 'orderNumber': 'ORD-$customer'},
            ],
          },
          ApiConfig.cart => {
            'id': customer,
            'items': const [],
            'itemCount': customer,
            'totalQuantity': customer,
            'subtotal': 0,
            'checkoutReady': false,
          },
          _ => null,
        };
        handler.resolve(
          Response(
            requestOptions: options,
            statusCode: 200,
            data: {'data': data},
          ),
        );
      },
    ),
  );
  return dio;
}

Future<void> _settle() =>
    Future<void>.delayed(const Duration(milliseconds: 10));

void main() {
  late ProviderContainer container;
  late _FakeAuth auth;
  int? session;

  setUp(() {
    session = null;
    container = ProviderContainer(
      overrides: [
        authProvider.overrideWith(_FakeAuth.new),
        dioProvider.overrideWithValue(_fakeBackend(() => session)),
      ],
    );
    auth = container.read(authProvider.notifier) as _FakeAuth;
  });

  tearDown(() => container.dispose());

  void signInAs(int id) {
    session = id;
    auth.signInAs(id);
  }

  test('a new customer does not see the previous customer\'s orders', () async {
    signInAs(1);
    // Keep the provider alive the way the Orders screen does.
    container.listen(ordersProvider, (_, _) {});
    expect(
      (await container.read(ordersProvider.future)).single.orderNumber,
      'ORD-1',
    );

    auth.signOutLocally();
    signInAs(2);
    expect(
      (await container.read(ordersProvider.future)).single.orderNumber,
      'ORD-2',
    );
  });

  test('the cart is reloaded for the new customer', () async {
    signInAs(1);
    container.listen(cartProvider, (_, _) {});
    await _settle();
    expect(container.read(cartProvider).totalQuantity, 1);

    auth.signOutLocally();
    await _settle();
    expect(container.read(cartProvider).totalQuantity, 0);

    signInAs(2);
    await _settle();
    expect(container.read(cartProvider).totalQuantity, 2);
  });

  test('wishlist hearts are cleared when the customer changes', () async {
    signInAs(1);
    container.read(wishlistProvider.notifier).seed(5, true);
    expect(container.read(wishlistProvider)[5], isTrue);

    signInAs(2);
    expect(container.read(wishlistProvider)[5], isNull);
  });
}
