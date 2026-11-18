package com.example

import com.example.auth.AdminFirebaseAuthHelper
import com.example.data.MarketplaceSeedData
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test

class LubumarketUnitTest {

    @Test
    fun testCommuneDeliveryFees() {
        val settings = AppConfigSettings()
        assertEquals(2.00, settings.feeFor(CommuneLubumbashi.LUBUMBASHI), 0.001)
        assertEquals(2.50, settings.feeFor(CommuneLubumbashi.KENYA), 0.001)
        assertEquals(2.50, settings.feeFor(CommuneLubumbashi.KAMALONDO), 0.001)
        assertEquals(3.00, settings.feeFor(CommuneLubumbashi.KAMPEMBA), 0.001)
        assertEquals(3.00, settings.feeFor(CommuneLubumbashi.KATUBA), 0.001)
        assertEquals(3.50, settings.feeFor(CommuneLubumbashi.RUASHI), 0.001)
        assertEquals(4.00, settings.feeFor(CommuneLubumbashi.ANNEXE), 0.001)
    }

    @Test
    fun testFivePercentPlatformCommissionCalculation() {
        val grossAmount = 100.00
        val commissionPct = 5.0
        val commissionUsd = grossAmount * (commissionPct / 100.0)
        val netUsd = grossAmount - commissionUsd
        val request = WithdrawalRequest(
            id = "RET-1001",
            vendorId = "vendor_1",
            vendorName = "Katanga Tech",
            amountGrossUsd = grossAmount,
            commissionPercent = commissionPct,
            commissionUsd = commissionUsd,
            amountNetUsd = netUsd,
            paymentMethod = PaymentMethod.MPESA,
            accountDetails = "+243 820 555 777",
            status = WithdrawalStatus.EN_ATTENTE
        )

        assertEquals(5.00, request.commissionUsd, 0.001)
        assertEquals(95.00, request.amountNetUsd, 0.001)
        assertEquals(WithdrawalStatus.EN_ATTENTE, request.status)
    }

    @Test
    fun testWithdrawalMethodsIncludeMobileMoneyAndBank() {
        val withdrawalMethods = PaymentMethod.values().filter { it.isWithdrawalMethod }
        assertTrue(withdrawalMethods.contains(PaymentMethod.MPESA))
        assertTrue(withdrawalMethods.contains(PaymentMethod.AIRTEL_MONEY))
        assertTrue(withdrawalMethods.contains(PaymentMethod.ORANGE_MONEY))
        assertTrue(withdrawalMethods.contains(PaymentMethod.VIREMENT_BANCAIRE))
        assertFalse(withdrawalMethods.contains(PaymentMethod.CASH_LIVRAISON))
    }

    @Test
    fun testFourWithdrawalStatuses() {
        assertEquals(4, WithdrawalStatus.values().size)
        assertEquals(WithdrawalStatus.EN_ATTENTE, WithdrawalStatus.fromFirestore("EN_ATTENTE"))
        assertEquals(WithdrawalStatus.APPROUVE, WithdrawalStatus.fromFirestore("APPROUVE"))
        assertEquals(WithdrawalStatus.PAYE, WithdrawalStatus.fromFirestore("PAYE"))
        assertEquals(WithdrawalStatus.REFUSE, WithdrawalStatus.fromFirestore("REFUSE"))
    }

    @Test
    fun testSevenDeliveryStatuses() {
        val statuses = OrderStatus.values()
        assertEquals(7, statuses.size)
        assertEquals(OrderStatus.RECU, statuses[0])
        assertEquals(OrderStatus.CONFIRME, statuses[1])
        assertEquals(OrderStatus.EN_PREPARATION, statuses[2])
        assertEquals(OrderStatus.EN_ATTENTE_LIVREUR, statuses[3])
        assertEquals(OrderStatus.EN_COURS_LIVRAISON, statuses[4])
        assertEquals(OrderStatus.LIVREE, statuses[5])
        assertEquals(OrderStatus.ANNULEE, statuses[6])
    }

    @Test
    fun testSeedDataAndRealProductPhotos() {
        val products = MarketplaceSeedData.starterProducts
        assertTrue(products.isNotEmpty())
        products.forEach { prod ->
            assertTrue("Seed product ${prod.name} must have imageUrls", prod.imageUrls.isNotEmpty())
            assertEquals(ProductModerationStatus.APPROVED, prod.moderationStatus)
        }
        val first = products[0]
        val expectedDiscounted = first.priceUsd * (1 - first.discountPercent / 100.0)
        assertEquals(expectedDiscounted, first.discountedPriceUsd(), 0.001)
    }

    @Test
    fun testModerationAndDisputeEnums() {
        assertEquals(5, AccountStatus.values().size)
        assertEquals(4, ProductModerationStatus.values().size)
        assertEquals(4, DisputeStatus.values().size)
        assertEquals(3, AdminAuthMethod.values().size)
    }

    @Test
    fun testNormalizeCongoPhoneE164() {
        assertEquals("+243997123456", normalizeCongoPhoneE164("+243 997 123 456"))
        assertEquals("+243997123456", normalizeCongoPhoneE164("0997123456"))
        assertEquals("+243820555777", normalizeCongoPhoneE164("243-820-555-777"))
        assertEquals("+243812345678", normalizeCongoPhoneE164("812345678"))
    }

    @Test
    fun testAuthorizedOwnerEmailCheck() {
        assertTrue(AdminFirebaseAuthHelper.isAuthorizedOwnerEmail("nkambajunior448@gmail.com"))
        assertTrue(AdminFirebaseAuthHelper.isAuthorizedOwnerEmail("  NKAMBAJUNIOR448@GMAIL.COM "))
        assertFalse(AdminFirebaseAuthHelper.isAuthorizedOwnerEmail("autre@gmail.com"))
    }
}
