import '../../models/models.dart';

/// A product's variants laid out as option groups (e.g. Color × Size).
///
/// `values[i]` holds variant i's option values in the order of `names`.
/// Products whose variants don't line up with `optionNames` fall back to a
/// single unnamed group that lists every variant by its label.
class VariantMatrix {
  final List<String> names;
  final List<ProductVariant> variants;
  final List<List<String>> values;

  VariantMatrix._(this.names, this.variants, this.values);

  factory VariantMatrix.of(Product product) {
    final names = product.optionNames;
    final aligned =
        names.isNotEmpty &&
        product.variants.every((v) => v.options.length == names.length);
    if (aligned) {
      return VariantMatrix._(names, product.variants, [
        for (final v in product.variants) v.options,
      ]);
    }
    return VariantMatrix._(
      const [''],
      product.variants,
      [
        for (final v in product.variants)
          [
            v.label.isNotEmpty
                ? v.label
                : v.options.isNotEmpty
                ? v.options.join(' / ')
                : '#${v.id}',
          ],
      ],
    );
  }

  int get groupCount => names.length;

  bool _isBuyable(int i) => variants[i].enabled && variants[i].inStock;

  /// Whether variant [i] has the [selection] values, ignoring group [except].
  bool _matches(int i, List<String> selection, {int? except}) {
    for (var g = 0; g < groupCount; g++) {
      if (g != except && values[i][g] != selection[g]) return false;
    }
    return true;
  }

  int _indexOf(List<String> selection) {
    var first = -1;
    for (var i = 0; i < variants.length; i++) {
      if (!_matches(i, selection)) continue;
      if (_isBuyable(i)) return i;
      if (first < 0) first = i;
    }
    return first;
  }

  List<String> valuesFor(int group) {
    final seen = <String>{};
    return [
      for (final row in values)
        if (seen.add(row[group])) row[group],
    ];
  }

  /// The variant with exactly these values, preferring a buyable one.
  ProductVariant? match(List<String> selection) {
    final index = _indexOf(selection);
    return index < 0 ? null : variants[index];
  }

  List<String> initialSelection() {
    final index = List.generate(
      variants.length,
      (i) => i,
    ).firstWhere(_isBuyable, orElse: () => 0);
    return [...values[index]];
  }

  /// Whether tapping [value] leads to something purchasable. When the exact
  /// combination with the other current choices exists, its stock decides;
  /// otherwise [select] jumps to another variant, so any buyable one counts.
  bool isAvailable(int group, String value, List<String> selection) {
    final exact = _indexOf([...selection]..[group] = value);
    if (exact >= 0) return _isBuyable(exact);
    for (var i = 0; i < variants.length; i++) {
      if (values[i][group] == value && _isBuyable(i)) return true;
    }
    return false;
  }

  /// The selection after choosing [value] for [group]. Keeps the other
  /// choices when that combination exists, otherwise jumps to the closest
  /// variant carrying [value], preferring ones in stock.
  List<String> select(int group, String value, List<String> current) {
    final wanted = [...current]..[group] = value;
    if (match(wanted) != null) return wanted;

    List<String>? best;
    var bestScore = -1;
    for (var i = 0; i < variants.length; i++) {
      if (values[i][group] != value) continue;
      var score = _isBuyable(i) ? 100 : 0;
      for (var g = 0; g < groupCount; g++) {
        if (values[i][g] == current[g]) score++;
      }
      if (score > bestScore) {
        bestScore = score;
        best = values[i];
      }
    }
    return [...(best ?? wanted)];
  }

  /// Whether [group] is the one that picks the photo (typically Color): each
  /// of its values has exactly one image and they are not all the same.
  /// Other groups (e.g. Size) are shown as text chips.
  bool showsImages(int group) {
    final imagesByValue = <String, Set<String>>{};
    for (var i = 0; i < variants.length; i++) {
      final urls = imagesByValue.putIfAbsent(values[i][group], () => {});
      final url = variants[i].imageUrl;
      if (url != null && url.isNotEmpty) urls.add(url);
    }
    final images = imagesByValue.values;
    return images.every((urls) => urls.length == 1) &&
        images.map((urls) => urls.single).toSet().length > 1;
  }

  /// A photo for [value], preferring the variant that matches the rest of
  /// the current selection.
  String? imageFor(int group, String value, List<String> selection) {
    String? fallback;
    for (var i = 0; i < variants.length; i++) {
      final url = variants[i].imageUrl;
      if (values[i][group] != value || url == null || url.isEmpty) continue;
      if (_matches(i, selection, except: group)) return url;
      fallback ??= url;
    }
    return fallback;
  }
}
