/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.build.apkzlib.zfile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.build.apkzlib.sign.SigningOptions;
import com.android.tools.build.apkzlib.zip.CompressionMethod;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileOptions;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ApkAlignmentTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final SigningOptions signingOptions =
      new SigningOptions(null, (X509Certificate) null, false, false, 20);

  @Test
  public void soFilesUncompressedAndAligned() throws Exception {
    File apk = new File(temporaryFolder.getRoot(), "a.apk");

    File soFile = new File(temporaryFolder.getRoot(), "doesnt_work.so");
    Files.write(soFile.toPath(), new byte[500]);

    ApkZFileCreatorFactory cf = new ApkZFileCreatorFactory(new ZFileOptions());
    ApkCreatorFactory.CreationData creationData =
        new ApkCreatorFactory.CreationData(
            apk,
            signingOptions,
            null,
            null,
            NativeLibrariesPackagingMode.UNCOMPRESSED_AND_ALIGNED,
            path -> false);

    try (ApkCreator creator = cf.make(creationData)) {
      creator.writeFile(soFile, "/doesnt_work.so");
    }

    try (ZFile zf = ZFile.openReadWrite(apk)) {
      StoredEntry soEntry = zf.get("/doesnt_work.so");
      assertNotNull(soEntry);
      assertEquals(
          CompressionMethod.STORE,
          soEntry.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());
      long offset = soEntry.getCentralDirectoryHeader().getOffset() + soEntry.getLocalHeaderSize();
      assertEquals(0, offset % 4096);
    }
  }

  @Test
  public void soFilesMergedFromZipsCanBeUncompressedAndAligned() throws Exception {

    // Create a zip file with a compressed, unaligned so file.
    File zipToMerge = new File(temporaryFolder.getRoot(), "a.zip");
    try (ZFile zf = ZFile.openReadWrite(zipToMerge)) {
      zf.add("/zero.so", new ByteArrayInputStream(new byte[500]));
    }

    try (ZFile zf = ZFile.openReadWrite(zipToMerge)) {
      StoredEntry zeroSo = zf.get("/zero.so");
      assertNotNull(zeroSo);
      assertEquals(
          CompressionMethod.DEFLATE,
          zeroSo.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());
      long offset = zeroSo.getCentralDirectoryHeader().getOffset() + zeroSo.getLocalHeaderSize();
      assertFalse(offset % 4096 == 0);
    }

    // Create an APK and merge the zip file.
    File apk = new File(temporaryFolder.getRoot(), "b.apk");
    ApkZFileCreatorFactory cf = new ApkZFileCreatorFactory(new ZFileOptions());
    ApkCreatorFactory.CreationData creationData =
        new ApkCreatorFactory.CreationData(
            apk,
            signingOptions,
            null,
            null,
            NativeLibrariesPackagingMode.UNCOMPRESSED_AND_ALIGNED,
            path -> false);

    try (ApkCreator creator = cf.make(creationData)) {
      creator.writeZip(zipToMerge, null, null);
    }

    // Make sure the file is uncompressed and aligned.
    try (ZFile zf = ZFile.openReadWrite(apk)) {
      StoredEntry soEntry = zf.get("/zero.so");
      assertNotNull(soEntry);
      assertEquals(
          CompressionMethod.STORE,
          soEntry.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());
      long offset = soEntry.getCentralDirectoryHeader().getOffset() + soEntry.getLocalHeaderSize();
      assertEquals(0, offset % 4096);

      byte[] data = soEntry.read();
      assertEquals(500, data.length);
      for (int i = 0; i < data.length; i++) {
        assertEquals(0, data[i]);
      }
    }
  }

  @Test
  public void soFilesUncompressedAndNotAligned() throws Exception {
    File apk = new File(temporaryFolder.getRoot(), "a.apk");

    File soFile = new File(temporaryFolder.getRoot(), "doesnt_work.so");
    Files.write(soFile.toPath(), new byte[500]);

    ApkZFileCreatorFactory cf = new ApkZFileCreatorFactory(new ZFileOptions());
    ApkCreatorFactory.CreationData creationData =
        new ApkCreatorFactory.CreationData(
            apk,
            signingOptions,
            null,
            null,
            NativeLibrariesPackagingMode.COMPRESSED,
            path -> false);

    try (ApkCreator creator = cf.make(creationData)) {
      creator.writeFile(soFile, "/doesnt_work.so");
    }

    try (ZFile zf = ZFile.openReadWrite(apk)) {
      StoredEntry soEntry = zf.get("/doesnt_work.so");
      assertNotNull(soEntry);
      assertEquals(
          CompressionMethod.DEFLATE,
          soEntry.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());
      long offset = soEntry.getCentralDirectoryHeader().getOffset() + soEntry.getLocalHeaderSize();
      assertTrue(offset % 4096 != 0);
    }
  }

  @Test
  public void soFilesMergedFromZipsCanBeUncompressedAndNotAligned() throws Exception {

    // Create a zip file with a compressed, unaligned so file.
    File zipToMerge = new File(temporaryFolder.getRoot(), "a.zip");
    try (ZFile zf = ZFile.openReadWrite(zipToMerge)) {
      zf.add("/zero.so", new ByteArrayInputStream(new byte[500]));
    }

    try (ZFile zf = ZFile.openReadWrite(zipToMerge)) {
      StoredEntry zeroSo = zf.get("/zero.so");
      assertNotNull(zeroSo);
      assertEquals(
          CompressionMethod.DEFLATE,
          zeroSo.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());
      long offset = zeroSo.getCentralDirectoryHeader().getOffset() + zeroSo.getLocalHeaderSize();
      assertFalse(offset % 4096 == 0);
    }

    // Create an APK and merge the zip file.
    File apk = new File(temporaryFolder.getRoot(), "b.apk");
    ApkZFileCreatorFactory cf = new ApkZFileCreatorFactory(new ZFileOptions());
    ApkCreatorFactory.CreationData creationData =
        new ApkCreatorFactory.CreationData(
            apk,
            signingOptions,
            null,
            null,
            NativeLibrariesPackagingMode.COMPRESSED,
            path -> false);

    try (ApkCreator creator = cf.make(creationData)) {
      creator.writeZip(zipToMerge, null, null);
    }

    // Make sure the file is uncompressed and aligned.
    try (ZFile zf = ZFile.openReadWrite(apk)) {
      StoredEntry soEntry = zf.get("/zero.so");
      assertNotNull(soEntry);
      assertEquals(
          CompressionMethod.DEFLATE,
          soEntry.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());
      long offset = soEntry.getCentralDirectoryHeader().getOffset() + soEntry.getLocalHeaderSize();
      assertTrue(offset % 4096 != 0);

      byte[] data = soEntry.read();
      assertEquals(500, data.length);
      for (int i = 0; i < data.length; i++) {
        assertEquals(0, data[i]);
      }
    }
  }
}
