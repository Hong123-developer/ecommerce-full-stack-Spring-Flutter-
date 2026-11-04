import 'package:ecomerce_shop/models/models.dart';
import 'package:ecomerce_shop/screens/product/variant_matrix.dart';
import 'package:flutter_test/flutter_test.dart';

ProductVariant _variant(
  int id,
  List<String> options, {
  int stock = 10,
  bool enabled = true,
  String? imageUrl,
}) {
  return ProductVariant(
    id: id,
    options: options,
    label: options.join(' / '),
    price: 10,
    originalPrice: 10,
    discountPercent: 0,
    stockQuantity: stock,
    inStock: stock > 0,
    imageUrl: imageUrl,
    enabled: enabled,
  );
}

VariantMatrix _matrix(List<String> optionNames, List<ProductVariant> variants) {
  return VariantMatrix.of(
    Product(
      id: 1,
      name: 'Watch',
      price: 10,
      originalPrice: 10,
      discountPercent: 0,
      stockQuantity: 10,
      inStock: true,
      galleryUrls: const [],
      optionNames: optionNames,
      variants: variants,
      enabled: true,
      rating: 0,
      reviewCount: 0,
    ),
  );
}

void main() {
  group('showsImages', () {
    test('only the group that picks the photo gets image tiles', () {
      final matrix = _matrix(
        ['Color', 'Size'],
        [
          _variant(1, ['Black', 'One Size'], imageUrl: 'black.png'),
          _variant(2, ['Brown', 'One Size'], imageUrl: 'brown.png'),
        ],
      );
      expect(matrix.showsImages(0), isTrue);
      expect(matrix.showsImages(1), isFalse);
    });

    test('sizes sharing one photo stay text chips', () {
      final matrix = _matrix(
        ['Size'],
        [
          _variant(1, ['S'], imageUrl: 'shirt.png'),
          _variant(2, ['M'], imageUrl: 'shirt.png'),
          _variant(3, ['L'], imageUrl: 'shirt.png'),
        ],
      );
      expect(matrix.showsImages(0), isFalse);
    });

    test('no images means text chips', () {
      final matrix = _matrix(
        ['Color'],
        [
          _variant(1, ['Black']),
          _variant(2, ['Brown']),
        ],
      );
      expect(matrix.showsImages(0), isFalse);
    });
  });

  group('isAvailable', () {
    // [Black,S], [Black,M], [Brown,M] with Black/S selected.
    final matrix = _matrix(
      ['Color', 'Size'],
      [
        _variant(1, ['Black', 'S']),
        _variant(2, ['Black', 'M']),
        _variant(3, ['Brown', 'M']),
      ],
    );

    test('a value reachable by jumping to another variant is available', () {
      expect(matrix.isAvailable(0, 'Brown', ['Black', 'S']), isTrue);
      expect(matrix.select(0, 'Brown', ['Black', 'S']), ['Brown', 'M']);
    });

    test('an existing but sold-out combination is unavailable', () {
      final soldOut = _matrix(
        ['Color', 'Size'],
        [
          _variant(1, ['Black', 'S']),
          _variant(2, ['Brown', 'S'], stock: 0),
          _variant(3, ['Brown', 'M']),
        ],
      );
      expect(soldOut.isAvailable(0, 'Brown', ['Black', 'S']), isFalse);
      // Tapping it still selects that combination, which shows Out of Stock.
      expect(soldOut.select(0, 'Brown', ['Black', 'S']), ['Brown', 'S']);
    });

    test('a value with no buyable variant at all is unavailable', () {
      final matrix = _matrix(
        ['Color'],
        [
          _variant(1, ['Black']),
          _variant(2, ['Silver'], stock: 0),
          _variant(3, ['Gold'], enabled: false),
        ],
      );
      expect(matrix.isAvailable(0, 'Black', ['Black']), isTrue);
      expect(matrix.isAvailable(0, 'Silver', ['Black']), isFalse);
      expect(matrix.isAvailable(0, 'Gold', ['Black']), isFalse);
    });
  });

  test('initial selection and match prefer a buyable variant', () {
    final matrix = _matrix(
      ['Color'],
      [
        _variant(1, ['Black'], stock: 0),
        _variant(2, ['Black']),
        _variant(3, ['Brown']),
      ],
    );
    expect(matrix.initialSelection(), ['Black']);
    expect(matrix.match(['Black'])!.id, 2);
  });

  test('variants not aligned with optionNames fall back to labels', () {
    final matrix = _matrix(const [], [
      _variant(1, ['Red', 'S']),
      _variant(2, ['Blue', 'M']),
    ]);
    expect(matrix.groupCount, 1);
    expect(matrix.valuesFor(0), ['Red / S', 'Blue / M']);
  });
}
