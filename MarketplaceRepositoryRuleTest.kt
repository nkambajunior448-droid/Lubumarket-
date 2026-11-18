package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MarketplaceRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun registerClient_andPlaceOrder_succeedsAndScopesToOwner() = runBlocking {
        val clientUid = signInTestUser("client_${UUID.randomUUID().toString().take(6)}@lubum.cd")
        val repo = MarketplaceFirestoreRepository(firestore, auth)

        val regResult = withTimeout(TIMEOUT_MS) {
            repo.registerUserProfile(
                PendingRegistrationData(
                    role = UserRole.CLIENT,
                    fullName = "Patrick Mwamba",
                    phone = "+243997123456",
                    emailHint = "patrick@lubum.cd",
                    commune = "Lubumbashi",
                    quartier = "Golf"
                )
            )
        }
        assertTrue(regResult.isSuccess)
        assertEquals(AccountStatus.APPROVED, regResult.getOrThrow().accountStatus)

        val address = DeliveryAddress(
            fullName = "Patrick Mwamba",
            phone = "+243997123456",
            type = DeliveryType.DOMICILE,
            commune = CommuneLubumbashi.LUBUMBASHI,
            quartier = "Golf",
            avenue = "Av. des Mesanges 14",
            referenceLandmark = "En face station Total"
        )
        val item = CartItem(MarketplaceSeedData.starterProducts[0], 1)

        val orderResult = withTimeout(TIMEOUT_MS) {
            repo.placeOrder(address, listOf(item), 2.0, 5.0, PaymentMethod.MPESA, null)
        }
        assertTrue(orderResult.isSuccess)
        val order = orderResult.getOrThrow()
        assertFalse(order.paymentConfirmed)
        assertFalse(order.commissionCredited)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeCustomerOrders(clientUid).first { list -> list.any { it.id == order.id } }
        }
        assertTrue(observed.any { it.id == order.id })

        val disputeRes = withTimeout(TIMEOUT_MS) {
            repo.createOrderDispute(order.id, "Patrick Mwamba", "Produit incomplet à la livraison")
        }
        assertTrue(disputeRes.isSuccess)
    }

    @Test
    fun registerVendor_startsPending_andCannotCreateWithdrawalUntilApproved() = runBlocking {
        val vendorEmail = "vendor_${UUID.randomUUID().toString().take(6)}@lubum.cd"
        signInTestUser(vendorEmail)
        val repo = MarketplaceFirestoreRepository(firestore, auth)

        val regResult = withTimeout(TIMEOUT_MS) {
            repo.registerUserProfile(
                PendingRegistrationData(
                    role = UserRole.VENDEUR,
                    fullName = "Jean Kasongo",
                    phone = "+243820555777",
                    emailHint = vendorEmail,
                    commune = "Lubumbashi",
                    quartier = "Centre",
                    storeName = "Kasongo Tech"
                )
            )
        }
        assertTrue(regResult.isSuccess)
        val vendorProfile = regResult.getOrThrow()
        assertEquals(AccountStatus.PENDING, vendorProfile.accountStatus)

        val withdrawalResult = withTimeout(TIMEOUT_MS) {
            repo.createVendorWithdrawal(
                vendor = vendorProfile,
                amountGrossUsd = 50.0,
                commissionPercent = 5.0,
                paymentMethod = PaymentMethod.MPESA,
                accountDetails = "+243820555777",
                availableBalanceUsd = 100.0
            )
        }
        assertTrue(withdrawalResult.isFailure)
        val firestoreEx = withdrawalResult.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun principalAdmin_paymentConfirmation_isSingleCreditIdempotent() = runBlocking {
        val clientUid = signInTestUser("buyer_${UUID.randomUUID().toString().take(6)}@lubum.cd")
        val repo = MarketplaceFirestoreRepository(firestore, auth)
        repo.registerUserProfile(
            PendingRegistrationData(UserRole.CLIENT, "Buyer Test", "+243990000111", "buyer@lubum.cd", "Lubumbashi", "Centre")
        )
        val address = DeliveryAddress(
            fullName = "Buyer Test",
            phone = "+243990000111",
            type = DeliveryType.DOMICILE,
            commune = CommuneLubumbashi.LUBUMBASHI,
            quartier = "Centre",
            avenue = "Av. Mobutu 10",
            referenceLandmark = "Place de la Poste"
        )
        val order = repo.placeOrder(
            address,
            listOf(CartItem(MarketplaceSeedData.starterProducts[0], 1)),
            2.0,
            5.0,
            PaymentMethod.MPESA,
            null
        ).getOrThrow()

        signInPrincipalAdmin()
        val firstConfirm = withTimeout(TIMEOUT_MS) {
            repo.confirmOrderPayment(order.id, "MPESA-REF-9911")
        }
        assertTrue("Principal admin should confirm payment once", firstConfirm.isSuccess)

        val secondConfirm = withTimeout(TIMEOUT_MS) {
            repo.confirmOrderPayment(order.id, "MPESA-REF-9911")
        }
        assertTrue("Second confirmation must be rejected to avoid double commission credit", secondConfirm.isFailure)

        val adminOrders = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeOrdersForAdmin().first { list -> list.any { it.id == order.id && it.paymentConfirmed } }
        }
        val confirmed = adminOrders.first { it.id == order.id }
        assertTrue(confirmed.paymentConfirmed)
        assertTrue(confirmed.commissionCredited)
        assertEquals("MPESA-REF-9911", confirmed.paymentReference)
        assertEquals(clientUid, confirmed.customerId)
    }

    @Test
    fun unauthenticatedCaller_observeOrders_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = MarketplaceFirestoreRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeProducts().first()
            }
        } catch (e: Exception) {
            caught = e
        }
        assertNotNull(caught)
        val firestoreEx = caught?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun nonAdmin_cannotWriteOwnerWalletOrWithdrawals() = runBlocking {
        signInTestUser("client_nonadmin_${UUID.randomUUID().toString().take(6)}@lubum.cd")
        val repo = MarketplaceFirestoreRepository(firestore, auth)

        val walletWrite = withTimeout(TIMEOUT_MS) {
            repo.saveOwnerWalletConfig(
                OwnerWalletConfig(
                    ownerId = "hacker",
                    preferredMethod = PaymentMethod.MPESA,
                    accountDetails = "+243999999999",
                    accountHolderName = "Hacker"
                )
            )
        }
        assertTrue(walletWrite.isFailure)
        val firestoreEx = walletWrite.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun registerSuperAdmin_onlyAllowedForVerifiedOwner_andDeniedForOrdinaryUsers() = runBlocking {
        signInTestUser("ordinary_${UUID.randomUUID().toString().take(6)}@lubum.cd")
        val repo = MarketplaceFirestoreRepository(firestore, auth)

        val deniedReg = withTimeout(TIMEOUT_MS) {
            repo.registerSuperAdminProfile(
                fullName = "Ordinary Intruder",
                email = "ordinary@lubum.cd",
                phone = "+243990000111",
                authMethod = AdminAuthMethod.PHONE_SMS
            )
        }
        assertTrue("Ordinary user must never be allowed to create Super Admin", deniedReg.isFailure)
        assertFalse(repo.checkIsAdmin(auth.currentUser))

        val ownerUid = signInPrincipalAdmin()
        val ownerReg = withTimeout(TIMEOUT_MS) {
            repo.registerSuperAdminProfile(
                fullName = "Junior Nkamba",
                email = MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL,
                phone = "+243997123456",
                authMethod = AdminAuthMethod.GMAIL_AND_SMS
            )
        }
        assertTrue("Verified owner must succeed registering Super Admin", ownerReg.isSuccess)
        val profile = repo.getSuperAdminProfile(ownerUid)
        assertNotNull(profile)
        assertEquals("SUPER_ADMIN", profile?.role)
        assertEquals("+243997123456", profile?.phone)
        assertTrue(profile?.isActive == true)
    }

    private companion object {
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
