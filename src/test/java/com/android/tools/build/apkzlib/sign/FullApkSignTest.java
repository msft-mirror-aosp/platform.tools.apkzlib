/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.build.apkzlib.sign;

import static com.android.tools.build.apkzlib.sign.SigningExtension.DEPENDENCY_INFO_BLOCK_ID;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.build.apkzlib.utils.ApkZFileTestUtils;
import com.android.tools.build.apkzlib.utils.ApkZLibPair;
import com.android.tools.build.apkzlib.zip.AlignmentRule;
import com.android.tools.build.apkzlib.zip.AlignmentRules;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileOptions;
import com.android.tools.build.apkzlib.zip.ZFileTestConstants;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/** Tests that verify APK Signature Scheme v2 signing using {@link SigningExtension}. */
@RunWith(Theories.class)
public class FullApkSignTest {

  /** Size of the apk signing block size is encoded in 8 bytes. */
  private static final int SIGNING_BLOCK_SIZE_SIZE = 8;

  /** Block IDs in apk signing block are encoded in 4 bytes. */
  private static final int BLOCK_ID_SIZE = 4;

  /** Size of the signing block magic bytes. */
  private static final int MAGIC_BYTES_SIZE = 16;

  private static final String F1_NAME = "abc";
  private static final String F2_NAME = "defg";

  private static final byte[] F2_DATA = new byte[10000];
  private static final byte[] F1_DATA = new byte[20000];
  private static final byte[] DEPENDENCY_BLOCK_VALUE = new byte[500];

  static {
    Arrays.fill(F1_DATA, (byte) 1);
    Arrays.fill(F2_DATA, (byte) 3);
    Arrays.fill(DEPENDENCY_BLOCK_VALUE, (byte) 4);
  }

  /** Folder used for tests. */
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @DataPoints("signing_data")
  public static final ApkZLibPair<PrivateKey, X509Certificate>[] SIGNING_DATA =
      SignatureTestUtils.getAllSigningData();

  /** Generates a signed zip file signed with signData. */
  private void generateSignedZip(File file, ApkZLibPair<PrivateKey, X509Certificate> signData)
      throws Exception {
    // The byte arrays below are larger when compressed, so we end up storing them uncompressed,
    // which would normally cause them to be 4-aligned. Disable that, to make calculations
    // easier.
    ZFileOptions options = new ZFileOptions();
    options.setAlignmentRule(AlignmentRules.constant(AlignmentRule.NO_ALIGNMENT));

    /*
     * Generate a signed zip.
     */
    SigningOptions signingOptions =
        SigningOptions.builder()
            .setKey(signData.v1)
            .setCertificates(signData.v2)
            .setV2SigningEnabled(true)
            .setMinSdkVersion(13)
            .setSdkDependencyData(DEPENDENCY_BLOCK_VALUE)
            .build();
    try (ZFile zf = ZFile.openReadWrite(file, options)) {
      new SigningExtension(signingOptions).register(zf);
      zf.add(F1_NAME, new ByteArrayInputStream(F1_DATA), /* mayCompress= */ false);
      zf.add(F2_NAME, new ByteArrayInputStream(F2_DATA), /* mayCompress= */ false);
    }
  }

  private void verifyZipValid(File out) throws Exception {
    /*
     * We should see the data in place.
     */
    int f1DataStart = ZFileTestConstants.LOCAL_HEADER_SIZE + F1_NAME.length();
    int f1DataEnd = f1DataStart + F1_DATA.length;
    int f2DataStart = f1DataEnd + ZFileTestConstants.LOCAL_HEADER_SIZE + F2_NAME.length();

    byte[] read1 = ApkZFileTestUtils.readSegment(out, f1DataStart, F1_DATA.length);
    assertArrayEquals(F1_DATA, read1);
    byte[] read2 = ApkZFileTestUtils.readSegment(out, f2DataStart, F2_DATA.length);
    assertArrayEquals(F2_DATA, read2);

    /*
     * Read the signed zip.
     */
    try (ZFile zf = ZFile.openReadWrite(out)) {
      StoredEntry se1 = zf.get(F1_NAME);
      assertNotNull(se1);
      assertArrayEquals(F1_DATA, se1.read());
      StoredEntry se2 = zf.get(F2_NAME);
      assertNotNull(se2);
      assertArrayEquals(F2_DATA, se2.read());
    }
  }

  @Theory
  public void checkSignature(
      @FromDataPoints("signing_data") ApkZLibPair<PrivateKey, X509Certificate> signingData)
      throws Exception {
    File out = new File(temporaryFolder.getRoot(), "apk");
    generateSignedZip(out, signingData);
    verifyZipValid(out);
  }

  @Theory
  public void resignKeepsSameSize(
      @FromDataPoints("signing_data") ApkZLibPair<PrivateKey, X509Certificate> signingData)
      throws Exception {
    File out = new File(temporaryFolder.getRoot(), "apk");

    generateSignedZip(out, signingData);
    verifyZipValid(out);
    long sizeAfterSigned = out.length();
    byte[] signatureBlock;
    try (ZFile zf = ZFile.openReadWrite(out)) {
      long signBlockEnd = zf.getCentralDirectoryOffset();
      long signBlockStart = zf.getCentralDirectoryOffset() - zf.getExtraDirectoryOffset();
      assertTrue(signBlockEnd > signBlockStart);
      signatureBlock = new byte[(int) (signBlockEnd - signBlockStart)];
      zf.directRead(signBlockStart, signatureBlock, 0, signatureBlock.length);
    }

    ApkZLibPair<PrivateKey, X509Certificate> newSigningData =
        SignatureTestUtils.generateAnother(signingData);

    /* Resign the zip. */
    SigningOptions signingOptions =
        SigningOptions.builder()
            .setKey(newSigningData.v1)
            .setCertificates(newSigningData.v2)
            .setV2SigningEnabled(true)
            .setMinSdkVersion(13)
            .build();
    try (ZFile zf = ZFile.openReadWrite(out)) {
      new SigningExtension(signingOptions).register(zf);
    }

    long newSizeAfterSigned = out.length();
    assertEquals(newSizeAfterSigned, sizeAfterSigned);
    byte[] newSignatureBlock;
    try (ZFile zf = ZFile.openReadWrite(out)) {
      long signBlockEnd = zf.getCentralDirectoryOffset();
      long signBlockStart = zf.getCentralDirectoryOffset() - zf.getExtraDirectoryOffset();
      assertTrue(signBlockEnd > signBlockStart);
      assertEquals(signBlockEnd - signBlockStart, signatureBlock.length);
      newSignatureBlock = new byte[signatureBlock.length];
      zf.directRead(signBlockStart, newSignatureBlock, 0, newSignatureBlock.length);
      assertNotEquals(signatureBlock, newSignatureBlock);
    }
  }

  @Theory
  public void signingBlockAndCentralDirectoryAre4KAligned(
      @FromDataPoints("signing_data") ApkZLibPair<PrivateKey, X509Certificate> signingData)
      throws Exception {
    File out = new File(temporaryFolder.getRoot(), "apk");
    generateSignedZip(out, signingData);

    try (ZFile apk = ZFile.openReadOnly(out)) {
      long cdOffset = apk.getCentralDirectoryOffset();
      assertEquals(0, cdOffset % 4096);

      // Find the actual start of the signing block, i.e. without the padding that makes it
      // 4k-aligned.
      ByteBuffer buffer = ByteBuffer.allocate(SIGNING_BLOCK_SIZE_SIZE).order(LITTLE_ENDIAN);
      apk.directRead(
          cdOffset - MAGIC_BYTES_SIZE - SIGNING_BLOCK_SIZE_SIZE,
          buffer.array(),
          0,
          SIGNING_BLOCK_SIZE_SIZE);
      long actualSigningBlockSize = buffer.getLong();
      long actualSigningBlockStart = cdOffset - actualSigningBlockSize - SIGNING_BLOCK_SIZE_SIZE;

      assertEquals(0, actualSigningBlockStart % 4096);
    }
  }

  @Theory
  public void dependencyInfoBlockPresent(
      @FromDataPoints("signing_data") ApkZLibPair<PrivateKey, X509Certificate> signingData)
      throws Exception {
    File out = new File(temporaryFolder.getRoot(), "apk");
    generateSignedZip(out, signingData);

    try (ZFile apk = ZFile.openReadOnly(out)) {
      long cdOffset = apk.getCentralDirectoryOffset();

      // Find the actual start of the signing block, i.e. without the padding that makes it
      // 4k-aligned.
      ByteBuffer buffer = ByteBuffer.allocate(SIGNING_BLOCK_SIZE_SIZE).order(LITTLE_ENDIAN);
      apk.directRead(
          cdOffset - MAGIC_BYTES_SIZE - SIGNING_BLOCK_SIZE_SIZE,
          buffer.array(),
          0,
          SIGNING_BLOCK_SIZE_SIZE);
      long actualSigningBlockSize = buffer.getLong();
      long actualSigningBlockStart = cdOffset - actualSigningBlockSize - SIGNING_BLOCK_SIZE_SIZE;

      ByteBuffer signingBlock =
          ByteBuffer.allocate((int) actualSigningBlockSize).order(LITTLE_ENDIAN);
      apk.directRead(
          actualSigningBlockStart + SIGNING_BLOCK_SIZE_SIZE,
          signingBlock.array(),
          0,
          (int) actualSigningBlockSize);

      signingBlock.get(new byte[(int) signingBlock.getLong()]); // Id + value of signature
      assertEquals(
          signingBlock.getLong(),
          DEPENDENCY_BLOCK_VALUE.length + BLOCK_ID_SIZE); // length of Dependency Info block
      assertEquals(DEPENDENCY_INFO_BLOCK_ID, signingBlock.getInt()); // Dependency Info block Id

      byte[] dependencyInfoPresent = new byte[DEPENDENCY_BLOCK_VALUE.length];
      signingBlock.get(dependencyInfoPresent);
      assertArrayEquals(
          dependencyInfoPresent, DEPENDENCY_BLOCK_VALUE); // Dependency Info block value
    }
  }
}
