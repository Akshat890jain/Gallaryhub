const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated users cannot read or write mediaOverrides or studioState", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("mediaOverrides").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("mediaOverrides")
      .doc("media_1")
      .set({
        userId: ALICE_UID,
        mediaId: "media_1",
        favorite: true,
        customTags: ["travel"],
        hiddenInVault: false,
        createdAt: now,
        updatedAt: now,
      })
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("studioState")
      .doc("profile")
      .get()
  );
});

test("Authenticated owner: valid MediaOverride create, list, partial update, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("mediaOverrides")
    .doc("673a1b2c3d4e5f6a7b8c9d0e");

  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      mediaId: "673a1b2c3d4e5f6a7b8c9d0e",
      favorite: true,
      customTags: ["resume", "pdf"],
      renamedTitle: "Akshat Resume Final.pdf",
      hiddenInVault: false,
      ocrText: "Akshat Jain Android Engineer",
      createdAt: created,
      updatedAt: created,
    })
  );

  // List subcollection (mirrors Kotlin observeMediaOverrides)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("mediaOverrides").get()
  );

  // Partial update preserving immutable userId/mediaId/createdAt
  await assertSucceeds(
    docRef.update({
      hiddenInVault: true,
      customTags: ["resume", "private"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Delete allowed by owner
  await assertSucceeds(docRef.delete());
});

test("MediaOverride rejects cross-user access, ghost fields, immutable mutation, future timestamps, and @DocumentId id injection", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("mediaOverrides")
      .doc("bob_item_1")
      .set({
        userId: BOB_UID,
        mediaId: "bob_item_1",
        favorite: false,
        customTags: [],
        hiddenInVault: true,
        createdAt: created,
        updatedAt: created,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Cross-user read & write denied
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("mediaOverrides").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("mediaOverrides")
      .doc("bob_item_1")
      .update({ favorite: true, updatedAt: new Date(Date.now() - 1000) })
  );

  // Reject ghost field / @DocumentId `id` injection
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("mediaOverrides")
      .doc("item_ghost")
      .set({
        id: "item_ghost",
        userId: ALICE_UID,
        mediaId: "item_ghost",
        favorite: true,
        customTags: [],
        hiddenInVault: false,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("mediaOverrides")
      .doc("item_future")
      .set({
        userId: ALICE_UID,
        mediaId: "item_future",
        favorite: false,
        customTags: [],
        hiddenInVault: false,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );
});

test("StudioState create, update, and schema bounds work cleanly", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const profileRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("studioState")
    .doc("profile");

  await assertSucceeds(
    profileRef.set({
      userId: ALICE_UID,
      vaultPinHash: "a".repeat(64),
      vaultBiometricEnabled: true,
      clearedActivityIds: ["act_1", "act_2"],
      themeMode: "dark",
      accentHex: "#3B82F6",
      exifScrubDefaultOn: true,
      wifiOnlyUploads: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid partial update
  await assertSucceeds(
    profileRef.update({
      wifiOnlyUploads: true,
      clearedActivityIds: ["act_1", "act_2", "act_3"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject invalid themeMode enum
  await assertFails(
    profileRef.update({
      themeMode: "neon_purple",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    profileRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
