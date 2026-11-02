package com.ecomerceshopeonline.ecomercewepapp.config;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Category;
import com.ecomerceshopeonline.ecomercewepapp.entity.Product;
import com.ecomerceshopeonline.ecomercewepapp.repository.CategoryRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
public class CatalogSeeder implements ApplicationRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (productRepository.count() < 100) {
            log.info("Catalog has {} products. Seeding up to 100+ clean products...", productRepository.count());
            seedCatalog();
        }
    }

    @GetMapping("/seed-catalog")
    @PostMapping("/seed-catalog")
    @Transactional
    public ApiResponse<Map<String, Object>> triggerSeed() {
        int seeded = seedCatalog();
        long totalProducts = productRepository.count();
        long totalCategories = categoryRepository.count();
        
        return ApiResponse.ok(Map.of(
                "seededProducts", seeded,
                "totalProducts", totalProducts,
                "totalCategories", totalCategories,
                "message", "Catalog successfully seeded with 100 products across 10 categories"
        ));
    }

    @Transactional
    public int seedCatalog() {
        int count = 0;
        for (CategoryData catData : CATALOG) {
            Category category = categoryRepository.findByName(catData.name)
                    .orElseGet(() -> categoryRepository.save(Category.builder()
                            .name(catData.name)
                            .description(catData.description)
                            .imageUrl(catData.imageUrl)
                            .enabled(true)
                            .sortOrder(catData.sortOrder)
                            .build()));

            for (ItemData item : catData.items) {
                if (!productRepository.existsBySku(item.sku)) {
                    Product product = Product.builder()
                            .category(category)
                            .name(item.name)
                            .description(item.description)
                            .sku(item.sku)
                            .price(new BigDecimal(item.price))
                            .stockQuantity(item.stock)
                            .imageUrl(item.imageUrl)
                            .galleryUrls(item.gallery)
                            .enabled(true)
                            .ratingAverage(BigDecimal.valueOf(item.rating))
                            .ratingCount(item.reviews)
                            .shopRating(BigDecimal.valueOf(item.shopRating))
                            .shopRatingLabel(item.shopRatingLabel)
                            .build();
                    productRepository.save(product);
                    count++;
                }
            }
        }
        log.info("Finished seeding catalog: {} new products inserted. Total products: {}", count, productRepository.count());
        return count;
    }

    private record ItemData(String name, String sku, String price, int stock, String imageUrl,
                            List<String> gallery, double rating, int reviews, double shopRating,
                            String shopRatingLabel, String description) {}

    private record CategoryData(String name, String description, String imageUrl, int sortOrder, List<ItemData> items) {}

    private static final List<CategoryData> CATALOG = List.of(
            // ──────────────────────────────────────────────
            // 1. Smartphones & Tablets
            // ──────────────────────────────────────────────
            new CategoryData(
                    "Smartphones & Tablets",
                    "Flagship phones, powerful tablets, and portable tech",
                    "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=400&q=80",
                    1,
                    List.of(
                            new ItemData("iPhone 16 Pro Max", "SKU-PHN-001", "1199.00", 45,
                                    "https://images.unsplash.com/photo-1592750475338-74b7b21085ab?auto=format&fit=crop&w=800&q=80",
                                    List.of("https://images.unsplash.com/photo-1510557880182-3d4d3cba35a5?auto=format&fit=crop&w=800&q=80"),
                                    4.9, 142, 5.0, "Best Seller", "Titanium design, A18 Pro chip, 48MP Fusion camera, and Camera Control."),
                            new ItemData("iPhone 16", "SKU-PHN-002", "799.00", 60,
                                    "https://images.unsplash.com/photo-1510557880182-3d4d3cba35a5?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 98, 4.5, "Popular", "Dynamic Island, 48MP main camera with 2x Telephoto, and vivid color-infused back glass."),
                            new ItemData("Samsung Galaxy S25 Ultra", "SKU-PHN-003", "1299.00", 35,
                                    "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 115, 5.0, "Top Rated", "Snapdragon 8 Elite, Galaxy AI, built-in S Pen, and 200MP pro-grade quad camera system."),
                            new ItemData("Samsung Galaxy Z Fold 6", "SKU-PHN-004", "1799.00", 20,
                                    "https://images.unsplash.com/photo-1580910051074-3eb694886505?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 45, 4.5, "Flagship", "Dual screen foldable powerhouse with ultra-thin armor aluminum frame and multitasking."),
                            new ItemData("Google Pixel 9 Pro XL", "SKU-PHN-005", "1099.00", 40,
                                    "https://images.unsplash.com/photo-1598327105666-5b89351aff97?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 76, 4.5, "Editor Choice", "Google Tensor G4, Gemini AI integrated, superlative low-light photography."),
                            new ItemData("Google Pixel 9", "SKU-PHN-006", "799.00", 55,
                                    "https://images.unsplash.com/photo-1565849904461-04a58ad377e0?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.6, 52, 4.5, "Staff Pick", "Sleek compact design, Actua display, all-day battery life and pure Android."),
                            new ItemData("iPad Pro 13-inch M4", "SKU-PHN-007", "1299.00", 28,
                                    "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 89, 5.0, "Top Tier", "Breakthrough Ultra Retina XDR tandem OLED display with astonishing M4 performance."),
                            new ItemData("iPad Air 11-inch M2", "SKU-PHN-008", "599.00", 70,
                                    "https://images.unsplash.com/photo-1561154464-82e9adf32764?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 120, 4.5, "Great Value", "Blazing-fast M2 chip, Liquid Retina display, Apple Pencil Pro support."),
                            new ItemData("Samsung Galaxy Tab S10 Ultra", "SKU-PHN-009", "1199.00", 25,
                                    "https://images.unsplash.com/photo-1589739900243-4b52cd9b104e?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 34, 4.5, "Premium", "Huge 14.6-inch Dynamic AMOLED 2X display, IP68 water resistance, bundled S Pen."),
                            new ItemData("OnePlus 12", "SKU-PHN-010", "799.00", 50,
                                    "https://images.unsplash.com/photo-1574944985070-8f3ebc6b79d2?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 63, 4.5, "Performance", "Snapdragon 8 Gen 3, 4th Gen Hasselblad Camera, and 80W SUPERVOOC charging.")
                    )
            ),

            // ──────────────────────────────────────────────
            // 2. Laptops & Computers
            // ──────────────────────────────────────────────
            new CategoryData(
                    "Laptops & Computers",
                    "Ultra-portables, creator workstations, and desktop rigs",
                    "https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=400&q=80",
                    2,
                    List.of(
                            new ItemData("MacBook Pro 16-inch M4 Pro", "SKU-LPT-001", "2499.00", 20,
                                    "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 110, 5.0, "Pro Workhorse", "Liquid Retina XDR display, up to 24 hours of battery, blazing fast M4 Pro processor."),
                            new ItemData("MacBook Air 15-inch M3", "SKU-LPT-002", "1299.00", 45,
                                    "https://images.unsplash.com/photo-1611186871348-b1ce696e52c9?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 134, 5.0, "Bestseller", "Impossibly thin, silent fanless architecture, 1080p FaceTime HD camera."),
                            new ItemData("Dell XPS 16 OLED", "SKU-LPT-003", "2199.00", 18,
                                    "https://images.unsplash.com/photo-1593642632823-8f785ba67e45?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 48, 4.5, "Premium PC", "Intel Core Ultra 9, NVIDIA RTX 4070, stunning 4K OLED borderless touch display."),
                            new ItemData("Lenovo ThinkPad X1 Carbon Gen 12", "SKU-LPT-004", "1899.00", 22,
                                    "https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 62, 5.0, "Business Elite", "Ultralight carbon-fiber chassis, legendary ergonomic keyboard, enterprise security."),
                            new ItemData("Asus ROG Zephyrus G16", "SKU-LPT-005", "1999.00", 25,
                                    "https://images.unsplash.com/photo-1603302576837-37561b2e2302?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 85, 4.5, "Gaming Beast", "2.5K 240Hz OLED ROG Nebula display, Intel Core Ultra 9 and RTX 4080 graphics."),
                            new ItemData("Razer Blade 16", "SKU-LPT-006", "2799.00", 12,
                                    "https://images.unsplash.com/photo-1525547719571-a2d4ac8945e2?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 39, 5.0, "Ultimate Gaming", "Dual-mode Mini-LED display, anodized CNC aluminum unibody, desktop-grade power."),
                            new ItemData("Microsoft Surface Laptop 7", "SKU-LPT-007", "999.00", 40,
                                    "https://images.unsplash.com/photo-1541807084-5c52b6b3adef?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.6, 57, 4.5, "Copilot+ PC", "Snapdragon X Elite, all-day 20hr battery, vibrant PixelSense Flow touchscreen."),
                            new ItemData("HP Spectre x360 14", "SKU-LPT-008", "1449.00", 30,
                                    "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 41, 4.5, "2-in-1 Versatile", "360-degree convertible design, 9MP AI camera, Poly Audio tuned quad speakers."),
                            new ItemData("Apple Mac Studio M2 Max", "SKU-LPT-009", "1999.00", 15,
                                    "https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 68, 5.0, "Studio Power", "Compact footprint desktop with exceptional thermal design and extensive connectivity."),
                            new ItemData("Apple Mac mini M4", "SKU-LPT-010", "599.00", 80,
                                    "https://images.unsplash.com/photo-1517059224940-d4af9eec41b7?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 190, 5.0, "Incredible Value", "Redesigned tiny 5x5 inch frame, M4 chip, front USB-C and headphone jack.")
                    )
            ),

            // ──────────────────────────────────────────────
            // 3. Audio & Sound
            // ──────────────────────────────────────────────
            new CategoryData(
                    "Audio & Sound",
                    "Audiophile headphones, noise-cancelling earbuds, and wireless speakers",
                    "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=400&q=80",
                    3,
                    List.of(
                            new ItemData("Apple AirPods Pro 2", "SKU-AUD-001", "249.00", 90,
                                    "https://images.unsplash.com/photo-1600294037681-c80b4cb5b434?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 320, 5.0, "Top Choice", "Pro-level Active Noise Cancellation, Adaptive Audio, USB-C case with MagSafe."),
                            new ItemData("Apple AirPods Max", "SKU-AUD-002", "549.00", 35,
                                    "https://images.unsplash.com/photo-1546435770-a3e426bf472b?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 140, 4.5, "Premium Sound", "Custom acoustic design, H1 chips, computational audio and luxurious knit canopy."),
                            new ItemData("Sony WH-1000XM5", "SKU-AUD-003", "399.00", 55,
                                    "https://images.unsplash.com/photo-1583394838336-acd977736f90?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 210, 5.0, "Industry Leader", "Industry-leading noise cancellation with 8 microphones and Auto NC Optimizer."),
                            new ItemData("Sony WF-1000XM5 Earbuds", "SKU-AUD-004", "299.00", 60,
                                    "https://images.unsplash.com/photo-1590658268037-6bf12165a8df?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 115, 4.5, "Compact ANC", "Dynamic Driver X for rich vocals, bone conduction sensor for crystal calls."),
                            new ItemData("Bose QuietComfort Ultra", "SKU-AUD-005", "429.00", 40,
                                    "https://images.unsplash.com/photo-1545127398-14699f92334b?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 95, 4.5, "Ultra Comfort", "Bose Immersive Audio spatialized sound with CustomTune calibration."),
                            new ItemData("Bose QuietComfort Earbuds", "SKU-AUD-006", "299.00", 45,
                                    "https://images.unsplash.com/photo-1572536147248-ac59a8abfa4b?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.6, 70, 4.0, "Everyday ANC", "Legendary quiet with comfortable silicone stability bands and wireless charging."),
                            new ItemData("Marshall Stanmore III", "SKU-AUD-007", "379.00", 25,
                                    "https://images.unsplash.com/photo-1545454675-3531b543be5d?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 88, 5.0, "Iconic Design", "Heavy-hitting rock sound, classic vintage vinyl detailing, and wide soundstage."),
                            new ItemData("Marshall Emberton II", "SKU-AUD-008", "169.00", 50,
                                    "https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 130, 4.5, "Outdoor Legend", "True Stereophonic 360-degree sound, 30+ hours of playtime, IP67 waterproof."),
                            new ItemData("Sonos Era 300", "SKU-AUD-009", "449.00", 20,
                                    "https://images.unsplash.com/photo-1543512214-318c7553f230?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 64, 5.0, "Spatial Audio", "Dolby Atmos revolutionary acoustic design with 6 drivers pointing all directions."),
                            new ItemData("JBL Flip 6", "SKU-AUD-010", "129.00", 110,
                                    "https://images.unsplash.com/photo-1518444065439-e933c06ce9cd?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 240, 4.5, "Party Ready", "Bold JBL Original Pro Sound, 12 hours playtime, dual passive radiators.")
                    )
            ),

            // ──────────────────────────────────────────────
            // 4. Smartwatches & Wearables
            // ──────────────────────────────────────────────
            new CategoryData(
                    "Smartwatches & Wearables",
                    "Fitness trackers, health monitors, and smart lifestyle watches",
                    "https://images.unsplash.com/photo-1546868871-7041f2a55e12?auto=format&fit=crop&w=400&q=80",
                    4,
                    List.of(
                            new ItemData("Apple Watch Ultra 2", "SKU-WCH-001", "799.00", 30,
                                    "https://images.unsplash.com/photo-1508685096489-7aacd43bd3b1?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 160, 5.0, "Extreme Athlete", "Rugged 49mm titanium case, 3000-nit display, precision dual-frequency GPS."),
                            new ItemData("Apple Watch Series 10", "SKU-WCH-002", "399.00", 65,
                                    "https://images.unsplash.com/photo-1546868871-7041f2a55e12?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 195, 5.0, "Everyday Smart", "Thinnest Apple Watch ever, wide-angle OLED display, sleep apnea notifications."),
                            new ItemData("Samsung Galaxy Watch Ultra", "SKU-WCH-003", "649.00", 25,
                                    "https://images.unsplash.com/photo-1579586337278-3befd40fd17a?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 85, 4.5, "Tough Android", "Titanium cushion frame, Dual GPS, Energy Score with Galaxy AI and 100hr battery."),
                            new ItemData("Samsung Galaxy Watch 7", "SKU-WCH-004", "299.00", 55,
                                    "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 105, 4.5, "Popular Watch", "3nm processor, BioActive Sensor, advanced sleep tracking and dual-frequency GPS."),
                            new ItemData("Garmin Fenix 8 Solar", "SKU-WCH-005", "999.00", 15,
                                    "https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 45, 5.0, "Pro Adventure", "Solar charging lens giving 28+ days battery, built-in speaker/mic, depth gauge."),
                            new ItemData("Garmin Forerunner 965", "SKU-WCH-006", "599.00", 22,
                                    "https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 72, 5.0, "Triathlete Pick", "Brilliant AMOLED touchscreen, titanium bezel, full-color built-in maps."),
                            new ItemData("Google Pixel Watch 3", "SKU-WCH-007", "349.00", 40,
                                    "https://images.unsplash.com/photo-1510017803434-a899398421b3?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.6, 55, 4.0, "Sleek Circular", "Actua display with 2000 nits, Fitbit deep health integration, Loss of Pulse Detection."),
                            new ItemData("Oura Ring Gen 3 Horizon", "SKU-WCH-008", "349.00", 35,
                                    "https://images.unsplash.com/photo-1605100804763-247f67b3557e?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 90, 4.5, "Discreet Health", "Sleek titanium ring measuring sleep quality, readiness, resilience and heart metrics."),
                            new ItemData("Whoop 4.0 Fitness Strap", "SKU-WCH-009", "239.00", 50,
                                    "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 65, 4.5, "Strain Tracker", "Screenless continuous biometric tracker for recovery, sleep, and strain monitoring."),
                            new ItemData("Amazfit GTR 4", "SKU-WCH-010", "199.00", 60,
                                    "https://images.unsplash.com/photo-1524805444758-089113d48a6d?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.5, 80, 4.0, "Value Champion", "14-day battery life, dual-band circularly-polarized GPS antenna, 150+ sports modes.")
                    )
            ),

            // ──────────────────────────────────────────────
            // 5. Cameras & Drones
            // ──────────────────────────────────────────────
            new CategoryData(
                    "Cameras & Drones",
                    "Mirrorless cameras, action cams, cinema lenses, and camera drones",
                    "https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=400&q=80",
                    5,
                    List.of(
                            new ItemData("Sony Alpha 7 IV", "SKU-CAM-001", "2498.00", 15,
                                    "https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 130, 5.0, "Pro Mirrorless", "33MP Exmor R sensor, 4K 60p 10-bit recording, AI-driven real-time autofocus."),
                            new ItemData("Canon EOS R6 Mark II", "SKU-CAM-002", "2299.00", 18,
                                    "https://images.unsplash.com/photo-1606830733744-0ad778449672?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 95, 5.0, "Speed & Lowlight", "40 fps continuous electronic shooting, 6K oversampled 4K 60p, 8 stops of IS."),
                            new ItemData("Fujifilm X-T5", "SKU-CAM-003", "1699.00", 20,
                                    "https://images.unsplash.com/photo-1502920917128-1aa500764cbd?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 145, 5.0, "Film Simulation", "40.2MP X-Trans CMOS 5 HR sensor, classic mechanical dials, 7 stops IBIS."),
                            new ItemData("Nikon Z6 III", "SKU-CAM-004", "2499.00", 12,
                                    "https://images.unsplash.com/photo-1581591524425-c7e0978865fc?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 42, 4.5, "Partially Stacked", "Partially-stacked CMOS sensor, 6K N-RAW internal, bright 4000-nit EVF."),
                            new ItemData("DJI Mini 4 Pro", "SKU-CAM-005", "759.00", 35,
                                    "https://images.unsplash.com/photo-1508614589041-895b88991e3e?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 210, 5.0, "Fly Under 249g", "Omnidirectional obstacle sensing, 4K/60fps HDR true vertical shooting, 20km O4."),
                            new ItemData("DJI Air 3 Fly More Combo", "SKU-CAM-006", "1549.00", 16,
                                    "https://images.unsplash.com/photo-1527977966376-1c8408f9f108?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 88, 5.0, "Dual Main Cameras", "Wide-angle and 3x medium tele cameras, 46-min max flight time, 48MP photos."),
                            new ItemData("GoPro HERO13 Black", "SKU-CAM-007", "399.00", 50,
                                    "https://images.unsplash.com/photo-1565849904461-04a58ad377e0?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 115, 4.5, "Action Tough", "5.3K 60fps video, HB-Series lens compatibility, magnetic latch mount and Enduro battery."),
                            new ItemData("Insta360 X4 8K", "SKU-CAM-008", "499.00", 30,
                                    "https://images.unsplash.com/photo-1512790182412-b19e6d62bc39?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 92, 4.5, "8K 360", "Unbeatable 8K 360 capture, invisible selfie stick effect, FlowState stabilization."),
                            new ItemData("DJI Osmo Pocket 3 Creator Combo", "SKU-CAM-009", "669.00", 25,
                                    "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 180, 5.0, "Vlog Perfection", "1-inch CMOS sensor, 2-inch rotatable OLED screen, mechanical 3-axis stabilization."),
                            new ItemData("Sony ZV-E10 II", "SKU-CAM-010", "998.00", 28,
                                    "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 70, 4.5, "Creator Studio", "26MP APS-C sensor, Cinematic Vlog setting, product showcase autofocus mode.")
                    )
            ),

            // ──────────────────────────────────────────────
            // 6. Gaming & Consoles
            // ──────────────────────────────────────────────
            new CategoryData(
                    "Gaming & Consoles",
                    "Home consoles, handhelds, precision controllers, and gaming peripherals",
                    "https://images.unsplash.com/photo-1600080972464-8e5f35f63d08?auto=format&fit=crop&w=400&q=80",
                    6,
                    List.of(
                            new ItemData("PlayStation 5 Pro", "SKU-GAM-001", "699.00", 30,
                                    "https://images.unsplash.com/photo-1606813907291-d86efa9b94db?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 210, 5.0, "Most Powerful", "PlayStation Spectral Super Resolution (PSSR), 2TB SSD, advanced ray tracing."),
                            new ItemData("PlayStation 5 Slim Digital", "SKU-GAM-002", "449.00", 45,
                                    "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 180, 4.5, "Slim Design", "All-digital PS5 with 1TB SSD, lightning-fast loading, 3D Audio, DualSense haptics."),
                            new ItemData("Xbox Series X 1TB", "SKU-GAM-003", "499.00", 40,
                                    "https://images.unsplash.com/photo-1621259182978-fbf93132d53d?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 150, 4.5, "12 Teraflops", "True 4K gaming, Quick Resume, Xbox Game Pass compatible, carbon black design."),
                            new ItemData("Nintendo Switch OLED", "SKU-GAM-004", "349.00", 75,
                                    "https://images.unsplash.com/photo-1578303512597-81e6cc155b3e?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 310, 5.0, "Family Favorite", "Vibrant 7-inch OLED screen, wide adjustable stand, wired LAN port dock."),
                            new ItemData("Steam Deck OLED 512GB", "SKU-GAM-005", "549.00", 35,
                                    "https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 240, 5.0, "PC in Your Hands", "90Hz HDR OLED display, custom 6nm AMD APU, incredible ergonomic controls."),
                            new ItemData("ASUS ROG Ally X", "SKU-GAM-006", "799.00", 25,
                                    "https://images.unsplash.com/photo-1598550476439-6847785fcea6?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 88, 4.5, "Windows Handheld", "Ryzen Z1 Extreme, 24GB LPDDR5X RAM, 80Wh massive battery, full Windows freedom."),
                            new ItemData("DualSense Edge Controller", "SKU-GAM-007", "199.00", 50,
                                    "https://images.unsplash.com/photo-1592840496073-e72bbe619680?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 95, 4.5, "Pro Controller", "Customizable back buttons, changeable stick caps and replaceable stick modules."),
                            new ItemData("Xbox Elite Wireless Series 2", "SKU-GAM-008", "179.00", 55,
                                    "https://images.unsplash.com/photo-1600080972464-8e5f35f63d08?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 120, 4.5, "Elite Precision", "Adjustable-tension thumbsticks, wrap-around rubberized grip, shorter hair triggers."),
                            new ItemData("Logitech G Pro X Superlight 2", "SKU-GAM-009", "159.00", 65,
                                    "https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 175, 5.0, "Esports Legend", "60g ultralight wireless mouse, HERO 2 sensor with 32,000 DPI, hybrid switches."),
                            new ItemData("SteelSeries Apex Pro TKL", "SKU-GAM-010", "189.00", 40,
                                    "https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 80, 4.5, "World's Fastest", "OmniPoint 2.0 adjustable hypermagnetic switches, OLED smart display.")
                    )
            ),

            // ──────────────────────────────────────────────
            // 7. Men & Women Fashion
            // ──────────────────────────────────────────────
            new CategoryData(
                    "Men & Women Fashion",
                    "Iconic sneakers, durable denim, timeless outerwear, and casual apparel",
                    "https://images.unsplash.com/photo-1489987707025-afc232f7ea0f?auto=format&fit=crop&w=400&q=80",
                    7,
                    List.of(
                            new ItemData("Nike Air Force 1 '07 Classic", "SKU-FSH-001", "115.00", 120,
                                    "https://images.unsplash.com/photo-1595950653106-6c9ebd614d3a?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 450, 5.0, "Streetwear Icon", "Crisp all-white leather, stitched overlays, Nike Air cushioning for all-day comfort."),
                            new ItemData("Nike Dunk Low Retro", "SKU-FSH-002", "115.00", 85,
                                    "https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 380, 5.0, "Classic Low", "Panda white/black heritage hoops style with padded low-cut collar."),
                            new ItemData("Adidas Ultraboost Light", "SKU-FSH-003", "190.00", 70,
                                    "https://images.unsplash.com/photo-1587563871167-1ee9c731aefb?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 210, 4.5, "Max Cushioning", "Lightest BOOST foam yet, Primeknit+ upper, Continental WinterGrip rubber."),
                            new ItemData("Adidas Samba OG Shoes", "SKU-FSH-004", "100.00", 95,
                                    "https://images.unsplash.com/photo-1518002171953-a080ee817e1f?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 410, 5.0, "Retro Classic", "Smooth leather upper, suede T-toe, gum rubber outsole that defined indoor soccer."),
                            new ItemData("New Balance 9060 Lifestyle", "SKU-FSH-005", "150.00", 55,
                                    "https://images.unsplash.com/photo-1539185441755-769473a23570?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 160, 4.5, "Y2K Aesthetic", "Mesh upper with pigskin suede overlays, dual-density ABZORB and SBS cushioning."),
                            new ItemData("New Balance 574 Core", "SKU-FSH-006", "90.00", 110,
                                    "https://images.unsplash.com/photo-1551107696-a4b0c5a0d9a2?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 290, 4.5, "Timeless Casual", "Lightweight EVA foam midsole and ENCAP cushioning deliver all-day support."),
                            new ItemData("Levi's 501 Original Fit Jeans", "SKU-FSH-007", "80.00", 130,
                                    "https://images.unsplash.com/photo-1542272604-780c96856592?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 340, 5.0, "Denim Standard", "Since 1873: straight leg, iconic button fly, crafted from 100% durable cotton."),
                            new ItemData("Patagonia Better Sweater Jacket", "SKU-FSH-008", "159.00", 65,
                                    "https://images.unsplash.com/photo-1556905055-8f358a7a47b2?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 175, 5.0, "Cozy Warmth", "100% recycled polyester fleece with sweater-knit aesthetic, Fair Trade Certified."),
                            new ItemData("The North Face 1996 Nuptse", "SKU-FSH-009", "330.00", 40,
                                    "https://images.unsplash.com/photo-1544441893-675973e31985?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 230, 5.0, "Winter Legend", "700-fill goose down insulation, oversized baffles, water-repellent ripstop shell."),
                            new ItemData("Champion Reverse Weave Hoodie", "SKU-FSH-010", "65.00", 140,
                                    "https://images.unsplash.com/photo-1556905055-8f358a7a47b2?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 310, 4.5, "Heavyweight Soft", "Signature cross-grain cut resists shrinkage, 12 oz heavyweight fleece.")
                    )
            ),

            // ──────────────────────────────────────────────
            // 8. Home & Smart Appliances
            // ──────────────────────────────────────────────
            new CategoryData(
                    "Home & Smart Appliances",
                    "Smart vacuum cleaners, luxury coffee makers, kitchen essentials, and smart lights",
                    "https://images.unsplash.com/photo-1556911220-e15b29be8c8f?auto=format&fit=crop&w=400&q=80",
                    8,
                    List.of(
                            new ItemData("Dyson V15 Detect Cordless", "SKU-HOM-001", "749.00", 25,
                                    "https://images.unsplash.com/photo-1558317374-067fb5f30001?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 140, 5.0, "Deep Clean", "Laser reveals microscopic dust, acoustic piezo sensor measures particle counts."),
                            new ItemData("Dyson Supersonic Hair Dryer", "SKU-HOM-002", "499.00", 35,
                                    "https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 220, 5.0, "Salon Results", "Intelligent heat control protects shine, fast drying powered by the Dyson digital V9 motor."),
                            new ItemData("Nespresso Vertuo Pop+ Machine", "SKU-HOM-003", "129.00", 70,
                                    "https://images.unsplash.com/photo-1517668808822-9ebb02f2a0e6?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 180, 4.5, "Morning Espresso", "Centrifusion technology brews 5 cup sizes with velvety crema at touch of one button."),
                            new ItemData("Breville Barista Touch Espresso", "SKU-HOM-004", "999.00", 15,
                                    "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 95, 5.0, "Cafe Quality", "Touchscreen pre-programmed cafe drinks, ThermoJet heating system ready in 3 seconds."),
                            new ItemData("Philips Hue Starter Kit", "SKU-HOM-005", "199.00", 60,
                                    "https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 130, 4.5, "Smart Lighting", "16 million colors, includes 4 A19 smart color bulbs, Hue Bridge, and smart dimmer."),
                            new ItemData("Roborock S8 MaxV Ultra", "SKU-HOM-006", "1799.00", 10,
                                    "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 50, 5.0, "Hands-Free Mop", "10,000Pa suction, FlexiArm robotic corner mop, self-washing & hot air drying dock."),
                            new ItemData("Ninja Foodi DualZone Air Fryer", "SKU-HOM-007", "199.00", 55,
                                    "https://images.unsplash.com/photo-1584269600464-37b1b58a9fe7?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 260, 4.5, "Kitchen Must", "2 independent 4-qt baskets let you cook 2 foods, 2 ways that finish at the same time."),
                            new ItemData("KitchenAid Artisan 5-Qt Mixer", "SKU-HOM-008", "449.00", 30,
                                    "https://images.unsplash.com/photo-1594385208974-2e75f8d7bb48?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 310, 5.0, "Baker's Dream", "10 speeds, tilt-head design, stainless steel bowl with comfortable ergonomic handle."),
                            new ItemData("De'Longhi Dedica Deluxe", "SKU-HOM-009", "299.00", 40,
                                    "https://images.unsplash.com/photo-1517668808822-9ebb02f2a0e6?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.6, 85, 4.0, "Slim Espresso", "Compact 6-inch wide footprint, 15-bar professional pump, manual frother wand."),
                            new ItemData("Le Creuset Enameled Dutch Oven", "SKU-HOM-010", "420.00", 25,
                                    "https://images.unsplash.com/photo-1584990347449-3893608154fa?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 160, 5.0, "Heirloom Quality", "French cast iron delivers superior heat distribution and retention for braises and stews.")
                    )
            ),

            // ──────────────────────────────────────────────
            // 9. Food & Beverages
            // ──────────────────────────────────────────────
            new CategoryData(
                    "Food & Beverages",
                    "Refreshing beverages, premium roasted coffee, artisanal teas, and gourmet treats",
                    "https://images.unsplash.com/photo-1544816155-12df9643f363?auto=format&fit=crop&w=400&q=80",
                    9,
                    List.of(
                            new ItemData("Coca-Cola Original 24 Cans", "SKU-BEV-001", "16.99", 200,
                                    "https://images.unsplash.com/photo-1622483767028-3f66f32aef97?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 520, 5.0, "Refreshing", "Iconic crisp taste, perfectly carbonated 330ml aluminum cans in a 24-can pantry pack."),
                            new ItemData("Coca-Cola Zero Sugar 24 Cans", "SKU-BEV-002", "16.99", 180,
                                    "https://images.unsplash.com/photo-1554866585-cd94860890b7?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 410, 4.5, "Zero Calories", "Real Coca-Cola taste with zero sugar and zero calories. Best served chilled."),
                            new ItemData("Red Bull Energy Drink 12-Pack", "SKU-BEV-003", "24.99", 150,
                                    "https://images.unsplash.com/photo-1551024709-8f23befc6f87?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 310, 4.5, "Energy Boost", "Vitalizes body and mind with high quality ingredients: Caffeine, Taurine, B-group vitamins."),
                            new ItemData("Monster Energy Ultra 16-Pack", "SKU-BEV-004", "29.99", 120,
                                    "https://images.unsplash.com/photo-1622543925917-763c34d1a86e?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 190, 4.5, "Zero Sugar", "Crisp citrus flavor with full Monster energy blend without the sugar."),
                            new ItemData("San Pellegrino Sparkling 12-Pack", "SKU-BEV-005", "18.50", 140,
                                    "https://images.unsplash.com/photo-1560512823-829485b8bf24?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 230, 5.0, "Italian Mineral", "Naturally carbonated mineral water filtered through the Italian Alps for 30 years."),
                            new ItemData("Perrier Carbonated Water 24 Cans", "SKU-BEV-006", "22.99", 110,
                                    "https://images.unsplash.com/photo-1527661591475-527312dd65f5?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 170, 4.5, "Extra Bubbles", "Invigorating French sparkling water with extraordinary bursting bubbles."),
                            new ItemData("Starbucks Pike Place Roast 1kg", "SKU-BEV-007", "24.99", 90,
                                    "https://images.unsplash.com/photo-1559056199-641a0ac8b55e?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 380, 5.0, "Whole Bean", "Medium-roasted coffee beans with subtle notes of cocoa and rich toasted praline."),
                            new ItemData("Ippodo Ceremonial Matcha 40g", "SKU-BEV-008", "38.00", 60,
                                    "https://images.unsplash.com/photo-1576092768241-dec231879fc3?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 140, 5.0, "Kyoto Japan", "Stone-ground vibrant green tea powder from Uji Kyoto with rich umami sweetness."),
                            new ItemData("Lindt Swiss Luxury Selection", "SKU-BEV-009", "19.99", 100,
                                    "https://images.unsplash.com/photo-1549007994-cb92caebd54b?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 290, 5.0, "Chocolatier", "Master crafted milk, dark and white chocolate pralines with delicate fillings."),
                            new ItemData("Ferrero Rocher Box 24-Piece", "SKU-BEV-010", "14.99", 160,
                                    "https://images.unsplash.com/photo-1548907040-4baa42d10919?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 440, 5.0, "Golden Classic", "Whole crunchy hazelnut enveloped in creamy filling and crisp wafer shell.")
                    )
            ),

            // ──────────────────────────────────────────────
            // 10. Accessories & Bags
            // ──────────────────────────────────────────────
            new CategoryData(
                    "Accessories & Bags",
                    "Commuter backpacks, power banks, leather wallets, and everyday carry gear",
                    "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=400&q=80",
                    10,
                    List.of(
                            new ItemData("Peak Design Everyday Backpack 20L", "SKU-BAG-001", "279.00", 35,
                                    "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 185, 5.0, "Camera & Tech", "MagLatch hardware, dual side access, origami FlexFold dividers, 100% recycled shell."),
                            new ItemData("Bellroy Transit Backpack 28L", "SKU-BAG-002", "259.00", 40,
                                    "https://images.unsplash.com/photo-1546938576-6e6a64f317cc?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 110, 4.5, "Travel Commute", "Separate 16-inch laptop compartment, hidden passport pocket, water-resistant woven fabric."),
                            new ItemData("Bellroy Hide & Seek Wallet", "SKU-BAG-003", "89.00", 90,
                                    "https://images.unsplash.com/photo-1627123424574-724758594e93?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 240, 5.0, "Slim Leather", "Environmentally certified leather, hidden coin pouch, RFID protection."),
                            new ItemData("Anker Prime 27,650mAh (250W)", "SKU-BAG-004", "179.00", 55,
                                    "https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 145, 5.0, "Power Monster", "Powers 2 laptops simultaneously at 140W each, smart digital display and app control."),
                            new ItemData("Anker MagGo 3-in-1 Station", "SKU-BAG-005", "109.00", 75,
                                    "https://images.unsplash.com/photo-1586953208448-b95a79798f07?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.8, 190, 4.5, "Foldable Qi2", "Certified 15W Qi2 fast wireless charging for iPhone, Apple Watch and AirPods in one fold."),
                            new ItemData("Apple MagSafe Leather Wallet", "SKU-BAG-006", "59.00", 80,
                                    "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 160, 4.5, "Find My Ready", "Tanned French leather with built-in magnets that snap onto the back of your iPhone."),
                            new ItemData("Tom Bihn Synik 30 Travel Pack", "SKU-BAG-007", "340.00", 20,
                                    "https://images.unsplash.com/photo-1622560480605-d83c853bc5c3?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 75, 5.0, "Indestructible", "Full clamshell opening, edgeless EV50 shoulder straps, ballistic nylon durability."),
                            new ItemData("Matador Seg42 Travel Pack", "SKU-BAG-008", "200.00", 30,
                                    "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.7, 60, 4.0, "Segmented", "Subdivided into five organized compartments with waterproof UTS coating."),
                            new ItemData("YETI Rambler 30 oz Tumbler", "SKU-BAG-009", "38.00", 150,
                                    "https://images.unsplash.com/photo-1577705998148-6da4f3963bc8?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 5.0, 480, 5.0, "Ice Cold 24h", "Double-wall vacuum insulation, MagSlider lid, 18/8 kitchen-grade stainless steel."),
                            new ItemData("Hydro Flask 32 oz Wide Mouth", "SKU-BAG-010", "44.95", 130,
                                    "https://images.unsplash.com/photo-1602143407151-7111542de6e8?auto=format&fit=crop&w=800&q=80",
                                    List.of(), 4.9, 390, 5.0, "Flex Cap", "TempShield cold insulation up to 24 hours, pro-grade stainless steel, BPA-free.")
                    )
            )
    );
}
