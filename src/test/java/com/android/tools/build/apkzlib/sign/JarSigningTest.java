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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.build.apkzlib.utils.ApkZFileTestUtils;
import com.android.tools.build.apkzlib.utils.ApkZLibPair;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.google.common.base.Charsets;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.junit.Rule;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class JarSigningTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @DataPoints("signing_data")
  public static final ApkZLibPair<PrivateKey, X509Certificate>[] SIGNING_DATA =
      SignatureTestUtils.getAllSigningData();

  private static SigningOptions createSigningOptionsV1(
      ApkZLibPair<PrivateKey, X509Certificate> signingData) {
    return createSigningOptionsV1(
        signingData, SignatureTestUtils.getApiLevelForKey(signingData.v1));
  }

  private static SigningOptions createSigningOptionsV1(
      ApkZLibPair<PrivateKey, X509Certificate> signingData, int minSdk) {
    return new SigningOptions(signingData.v1, signingData.v2, true, false, minSdk);
  }

  private static SigningOptions createSigningOptionsV2(
      ApkZLibPair<PrivateKey, X509Certificate> signingData) {
    return new SigningOptions(signingData.v1, signingData.v2, false, true, 12);
  }

  @Theory
  public void signEmptyJar(
      @FromDataPoints("signing_data") ApkZLibPair<PrivateKey, X509Certificate> signingData)
      throws Exception {
    File zipFile = new File(temporaryFolder.getRoot(), "a.zip");

    try (ZFile zf = new ZFile(zipFile)) {
      ApkZFileTestUtils.addAndroidManifest(zf);
      ManifestGenerationExtension manifestExtension = new ManifestGenerationExtension("Me", "Me");
      manifestExtension.register(zf);

      new SigningExtension(createSigningOptionsV1(signingData)).register(zf);
    }

    try (ZFile verifyZFile = new ZFile(zipFile)) {
      StoredEntry manifestEntry = verifyZFile.get("META-INF/MANIFEST.MF");
      assertNotNull(manifestEntry);

      Manifest manifest = new Manifest(new ByteArrayInputStream(manifestEntry.read()));
      assertEquals(3, manifest.getMainAttributes().size());
      assertEquals("1.0", manifest.getMainAttributes().getValue("Manifest-Version"));
      assertEquals("Me", manifest.getMainAttributes().getValue("Created-By"));
      assertEquals("Me", manifest.getMainAttributes().getValue("Built-By"));
    }
  }

  @Theory
  public void signJarWithPrexistingSimpleTextFile(
      @FromDataPoints("signing_data") ApkZLibPair<PrivateKey, X509Certificate> signingData)
      throws Exception {
    File zipFile = new File(temporaryFolder.getRoot(), "a.zip");

    try (ZFile zf1 = new ZFile(zipFile)) {
      ApkZFileTestUtils.addAndroidManifest(zf1);
      zf1.add(
          "directory/file", new ByteArrayInputStream("useless text".getBytes(Charsets.US_ASCII)));
    }

    try (ZFile zf2 = new ZFile(zipFile)) {
      ManifestGenerationExtension me = new ManifestGenerationExtension("Merry", "Christmas");
      me.register(zf2);
      new SigningExtension(createSigningOptionsV1(signingData)).register(zf2);
    }

    try (ZFile zf3 = new ZFile(zipFile)) {
      StoredEntry manifestEntry = zf3.get("META-INF/MANIFEST.MF");
      assertNotNull(manifestEntry);

      Manifest manifest = new Manifest(new ByteArrayInputStream(manifestEntry.read()));
      assertEquals(3, manifest.getMainAttributes().size());
      assertEquals("1.0", manifest.getMainAttributes().getValue("Manifest-Version"));
      assertEquals("Merry", manifest.getMainAttributes().getValue("Built-By"));
      assertEquals("Christmas", manifest.getMainAttributes().getValue("Created-By"));

      Attributes attrs = manifest.getAttributes("directory/file");
      assertNotNull(attrs);
      assertEquals(1, attrs.size());

      boolean isSha1 = attrs.getValue("SHA1-Digest") != null;

      // Depending on the signing algorithm, we'll have the digest with SHA-1 or SHA-256.
      // The values below are base64 encoding of the SHA digest of a file with contents
      // "useless text". Easy way to get them is to sign a jar manually and check the contents
      // of MANIFEST.MF.
      if (isSha1) {
        assertEquals("OOQgIEXBissIvva3ydRoaXk29Rk=", attrs.getValue("SHA1-Digest"));
      } else {
        assertEquals(
            "QjupZsopQM/01O6+sWHqH64ilMmoBEtljg9VEqN6aI4=", attrs.getValue("SHA-256-Digest"));
      }

      StoredEntry signatureEntry = zf3.get("META-INF/CERT.SF");
      assertNotNull(signatureEntry);

      Manifest signature = new Manifest(new ByteArrayInputStream(signatureEntry.read()));
      assertEquals("1.0", signature.getMainAttributes().getValue("Signature-Version"));
      assertEquals("1.0 (Android)", signature.getMainAttributes().getValue("Created-By"));

      byte[] manifestTextBytes = manifestEntry.read();
      HashFunction hashFunction = isSha1 ? Hashing.sha1() : Hashing.sha256();
      byte[] manifestHashBytes = hashFunction.hashBytes(manifestTextBytes).asBytes();
      String manifestHash = Base64.getEncoder().encodeToString(manifestHashBytes);

      Attributes signAttrs = signature.getAttributes("directory/file");
      assertNotNull(signAttrs);
      assertEquals(1, signAttrs.size());

      // Depending on the signing algorithm, we'll have the digest hashed with SHA-1 or SHA-256.
      // The values below are base53 encoding of the SHA digest of the corresponding entry in the
      // manifest file. Easy way to get them is to sign a jar manually and check the contents of
      // CERT.SF.
      if (isSha1) {
        assertEquals(manifestHash, signature.getMainAttributes().getValue("SHA1-Digest-Manifest"));
        assertEquals("LGSOwy4uGcUWoc+ZhS8ukzmf0fY=", signAttrs.getValue("SHA1-Digest"));
      } else {
        assertEquals(
            manifestHash, signature.getMainAttributes().getValue("SHA-256-Digest-Manifest"));
        assertEquals(
            "dBnaLpqNjmUnLlZF4tNqOcDWL8wy8Tsw1ZYFqTZhjIs=", signAttrs.getValue("SHA-256-Digest"));
      }

      // Depending on the signing algorithm, we'll have the signature with RSA or ECDSA.
      StoredEntry rsaEntry = zf3.get("META-INF/CERT.RSA");
      if (rsaEntry == null) {
        StoredEntry ecdsaEntry = zf3.get("META-INF/CERT.EC");
        assertNotNull(ecdsaEntry);
      }
    }
  }

  @Theory
  public void v2SignAddsApkSigningBlock(
      @FromDataPoints("signing_data") ApkZLibPair<PrivateKey, X509Certificate> signingData)
      throws Exception {
    File zipFile = new File(temporaryFolder.getRoot(), "a.zip");
    try (ZFile zf = new ZFile(zipFile)) {
      ApkZFileTestUtils.addAndroidManifest(zf);
      ManifestGenerationExtension manifestExtension = new ManifestGenerationExtension("Me", "Me");
      manifestExtension.register(zf);

      new SigningExtension(createSigningOptionsV2(signingData)).register(zf);
    }

    try (ZFile verifyZFile = new ZFile(zipFile)) {
      long centralDirOffset = verifyZFile.getCentralDirectoryOffset();
      byte[] apkSigningBlockMagic = new byte[16];
      verifyZFile.directFullyRead(
          centralDirOffset - apkSigningBlockMagic.length, apkSigningBlockMagic);
      assertEquals("APK Sig Block 42", new String(apkSigningBlockMagic, "US-ASCII"));
    }
  }

  @Theory
  public void v1ReSignOnFileChange(
      @FromDataPoints("signing_data") ApkZLibPair<PrivateKey, X509Certificate> signingData)
      throws Exception {
    File zipFile = new File(temporaryFolder.getRoot(), "a.zip");

    byte[] file1Contents = "I am a test file".getBytes(Charsets.US_ASCII);
    String file1Name = "path/to/file1";
    byte[] file1Sha = Hashing.sha256().hashBytes(file1Contents).asBytes();
    String file1ShaTxt = Base64.getEncoder().encodeToString(file1Sha);

    String builtBy = "Santa Claus";
    String createdBy = "Uses Android";

    try (ZFile zf1 = new ZFile(zipFile)) {
      ApkZFileTestUtils.addAndroidManifest(zf1);
      zf1.add(file1Name, new ByteArrayInputStream(file1Contents));
      ManifestGenerationExtension me = new ManifestGenerationExtension(builtBy, createdBy);
      me.register(zf1);
      new SigningExtension(createSigningOptionsV1(signingData, 21)).register(zf1);

      zf1.update();

      StoredEntry manifestEntry = zf1.get("META-INF/MANIFEST.MF");
      assertNotNull(manifestEntry);

      try (InputStream manifestIs = manifestEntry.open()) {
        Manifest manifest = new Manifest(manifestIs);

        assertEquals(2, manifest.getEntries().size());

        Attributes file1Attrs = manifest.getEntries().get(file1Name);
        assertNotNull(file1Attrs);
        assertEquals(file1ShaTxt, file1Attrs.getValue("SHA-256-Digest"));
      }

      /*
       * Change the file without closing the zip.
       */
      file1Contents = "I am a modified test file".getBytes(Charsets.US_ASCII);
      file1Sha = Hashing.sha256().hashBytes(file1Contents).asBytes();
      file1ShaTxt = Base64.getEncoder().encodeToString(file1Sha);

      zf1.add(file1Name, new ByteArrayInputStream(file1Contents));

      zf1.update();

      manifestEntry = zf1.get("META-INF/MANIFEST.MF");
      assertNotNull(manifestEntry);

      try (InputStream manifestIs = manifestEntry.open()) {
        Manifest manifest = new Manifest(manifestIs);

        assertEquals(2, manifest.getEntries().size());

        Attributes file1Attrs = manifest.getEntries().get(file1Name);
        assertNotNull(file1Attrs);
        assertEquals(file1ShaTxt, file1Attrs.getValue("SHA-256-Digest"));
      }
    }

    /*
     * Change the file closing the zip.
     */
    file1Contents = "I have changed again!".getBytes(Charsets.US_ASCII);
    file1Sha = Hashing.sha256().hashBytes(file1Contents).asBytes();
    file1ShaTxt = Base64.getEncoder().encodeToString(file1Sha);

    try (ZFile zf2 = new ZFile(zipFile)) {
      ApkZFileTestUtils.addAndroidManifest(zf2);
      ManifestGenerationExtension me = new ManifestGenerationExtension(builtBy, createdBy);
      me.register(zf2);
      new SigningExtension(createSigningOptionsV1(signingData, 21)).register(zf2);

      zf2.add(file1Name, new ByteArrayInputStream(file1Contents));

      zf2.update();

      StoredEntry manifestEntry = zf2.get("META-INF/MANIFEST.MF");
      assertNotNull(manifestEntry);

      try (InputStream manifestIs = manifestEntry.open()) {
        Manifest manifest = new Manifest(manifestIs);

        assertEquals(2, manifest.getEntries().size());

        Attributes file1Attrs = manifest.getEntries().get(file1Name);
        assertNotNull(file1Attrs);
        assertEquals(file1ShaTxt, file1Attrs.getValue("SHA-256-Digest"));
      }
    }
  }

  @Theory
  public void openSignedJarDoesNotForcesWriteIfSignatureIsNotCorrect(
      @FromDataPoints("signing_data") ApkZLibPair<PrivateKey, X509Certificate> signingData)
      throws Exception {
    File zipFile = new File(temporaryFolder.getRoot(), "a.zip");

    String fileName = "file";
    byte[] fileContents = "Very interesting contents".getBytes(Charsets.US_ASCII);

    try (ZFile zf = new ZFile(zipFile)) {
      ApkZFileTestUtils.addAndroidManifest(zf);
      ManifestGenerationExtension me = new ManifestGenerationExtension("I", "Android");
      me.register(zf);
      new SigningExtension(createSigningOptionsV1(signingData, 21)).register(zf);

      zf.add(fileName, new ByteArrayInputStream(fileContents));
    }

    long fileTimestamp = zipFile.lastModified();

    ApkZFileTestUtils.waitForFileSystemTick(fileTimestamp);

    /*
     * Open the zip file, but don't touch it.
     */
    try (ZFile zf = new ZFile(zipFile)) {
      ManifestGenerationExtension me = new ManifestGenerationExtension("I", "Android");
      me.register(zf);
      new SigningExtension(createSigningOptionsV1(signingData, 21)).register(zf);
    }

    /*
     * Check the file wasn't touched.
     */
    assertEquals(fileTimestamp, zipFile.lastModified());

    /*
     * Change the file contents ignoring any signing.
     */
    fileContents = "Not so interesting contents".getBytes(Charsets.US_ASCII);
    try (ZFile zf = new ZFile(zipFile)) {
      zf.add(fileName, new ByteArrayInputStream(fileContents));
    }

    fileTimestamp = zipFile.lastModified();

    /*
     * Wait to make sure the timestamp can increase.
     */
    while (true) {
      File notUsed = temporaryFolder.newFile();
      long notTimestamp = notUsed.lastModified();
      notUsed.delete();
      if (notTimestamp > fileTimestamp) {
        break;
      }
    }

    /*
     * Open the zip file, but do any changes. The need to updating the signature should force
     * a file update.
     */
    try (ZFile zf = new ZFile(zipFile)) {
      ManifestGenerationExtension me = new ManifestGenerationExtension("I", "Android");
      me.register(zf);
      new SigningExtension(createSigningOptionsV1(signingData, 21)).register(zf);
    }

    /*
     * Check the file was touched.
     */
    assertNotEquals(fileTimestamp, zipFile.lastModified());
  }
}
