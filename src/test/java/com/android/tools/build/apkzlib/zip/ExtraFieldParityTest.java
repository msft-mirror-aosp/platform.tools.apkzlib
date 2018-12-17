/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.build.apkzlib.zip;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ExtraFieldParityTest {
  /*
   * Header ID: 0x0A0B
   * Data Size: 0x0004
   * Data: 0x01 0x02 0x03 0x04
   *
   * In little endian is:
   *
   * 0x0B0C040001020304
   */
  private static final byte[] EXTRA_FIELD =
      new byte[] {0x0B, 0x0A, 0x04, 0x00, 0x01, 0x02, 0x03, 0x04};

  private static final byte[] DUMMY_BYTES = new byte[] {1, 2, 3};

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testSetExtraField() throws Exception {

    File zipFile = new File(temporaryFolder.getRoot(), "a.zip");

    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
      ZipEntry ze = new ZipEntry("foo");
      zos.putNextEntry(ze);
      zos.write(DUMMY_BYTES);
    }

    checkParity(zipFile, "foo", new byte[0]);

    try (ZFile zf = ZFile.openReadWrite(zipFile)) {
      StoredEntry foo = zf.get("foo");
      assertNotNull(foo);
      foo.setLocalExtra(new ExtraField(EXTRA_FIELD));
    }

    checkParity(zipFile, "foo", EXTRA_FIELD);
  }

  @Test
  public void testAddStoredEntryWithExtraField() throws Exception {

    File zipFile = new File(temporaryFolder.getRoot(), "a.zip");

    try (ZFile zf = ZFile.openReadWrite(zipFile)) {
      zf.add("foo", new ByteArrayInputStream(new byte[] {1, 2, 3}));
      StoredEntry foo = zf.get("foo");
      assertNotNull(foo);
      foo.setLocalExtra(new ExtraField(EXTRA_FIELD));
    }

    checkParity(zipFile, "foo", EXTRA_FIELD);
  }

  @Test
  public void testMergeFromEntryWithExtraField() throws Exception {

    File sourceZipFile = new File(temporaryFolder.getRoot(), "source.zip");

    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(sourceZipFile))) {
      ZipEntry ze = new ZipEntry("foo");
      ze.setExtra(EXTRA_FIELD);
      zos.putNextEntry(ze);
      zos.write(DUMMY_BYTES);
    }

    File zipFile = new File(temporaryFolder.getRoot(), "a.zip");

    try (ZFile zf = ZFile.openReadWrite(zipFile);
        ZFile szf = ZFile.openReadOnly(sourceZipFile)) {
      zf.mergeFrom(szf, s -> false);
    }

    checkParity(zipFile, "foo", EXTRA_FIELD);
  }

  @Test
  public void testReadNonParityZip() throws Exception {

    File sourceZipFile = new File(temporaryFolder.getRoot(), "source.zip");

    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(sourceZipFile))) {
      ZipEntry ze = new ZipEntry("foo");
      ze.setExtra(EXTRA_FIELD);
      zos.putNextEntry(ze);
      zos.write(DUMMY_BYTES);
    }

    File forgedZipFile = new File(temporaryFolder.getRoot(), "forged.zip");

    try (FileInputStream fis = new FileInputStream(sourceZipFile);
        FileOutputStream fos = new FileOutputStream(forgedZipFile)) {
      byte[] content = new byte[(int) sourceZipFile.length()];
      fis.read(content);
      // Different size
      content[28] = 0x07;
      content[35] = 0x03;
      // Different content
      content[37] = 0x05;
      content[38] = 0x06;
      content[38] = 0x07;
      // Move CD
      content[135] = 61;
      fos.write(content, 0, 39); // Skip 1 byte to reflect the size change
      fos.write(content, 40, content.length - 40);
    }

    final VerifyLog log = VerifyLogs.unlimited();
    ZFileOptions options = new ZFileOptions();
    options.setVerifyLogFactory(() -> log);
    try (ZFile zf = ZFile.openReadOnly(forgedZipFile, options)) {
      assertNotNull(zf.get("foo"));
    }

    Assert.assertEquals(
        "Central directory and local header extra fields for file 'foo' do not match",
        log.getLogs().get(0));
  }

  private static void checkParity(File zipFile, String entryName, byte[] bytes) throws Exception {
    try (ZFile zf = ZFile.openReadOnly(zipFile)) {
      StoredEntry foo = zf.get(entryName);
      assertNotNull(foo);
      assertEquals(foo.getLocalExtra(), foo.getCentralDirectoryHeader().getExtraField(), bytes);
    }
  }

  private static void assertEquals(ExtraField ef1, ExtraField ef2, byte[] bytes)
      throws IOException {
    byte[] buffer1 = new byte[ef1.size()];
    byte[] buffer2 = new byte[ef2.size()];

    ef1.write(ByteBuffer.wrap(buffer1));
    ef2.write(ByteBuffer.wrap(buffer2));

    assertArrayEquals(buffer1, buffer2);
    assertArrayEquals(buffer1, bytes);
  }
}
