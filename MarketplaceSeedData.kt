package com.example.data

import com.example.model.*

object MarketplaceSeedData {
    val starterProducts: List<Product> = listOf(
        Product(
            id = "prod_1",
            name = "Tecno Spark 20 Pro+ (256GB / 8GB)",
            description = "Écran AMOLED incurvé 120Hz, Caméra 108MP Ultra Claire, Processeur Helio G99 Ultimate, Batterie 5000mAh 33W Fast Charge. Garantie 13 mois Carlcare Lubumbashi.",
            priceUsd = 185.00,
            category = ProductCategory.TECH,
            categoryName = ProductCategory.TECH.label,
            sellerId = "seed_vendor_1",
            sellerName = "Katanga Tech Express",
            sellerCommune = "Lubumbashi Centre",
            stock = 14,
            rating = 4.8,
            reviewCount = 52,
            isPopular = true,
            isBestOffer = true,
            discountPercent = 8,
            isNew = true,
            imageUrls = listOf(
                "res://img_prod_smartphone_1794545054563",
                "res://img_prod_solaire_1794545055327"
            ),
            moderationStatusName = ProductModerationStatus.APPROVED.name
        ),
        Product(
            id = "prod_2",
            name = "Sac de Farine de Maïs Manne du Ciel 25kg",
            description = "Farine blanche extra de maïs cultivé au Haut-Katanga, texture fine idéale pour le Bukari (Fufu). Sac scellé de 25 kg.",
            priceUsd = 24.50,
            category = ProductCategory.ALIMENTATION,
            categoryName = ProductCategory.ALIMENTATION.label,
            sellerId = "seed_vendor_2",
            sellerName = "Marché Mzee Alimentation",
            sellerCommune = "Commune de la Kenya",
            stock = 45,
            rating = 4.9,
            reviewCount = 118,
            isPopular = true,
            isBestOffer = false,
            discountPercent = 0,
            isNew = false,
            imageUrls = listOf("res://img_prod_alimentation_1794545054755"),
            moderationStatusName = ProductModerationStatus.APPROVED.name
        ),
        Product(
            id = "prod_3",
            name = "Ventilateur Solaire Rechargeable 16\" avec Panneau & 2 Ampoules",
            description = "Indispensable pour Lubumbashi ! Batterie Lithium 12V 4.5Ah longue autonomie (8-12h), port USB charge téléphone, panneau solaire 20W inclus + veilleuse LED.",
            priceUsd = 42.00,
            category = ProductCategory.MAISON,
            categoryName = ProductCategory.MAISON.label,
            sellerId = "seed_vendor_3",
            sellerName = "Électro Katanga Solaire",
            sellerCommune = "Commune de Kampemba",
            stock = 20,
            rating = 4.7,
            reviewCount = 39,
            isPopular = true,
            isBestOffer = true,
            discountPercent = 10,
            isNew = true,
            imageUrls = listOf("res://img_prod_solaire_1794545055327"),
            moderationStatusName = ProductModerationStatus.APPROVED.name
        ),
        Product(
            id = "prod_4",
            name = "Robe Pagne Africain Wax Katanga Couture",
            description = "Création exclusive taillée à la main à Lubumbashi avec du vrai tissu Wax Hollandais de haute qualité. Coupe moderne cintrée très élégante.",
            priceUsd = 35.00,
            category = ProductCategory.MODE,
            categoryName = ProductCategory.MODE.label,
            sellerId = "seed_vendor_4",
            sellerName = "Boutique La Grâce Lubum",
            sellerCommune = "Commune de Kamalondo",
            stock = 8,
            rating = 4.9,
            reviewCount = 27,
            isPopular = false,
            isBestOffer = false,
            discountPercent = 0,
            isNew = true,
            imageUrls = listOf("res://img_prod_mode_beaute_1794545055955"),
            moderationStatusName = ProductModerationStatus.APPROVED.name
        ),
        Product(
            id = "prod_5",
            name = "Carton Poissons Frais Thomson (Chinchard 18+ / 20kg)",
            description = "Arrivage frais conservé en chambre froide moderne. Carton scellé de 20 kg, poissons de calibre moyen très appréciés.",
            priceUsd = 58.00,
            category = ProductCategory.ALIMENTATION,
            categoryName = ProductCategory.ALIMENTATION.label,
            sellerId = "seed_vendor_2",
            sellerName = "Marché Mzee Alimentation",
            sellerCommune = "Commune de la Kenya",
            stock = 30,
            rating = 4.6,
            reviewCount = 64,
            isPopular = true,
            isBestOffer = false,
            discountPercent = 0,
            isNew = false,
            imageUrls = listOf("res://img_prod_alimentation_1794545054755"),
            moderationStatusName = ProductModerationStatus.APPROVED.name
        ),
        Product(
            id = "prod_6",
            name = "Samsung Galaxy A15 4G (128GB / 6GB)",
            description = "Écran Super AMOLED 90Hz lumineux, Triple caméra 50MP, autonomie 2 jours avec batterie 5000mAh, One UI 6. Scellé avec reçu officiel.",
            priceUsd = 160.00,
            category = ProductCategory.TECH,
            categoryName = ProductCategory.TECH.label,
            sellerId = "seed_vendor_1",
            sellerName = "Katanga Tech Express",
            sellerCommune = "Lubumbashi Centre",
            stock = 11,
            rating = 4.7,
            reviewCount = 43,
            isPopular = false,
            isBestOffer = true,
            discountPercent = 5,
            isNew = false,
            imageUrls = listOf("res://img_prod_smartphone_1794545054563"),
            moderationStatusName = ProductModerationStatus.APPROVED.name
        ),
        Product(
            id = "prod_7",
            name = "Kit Perceuse à Percussion Ingco 850W + Boîte 101 Accessoires",
            description = "Outil professionnel robuste pour travaux maison et chantiers à Lubumbashi. Mandrin 13mm, vitesse variable réversible, coffret antichoc.",
            priceUsd = 55.00,
            category = ProductCategory.QUINCAILLERIE,
            categoryName = ProductCategory.QUINCAILLERIE.label,
            sellerId = "seed_vendor_5",
            sellerName = "Quincaillerie du Cuivre",
            sellerCommune = "Commune de Katuba",
            stock = 15,
            rating = 4.8,
            reviewCount = 21,
            isPopular = false,
            isBestOffer = false,
            discountPercent = 0,
            isNew = true,
            imageUrls = listOf("res://img_prod_quincaillerie_1794545056313"),
            moderationStatusName = ProductModerationStatus.APPROVED.name
        ),
        Product(
            id = "prod_8",
            name = "Pack Soins Corps Karité & Huile de Coco Pure (Katanga Bio)",
            description = "Beurre de karité pur non raffiné + huile de coco vierge pressée à froid. Hydratation profonde pour peaux sèches et protection solaire naturelle.",
            priceUsd = 15.00,
            category = ProductCategory.BEAUTE,
            categoryName = ProductCategory.BEAUTE.label,
            sellerId = "seed_vendor_4",
            sellerName = "Boutique La Grâce Lubum",
            sellerCommune = "Commune de Kamalondo",
            stock = 25,
            rating = 5.0,
            reviewCount = 48,
            isPopular = true,
            isBestOffer = true,
            discountPercent = 15,
            isNew = false,
            imageUrls = listOf("res://img_prod_mode_beaute_1794545055955"),
            moderationStatusName = ProductModerationStatus.APPROVED.name
        )
    )

    val starterCategories: List<CategoryConfig> = ProductCategory.values()
        .filterNot { it == ProductCategory.TOUS }
        .mapIndexed { idx, cat ->
            CategoryConfig(
                id = "cat_${cat.name.lowercase()}",
                name = cat.label,
                iconName = cat.iconName,
                isActive = true,
                displayOrder = idx + 1
            )
        }

    val starterPromotions: List<PromotionBanner> = listOf(
        PromotionBanner(
            id = "promo_livraison",
            title = "Livraison Rapide à Domicile & Bureau",
            subtitle = "7 communes desservies : Lubumbashi, Kenya, Katuba, Kampemba, Kamalondo, Ruashi, Annexe.",
            badgeText = "LIVRAISON EXPRESS",
            isActive = true
        )
    )
}
