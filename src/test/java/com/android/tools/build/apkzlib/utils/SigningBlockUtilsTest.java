/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.build.apkzlib.utils;

import static com.android.tools.build.apkzlib.utils.SigningBlockUtils.ANDROID_COMMON_PAGE_ALIGNMENT_NUM_BYTES;
import static com.android.tools.build.apkzlib.utils.SigningBlockUtils.BLOCK_ID_NUM_BYTES;
import static com.android.tools.build.apkzlib.utils.SigningBlockUtils.SIZE_OF_BLOCK_NUM_BYTES;
import static com.android.tools.build.apkzlib.utils.SigningBlockUtils.VERITY_PADDING_BLOCK_ID;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.android.apksig.internal.apk.ApkSigningBlockUtils;
import com.android.apksig.internal.util.Pair;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SigningBlockUtilsTest {
  private static byte[] signer;
  private static final int SIGNER_LENGTH = 500;
  private static final int SIGNER_LENGTH_WITHOUT_PADDING = 4052;
  private static final int NEW_BLOCK_ID = 0x504b4453;
  private static final int APK_SIGNATURE_SCHEME_V2_BLOCK_ID = 0x7109871a;
  private static final long APK_SIG_BLOCK_MAGIC_HI = 0x3234206b636f6c42L;
  private static final long APK_SIG_BLOCK_MAGIC_LO = 0x20676953204b5041L;

  @Test
  public void testEmptyBlock() throws Exception {
    byte[] apkSigBlob = generateSignatureBlock();
    byte[] finalBlock =
        SigningBlockUtils.addToSigningBlock(apkSigBlob, new byte[0], NEW_BLOCK_ID);
    assertArrayEquals(apkSigBlob, finalBlock);
  }

  @Test
  public void testNullBlock() throws Exception {
    byte[] apkSigBlob = generateSignatureBlock();
    byte[] finalBlock = SigningBlockUtils.addToSigningBlock(apkSigBlob, null, NEW_BLOCK_ID);
    assertArrayEquals(apkSigBlob, finalBlock);
  }

  @Test
  public void testNewBlockAppend() throws Exception {
    byte[] apkSigBlob = generateSignatureBlock();
    byte[] newBlockValue = new byte[] {'s', 'd', 'k'};

    byte[] finalBlock =
        SigningBlockUtils.addToSigningBlock(apkSigBlob, newBlockValue, NEW_BLOCK_ID);
    int expectedLengthPrefix = ANDROID_COMMON_PAGE_ALIGNMENT_NUM_BYTES - SIZE_OF_BLOCK_NUM_BYTES;

    verifyBlocks(newBlockValue, finalBlock, expectedLengthPrefix);
  }

  @Test
  public void testNewBlockAppend_sizeMoreThan4k() throws Exception {
    byte[] apkSigBlob = generateSignatureBlock();
    byte[] newBlockValue = new byte[5000];
    new Random().nextBytes(newBlockValue);

    byte[] finalBlock =
        SigningBlockUtils.addToSigningBlock(apkSigBlob, newBlockValue, NEW_BLOCK_ID);
    int expectedLengthPrefix =
        2 * ANDROID_COMMON_PAGE_ALIGNMENT_NUM_BYTES - SIZE_OF_BLOCK_NUM_BYTES;

    verifyBlocks(newBlockValue, finalBlock, expectedLengthPrefix);
  }

  @Test
  public void testNewBlockAppend_sizeMoreThan4kWithoutInitialPadding() throws Exception {
    byte[] apkSigBlob = generateSignatureBlockWithoutPaddingBlock();
    byte[] newBlockValue = new byte[500];
    new Random().nextBytes(newBlockValue);

    byte[] finalBlock =
        SigningBlockUtils.addToSigningBlock(apkSigBlob, newBlockValue, NEW_BLOCK_ID);
    int expectedLengthPrefix =
        2 * ANDROID_COMMON_PAGE_ALIGNMENT_NUM_BYTES - SIZE_OF_BLOCK_NUM_BYTES;

    verifyBlocks(newBlockValue, finalBlock, expectedLengthPrefix);
  }

  @Test
  public void testNewBlockAppendWithoutSignature() throws Exception {
    byte[] newBlockValue = new byte[] {'s', 'd', 'k'};

    byte[] finalBlock = SigningBlockUtils.addToSigningBlock(null, newBlockValue, NEW_BLOCK_ID);

    int expectedLengthPrefix = ANDROID_COMMON_PAGE_ALIGNMENT_NUM_BYTES - SIZE_OF_BLOCK_NUM_BYTES;
    ByteBuffer result = ByteBuffer.wrap(finalBlock).order(LITTLE_ENDIAN);

    assertEquals(
        expectedLengthPrefix,
        result.getLong()); // Size of the entire block excluding length at the beginning

    // No signing block. Verify new block and Padding block
    verifyNewBlockAndPaddingBlock(newBlockValue, expectedLengthPrefix, result);
  }

  private static void verifyBlocks(
      byte[] newBlockValue, byte[] finalBlock, int expectedLengthPrefix) {
    ByteBuffer result = ByteBuffer.wrap(finalBlock).order(LITTLE_ENDIAN);

    assertEquals(
        expectedLengthPrefix,
        result.getLong()); // Size of the entire block excluding length at the beginning

    // Signature
    assertEquals(
        signer.length + BLOCK_ID_NUM_BYTES,
        result.getLong()); // Length prefix of first and only signer
    assertEquals(APK_SIGNATURE_SCHEME_V2_BLOCK_ID, result.getInt()); // Block ID of signer
    byte[] apkSigBlockOut = new byte[signer.length];
    result.get(apkSigBlockOut); // Signer value
    assertArrayEquals(signer, apkSigBlockOut);

    // New block and Padding block
    verifyNewBlockAndPaddingBlock(newBlockValue, expectedLengthPrefix, result);
  }

  private static void verifyNewBlockAndPaddingBlock(
      byte[] newBlockValue, int expectedLengthPrefix, ByteBuffer result) {
    // New block
    long lengthOfNewBlock = result.getLong(); // Length prefix of new block
    assertEquals(NEW_BLOCK_ID, result.getInt()); // New block block ID
    byte[] newBlockValueOut = new byte[(int) lengthOfNewBlock - BLOCK_ID_NUM_BYTES];
    result.get(newBlockValueOut); // New block value
    assertArrayEquals(newBlockValueOut, newBlockValue);

    // Padding
    long paddingLength = result.getLong();
    assertEquals(VERITY_PADDING_BLOCK_ID, result.getInt()); // Padding block ID
    result.get(new byte[(int) paddingLength - BLOCK_ID_NUM_BYTES]); // Padding

    assertEquals(
        expectedLengthPrefix,
        result.getLong()); // Size of the entire block excluding length at the beginning
    assertEquals(APK_SIG_BLOCK_MAGIC_LO, result.getLong());
    assertEquals(APK_SIG_BLOCK_MAGIC_HI, result.getLong());
  }

  private static byte[] generateSignatureBlock() {
    signer = new byte[SIGNER_LENGTH];
    new Random().nextBytes(signer);
    return ApkSigningBlockUtils.generateApkSigningBlock(
        ImmutableList.of(Pair.of(signer, APK_SIGNATURE_SCHEME_V2_BLOCK_ID)));
  }

  private static byte[] generateSignatureBlockWithoutPaddingBlock() {
    signer = new byte[SIGNER_LENGTH_WITHOUT_PADDING];
    new Random().nextBytes(signer);
    return ApkSigningBlockUtils.generateApkSigningBlock(
        ImmutableList.of(Pair.of(signer, APK_SIGNATURE_SCHEME_V2_BLOCK_ID)));
  }
}
