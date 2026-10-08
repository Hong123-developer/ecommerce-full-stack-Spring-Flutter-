package com.ecomerceshopeonline.ecomercewepapp.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Stores uploaded images and returns the path they are served from.
 */
public interface FileStorageService {

    /**
     * Validates and stores a product image.
     *
     * @return the public path, e.g. {@code /uploads/products/3f2c...e1.jpg}
     */
    String storeProductImage(MultipartFile file);
}
