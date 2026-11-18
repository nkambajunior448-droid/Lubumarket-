const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const CLIENT_UID = "client_alice";
const OTHER_UID = "client_bob";
const VENDOR_UID = "vendor_katanga";
const DRIVER_UID = "driver_christian";
const ADMIN_UID = "owner_admin";
const ADMIN_EMAIL = "nkambajunior448@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  });
}

function spoofedAdminContext() {
  return testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated caller: denied on all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("products").get());
  await assertFails(unauthDb.collection("orders").get());
  await assertFails(unauthDb.collection("users").doc(CLIENT_UID).get());
  await assertFails(unauthDb.collection("owner_wallet").get());
  await assertFails(unauthDb.collection("vendor_withdrawals").get());
  await assertFails(unauthDb.collection("order_disputes").get());
  await assertFails(unauthDb.collection("admin_audit_logs").get());
});

test("Registration & Role Escalation Guard: client can register as CLIENT/APPROVED, cannot self-assign ADMIN or APPROVED vendor", async () => {
  const aliceDb = testEnv.authenticatedContext(CLIENT_UID, { email: "alice@lubum.cd", email_verified: true }).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(CLIENT_UID).set({
      userId: CLIENT_UID,
      fullName: "Alice Mwamba",
      phone: "+243997123456",
      email: "alice@lubum.cd",
      role: "CLIENT",
      accountStatus: "APPROVED",
      commune: "Lubumbashi",
      quartier: "Golf",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Cannot escalate own role or status on update
  await assertFails(
    aliceDb.collection("users").doc(CLIENT_UID).update({
      role: "VENDEUR",
      updatedAt: serverTimestamp(),
    })
  );

  // Public registration cannot register as ADMIN
  const bobDb = testEnv.authenticatedContext(OTHER_UID, { email: "bob@lubum.cd", email_verified: true }).firestore();
  await assertFails(
    bobDb.collection("users").doc(OTHER_UID).set({
      userId: OTHER_UID,
      fullName: "Bob Hacker",
      phone: "+243997000111",
      email: "bob@lubum.cd",
      role: "ADMIN",
      accountStatus: "APPROVED",
      commune: "Lubumbashi",
      quartier: "Centre",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Vendor cannot self-approve on registration
  const vendorDb = testEnv.authenticatedContext(VENDOR_UID, { email: "vendor@lubum.cd", email_verified: true }).firestore();
  await assertFails(
    vendorDb.collection("users").doc(VENDOR_UID).set({
      userId: VENDOR_UID,
      fullName: "Vendeur Test",
      phone: "+243820555777",
      email: "vendor@lubum.cd",
      role: "VENDEUR",
      accountStatus: "APPROVED",
      commune: "Lubumbashi",
      quartier: "Centre",
      storeName: "Ma Boutique",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Vendor registration with PENDING succeeds
  await assertSucceeds(
    vendorDb.collection("users").doc(VENDOR_UID).set({
      userId: VENDOR_UID,
      fullName: "Vendeur Test",
      phone: "+243820555777",
      email: "vendor@lubum.cd",
      role: "VENDEUR",
      accountStatus: "PENDING",
      commune: "Lubumbashi",
      quartier: "Centre",
      storeName: "Ma Boutique",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});

test("Email Spoofing, Admin Isolation & Audit Logs Immutability", async () => {
  const spoofDb = spoofedAdminContext().firestore();
  await assertFails(
    spoofDb.collection("owner_wallet").doc("config").set({
      ownerId: "spoof_uid",
      preferredMethod: "MPESA",
      accountDetails: "+243800000000",
      accountHolderName: "Spoof",
      updatedAt: serverTimestamp(),
    })
  );

  const realAdminDb = adminContext().firestore();
  await assertSucceeds(
    realAdminDb.collection("owner_wallet").doc("config").set({
      ownerId: ADMIN_UID,
      preferredMethod: "MPESA",
      accountDetails: "+243997000001",
      accountHolderName: "Proprietaire Lubumarket",
      updatedAt: serverTimestamp(),
    })
  );

  // Admin creates audit log
  await assertSucceeds(
    realAdminDb.collection("admin_audit_logs").doc("log_1").set({
      adminId: ADMIN_UID,
      adminEmail: ADMIN_EMAIL,
      action: "ADMIN_INIT",
      targetType: "SYSTEM",
      targetId: ADMIN_UID,
      details: "Initialisation administrateur principal",
      createdAt: serverTimestamp(),
    })
  );

  // Audit logs cannot be modified or deleted even by Admin
  await assertFails(
    realAdminDb.collection("admin_audit_logs").doc("log_1").update({ details: "tampered" })
  );
  await assertFails(
    realAdminDb.collection("admin_audit_logs").doc("log_1").delete()
  );

  // Regular vendor cannot read owner_wallet, owner_withdrawals, or admin_audit_logs
  const vendorDb = testEnv.authenticatedContext(VENDOR_UID).firestore();
  await assertFails(vendorDb.collection("owner_wallet").doc("config").get());
  await assertFails(vendorDb.collection("owner_withdrawals").get());
  await assertFails(vendorDb.collection("admin_audit_logs").get());

  // Ordinary client or spoofed account CANNOT self-register in /admins
  const randomPhoneDb = testEnv.authenticatedContext("random_phone_uid", { phone_number: "+243990000999" }).firestore();
  await assertFails(
    randomPhoneDb.collection("admins").doc("random_phone_uid").set({
      adminId: "random_phone_uid",
      fullName: "Hacker Phone",
      email: "hacker@gmail.com",
      phone: "+243990000999",
      role: "SUPER_ADMIN",
      authMethod: "PHONE_SMS",
      isActive: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    spoofDb.collection("admins").doc("spoof_uid").set({
      adminId: "spoof_uid",
      fullName: "Spoof Owner",
      email: ADMIN_EMAIL,
      role: "SUPER_ADMIN",
      authMethod: "GMAIL",
      isActive: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // ONLY verified owner email can bootstrap a SUPER_ADMIN profile (including linked phone UID)
  await assertSucceeds(
    realAdminDb.collection("admins").doc("owner_phone_uid").set({
      adminId: "owner_phone_uid",
      fullName: "Junior Nkamba",
      email: ADMIN_EMAIL,
      phone: "+243997123456",
      role: "SUPER_ADMIN",
      authMethod: "GMAIL_AND_SMS",
      isActive: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Authorized Super Admin phone UID can read admin collections
  const ownerPhoneDb = testEnv.authenticatedContext("owner_phone_uid", { phone_number: "+243997123456" }).firestore();
  await assertSucceeds(ownerPhoneDb.collection("owner_wallet").doc("config").get());
});

test("Product Moderation Guard: vendor products start PENDING, cannot self-approve or edit BLOCKED product", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(VENDOR_UID).set({
      userId: VENDOR_UID,
      fullName: "Katanga Tech",
      phone: "+243820555777",
      email: "vendor@lubum.cd",
      role: "VENDEUR",
      accountStatus: "APPROVED",
      commune: "Lubumbashi",
      quartier: "Centre",
      storeName: "Katanga Tech Express",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  });

  const vendorDb = testEnv.authenticatedContext(VENDOR_UID).firestore();
  const realAdminDb = adminContext().firestore();

  // Vendor cannot publish directly as APPROVED
  await assertFails(
    vendorDb.collection("products").doc("prod_x").set({
      name: "Telephone Test",
      description: "Description produit test",
      priceUsd: 120,
      category: "TECH",
      sellerId: VENDOR_UID,
      sellerName: "Katanga Tech Express",
      sellerCommune: "Lubumbashi",
      stock: 5,
      imageUrls: ["res://img_prod_smartphone_1794545054563"],
      moderationStatus: "APPROVED",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Vendor creates product with PENDING
  await assertSucceeds(
    vendorDb.collection("products").doc("prod_x").set({
      name: "Telephone Test",
      description: "Description produit test",
      priceUsd: 120,
      category: "TECH",
      sellerId: VENDOR_UID,
      sellerName: "Katanga Tech Express",
      sellerCommune: "Lubumbashi",
      stock: 5,
      imageUrls: ["res://img_prod_smartphone_1794545054563"],
      moderationStatus: "PENDING",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Admin approves then blocks fraudulent item
  await assertSucceeds(
    realAdminDb.collection("products").doc("prod_x").update({
      moderationStatus: "APPROVED",
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    realAdminDb.collection("products").doc("prod_x").update({
      moderationStatus: "BLOCKED",
      moderationReason: "Article contrefait",
      updatedAt: serverTimestamp(),
    })
  );

  // Vendor cannot edit or delete a BLOCKED product
  await assertFails(
    vendorDb.collection("products").doc("prod_x").update({
      name: "Renomme",
      moderationStatus: "PENDING",
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(vendorDb.collection("products").doc("prod_x").delete());
});

test("Blocked User Guard & Order Disputes Isolation", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(OTHER_UID).set({
      userId: OTHER_UID,
      fullName: "Bob Fraude",
      phone: "+243997000222",
      email: "bob@lubum.cd",
      role: "CLIENT",
      accountStatus: "BLOCKED",
      commune: "Lubumbashi",
      quartier: "Centre",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(CLIENT_UID).firestore();
  const bobDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const realAdminDb = adminContext().firestore();

  // Blocked user cannot place order or open dispute
  await assertFails(
    bobDb.collection("orders").doc("ord_blocked").set({
      customerId: OTHER_UID,
      customerName: "Bob Fraude",
      customerPhone: "+243997000222",
      sellerIds: [VENDOR_UID],
      deliveryAddress: { commune: "Lubumbashi", quartier: "Centre", avenue: "Av 1", referenceLandmark: "Ref" },
      items: [{ productId: "p1", name: "Item", quantity: 1, priceUsd: 20 }],
      deliveryFeeUsd: 2.0,
      subtotalUsd: 20.0,
      totalUsd: 22.0,
      commissionUsd: 1.0,
      paymentMethod: "MPESA",
      status: "RECU",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Alice creates a dispute
  await assertSucceeds(
    aliceDb.collection("order_disputes").doc("disp_1").set({
      orderId: "ord_1",
      customerId: CLIENT_UID,
      customerName: "Alice Mwamba",
      reason: "Colis endommage a la livraison",
      status: "OPEN",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Other user cannot read Alice's dispute
  await assertFails(bobDb.collection("order_disputes").doc("disp_1").get());

  // Admin resolves dispute
  await assertSucceeds(
    realAdminDb.collection("order_disputes").doc("disp_1").update({
      status: "RESOLVED",
      adminNotes: "Remboursement valide",
      updatedAt: serverTimestamp(),
    })
  );
});

test("Vendor & Owner Withdrawals Isolation + Terminal State Lock", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(VENDOR_UID).set({
      userId: VENDOR_UID,
      fullName: "Katanga Tech",
      phone: "+243820555777",
      email: "vendor@lubum.cd",
      role: "VENDEUR",
      accountStatus: "APPROVED",
      commune: "Lubumbashi",
      quartier: "Centre",
      storeName: "Katanga Tech Express",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  });

  const vendorDb = testEnv.authenticatedContext(VENDOR_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const realAdminDb = adminContext().firestore();

  await assertSucceeds(
    vendorDb.collection("vendor_withdrawals").doc("ret_1").set({
      vendorId: VENDOR_UID,
      vendorName: "Katanga Tech Express",
      amountGrossUsd: 100,
      commissionPercent: 5,
      commissionUsd: 5,
      amountNetUsd: 95,
      paymentMethod: "MPESA",
      accountDetails: "+243820555777",
      status: "EN_ATTENTE",
      verifiedByAdmin: false,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertFails(otherDb.collection("vendor_withdrawals").doc("ret_1").get());
  await assertFails(otherDb.collection("vendor_withdrawals").where("vendorId", "==", VENDOR_UID).get());
  await assertSucceeds(vendorDb.collection("vendor_withdrawals").where("vendorId", "==", VENDOR_UID).get());

  await assertFails(
    vendorDb.collection("vendor_withdrawals").doc("ret_1").update({
      status: "PAYE",
      verifiedByAdmin: true,
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    realAdminDb.collection("vendor_withdrawals").doc("ret_1").update({
      status: "PAYE",
      verifiedByAdmin: true,
      updatedAt: serverTimestamp(),
    })
  );

  await assertFails(
    realAdminDb.collection("vendor_withdrawals").doc("ret_1").update({
      status: "REFUSE",
      updatedAt: serverTimestamp(),
    })
  );
});

test("Orders PII Isolation, Driver Approval Gate & Payment Confirmation Lock", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(DRIVER_UID).set({
      userId: DRIVER_UID,
      fullName: "Christian Kabeya",
      phone: "+243812345678",
      email: "driver@lubum.cd",
      role: "LIVREUR",
      accountStatus: "APPROVED",
      commune: "Lubumbashi",
      quartier: "Golf",
      vehicleType: "Moto Express",
      deliveryZone: "Lubumbashi Centre",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(CLIENT_UID).firestore();
  const bobDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const driverDb = testEnv.authenticatedContext(DRIVER_UID).firestore();

  // Customer cannot create order already marked paymentConfirmed: true
  await assertFails(
    aliceDb.collection("orders").doc("ord_cheat").set({
      customerId: CLIENT_UID,
      customerName: "Alice Mwamba",
      customerPhone: "+243997123456",
      sellerIds: [VENDOR_UID],
      deliveryAddress: { commune: "Lubumbashi", quartier: "Golf", avenue: "Av. Mesanges", referenceLandmark: "Station Total" },
      items: [{ productId: "p1", name: "Tecno", quantity: 1, priceUsd: 185 }],
      deliveryFeeUsd: 2.0,
      subtotalUsd: 185.0,
      totalUsd: 187.0,
      commissionUsd: 9.25,
      paymentMethod: "MPESA",
      status: "RECU",
      paymentConfirmed: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("orders").doc("ord_1").set({
      customerId: CLIENT_UID,
      customerName: "Alice Mwamba",
      customerPhone: "+243997123456",
      sellerIds: [VENDOR_UID],
      deliveryAddress: { commune: "Lubumbashi", quartier: "Golf", avenue: "Av. Mesanges", referenceLandmark: "Station Total" },
      items: [{ productId: "p1", name: "Tecno", quantity: 1, priceUsd: 185 }],
      deliveryFeeUsd: 2.0,
      subtotalUsd: 185.0,
      totalUsd: 187.0,
      commissionUsd: 9.25,
      paymentMethod: "MPESA",
      status: "RECU",
      paymentConfirmed: false,
      commissionCredited: false,
      driverPayoutSettled: false,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertFails(bobDb.collection("orders").doc("ord_1").get());
  await assertFails(bobDb.collection("orders").get());
  await assertSucceeds(aliceDb.collection("orders").where("customerId", "==", CLIENT_UID).get());

  const realAdminDb = adminContext().firestore();
  await assertSucceeds(
    realAdminDb.collection("orders").doc("ord_1").update({
      status: "EN_ATTENTE_LIVREUR",
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    driverDb.collection("orders").doc("ord_1").update({
      assignedDriverId: DRIVER_UID,
      assignedDriverName: "Christian Kabeya",
      assignedDriverPhone: "+243812345678",
      status: "EN_COURS_LIVRAISON",
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    driverDb.collection("orders").doc("ord_1").update({
      status: "LIVREE",
      updatedAt: serverTimestamp(),
    })
  );

  // Admin confirms real payment & credits 5% commission once
  await assertSucceeds(
    realAdminDb.collection("orders").doc("ord_1").update({
      paymentConfirmed: true,
      paymentReference: "MPESA-TX-88412",
      commissionCredited: true,
      updatedAt: serverTimestamp(),
    })
  );

  // Driver cannot modify LIVREE order
  await assertFails(
    driverDb.collection("orders").doc("ord_1").update({
      status: "ANNULEE",
      updatedAt: serverTimestamp(),
    })
  );
});
