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
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
@Order(20)
public class ComputerCatalogSeeder implements ApplicationRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (productRepository.count() < 1000) {
            log.info("Catalog has {} products. Seeding 1,000 Computer products across all computer categories...", productRepository.count());
            seedComputerCatalog();
        }
    }

    @GetMapping("/seed-computers")
    @PostMapping("/seed-computers")
    @Transactional
    public ApiResponse<Map<String, Object>> triggerSeed() {
        int seeded = seedComputerCatalog();
        long totalProducts = productRepository.count();
        long totalCategories = categoryRepository.count();

        return ApiResponse.ok(Map.of(
                "seededProducts", seeded,
                "totalProducts", totalProducts,
                "totalCategories", totalCategories,
                "message", "Computer catalog successfully verified / seeded with 1,000 products across 20 computer categories"
        ));
    }

    @Transactional
    public int seedComputerCatalog() {
        int inserted = 0;
        int skuIndex = 1;

        String[] badges = {"Best Seller", "Editor's Choice", "Pro Pick", "Top Rated", "Staff Favorite", "New Arrival", "Flagship Tier", "Performance Beast"};

        for (CategoryDefinition def : COMPUTER_CATEGORIES) {
            Category category = categoryRepository.findByName(def.name())
                    .orElseGet(() -> categoryRepository.save(Category.builder()
                            .name(def.name())
                            .description(def.description())
                            .imageUrl(def.imageUrl())
                            .enabled(true)
                            .sortOrder(def.sortOrder())
                            .build()));

            List<ProductDefinition> items = def.generator().generate();
            List<Product> toSave = new ArrayList<>();

            for (int i = 0; i < items.size(); i++) {
                ProductDefinition item = items.get(i);
                String sku = String.format("SKU-CMP-%04d", skuIndex++);

                if (productRepository.existsBySku(sku)) {
                    continue;
                }

                String img = def.images()[i % def.images().length];
                String badge = badges[i % badges.length];
                double rating = Math.min(5.0, 4.5 + (i % 6) * 0.1);
                int reviews = 25 + (i * 17) % 450;
                double shopRating = (i % 2 == 0) ? 5.0 : 4.5;
                int stock = 15 + (i * 7) % 120;

                Product product = Product.builder()
                        .category(category)
                        .name(item.name())
                        .description(item.description())
                        .sku(sku)
                        .price(BigDecimal.valueOf(item.price()).setScale(2, RoundingMode.HALF_UP))
                        .stockQuantity(stock)
                        .imageUrl(img)
                        .galleryUrls(List.of())
                        .enabled(true)
                        .sortOrder(i)
                        .ratingAverage(BigDecimal.valueOf(rating).setScale(1, RoundingMode.HALF_UP))
                        .ratingCount(reviews)
                        .shopRating(BigDecimal.valueOf(shopRating).setScale(1, RoundingMode.HALF_UP))
                        .shopRatingLabel(badge)
                        .build();

                toSave.add(product);
            }

            if (!toSave.isEmpty()) {
                productRepository.saveAll(toSave);
                inserted += toSave.size();
            }
        }

        log.info("Finished seeding computer catalog. Newly inserted: {}, Total in DB: {}", inserted, productRepository.count());
        return inserted;
    }

    private record ProductDefinition(String name, double price, String description) {}

    @FunctionalInterface
    private interface ProductGenerator {
        List<ProductDefinition> generate();
    }

    private record CategoryDefinition(String name, String description, String imageUrl, int sortOrder,
                                      String[] images, ProductGenerator generator) {}

    private static final List<CategoryDefinition> COMPUTER_CATEGORIES = List.of(
            // 1. Laptops & Ultrabooks
            new CategoryDefinition(
                    "Laptops & Ultrabooks",
                    "Premium ultrabooks, lightweight notebooks, and productivity powerhouses",
                    "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=800&q=80",
                    21,
                    new String[]{
                            "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1611186871348-b1ce696e52c9?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1593642632823-8f785ba67e45?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1541807084-5c52b6b3adef?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"Apple MacBook Pro", "Apple MacBook Air", "Dell XPS", "Lenovo ThinkPad", "HP Spectre", "ASUS ZenBook", "Microsoft Surface Laptop", "LG Gram Pro", "Framework Laptop", "Acer Swift Edge"},
                            new String[]{"16-inch M4 Max (48GB RAM, 1TB SSD)", "14-inch M4 Pro (24GB RAM, 512GB SSD)", "15-inch M3 (16GB RAM, 512GB SSD)", "16 OLED (Core Ultra 9, 32GB, 1TB SSD)", "X1 Carbon Gen 12 (Ultra 7, 32GB, 1TB)", "x360 14 (Core Ultra 7, OLED 2.8K, 32GB)", "S 16 OLED (Ryzen AI 9, 32GB, 1TB)", "7 15\" (Snapdragon X Elite, 32GB, 1TB)", "17 Pro (Core Ultra 7, RTX 3050, 32GB)", "16 (Ryzen 7 7840U, 16GB, 1TB OLED)"},
                            new double[]{2499.0, 1599.0, 1299.0, 2199.0, 1899.0, 1449.0, 1699.0, 1599.0, 1799.0, 1299.0},
                            "High-performance ultrabook featuring premium display, industry-leading battery life, whisper-quiet thermals, and high-speed NVMe storage."
                    )
            ),

            // 2. Gaming Laptops
            new CategoryDefinition(
                    "Gaming Laptops",
                    "High-refresh esports laptops with top-tier NVIDIA RTX & AMD graphics",
                    "https://images.unsplash.com/photo-1603302576837-37561b2e2302?auto=format&fit=crop&w=800&q=80",
                    22,
                    new String[]{
                            "https://images.unsplash.com/photo-1603302576837-37561b2e2302?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1525547719571-a2d4ac8945e2?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1593642702821-c8da6771f0c6?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"ASUS ROG Zephyrus G16 OLED", "ASUS ROG Strix SCAR 18", "Razer Blade 16 Dual Mini-LED", "Razer Blade 14 Compact", "Alienware m18 R2 Gaming Laptop", "Alienware x16 R2 Ultra-Thin", "Lenovo Legion Pro 7i Gen 9", "Lenovo Legion 9i Liquid-Cooled", "MSI Titan 18 HX Dragon", "MSI Raider GE78 HX"},
                            new String[]{"(RTX 4090 16GB, i9-14900HX, 64GB, 2TB)", "(RTX 4080 12GB, Core Ultra 9, 32GB, 2TB)", "(RTX 4070 8GB, Ryzen 9, 32GB, 1TB)", "(RTX 4080 12GB, i9-14900HX, 32GB, 1TB)", "(RTX 4090 16GB, Liquid-Cooled, 64GB, 4TB)"},
                            new double[]{3699.0, 2899.0, 2299.0, 2799.0, 4299.0},
                            "Extreme gaming laptop with ultra-fast 240Hz+ display, high-TGP graphics, vapor chamber cooling, and per-key RGB backlighting."
                    )
            ),

            // 3. Desktop Computers & PCs
            new CategoryDefinition(
                    "Desktop Computers & PCs",
                    "Pre-built desktop towers, home office PCs, and all-round desktop systems",
                    "https://images.unsplash.com/photo-1587202372775-e229f172b9d7?auto=format&fit=crop&w=800&q=80",
                    23,
                    new String[]{
                            "https://images.unsplash.com/photo-1587202372775-e229f172b9d7?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1547082299-de196ea013d6?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"Dell XPS Desktop Special Edition", "HP Envy Desktop Tower", "Lenovo IdeaCentre Tower 5i", "Corsair Vengeance Desktop", "Acer Aspire TC Desktop", "ASUS ExpertCenter D7 Tower"},
                            new String[]{"Intel Core i9-14900, 32GB DDR5, 1TB SSD", "Intel Core i7-14700, 16GB DDR5, 1TB SSD", "AMD Ryzen 9 7900, 32GB DDR5, 2TB SSD", "AMD Ryzen 7 7700, 16GB DDR5, 512GB SSD", "Intel Core Ultra 7 265K, 32GB DDR5, 1TB SSD"},
                            new double[]{1699.0, 1299.0, 1549.0, 999.0, 1499.0},
                            "Reliable desktop computer built for multitasking, content creation, home office productivity, and seamless everyday performance."
                    )
            ),

            // 4. Gaming Desktop PCs
            new CategoryDefinition(
                    "Gaming Desktop PCs",
                    "Custom liquid-cooled gaming rigs, RTX 40-series monsters, and RGB battlestations",
                    "https://images.unsplash.com/photo-1587202372634-32705e3bf49c?auto=format&fit=crop&w=800&q=80",
                    24,
                    new String[]{
                            "https://images.unsplash.com/photo-1587202372634-32705e3bf49c?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"ASUS ROG Strix G16CHR Rig", "Alienware Aurora R16 Liquid Cooled", "Lenovo Legion Tower 7i Gen 8", "Corsair ONE i500 Compact Battlestation", "NZXT Player: Three Prime Elite", "CyberPowerPC Gamer Supreme", "iBUYPOWER Y60 Desktop", "MSI MEG Trident X2 Rig"},
                            new String[]{"RTX 4090 24GB, i9-14900KS, 64GB DDR5, 2TB SSD", "RTX 4080 Super 16GB, Ryzen 7 7800X3D, 32GB, 2TB", "RTX 4070 Ti Super 16GB, i7-14700K, 32GB, 1TB", "RTX 4090 24GB, Ryzen 9 7950X3D, 64GB, 4TB"},
                            new double[]{3899.0, 2799.0, 2199.0, 4199.0},
                            "Unrivaled gaming desktop engineered for 4K ray tracing, extreme frame rates, panoramic tempered glass acoustics, and custom liquid loops."
                    )
            ),

            // 5. Workstations & Mini PCs
            new CategoryDefinition(
                    "Workstations & Mini PCs",
                    "Compact mini computers, pro workstation powerhouses, and micro form factors",
                    "https://images.unsplash.com/photo-1517059224940-d4af9eec41b7?auto=format&fit=crop&w=800&q=80",
                    25,
                    new String[]{
                            "https://images.unsplash.com/photo-1517059224940-d4af9eec41b7?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"Apple Mac Studio M2 Max (32GB, 512GB)", "Apple Mac Studio M2 Ultra (64GB, 1TB)", "Apple Mac mini M4 (16GB, 256GB)", "Apple Mac mini M4 Pro (24GB, 512GB)", "Minisforum EliteMini UM790 Pro", "Beelink SER8 Ryzen 7 8845HS", "ASUS NUC 14 Pro Mini PC", "Dell Precision 3460 SFF Workstation"},
                            new String[]{"Pro Studio Edition", "Enterprise Workstation Build", "Compact Creator Tier", "Ultra-Density SFF Package"},
                            new double[]{1999.0, 3999.0, 599.0, 1399.0, 699.0, 649.0, 829.0, 1499.0},
                            "Ultra-compact form factor powerhouse delivering workstation-grade CPU rendering, multi-display 8K output, and quiet high-efficiency thermals."
                    )
            ),

            // 6. All-in-One Computers
            new CategoryDefinition(
                    "All-in-One Computers",
                    "Elegant all-in-one PCs with integrated high-resolution displays and minimal clutter",
                    "https://images.unsplash.com/photo-1547082299-de196ea013d6?auto=format&fit=crop&w=800&q=80",
                    26,
                    new String[]{
                            "https://images.unsplash.com/photo-1547082299-de196ea013d6?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"Apple iMac 24-inch 4.5K Retina M4", "Dell Inspiron 27 7720 All-in-One", "HP Envy 34-inch 5K Curved AIO", "Lenovo Yoga AIO 9i 32-inch 4K", "ASUS Zen AiO 24 M5401", "Acer Aspire C27 All-in-One"},
                            new String[]{"(16GB RAM, 512GB SSD, Silver)", "(24GB RAM, 1TB SSD, Space Gray)", "(32GB RAM, 1TB SSD, RTX 4050)", "(16GB RAM, 1TB SSD, Core i7)"},
                            new double[]{1499.0, 1899.0, 2399.0, 1199.0},
                            "Sleek all-in-one desktop integrating computer and high-resolution display into an impossibly slim chassis with studio-grade camera and speakers."
                    )
            ),

            // 7. Computer Monitors & Displays
            new CategoryDefinition(
                    "Computer Monitors & Displays",
                    "4K OLED, Ultrawide curved, and 240Hz+ gaming monitors for creators and gamers",
                    "https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=800&q=80",
                    27,
                    new String[]{
                            "https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1585792180666-f7347c490ee2?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1551645120-d70bfe84c826?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1593642634443-44adaa06623a?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"Samsung Odyssey OLED G9 49\" 240Hz Curved", "Samsung Odyssey Neo G8 32\" 4K Mini-LED", "Dell UltraSharp 32 4K USB-C Hub (U3223QE)", "Dell Alienware AW3423DWF 34\" QD-OLED", "LG UltraGear 45\" Curved OLED 240Hz (45GR95QE)", "LG UltraFine 32\" 4K OLED Pro", "Apple Studio Display 27\" 5K Retina", "ASUS ROG Swift OLED PG32UCDM 32\" 4K 240Hz", "BenQ PD3225U 32\" 4K Thunderbolt 3", "MSI MAG 341CQP QD-OLED 34\" 175Hz"},
                            new String[]{"Flagship Display Series", "Creator Color Grading Spec", "Esports Tournament Edition", "Ergonomic Arm Bundle"},
                            new double[]{1299.0, 999.0, 799.0, 899.0, 1399.0, 1999.0, 1899.0, 1299.0, 1099.0, 799.0},
                            "Professional monitor boasting breathtaking color accuracy (100% sRGB/DCI-P3), high dynamic range HDR, ergonomic stand, and USB-C power delivery."
                    )
            ),

            // 8. Computer Processors (CPUs)
            new CategoryDefinition(
                    "Computer Processors (CPUs)",
                    "Latest Intel Core Ultra, 14th Gen, and AMD Ryzen 9000/7000 desktop processors",
                    "https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?auto=format&fit=crop&w=800&q=80",
                    28,
                    new String[]{
                            "https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"Intel Core Ultra 9 285K 24-Core Desktop CPU", "Intel Core Ultra 7 265K 20-Core CPU", "Intel Core i9-14900KS 24-Core 6.2GHz CPU", "Intel Core i9-14900K 24-Core 6.0GHz CPU", "Intel Core i7-14700K 20-Core CPU", "AMD Ryzen 7 9800X3D 8-Core 3D V-Cache CPU", "AMD Ryzen 9 9950X 16-Core 32-Thread CPU", "AMD Ryzen 7 7800X3D 8-Core Gaming CPU", "AMD Ryzen 9 7950X3D 16-Core 3D V-Cache CPU", "AMD Ryzen Threadripper 7980X 64-Core HEDT CPU"},
                            new String[]{"Boxed Processor with Warranty", "Unlocked Overclocking Edition", "Retail Boxed Packaging", "Delidded High-Binned Spec"},
                            new double[]{589.0, 399.0, 689.0, 549.0, 399.0, 479.0, 649.0, 449.0, 599.0, 4999.0},
                            "Top-tier desktop CPU with PCIe 5.0 lanes, DDR5 support, advanced AI NPU acceleration, and exceptional single-core and multi-core performance."
                    )
            ),

            // 9. Graphics Cards (GPUs)
            new CategoryDefinition(
                    "Graphics Cards (GPUs)",
                    "NVIDIA GeForce RTX 4090, 4080 Super, and AMD Radeon RX 7900 graphics cards",
                    "https://images.unsplash.com/photo-1587202372616-b43abea06c2a?auto=format&fit=crop&w=800&q=80",
                    29,
                    new String[]{
                            "https://images.unsplash.com/photo-1587202372616-b43abea06c2a?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1591488320449-011701bb6704?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"NVIDIA GeForce RTX 4090 Founders Edition 24GB", "ASUS ROG Strix GeForce RTX 4090 24GB OC", "MSI GeForce RTX 4090 SUPRIM LIQUID X 24GB", "Gigabyte AORUS GeForce RTX 4090 Master 24GB", "NVIDIA GeForce RTX 4080 Super 16GB", "ASUS TUF Gaming GeForce RTX 4080 Super 16GB", "MSI GeForce RTX 4070 Ti Super 16GB Gaming X", "AMD Radeon RX 7900 XTX 24GB Reference", "Sapphire NITRO+ AMD Radeon RX 7900 XTX 24GB", "Sapphire PULSE AMD Radeon RX 7800 XT 16GB"},
                            new String[]{"Triple Fan Heatsink Design", "Factory Overclocked Edition", "Liquid Cooled Hybrid Shroud", "White Limited Edition"},
                            new double[]{1799.0, 1999.0, 1899.0, 1849.0, 999.0, 1049.0, 849.0, 949.0, 1079.0, 499.0},
                            "Flagship graphics card featuring ray tracing cores, DLSS 3.5 / FSR 3 frame generation, massive heatsink cooling, and 4K max-settings gaming power."
                    )
            ),

            // 10. Motherboards
            new CategoryDefinition(
                    "Motherboards",
                    "Intel Z890/Z790 and AMD X870E/B650 motherboards with PCIe 5.0 and Wi-Fi 7",
                    "https://images.unsplash.com/photo-1555680202-c86f0e12f086?auto=format&fit=crop&w=800&q=80",
                    30,
                    new String[]{
                            "https://images.unsplash.com/photo-1555680202-c86f0e12f086?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"ASUS ROG Maximus Z890 Hero LGA1851", "MSI MEG Z890 GODLIKE Flagship Board", "Gigabyte Z890 AORUS Master LGA1851", "ASUS ROG Strix Z790-E Gaming WiFi II", "MSI MAG Z790 Tomahawk WiFi", "ASUS ROG Crosshair X870E Hero AM5", "Gigabyte X870E AORUS Master AM5", "ASRock X870E Taichi AM5 Board", "NZXT N7 B650E Matte White AM5", "ASUS ProArt Z790-Creator WiFi"},
                            new String[]{"(Wi-Fi 7, PCIe 5.0 M.2)", "(Thunderbolt 4, 10GbE LAN)", "(DDR5 8000+ MHz OC)", "(Robust 20+1+2 Power Stages)"},
                            new double[]{649.0, 1199.0, 599.0, 439.0, 249.0, 699.0, 549.0, 499.0, 319.0, 469.0},
                            "High-end motherboard with robust power stages, PCIe 5.0 M.2 slots, DDR5 memory overclocking, Wi-Fi 7, and rear I/O USB4 connectivity."
                    )
            ),

            // 11. Memory & RAM (DDR5/DDR4)
            new CategoryDefinition(
                    "Memory & RAM (DDR5/DDR4)",
                    "High-speed DDR5 RGB gaming memory kits and low-profile workstation RAM",
                    "https://images.unsplash.com/photo-1562976540-1502c2145186?auto=format&fit=crop&w=800&q=80",
                    31,
                    new String[]{
                            "https://images.unsplash.com/photo-1562976540-1502c2145186?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"Corsair Dominator Titanium RGB DDR5 64GB 7200MHz", "Corsair Vengeance RGB DDR5 32GB 6000MHz CL30", "G.SKILL Trident Z5 RGB DDR5 64GB 6400MHz", "G.SKILL Trident Z5 Neo RGB DDR5 32GB 6000MHz", "Kingston Fury Beast RGB DDR5 64GB 6000MHz", "TeamGroup T-Force Delta RGB DDR5 32GB 7200MHz", "Crucial Pro DDR5 RAM 64GB 6000MHz Low-Profile", "Corsair Dominator Platinum RGB DDR5 32GB 6200MHz"},
                            new String[]{"Dual Channel Kit (2x32GB)", "Dual Channel Kit (2x16GB)", "Intel XMP 3.0 Ready", "AMD EXPO Profile Tuned"},
                            new double[]{319.0, 129.0, 219.0, 119.0, 199.0, 139.0, 169.0, 179.0},
                            "Ultra-fast DDR5 desktop memory engineered for maximum frequency stability, tight timings, aluminum heatspreaders, and vibrant RGB lighting."
                    )
            ),

            // 12. Computer Storage & SSDs
            new CategoryDefinition(
                    "Computer Storage & SSDs",
                    "Gen5 NVMe M.2 SSDs, high-capacity SATA drives, and portable rugged SSDs",
                    "https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?auto=format&fit=crop&w=800&q=80",
                    32,
                    new String[]{
                            "https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"Samsung 990 Pro 4TB PCIe 4.0 NVMe M.2 SSD", "Samsung 990 Pro 2TB PCIe 4.0 NVMe M.2", "Crucial T705 2TB PCIe 5.0 NVMe M.2 (14,500 MB/s)", "Crucial T700 4TB PCIe 5.0 NVMe with Heatsink", "Western Digital WD_BLACK SN850X 4TB NVMe", "Kingston KC3000 2TB PCIe 4.0 NVMe M.2", "Sabrent 8TB Rocket 4 Plus PCIe 4.0 NVMe", "Samsung T9 Portable SSD 4TB USB 3.2 Gen 2x2", "SanDisk Extreme PRO Portable SSD 4TB Rugged"},
                            new String[]{"with Integrated Aluminum Heatsink", "Bare Drive M.2 2280 Form Factor", "High Endurance 2400 TBW", "DirectStorage Optimized"},
                            new double[]{349.0, 179.0, 279.0, 489.0, 299.0, 149.0, 899.0, 379.0, 399.0},
                            "Blazing-fast solid state drive offering rapid game load times, seamless 4K video rendering, thermal throttling protection, and enterprise reliability."
                    )
            ),

            // 13. Power Supplies (PSUs)
            new CategoryDefinition(
                    "Power Supplies (PSUs)",
                    "80 PLUS Gold, Platinum, and Titanium modular power supplies with ATX 3.0",
                    "https://images.unsplash.com/photo-1587202372634-32705e3bf49c?auto=format&fit=crop&w=800&q=80",
                    33,
                    new String[]{
                            "https://images.unsplash.com/photo-1587202372634-32705e3bf49c?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"Corsair RM1000x Shift 1000W 80+ Gold Modular", "Corsair RM850x Shift 850W 80+ Gold Modular", "Seasonic PRIME TX-1300 Titanium 1300W", "Seasonic Focus GX-1000 ATX 3.0 1000W", "be quiet! Dark Power Pro 13 1300W Titanium", "ASUS ROG Thor 1200W Platinum II OLED", "ASUS ROG Strix 1000W Gold Aura Edition", "EVGA SuperNOVA 1000 G6 80+ Gold 1000W", "Thermaltake Toughpower GF3 1200W PCIe 5.0"},
                            new String[]{"ATX 3.0 & PCIe 5.0 12V-2x6 Ready", "Japanese 105C Capacitors", "Zero-RPM Silent Mode", "Fully Modular Flat Cables"},
                            new double[]{209.0, 159.0, 499.0, 179.0, 419.0, 389.0, 229.0, 189.0, 219.0},
                            "High-efficiency modular power supply unit delivering stable Japanese capacitors, zero-RPM silent fan mode, and native 12V-2x6 PCIe 5.0 cables."
                    )
            ),

            // 14. PC Cases & Chassis
            new CategoryDefinition(
                    "PC Cases & Chassis",
                    "Dual-chamber panoramic glass chassis, airflow mesh towers, and compact ITX cases",
                    "https://images.unsplash.com/photo-1587202372775-e229f172b9d7?auto=format&fit=crop&w=800&q=80",
                    34,
                    new String[]{
                            "https://images.unsplash.com/photo-1587202372775-e229f172b9d7?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"Lian Li O11 Dynamic EVO RGB Dual-Chamber", "Lian Li O11 Vision Chrome Panoramic Glass", "NZXT H9 Flow Dual-Chamber ATX Mid-Tower", "NZXT H6 Flow RGB Compact Dual-Chamber", "Fractal Design North Charcoal Black (Walnut Front)", "Fractal Design Torrent High-Airflow Tower", "Corsair 5000D Airflow Tempered Glass", "HYTE Y70 Touch Infinite with 4K Screen", "Phanteks NV7 Full Tower Showcase Chassis"},
                            new String[]{"Dual Chamber Architecture", "High Airflow Mesh Front", "Seamless Tempered Glass Panels", "Integrated Vertical GPU Mount"},
                            new double[]{169.0, 149.0, 159.0, 134.0, 139.0, 229.0, 174.0, 379.0, 219.0},
                            "Stunning computer chassis offering expansive component clearance, modular cable management routing, high-airflow ventilation, and tempered glass."
                    )
            ),

            // 15. Cooling Systems & Fans
            new CategoryDefinition(
                    "Cooling Systems & Fans",
                    "AIO liquid CPU coolers with LCD screens, high-static pressure fans, and tower heatsinks",
                    "https://images.unsplash.com/photo-1587202372634-32705e3bf49c?auto=format&fit=crop&w=800&q=80",
                    35,
                    new String[]{
                            "https://images.unsplash.com/photo-1587202372634-32705e3bf49c?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"NZXT Kraken Elite 360 RGB AIO with LCD", "Corsair iCUE LINK H150i LCD 360mm AIO", "Noctua NH-D15 G2 Dual-Tower Air Cooler", "Arctic Liquid Freezer III 360 A-RGB AIO", "Lian Li HydroShift LCD 360 RGB Cooler", "be quiet! Dark Rock Pro 5 High-End Air Cooler", "Thermalright Peerless Assassin 120 SE", "Lian Li UNI Fan TL LCD 120mm 3-Pack"},
                            new String[]{"360mm Radiator Liquid Cooler", "Dual-Tower Heatpipe Heatsink", "Customizable 2.8\" LCD Display", "Fluid Dynamic Bearing 2000 RPM"},
                            new double[]{279.0, 289.0, 149.0, 119.0, 259.0, 99.0, 39.0, 149.0},
                            "High-performance thermal solution engineered for silent acoustics, high heat dissipation TDP, fluid dynamic bearings, and customizable LCD screens."
                    )
            ),

            // 16. Keyboards & Mechanical
            new CategoryDefinition(
                    "Keyboards & Mechanical",
                    "Hot-swappable mechanical keyboards, wireless custom keyboards, and low-profile decks",
                    "https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=800&q=80",
                    36,
                    new String[]{
                            "https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1618384887929-16ec33fab9ef?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"Keychron Q1 Pro Wireless Custom Mechanical Keyboard", "Keychron Q3 Max Wireless 80% Metal Deck", "Logitech MX Keys S Advanced Illuminated Keyboard", "Logitech G PRO X TKL LIGHTSPEED Keyboard", "Razer BlackWidow V4 Pro Mechanical Keyboard", "Razer Huntsman V3 Pro TKL Analog Optical", "Wooting 60HE+ Rapid Trigger Hall Effect", "SteelSeries Apex Pro TKL Wireless HyperMagnetic", "ASUS ROG Azoth 75% Wireless Custom Keyboard OLED", "NuPhy Air75 V2 Ultra-Slim Mechanical"},
                            new String[]{"Hot-Swappable Gateron Red Switches", "Linear Magnetic Hall Effect Switches", "Tactile Silent Brown Switches", "Gasket Mounted Double-Shot PBT"},
                            new double[]{199.0, 214.0, 109.0, 199.0, 229.0, 219.0, 175.0, 249.0, 249.0, 119.0},
                            "Premium mechanical keyboard with hot-swappable switches, double-shot PBT keycaps, sound dampening foams, multi-device connectivity, and RGB."
                    )
            ),

            // 17. Computer Mice & Trackpads
            new CategoryDefinition(
                    "Computer Mice & Trackpads",
                    "Ultralight wireless esports mice, ergonomic trackballs, and precision glass trackpads",
                    "https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?auto=format&fit=crop&w=800&q=80",
                    37,
                    new String[]{
                            "https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"Logitech MX Master 3S Wireless Performance Mouse", "Logitech G Pro X Superlight 2 Wireless (60g)", "Logitech Lift Vertical Ergonomic Wireless Mouse", "Razer DeathAdder V3 Pro Wireless Ergonomic", "Razer Viper V3 Pro Esports Wireless (54g)", "Razer Basilisk V3 Pro Wireless Mouse", "Apple Magic Trackpad Multi-Touch Glass Surface", "Pulsar X2V2 Wireless Mini (52g)", "Finalmouse UltralightX Lion Carbon Fiber Mouse", "SteelSeries Aerox 3 Wireless Ultra-Light"},
                            new String[]{"32,000 DPI Optical Sensor", "8,000Hz HyperPolling Wireless", "Quiet Clicks & MagSpeed Scroll", "PTFE Skates & Ergonomic Grip"},
                            new double[]{99.0, 159.0, 69.0, 149.0, 159.0, 159.0, 149.0, 99.0, 189.0, 99.0},
                            "Flawless optical sensor mouse offering ultra-lightweight ergonomics, optical switches, low-latency wireless polling, and PTFE feet glide."
                    )
            ),

            // 18. Computer Audio & Headsets
            new CategoryDefinition(
                    "Computer Audio & Headsets",
                    "Spatial audio gaming headsets, studio monitor headphones, and podcast USB mics",
                    "https://images.unsplash.com/photo-1546435770-a3e426bf472b?auto=format&fit=crop&w=800&q=80",
                    38,
                    new String[]{
                            "https://images.unsplash.com/photo-1546435770-a3e426bf472b?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1590658268037-6bf12165a8df?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"SteelSeries Arctis Nova Pro Wireless Headset", "HyperX Cloud III Wireless Headset (120hr Battery)", "Audio-Technica ATH-M50xBT2 Wireless Monitors", "Sennheiser HD 660S2 Audiophile Headphones", "Corsair Virtuoso RGB Wireless XT Headset", "Rode NT-USB+ Professional Studio USB Mic", "Shure MV7+ USB/XLR Podcast Microphone DSP", "Elgato Wave:3 Premium USB Condenser Mic", "Schiit Magni & Modi Desktop DAC/Amp Stack", "Audioengine A2+ Wireless Desktop Speakers Pair"},
                            new String[]{"Active Noise Cancellation & Hot-Swap Battery", "Planar Magnetic Drivers 3D Spatial Audio", "Broadcast-Grade Cardioid Capsule", "High-Resolution 24-bit/96kHz Audio"},
                            new double[]{349.0, 169.0, 199.0, 499.0, 269.0, 169.0, 279.0, 149.0, 240.0, 269.0},
                            "Crystal-clear acoustic audio reproduction, high-resolution drivers, noise-cancelling boom microphone, and comfortable memory foam earpads."
                    )
            ),

            // 19. Networking & Wi-Fi Routers
            new CategoryDefinition(
                    "Networking & Wi-Fi Routers",
                    "Wi-Fi 7 quad-band gaming routers, mesh Wi-Fi systems, and 10GbE network switches",
                    "https://images.unsplash.com/photo-1544197150-b99a580bb7a8?auto=format&fit=crop&w=800&q=80",
                    39,
                    new String[]{
                            "https://images.unsplash.com/photo-1544197150-b99a580bb7a8?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1558494949-ef010cbdcc31?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"ASUS ROG Rapture GT-BE98 Pro Wi-Fi 7 Router", "Netgear Nighthawk RS700S Tri-Band WiFi 7 (19Gbps)", "TP-Link Archer BE800 Tri-Band WiFi 7 Router", "TP-Link Deco BE85 Whole Home Mesh WiFi 7 (2-Pack)", "Ubiquiti UniFi Cloud Gateway Ultra (UCG-Ultra)", "Ubiquiti UniFi U7 Pro WiFi 7 Ceiling AP", "ASUS PCE-BE92BT WiFi 7 PCIe Adapter Card", "QNAP QSW-M408-4C 10GbE Managed Desktop Switch", "Netgear Orbi 970 Quad-Band WiFi 7 Mesh (3-Pack)"},
                            new String[]{"Quad-Band 320MHz Channels with MLO", "Dual 10Gbps Ethernet WAN/LAN Ports", "Whole Home Ultra-Coverage Seamless Roaming", "Low-Latency Gaming QoS Priority"},
                            new double[]{799.0, 699.0, 499.0, 999.0, 129.0, 189.0, 79.0, 389.0, 2299.0},
                            "Ultra-low latency Wi-Fi 7 multi-gigabit networking hardware with 320MHz channels, Multi-Link Operation (MLO), and advanced network security."
                    )
            ),

            // 20. Docks, Hubs & Webcams
            new CategoryDefinition(
                    "Docks, Hubs & Webcams",
                    "Thunderbolt 4 docking stations, 4K HDR webcams, streaming consoles, and desk lights",
                    "https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?auto=format&fit=crop&w=800&q=80",
                    40,
                    new String[]{
                            "https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?auto=format&fit=crop&w=800&q=80",
                            "https://images.unsplash.com/photo-1586953208448-b95a79798f07?auto=format&fit=crop&w=800&q=80"
                    },
                    () -> generateItems(
                            new String[]{"CalDigit TS4 Thunderbolt 4 Dock 18-Port (98W)", "Anker PowerExpand 568 USB4 Docking Station (11-in-1)", "OWC Thunderbolt Go Dock with Internal Power", "Corsair Elgato Stream Deck MK.2 (15 LCD Keys)", "Corsair Elgato Stream Deck + with Dials", "Elgato Facecam Pro 4K60 True Ultra HD Webcam", "Logitech Brio 4K Ultra HD Webcam with HDR", "BenQ ScreenBar Halo LED Wireless Monitor Light Bar", "Anker 778 Thunderbolt 4 Docking Station 12-in-1", "Twelve South Curve Riser Desktop Stand for Displays"},
                            new String[]{"Dual 6K/8K Display Support", "98W Power Delivery Fast Charging", "Sony STARVIS Sensor 4K 60fps", "Customizable Touch Strip & Keys"},
                            new double[]{399.0, 249.0, 319.0, 149.0, 199.0, 299.0, 169.0, 179.0, 379.0, 79.0},
                            "Professional desktop productivity accessory providing comprehensive high-speed port expansion, 4K/8K video streaming, and rapid USB power delivery."
                    )
            )
    );

    private static List<ProductDefinition> generateItems(String[] brands, String[] modifiers, double[] prices, String baseDesc) {
        List<ProductDefinition> list = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String brand = brands[i % brands.length];
            String mod = modifiers[i % modifiers.length];
            double price = prices[i % prices.length] + ((i * 13) % 120);
            String name = brand + " " + mod + " #" + (i + 1);
            list.add(new ProductDefinition(name, price, baseDesc));
        }
        return list;
    }
}
