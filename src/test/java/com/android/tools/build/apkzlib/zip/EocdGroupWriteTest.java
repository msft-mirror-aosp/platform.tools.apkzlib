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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class EocdGroupWriteTest {

  public ZFile zFile;

  public FileUseMap fileUseMap;

  public CentralDirectory directory;

  public FileUseMapEntry<CentralDirectory> entry;

  @Before
  public void before() {
    zFile = Mockito.mock(ZFile.class);
    Mockito.when(zFile.getVerifyLog()).thenReturn(Mockito.mock(VerifyLog.class));
    entry = null;
    directory = Mockito.mock(CentralDirectory.class);
  }

  private void setupEmptyDirectory() {
    fileUseMap = new FileUseMap(0, 0);
    entry = null;
  }

  private void setupDirectory(long dirStart, long dirSize, long dirNumEntries, boolean zip64Files) {
    fileUseMap = new FileUseMap(dirStart + dirSize, 0);
    entry = fileUseMap.add(dirStart, dirStart+dirSize, directory);

    HashMap<String, StoredEntry> map = new HashMap<>();
    for (int i = 0; i < dirNumEntries; ++i) {
      map.put(Integer.toString(i), null);
    }
    Mockito.when(directory.containsZip64Files()).thenReturn(zip64Files);
    Mockito.when(directory.getEntries()).thenReturn(map);
  }

  @Test
  public void emptyFileEocdGroupWriteTest() throws IOException {
    setupEmptyDirectory();

    EocdGroup group = new EocdGroup(zFile, fileUseMap);
    group.computeRecord(entry, 0x45);

    byte[] expectedEocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x05, 0x06,
        /* number of the disk */
        0x00, 0x00,
        /* disk with start central directory */
        0x00, 0x00,
        /* total number of CD entries on this disk */
        0x00, 0x00,
        /* total number of CD entries */
        0x00, 0x00,
        /* size of CD */
        0x00, 0x00, 0x00, 0x00,
        /* offset of CD from start of archive */
        0x45, 0x00, 0x00, 0x00,
        /* comment length */
        0x00, 0x00
        /* comment (empty) */
    };
    assertArrayEquals(expectedEocdBytes, group.getEocdBytes());
    assertNull(group.getEocdLocatorBytes());
    assertNull(group.getZ64EocdBytes());
    assertEquals(0, group.getRecordStart());
  }

  @Test
  public void basicEocdGroupWriteTest() throws IOException {
    long dirStart = 0x018a26b2;
    long dirSize = 0x162;
    long extraOffset = 0;
    setupDirectory(dirStart, dirSize, 0x43, false);

    EocdGroup group = new EocdGroup(zFile, fileUseMap);
    group.computeRecord(entry, extraOffset);

    byte[] expectedEocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x05, 0x06,
        /* number of the disk */
        0x00, 0x00,
        /* disk with start central directory */
        0x00, 0x00,
        /* total number of CD entries on this disk */
        0x43, 0x00,
        /* total number of CD entries */
        0x43, 0x00,
        /* size of CD */
        0x62, 0x01, 0x00, 0x00,
        /* offset of CD from start of archive */
        (byte) 0xb2, 0x26, (byte) 0x8a, 0x01,
        /* comment length */
        0x00, 0x00
        /* comment (empty) */
    };
    long expectedStart = dirStart + dirSize;

    assertArrayEquals(expectedEocdBytes, group.getEocdBytes());
    assertNull(group.getEocdLocatorBytes());
    assertNull(group.getZ64EocdBytes());
    assertEquals(expectedStart, group.getRecordStart());
  }

  @Test
  public void zip64FilesForcesZip64EocdWriteTest() throws IOException {
    long dirStart = 0x018a26b2;
    long dirSize = 0x162;
    long extraOffset = 0;
    setupDirectory(dirStart, dirSize, 0x43, true);

    EocdGroup group = new EocdGroup(zFile, fileUseMap);
    group.computeRecord(entry, extraOffset);

    byte[] expectedEocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x05, 0x06,
        /* number of the disk */
        0x00, 0x00,
        /* disk with start central directory */
        0x00, 0x00,
        /* total number of CD entries on this disk */
        0x43, 0x00,
        /* total number of CD entries */
        0x43, 0x00,
        /* size of CD */
        0x62, 0x01, 0x00, 0x00,
        /* offset of CD from start of archive */
        (byte) 0xb2, 0x26, (byte) 0x8a, 0x01,
        /* comment length */
        0x00, 0x00
        /* comment (empty) */
    };
    byte[] expectedEocdLocator = new byte[] {
        /* signature (Little Endian)*/
        0x50, 0x4b, 0x06, 0x07,
        /* number of disk with zip64 EOCD */
        0x00, 0x00, 0x00, 0x00,
        /* relative offset of the zip64 EOCD (dir size + dir start) */
        0x14, 0x28, (byte) 0x8a, 0x01, 0x00, 0x00, 0x00, 0x00,
        /* total number of disks */
        0x00, 0x00, 0x00, 0x00
    };
    byte[] expectedZ64EocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x06, 0x06,
        /* size of zip64 end of central directory record (44) */
        0x2c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* version made by */
        0x18, 0x00,
        /* version needed to extract (v1) */
        45, 0x00,
        /* disk number */
        0x00, 0x00, 0x00, 0x00,
        /* disk with start of CD */
        0x00, 0x00, 0x00, 0x00,
        /* total number of CD entries on this disk */
        0x43, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* total number of CD entries */
        0x43, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* size of CD */
        0x62, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* offset of CD from start of archive */
        (byte) 0xb2, 0x26, (byte) 0x8a, 0x01, 0x00, 0x00, 0x00, 0x00,
        /* extensible data sector (empty) */
    };
    long expectedStart = dirStart + dirSize;

    assertArrayEquals(expectedEocdBytes, group.getEocdBytes());
    assertNotNull(group.getEocdLocatorBytes());
    assertArrayEquals(expectedEocdLocator, group.getEocdLocatorBytes());
    assertNotNull(group.getZ64EocdBytes());
    assertArrayEquals(expectedZ64EocdBytes, group.getZ64EocdBytes());
    assertEquals(expectedStart, group.getRecordStart());
  }

  @Test
  public void directoryOffsetForcesZip64EocdWriteTest() throws IOException {
    long dirStart = 0x0180a09c4dL;
    long dirSize = 0x089341a2;
    long extraOffset = 0;
    setupDirectory(dirStart, dirSize, 0xF389, false);

    EocdGroup group = new EocdGroup(zFile, fileUseMap);
    group.computeRecord(entry, extraOffset);

    byte[] expectedEocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x05, 0x06,
        /* number of the disk */
        0x00, 0x00,
        /* disk with start central directory */
        0x00, 0x00,
        /* total number of CD entries on this disk */
        (byte) 0x89, (byte) 0xf3,
        /* total number of CD entries */
        (byte) 0x89, (byte) 0xf3,
        /* size of CD */
        (byte) 0xa2, 0x41, (byte) 0x93, 0x08,
        /* offset of CD from start of archive (0xFFFFFFFF) */
        -1, -1, -1, -1,
        /* comment length */
        0x00, 0x00
        /* comment (empty) */
    };
    byte[] expectedEocdLocator = new byte[] {
        /* signature (Little Endian)*/
        0x50, 0x4b, 0x06, 0x07,
        /* number of disk with zip64 EOCD */
        0x00, 0x00, 0x00, 0x00,
        /* relative offset of the zip64 EOCD (dir size + dir start) */
        (byte) 0xef, (byte) 0xdd, 0x33, (byte) 0x89, 0x01, 0x00, 0x00, 0x00,
        /* total number of disks */
        0x00, 0x00, 0x00, 0x00
    };
    byte[] expectedZ64EocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x06, 0x06,
        /* size of zip64 end of central directory record (44) */
        0x2c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* version made by */
        0x18, 0x00,
        /* version needed to extract (v1) */
        45, 0x00,
        /* disk number */
        0x00, 0x00, 0x00, 0x00,
        /* disk with start of CD */
        0x00, 0x00, 0x00, 0x00,
        /* total number of CD entries on this disk */
        (byte) 0x89, (byte) 0xf3, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* total number of CD entries */
        (byte) 0x89, (byte) 0xf3, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* size of CD */
        (byte) 0xa2, 0x41, (byte) 0x93, 0x08, 0x00, 0x00, 0x00, 0x00,
        /* offset of CD from start of archive */
        0x4d, (byte) 0x9c, (byte) 0xa0, (byte) 0x80, 0x01, 0x00, 0x00, 0x00
        /* extensible data sector (empty) */
    };
    long expectedStart = dirStart + dirSize;
    long expectedSize =
        ((long) expectedEocdBytes.length)
        + expectedEocdLocator.length
        + expectedZ64EocdBytes.length;

    assertArrayEquals(expectedEocdBytes, group.getEocdBytes());
    assertNotNull(group.getEocdLocatorBytes());
    assertArrayEquals(expectedEocdLocator, group.getEocdLocatorBytes());
    assertNotNull(group.getZ64EocdBytes());
    assertArrayEquals(expectedZ64EocdBytes, group.getZ64EocdBytes());
    assertEquals(expectedStart, group.getRecordStart());
    assertEquals(expectedSize, group.getRecordSize());
  }

@Test
  public void storedEntriesForcesZip64EocdWriteTest() throws IOException {
    long dirStart = 0x453a297cL;
    long dirSize = 0x089341a2;
    long extraOffset = 0;
    setupDirectory(dirStart, dirSize, 0x37cd32L, false);

    EocdGroup group = new EocdGroup(zFile, fileUseMap);
    group.computeRecord(entry, extraOffset);

    byte[] expectedEocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x05, 0x06,
        /* number of the disk */
        0x00, 0x00,
        /* disk with start central directory */
        0x00, 0x00,
        /* total number of CD entries on this disk (0xFFFF) */
        -1, -1,
        /* total number of CD entries (0xFFFF) */
        -1, -1,
        /* size of CD */
        (byte) 0xa2, 0x41, (byte) 0x93, 0x08,
        /* offset of CD from start of archive */
        0x7c, 0x29, 0x3a, 0x45,
        /* comment length */
        0x00, 0x00
        /* comment (empty) */
    };
    byte[] expectedEocdLocator = new byte[] {
        /* signature (Little Endian)*/
        0x50, 0x4b, 0x06, 0x07,
        /* number of disk with zip64 EOCD */
        0x00, 0x00, 0x00, 0x00,
        /* relative offset of the zip64 EOCD (dir size + dir start) */
        0x1e, 0x6b, (byte) 0xcd, 0x4d, 0x00, 0x00, 0x00, 0x00,
        /* total number of disks */
        0x00, 0x00, 0x00, 0x00
    };
    byte[] expectedZ64EocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x06, 0x06,
        /* size of zip64 end of central directory record (44) */
        0x2c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* version made by */
        0x18, 0x00,
        /* version needed to extract (v1) */
        45, 0x00,
        /* disk number */
        0x00, 0x00, 0x00, 0x00,
        /* disk with start of CD */
        0x00, 0x00, 0x00, 0x00,
        /* total number of CD entries on this disk */
        0x32, (byte) 0xcd, 0x37, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* total number of CD entries */
        0x32, (byte) 0xcd, 0x37, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* size of CD */
        (byte) 0xa2, 0x41, (byte) 0x93, 0x08, 0x00, 0x00, 0x00, 0x00,
        /* offset of CD from start of archive */
        0x7c, 0x29, 0x3a, 0x45, 0x00, 0x00, 0x00, 0x00
        /* extensible data sector (empty) */
    };
    long expectedStart = dirStart + dirSize;

    assertArrayEquals(expectedEocdBytes, group.getEocdBytes());
    assertNotNull(group.getEocdLocatorBytes());
    assertArrayEquals(expectedEocdLocator, group.getEocdLocatorBytes());
    assertNotNull(group.getZ64EocdBytes());
    assertArrayEquals(expectedZ64EocdBytes, group.getZ64EocdBytes());
    assertEquals(expectedStart, group.getRecordStart());
  }

  @Test
  public void zip64EocdVersion2WriteTest() throws IOException {
    long dirStart = 0x453a297cL;
    long dirSize = 0x089341a2;
    long extraOffset = 0;
    setupDirectory(dirStart, dirSize, 0x37cd32L, false);

    EocdGroup group = new EocdGroup(zFile, fileUseMap);
    group.setUseVersion2Header(true);
    group.computeRecord(entry, extraOffset);

    byte[] expectedEocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x05, 0x06,
        /* number of the disk */
        0x00, 0x00,
        /* disk with start central directory */
        0x00, 0x00,
        /* total number of CD entries on this disk (0xFFFF) */
        -1, -1,
        /* total number of CD entries (0xFFFF) */
        -1, -1,
        /* size of CD */
        (byte) 0xa2, 0x41, (byte) 0x93, 0x08,
        /* offset of CD from start of archive */
        0x7c, 0x29, 0x3a, 0x45,
        /* comment length */
        0x00, 0x00
        /* comment (empty) */
    };
    byte[] expectedEocdLocator = new byte[] {
        /* signature (Little Endian)*/
        0x50, 0x4b, 0x06, 0x07,
        /* number of disk with zip64 EOCD */
        0x00, 0x00, 0x00, 0x00,
        /* relative offset of the zip64 EOCD (dir size + dir start) */
        0x1e, 0x6b, (byte) 0xcd, 0x4d, 0x00, 0x00, 0x00, 0x00,
        /* total number of disks */
        0x00, 0x00, 0x00, 0x00
    };
    byte[] expectedZ64EocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x06, 0x06,
        /* size of zip64 end of central directory record (72) */
        0x48, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* version made by */
        0x18, 0x00,
        /* version needed to extract (v1) */
        62, 0x00,
        /* disk number */
        0x00, 0x00, 0x00, 0x00,
        /* disk with start of CD */
        0x00, 0x00, 0x00, 0x00,
        /* total number of CD entries on this disk */
        0x32, (byte) 0xcd, 0x37, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* total number of CD entries */
        0x32, (byte) 0xcd, 0x37, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* size of CD */
        (byte) 0xa2, 0x41, (byte) 0x93, 0x08, 0x00, 0x00, 0x00, 0x00,
        /* offset of CD from start of archive */
        0x7c, 0x29, 0x3a, 0x45, 0x00, 0x00, 0x00, 0x00,
        /* compression method */
        0x00, 0x00,
        /* size of compressed data */
        (byte) 0xa2, 0x41, (byte) 0x93, 0x08, 0x00, 0x00, 0x00, 0x00,
        /* size of uncompressed data */
        (byte) 0xa2, 0x41, (byte) 0x93, 0x08, 0x00, 0x00, 0x00, 0x00,
        /* encryption algorithm id */
        0x00, 0x00,
        /* encryption key length */
        0x00, 0x00,
        /* encryption flags */
        0x00, 0x00,
        /* hash algorithm id */
        0x00, 0x00,
        /* length of hash data */
        0x00, 0x00,
        /* hash data (empty) */
        /* extensible data sector (empty) */
    };
    long expectedStart = dirStart + dirSize;

    assertArrayEquals(expectedEocdBytes, group.getEocdBytes());
    assertNotNull(group.getEocdLocatorBytes());
    assertArrayEquals(expectedEocdLocator, group.getEocdLocatorBytes());
    assertNotNull(group.getZ64EocdBytes());
    assertArrayEquals(expectedZ64EocdBytes, group.getZ64EocdBytes());
    assertEquals(expectedStart, group.getRecordStart());
  }
}
