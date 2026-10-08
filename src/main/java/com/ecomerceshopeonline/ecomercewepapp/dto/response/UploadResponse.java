package com.ecomerceshopeonline.ecomercewepapp.dto.response;

/**
 * @param url  absolute URL to store as a product's imageUrl
 * @param path server-relative path, e.g. /uploads/products/abc.jpg
 */
public record UploadResponse(String url, String path) {
}
