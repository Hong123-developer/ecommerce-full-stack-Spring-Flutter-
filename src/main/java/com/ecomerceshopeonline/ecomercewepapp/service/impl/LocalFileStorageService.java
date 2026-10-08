package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.config.AppProperties;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

/**
 * Writes images to the local disk under {@code app.upload.dir}. Swap for an S3/Cloud
 * Storage implementation later without touching callers.
 * <p>
 * The file type is decided from the file's own leading bytes - never from the
 * client-supplied name or Content-Type, which are trivially forged - and the stored
 * name is a random UUID, so an upload can neither choose its path nor overwrite
 * another file.
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final String PRODUCT_FOLDER = "products";

    /** Signature bytes -> extension. WebP is RIFF....WEBP, checked separately. */
    private enum ImageType {
        JPEG("jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        PNG("png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
        GIF("gif", new byte[]{'G', 'I', 'F', '8'}),
        WEBP("webp", new byte[]{'R', 'I', 'F', 'F'});

        final String extension;
        final byte[] magic;

        ImageType(String extension, byte[] magic) {
            this.extension = extension;
            this.magic = magic;
        }
    }

    private final Path productDir;
    private final long maxBytes;

    public LocalFileStorageService(AppProperties properties) {
        Path root = Path.of(properties.upload().dir()).toAbsolutePath().normalize();
        this.productDir = root.resolve(PRODUCT_FOLDER);
        this.maxBytes = properties.upload().maxImageSize().toBytes();
        try {
            Files.createDirectories(productDir);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create upload directory " + productDir, ex);
        }
    }

    @Override
    public String storeProductImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please choose an image to upload");
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException(
                    "Image must be at most " + (maxBytes / (1024 * 1024)) + " MB");
        }

        ImageType type = detectType(file);
        String name = UUID.randomUUID() + "." + type.extension;
        Path target = productDir.resolve(name).normalize();

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.error("Failed to store uploaded image", ex);
            throw new UncheckedIOException("Could not store the image", ex);
        }
        return "/uploads/" + PRODUCT_FOLDER + "/" + name;
    }

    private static ImageType detectType(MultipartFile file) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException ex) {
            throw new BadRequestException("The uploaded file could not be read");
        }

        for (ImageType type : ImageType.values()) {
            if (read >= type.magic.length
                    && Arrays.equals(head, 0, type.magic.length, type.magic, 0, type.magic.length)) {
                if (type == ImageType.WEBP
                        && !(read >= 12 && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P')) {
                    continue;
                }
                return type;
            }
        }
        throw new BadRequestException("Only JPG, PNG, WebP or GIF images are allowed");
    }
}
