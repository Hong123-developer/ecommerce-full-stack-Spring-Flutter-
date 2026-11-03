// ──────────────────────────────────────────────
// Data Models matching Java DTOs
// ──────────────────────────────────────────────

class User {
  final int id;
  final String fullName;
  final String email;
  final String? phone;
  final String? profileImage;
  final String provider;
  final bool emailVerified;
  final bool enabled;
  final List<String> roles;
  final String? createdAt;

  User({
    required this.id,
    required this.fullName,
    required this.email,
    this.phone,
    this.profileImage,
    required this.provider,
    required this.emailVerified,
    required this.enabled,
    required this.roles,
    this.createdAt,
  });

  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id'] ?? 0,
      fullName: json['fullName'] ?? '',
      email: json['email'] ?? '',
      phone: json['phone'],
      profileImage: json['profileImage'],
      provider: json['provider'] ?? 'LOCAL',
      emailVerified: json['emailVerified'] ?? false,
      enabled: json['enabled'] ?? true,
      roles: (json['roles'] as List?)?.map((e) => e.toString()).toList() ?? [],
      createdAt: json['createdAt'],
    );
  }
}

class AuthData {
  final String accessToken;
  final String refreshToken;
  final String tokenType;
  final int expiresIn;
  final User user;

  AuthData({
    required this.accessToken,
    required this.refreshToken,
    required this.tokenType,
    required this.expiresIn,
    required this.user,
  });

  factory AuthData.fromJson(Map<String, dynamic> json) {
    return AuthData(
      accessToken: json['accessToken'] ?? '',
      refreshToken: json['refreshToken'] ?? '',
      tokenType: json['tokenType'] ?? 'Bearer',
      expiresIn: json['expiresIn'] ?? 0,
      user: User.fromJson(json['user'] ?? {}),
    );
  }
}

class Category {
  final int id;
  final String name;
  final String? description;
  final String? imageUrl;
  final bool enabled;
  final String? createdAt;

  Category({
    required this.id,
    required this.name,
    this.description,
    this.imageUrl,
    required this.enabled,
    this.createdAt,
  });

  factory Category.fromJson(Map<String, dynamic> json) {
    return Category(
      id: json['id'] ?? 0,
      name: json['name'] ?? '',
      description: json['description'],
      imageUrl: json['imageUrl'],
      enabled: json['enabled'] ?? true,
      createdAt: json['createdAt'],
    );
  }
}

class ProductVariant {
  final int id;
  final List<String> options;
  final String label;
  final String? sku;
  final double price;
  final double originalPrice;
  final int discountPercent;
  final int stockQuantity;
  final bool inStock;
  final String? imageUrl;
  final bool enabled;

  ProductVariant({
    required this.id,
    required this.options,
    required this.label,
    this.sku,
    required this.price,
    required this.originalPrice,
    required this.discountPercent,
    required this.stockQuantity,
    required this.inStock,
    this.imageUrl,
    required this.enabled,
  });

  factory ProductVariant.fromJson(Map<String, dynamic> json) {
    return ProductVariant(
      id: json['id'] ?? 0,
      options:
          (json['options'] as List?)?.map((e) => e.toString()).toList() ?? [],
      label: json['label'] ?? '',
      sku: json['sku'],
      price: (json['price'] ?? 0).toDouble(),
      originalPrice: (json['originalPrice'] ?? json['price'] ?? 0).toDouble(),
      discountPercent: json['discountPercent'] ?? 0,
      stockQuantity: json['stockQuantity'] ?? 0,
      inStock: json['inStock'] ?? false,
      imageUrl: json['imageUrl'],
      enabled: json['enabled'] ?? true,
    );
  }
}

class Product {
  final int id;
  final int? categoryId;
  final String? categoryName;
  final String name;
  final String? description;
  final String? sku;
  final double price;
  final double originalPrice;
  final int discountPercent;
  final int stockQuantity;
  final bool inStock;
  final String? imageUrl;
  final List<String> galleryUrls;
  final List<String> optionNames;
  final List<ProductVariant> variants;
  final bool enabled;
  final double rating;
  final int reviewCount;
  final double? shopRating;
  final String? shopRatingLabel;
  final int likeCount;
  final bool liked;
  final String? createdAt;

  Product({
    required this.id,
    this.categoryId,
    this.categoryName,
    required this.name,
    this.description,
    this.sku,
    required this.price,
    required this.originalPrice,
    required this.discountPercent,
    required this.stockQuantity,
    required this.inStock,
    this.imageUrl,
    required this.galleryUrls,
    required this.optionNames,
    required this.variants,
    required this.enabled,
    required this.rating,
    required this.reviewCount,
    this.shopRating,
    this.shopRatingLabel,
    this.likeCount = 0,
    this.liked = false,
    this.createdAt,
  });

  Product copyWith({bool? liked, int? likeCount}) {
    return Product(
      id: id,
      categoryId: categoryId,
      categoryName: categoryName,
      name: name,
      description: description,
      sku: sku,
      price: price,
      originalPrice: originalPrice,
      discountPercent: discountPercent,
      stockQuantity: stockQuantity,
      inStock: inStock,
      imageUrl: imageUrl,
      galleryUrls: galleryUrls,
      optionNames: optionNames,
      variants: variants,
      enabled: enabled,
      rating: rating,
      reviewCount: reviewCount,
      shopRating: shopRating,
      shopRatingLabel: shopRatingLabel,
      likeCount: likeCount ?? this.likeCount,
      liked: liked ?? this.liked,
      createdAt: createdAt,
    );
  }

  factory Product.fromJson(Map<String, dynamic> json) {
    return Product(
      id: json['id'] ?? 0,
      categoryId: json['categoryId'],
      categoryName: json['categoryName'],
      name: json['name'] ?? '',
      description: json['description'],
      sku: json['sku'],
      price: (json['price'] ?? 0).toDouble(),
      originalPrice: (json['originalPrice'] ?? json['price'] ?? 0).toDouble(),
      discountPercent: json['discountPercent'] ?? 0,
      stockQuantity: json['stockQuantity'] ?? 0,
      inStock: json['inStock'] ?? false,
      imageUrl: json['imageUrl'],
      galleryUrls:
          (json['galleryUrls'] as List?)?.map((e) => e.toString()).toList() ??
          [],
      optionNames:
          (json['optionNames'] as List?)?.map((e) => e.toString()).toList() ??
          [],
      variants:
          (json['variants'] as List?)
              ?.map((e) => ProductVariant.fromJson(e))
              .toList() ??
          [],
      enabled: json['enabled'] ?? true,
      rating: (json['rating'] ?? 0).toDouble(),
      reviewCount: json['reviewCount'] ?? 0,
      shopRating: json['shopRating']?.toDouble(),
      shopRatingLabel: json['shopRatingLabel'],
      likeCount: json['likeCount'] ?? 0,
      liked: json['liked'] ?? false,
      createdAt: json['createdAt'],
    );
  }
}

class CartItem {
  final int id;
  final int productId;
  final String productName;
  final int? variantId;
  final String? variantLabel;
  final String? sku;
  final String? imageUrl;
  final double unitPrice;
  final int quantity;
  final double lineTotal;
  final bool available;
  final int? availableStock;

  CartItem({
    required this.id,
    required this.productId,
    required this.productName,
    this.variantId,
    this.variantLabel,
    this.sku,
    this.imageUrl,
    required this.unitPrice,
    required this.quantity,
    required this.lineTotal,
    required this.available,
    this.availableStock,
  });

  factory CartItem.fromJson(Map<String, dynamic> json) {
    return CartItem(
      id: json['id'] ?? 0,
      productId: json['productId'] ?? 0,
      productName: json['productName'] ?? '',
      variantId: json['variantId'],
      variantLabel: json['variantLabel'],
      sku: json['sku'],
      imageUrl: json['imageUrl'],
      unitPrice: (json['unitPrice'] ?? 0).toDouble(),
      quantity: json['quantity'] ?? 0,
      lineTotal: (json['lineTotal'] ?? 0).toDouble(),
      available: json['available'] ?? true,
      availableStock: json['availableStock'],
    );
  }
}

class Cart {
  final int id;
  final List<CartItem> items;
  final int itemCount;
  final int totalQuantity;
  final double subtotal;
  final bool checkoutReady;

  Cart({
    required this.id,
    required this.items,
    required this.itemCount,
    required this.totalQuantity,
    required this.subtotal,
    required this.checkoutReady,
  });

  factory Cart.fromJson(Map<String, dynamic> json) {
    return Cart(
      id: json['id'] ?? 0,
      items:
          (json['items'] as List?)?.map((e) => CartItem.fromJson(e)).toList() ??
          [],
      itemCount: json['itemCount'] ?? 0,
      totalQuantity: json['totalQuantity'] ?? 0,
      subtotal: (json['subtotal'] ?? 0).toDouble(),
      checkoutReady: json['checkoutReady'] ?? false,
    );
  }

  static Cart empty() => Cart(
    id: 0,
    items: [],
    itemCount: 0,
    totalQuantity: 0,
    subtotal: 0,
    checkoutReady: false,
  );
}

class OrderItem {
  final int id;
  final int? productId;
  final String productName;
  final int? variantId;
  final String? variantLabel;
  final String? sku;
  final String? imageUrl;
  final int quantity;
  final double unitPrice;
  final double subtotal;

  OrderItem({
    required this.id,
    this.productId,
    required this.productName,
    this.variantId,
    this.variantLabel,
    this.sku,
    this.imageUrl,
    required this.quantity,
    required this.unitPrice,
    required this.subtotal,
  });

  factory OrderItem.fromJson(Map<String, dynamic> json) {
    return OrderItem(
      id: json['id'] ?? 0,
      productId: json['productId'],
      productName: json['productName'] ?? '',
      variantId: json['variantId'],
      variantLabel: json['variantLabel'],
      sku: json['sku'],
      imageUrl: json['imageUrl'],
      quantity: json['quantity'] ?? 0,
      unitPrice: (json['unitPrice'] ?? 0).toDouble(),
      subtotal: (json['subtotal'] ?? 0).toDouble(),
    );
  }
}

class Order {
  final int id;
  final String orderNumber;
  final String status;
  final double subtotal;
  final double? deliveryFee;
  final double? discount;
  final double totalAmount;
  final String? shippingAddress;
  final double? latitude;
  final double? longitude;
  final int? deliveryOptionId;
  final String? deliveryMethodName;
  final String? deliveryType;
  final String? contactName;
  final String? contactPhone;
  final String? note;
  final List<OrderItem> items;
  final String paymentMethod;
  final String paymentStatus;
  final int? paymentId;
  final bool cancellable;
  final String? createdAt;
  final String? updatedAt;

  Order({
    required this.id,
    required this.orderNumber,
    required this.status,
    required this.subtotal,
    this.deliveryFee,
    this.discount,
    required this.totalAmount,
    this.shippingAddress,
    this.latitude,
    this.longitude,
    this.deliveryOptionId,
    this.deliveryMethodName,
    this.deliveryType,
    this.contactName,
    this.contactPhone,
    this.note,
    required this.items,
    required this.paymentMethod,
    required this.paymentStatus,
    this.paymentId,
    required this.cancellable,
    this.createdAt,
    this.updatedAt,
  });

  factory Order.fromJson(Map<String, dynamic> json) {
    return Order(
      id: json['id'] ?? 0,
      orderNumber: json['orderNumber'] ?? '',
      status: json['status'] ?? '',
      subtotal: (json['subtotal'] ?? 0).toDouble(),
      deliveryFee: json['deliveryFee']?.toDouble(),
      discount: json['discount']?.toDouble(),
      totalAmount: (json['totalAmount'] ?? 0).toDouble(),
      shippingAddress: json['shippingAddress'],
      latitude: json['latitude']?.toDouble(),
      longitude: json['longitude']?.toDouble(),
      deliveryOptionId: json['deliveryOptionId'],
      deliveryMethodName: json['deliveryMethodName'],
      deliveryType: json['deliveryType'],
      contactName: json['contactName'],
      contactPhone: json['contactPhone'],
      note: json['note'],
      items:
          (json['items'] as List?)
              ?.map((e) => OrderItem.fromJson(e))
              .toList() ??
          [],
      paymentMethod: json['paymentMethod'] ?? '',
      paymentStatus: json['paymentStatus'] ?? '',
      paymentId: json['paymentId'],
      cancellable: json['cancellable'] ?? false,
      createdAt: json['createdAt'],
      updatedAt: json['updatedAt'],
    );
  }
}

class OrderTrackingEvent {
  final String status;
  final String? note;
  final String? createdAt;

  OrderTrackingEvent({required this.status, this.note, this.createdAt});

  factory OrderTrackingEvent.fromJson(Map<String, dynamic> json) {
    return OrderTrackingEvent(
      status: json['status'] ?? '',
      note: json['note'],
      createdAt: json['createdAt'],
    );
  }
}

class OrderTracking {
  final int orderId;
  final String orderNumber;
  final String currentStatus;
  final List<OrderTrackingEvent> events;

  OrderTracking({
    required this.orderId,
    required this.orderNumber,
    required this.currentStatus,
    required this.events,
  });

  factory OrderTracking.fromJson(Map<String, dynamic> json) {
    return OrderTracking(
      orderId: json['orderId'] ?? 0,
      orderNumber: json['orderNumber'] ?? '',
      currentStatus: json['currentStatus'] ?? '',
      events:
          (json['events'] as List?)
              ?.map((e) => OrderTrackingEvent.fromJson(e))
              .toList() ??
          [],
    );
  }
}

class InvoiceLine {
  final String productName;
  final String? variantLabel;
  final String? sku;
  final int quantity;
  final double unitPrice;
  final double subtotal;

  InvoiceLine({
    required this.productName,
    this.variantLabel,
    this.sku,
    required this.quantity,
    required this.unitPrice,
    required this.subtotal,
  });

  factory InvoiceLine.fromJson(Map<String, dynamic> json) {
    return InvoiceLine(
      productName: json['productName'] ?? '',
      variantLabel: json['variantLabel'],
      sku: json['sku'],
      quantity: json['quantity'] ?? 0,
      unitPrice: (json['unitPrice'] ?? 0).toDouble(),
      subtotal: (json['subtotal'] ?? 0).toDouble(),
    );
  }
}

class Invoice {
  final String invoiceNumber;
  final String orderNumber;
  final String? issuedAt;
  final String? shippingAddress;
  final String? contactName;
  final String? contactPhone;
  final double subtotal;
  final double deliveryFee;
  final double discount;
  final double totalAmount;
  final String? couponCode;
  final String? paymentMethod;
  final String? paymentStatus;
  final List<InvoiceLine> items;

  Invoice({
    required this.invoiceNumber,
    required this.orderNumber,
    this.issuedAt,
    this.shippingAddress,
    this.contactName,
    this.contactPhone,
    required this.subtotal,
    required this.deliveryFee,
    required this.discount,
    required this.totalAmount,
    this.couponCode,
    this.paymentMethod,
    this.paymentStatus,
    required this.items,
  });

  factory Invoice.fromJson(Map<String, dynamic> json) {
    return Invoice(
      invoiceNumber: json['invoiceNumber'] ?? '',
      orderNumber: json['orderNumber'] ?? '',
      issuedAt: json['issuedAt'],
      shippingAddress: json['shippingAddress'],
      contactName: json['contactName'],
      contactPhone: json['contactPhone'],
      subtotal: (json['subtotal'] ?? 0).toDouble(),
      deliveryFee: (json['deliveryFee'] ?? 0).toDouble(),
      discount: (json['discount'] ?? 0).toDouble(),
      totalAmount: (json['totalAmount'] ?? 0).toDouble(),
      couponCode: json['couponCode'],
      paymentMethod: json['paymentMethod'],
      paymentStatus: json['paymentStatus'],
      items:
          (json['items'] as List?)
              ?.map((e) => InvoiceLine.fromJson(e))
              .toList() ??
          [],
    );
  }
}

class Address {
  final int id;
  final String recipientName;
  final String phone;
  final String? province;
  final String? district;
  final String? commune;
  final String? village;
  final String? addressDetail;
  final double? latitude;
  final double? longitude;
  final bool isDefault;
  final String? fullAddress;

  Address({
    required this.id,
    required this.recipientName,
    required this.phone,
    this.province,
    this.district,
    this.commune,
    this.village,
    this.addressDetail,
    this.latitude,
    this.longitude,
    required this.isDefault,
    this.fullAddress,
  });

  factory Address.fromJson(Map<String, dynamic> json) {
    return Address(
      id: json['id'] ?? 0,
      recipientName: json['recipientName'] ?? '',
      phone: json['phone'] ?? '',
      province: json['province'],
      district: json['district'],
      commune: json['commune'],
      village: json['village'],
      addressDetail: json['addressDetail'],
      latitude: json['latitude']?.toDouble(),
      longitude: json['longitude']?.toDouble(),
      isDefault: json['isDefault'] ?? false,
      fullAddress: json['fullAddress'],
    );
  }
}

class HeroSlide {
  final int id;
  final String? eyebrow;
  final String? title;
  final String? subtitle;
  final String? imageUrl;
  final String? imageAlt;
  final String? primaryLabel;
  final String? primaryLink;
  final String? accentColor;
  final int sortOrder;
  final bool enabled;

  HeroSlide({
    required this.id,
    this.eyebrow,
    this.title,
    this.subtitle,
    this.imageUrl,
    this.imageAlt,
    this.primaryLabel,
    this.primaryLink,
    this.accentColor,
    required this.sortOrder,
    required this.enabled,
  });

  factory HeroSlide.fromJson(Map<String, dynamic> json) {
    return HeroSlide(
      id: json['id'] ?? 0,
      eyebrow: json['eyebrow'],
      title: json['title'],
      subtitle: json['subtitle'],
      imageUrl: json['imageUrl'],
      imageAlt: json['imageAlt'],
      primaryLabel: json['primaryLabel'],
      primaryLink: json['primaryLink'],
      accentColor: json['accentColor'],
      sortOrder: json['sortOrder'] ?? 0,
      enabled: json['enabled'] ?? true,
    );
  }
}

class AppNotification {
  final int id;
  final String type;
  final String title;
  final String message;
  final String? link;
  final int? orderId;
  final String? orderNumber;
  final String? orderStatus;
  final bool read;
  final String? createdAt;

  AppNotification({
    required this.id,
    required this.type,
    required this.title,
    required this.message,
    this.link,
    this.orderId,
    this.orderNumber,
    this.orderStatus,
    required this.read,
    this.createdAt,
  });

  factory AppNotification.fromJson(Map<String, dynamic> json) {
    return AppNotification(
      id: json['id'] ?? 0,
      type: json['type'] ?? '',
      title: json['title'] ?? '',
      message: json['message'] ?? '',
      link: json['link'],
      orderId: json['orderId'],
      orderNumber: json['orderNumber'],
      orderStatus: json['orderStatus'],
      read: json['read'] ?? false,
      createdAt: json['createdAt'],
    );
  }
}

class DeliveryOption {
  final int id;
  final String name;
  final String? description;
  final String type;
  final double fee;
  final String? estimatedTime;
  final String? logoUrl;
  final bool enabled;
  final int sortOrder;

  DeliveryOption({
    required this.id,
    required this.name,
    this.description,
    required this.type,
    required this.fee,
    this.estimatedTime,
    this.logoUrl,
    required this.enabled,
    required this.sortOrder,
  });

  factory DeliveryOption.fromJson(Map<String, dynamic> json) {
    return DeliveryOption(
      id: json['id'] ?? 0,
      name: json['name'] ?? '',
      description: json['description'],
      type: json['type'] ?? '',
      fee: (json['fee'] ?? 0).toDouble(),
      estimatedTime: json['estimatedTime'],
      logoUrl: json['logoUrl'],
      enabled: json['enabled'] ?? true,
      sortOrder: json['sortOrder'] ?? 0,
    );
  }
}

class ReviewSummary {
  final double average;
  final int count;
  final Map<String, int> distribution;

  ReviewSummary({
    required this.average,
    required this.count,
    required this.distribution,
  });

  factory ReviewSummary.fromJson(Map<String, dynamic> json) {
    return ReviewSummary(
      average: (json['average'] ?? 0).toDouble(),
      count: (json['count'] ?? 0).toInt(),
      distribution:
          (json['distribution'] as Map<String, dynamic>?)?.map(
            (k, v) => MapEntry(k, (v as num).toInt()),
          ) ??
          {},
    );
  }
}

class Review {
  final int id;
  final int rating;
  final String? title;
  final String? comment;
  final String? variantLabel;
  final String authorName;
  final String? authorAvatar;
  final bool verifiedPurchase;
  final String? createdAt;
  final bool mine;

  Review({
    required this.id,
    required this.rating,
    this.title,
    this.comment,
    this.variantLabel,
    required this.authorName,
    this.authorAvatar,
    required this.verifiedPurchase,
    this.createdAt,
    required this.mine,
  });

  factory Review.fromJson(Map<String, dynamic> json) {
    return Review(
      id: json['id'] ?? 0,
      rating: json['rating'] ?? 0,
      title: json['title'],
      comment: json['comment'],
      variantLabel: json['variantLabel'],
      authorName: json['authorName'] ?? '',
      authorAvatar: json['authorAvatar'],
      verifiedPurchase: json['verifiedPurchase'] ?? false,
      createdAt: json['createdAt'],
      mine: json['mine'] ?? false,
    );
  }
}

// ──────────────────────────────────────────────
// Paginated Response
// ──────────────────────────────────────────────
class PageData<T> {
  final List<T> content;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final bool first;
  final bool last;

  PageData({
    required this.content,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.first,
    required this.last,
  });

  factory PageData.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic>) fromJson,
  ) {
    return PageData(
      content:
          (json['content'] as List?)?.map((e) => fromJson(e)).toList() ?? [],
      page: json['page'] ?? 0,
      size: json['size'] ?? 0,
      totalElements: (json['totalElements'] ?? 0).toInt(),
      totalPages: json['totalPages'] ?? 0,
      first: json['first'] ?? true,
      last: json['last'] ?? true,
    );
  }
}

// ──────────────────────────────────────────────
// Payment Models
// ──────────────────────────────────────────────
class KhqrData {
  final String? payload;
  final String? md5;
  final String? qrImage;
  final String? bankName;
  final String? accountId;
  final String? expiresAt;
  final int expiresInSeconds;

  KhqrData({
    this.payload,
    this.md5,
    this.qrImage,
    this.bankName,
    this.accountId,
    this.expiresAt,
    this.expiresInSeconds = 0,
  });

  factory KhqrData.fromJson(Map<String, dynamic> json) {
    return KhqrData(
      payload: json['payload'],
      md5: json['md5'],
      qrImage: json['qrImage'],
      bankName: json['bankName'],
      accountId: json['accountId'],
      expiresAt: json['expiresAt'],
      expiresInSeconds: (json['expiresInSeconds'] ?? 0).toInt(),
    );
  }
}

class PaymentData {
  final int id;
  final int? orderId;
  final String? orderNumber;
  final double amount;
  final String currency;
  final String paymentMethod;
  final String paymentStatus;
  final String? transactionId;
  final String? gatewayProvider;
  final KhqrData? khqr;
  final String? cardMasked;
  final String? paidAt;
  final String? createdAt;

  PaymentData({
    required this.id,
    this.orderId,
    this.orderNumber,
    required this.amount,
    required this.currency,
    required this.paymentMethod,
    required this.paymentStatus,
    this.transactionId,
    this.gatewayProvider,
    this.khqr,
    this.cardMasked,
    this.paidAt,
    this.createdAt,
  });

  factory PaymentData.fromJson(Map<String, dynamic> json) {
    return PaymentData(
      id: json['id'] ?? 0,
      orderId: json['orderId'],
      orderNumber: json['orderNumber'],
      amount: (json['amount'] ?? 0).toDouble(),
      currency: json['currency'] ?? 'USD',
      paymentMethod: json['paymentMethod'] ?? '',
      paymentStatus: json['paymentStatus'] ?? '',
      transactionId: json['transactionId'],
      gatewayProvider: json['gatewayProvider'],
      khqr: json['khqr'] != null ? KhqrData.fromJson(json['khqr']) : null,
      cardMasked: json['cardMasked'],
      paidAt: json['paidAt'],
      createdAt: json['createdAt'],
    );
  }
}

class ProductLike {
  final int productId;
  final String productName;
  final double productPrice;
  final String? productImageUrl;
  final bool inStock;
  final bool liked;
  final int likeCount;
  final String? likedAt;

  ProductLike({
    required this.productId,
    required this.productName,
    required this.productPrice,
    this.productImageUrl,
    this.inStock = true,
    this.liked = true,
    this.likeCount = 0,
    this.likedAt,
  });

  factory ProductLike.fromJson(Map<String, dynamic> json) {
    return ProductLike(
      productId: json['productId'] ?? 0,
      productName: json['productName'] ?? '',
      productPrice: (json['productPrice'] ?? 0).toDouble(),
      productImageUrl: json['productImageUrl'],
      inStock: json['inStock'] ?? false,
      liked: json['liked'] ?? true,
      likeCount: json['likeCount'] ?? 0,
      likedAt: json['likedAt'],
    );
  }
}
