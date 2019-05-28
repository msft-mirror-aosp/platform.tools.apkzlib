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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.build.apkzlib.zip.Zip64ExtensibleDataSector.Z64SpecialPurposeData;
import com.google.common.primitives.Ints;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class EocdGroupReadTest {

  private ZFile zf;
  private FileUseMap fileUseMap;

  private long eocdRecordOffset = 0;
  private byte[] eocdRecord = new byte[0];

  @Before
  public void before() throws IOException {
    zf = Mockito.mock(ZFile.class);
    Mockito.when(zf.getVerifyLog()).thenReturn(Mockito.mock(VerifyLog.class));
    Mockito.doAnswer(
            (invocation) -> {
              Object[] args = invocation.getArguments();
              long offset = (Long) args[0];
              byte[] bytes = (byte[]) args[1];

              if (offset < 0) {
                throw new IOException("error reading eocd correctly, tried to read before file");
              }
              if (offset + bytes.length > getFileSize()) {
                throw new IOException("error reading eocd correctly, tried to read past file");
              }

              int currentByteOffset = 0;

              // need to fill the byte array before it hits the eocdRecord.
              if (offset < eocdRecordOffset) {
                int emptyReads =
                    Math.min(bytes.length, Ints.checkedCast(eocdRecordOffset - offset));
                Arrays.fill(bytes, 0, emptyReads, (byte) 0x00);
                currentByteOffset = emptyReads;
                offset = 0L;
              } else {
                offset -= eocdRecordOffset;
              }

              System.arraycopy(
                  eocdRecord,
                  Ints.checkedCast(offset),
                  bytes,
                  currentByteOffset,
                  bytes.length - currentByteOffset);

              return null;
            })
        .when(zf)
        .directFullyRead(ArgumentMatchers.anyLong(), ArgumentMatchers.any(byte[].class));
  }

  private void setup(
      long eocdRecordOffset,
      byte[] z64Eocd,
      int locatorOffset,
      byte[] z64EocdLocator,
      byte[] eocd) throws IOException {
    this.eocdRecordOffset = eocdRecordOffset;

    int totalEocdBytes = z64Eocd.length + locatorOffset + z64EocdLocator.length + eocd.length;
    // check overflow
    if (totalEocdBytes < 0) {
      throw new IOException("int overflow in creating eocdRecord in setup");
    }
    // build the record.
    eocdRecord = new byte[totalEocdBytes];

    int currentOffset = 0;

    System.arraycopy(z64Eocd, 0, eocdRecord, currentOffset, z64Eocd.length);
    currentOffset += z64Eocd.length;

    Arrays.fill(eocdRecord, currentOffset, currentOffset + locatorOffset, (byte) 0x00);
    currentOffset += locatorOffset;

    System.arraycopy(z64EocdLocator, 0, eocdRecord, currentOffset, z64EocdLocator.length);
    currentOffset += z64EocdLocator.length;

    System.arraycopy(eocd, 0, eocdRecord, currentOffset, eocd.length);

    fileUseMap = new FileUseMap(getFileSize(), 0);
  }

  private long getFileSize() {
    return eocdRecordOffset + eocdRecord.length;
  }

  @Test
  public void basicEocdRead() throws IOException {
    byte[] eocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x05, 0x06,
        /* number of the disk */
        0x00, 0x00,
        /* disk with start central directory */
        0x00, 0x00,
        /* total number of CD entries on this disk */
        0x20, 0x00,
        /* total number of CD entries */
        0x20, 0x00,
        /* size of CD */
        0x40, 0x06, 0x00, 0x00,
        /* offset of CD from start of archive */
        0x24, 0x45, 0x02, 0x00,
        /* comment length */
        0x00, 0x00
        /* comment (empty) */
    };
    byte[] zip64EocdBytes;
    byte[] eocdLocatorBytes;
    zip64EocdBytes = eocdLocatorBytes = new byte[0];

    setup(0x24b64L, zip64EocdBytes, 0, eocdLocatorBytes, eocdBytes);

    EocdGroup group = new EocdGroup(zf, fileUseMap);
    group.readRecord(getFileSize());

    assertEquals(0x24b64L, group.getRecordStart());
    assertEquals(22, group.getRecordSize());
    assertEquals(0x24524L, group.getDirectoryOffset());
    assertEquals(0x640L, group.getDirectorySize());
    assertEquals(0x20, group.getTotalDirectoryRecords());
    assertEquals(eocdRecord.length, group.getRecordSize());
    assertArrayEquals(new byte[0], group.getEocdComment());

    assertNull(group.getExtensibleData());
  }

  @Test
  public void basicEocdReadWithComment() throws IOException {
    byte[] eocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x05, 0x06,
        /* number of the disk */
        0x00, 0x00,
        /* disk with start central directory */
        0x00, 0x00,
        /* total number of CD entries on this disk */
        0x01, 0x00,
        /* total number of CD entries */
        0x01, 0x00,
        /* size of CD */
        0x20, 0x00, 0x00, 0x00,
        /* offset of CD from start of archive */
        0x28, 0x00, 0x00, 0x00,
        /* comment length */
        0x08, 0x00,
        /* comment */
        0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, (byte) 0x80
    };
    byte[] zip64EocdBytes;
    byte[] eocdLocatorBytes;
    zip64EocdBytes = eocdLocatorBytes = new byte[0];

    setup(0x48L, zip64EocdBytes, 0, eocdLocatorBytes, eocdBytes);

    EocdGroup group = new EocdGroup(zf, fileUseMap);
    group.readRecord(getFileSize());

    assertEquals(0x48L, group.getRecordStart());
    assertEquals(30, group.getRecordSize());
    assertEquals(0x28L, group.getDirectoryOffset());
    assertEquals(0x20L, group.getDirectorySize());
    assertEquals(1, group.getTotalDirectoryRecords());
    assertEquals(eocdRecord.length, group.getRecordSize());
    assertArrayEquals(
        new byte[]{0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, (byte) 0x80}, group.getEocdComment());

    assertNull(group.getExtensibleData());
  }

  @Test
  public void zip64EocdReadWithVersionOneRecord() throws IOException {
    byte[] zip64EocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x06, 0x06,
        /* size of zip64 end of central directory record (44) */
        0x2c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* version made by  (20)*/
        0x14, 0x00,
        /* version needed to extract (45, specifies v1 header is being used) */
        45, 0x00,
        /* disk number */
        0x00, 0x00, 0x00, 0x00,
        /* disk with start of CD */
        0x00, 0x00, 0x00, 0x00,
        /* total number of CD entries on this disk */
        0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* total number of CD entries */
        0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* size of CD */
        0x20, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* offset of CD from start of archive */
        (byte) 0xe0, 0x04, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
        /* extensible data sector (empty) */
    };

    byte[] locatorBytes = new byte[] {
        /* signature (Little Endian)*/
        0x50, 0x4b, 0x06, 0x07,
        /* number of disk with zip64 EOCD */
        0x00, 0x00, 0x00, 0x00,
        /* relative offset of the zip64 EOCD */
        0x00, 0x08, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
        /* total number of disks */
        0x00, 0x00, 0x00, 0x00
    };

    byte[] eocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x05, 0x06,
        /* number of the disk */
        0x00, 0x00,
        /* disk with start central directory */
        0x00, 0x00,
        /* total number of CD entries on this disk */
        0x06, 0x00,
        /* total number of CD entries */
        0x06, 0x00,
        /* size of CD */
        0x20, 0x03, 0x00, 0x00,
        /* offset of CD from start of archive (0xFFFFFFFF)*/
        -1, -1, -1, -1,
        /* comment length */
        0x00, 0x00
        /* comment (empty) */
    };

    setup(0x100000800L, zip64EocdBytes, 0, locatorBytes, eocdBytes);

    EocdGroup group = new EocdGroup(zf, fileUseMap);
    group.readRecord(getFileSize());

    assertEquals(0x100000800L, group.getRecordStart());
    assertEquals(eocdRecord.length, group.getRecordSize());
    assertEquals(0x1000004e0L, group.getDirectoryOffset());
    assertEquals(0x320L, group.getDirectorySize());
    assertEquals(6, group.getTotalDirectoryRecords());
    assertArrayEquals(new byte[0], group.getEocdComment());
    assertFalse(group.usingVersion2Header());

    assertNotNull(group.getEocdLocatorBytes());

    assertNotNull(group.getExtensibleData());
    List<Z64SpecialPurposeData> fields = group.getExtensibleData().getFields();
    Truth.assertThat(fields).isEmpty();
  }

  @Test
  public void zip64EocdReadWithVersionTwoRecord() throws IOException {
    byte[] zip64EocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x06, 0x06,
        /* size of zip64 end of central directory record (72) */
        0x48, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* version made by  (0)*/
        0x00, 0x00,
        /* version needed to extract (62, minimum to specify V2 header is being used) */
        62, 0x00,
        /* disk number */
        0x00, 0x00, 0x00, 0x00,
        /* disk with start of CD */
        0x00, 0x00, 0x00, 0x00,
        /* total number of CD entries on this disk */
        0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* total number of CD entries */
        0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* size of CD */
        0x00, 0x50, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* offset of CD from start of archive */
        0x00, (byte) 0xa2, 0x21, 0x08, 0x14, 0x00, 0x00, 0x00,
        /* CD compression method */
        0x00, 0x00,
        /* Compressed Size */
        0x00, 0x50, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* Uncompressed Size */
        0x00, 0x50, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* Encryption algorithm id */
        0x00, 0x00,
        /* Encryption key length */
        0x00, 0x00,
        /* Encryption flags */
        0x00, 0x00,
        /* Hash Algorithm Id */
        0x00, 0x00,
        /* Hash Length */
        0x00, 0x00,
        /* Hash (empty) */
        /* extensible data sector (empty) */
    };

    byte[] locatorBytes = new byte[] {
        /* signature (Little Endian)*/
        0x50, 0x4b, 0x06, 0x07,
        /* number of disk with zip64 EOCD */
        0x00, 0x00, 0x00, 0x00,
        /* relative offset of the zip64 EOCD */
        0x50, (byte) 0xe2, 0x21, 0x08, 0x14, 0x00, 0x00, 0x00,
        /* total number of disks */
        0x00, 0x00, 0x00, 0x00
    };

    byte[] eocdBytes = new byte[] {
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
        0x00, 0x50, 0x40, 0x00,
        /* offset of CD from start of archive (0xFFFFFFFF)*/
        -1, -1, -1, -1,
        /* comment length */
        0x00, 0x00
        /* comment (empty) */
    };

    setup(0x140821e250L, zip64EocdBytes, 87, locatorBytes, eocdBytes);

    EocdGroup group = new EocdGroup(zf, fileUseMap);
    group.readRecord(getFileSize());

    assertEquals(0x140821e250L, group.getRecordStart());
    assertEquals(eocdRecord.length, group.getRecordSize());
    assertEquals(0x140821a200L, group.getDirectoryOffset());
    assertEquals(0x405000L, group.getDirectorySize());
    assertEquals(0x10800L, group.getTotalDirectoryRecords());
    assertArrayEquals(new byte[0], group.getEocdComment());
    assertTrue(group.usingVersion2Header());

    assertNotNull(group.getEocdLocatorBytes());

    assertNotNull(group.getExtensibleData());
    List<Z64SpecialPurposeData> fields = group.getExtensibleData().getFields();
    Truth.assertThat(fields).isEmpty();
  }

  @Test
  public void zip64EocdReadWithVersionOneRecordAndExtensibleField() throws IOException {
    byte[] zip64EocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x06, 0x06,
        /* size of zip64 end of central directory record (76) */
        0x4c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* version made by  (20)*/
        0x14, 0x00,
        /* version needed to extract (45, specifies v1 header is being used) */
        45, 0x00,
        /* disk number */
        0x00, 0x00, 0x00, 0x00,
        /* disk with start of CD */
        0x00, 0x00, 0x00, 0x00,
        /* total number of CD entries on this disk */
        0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* total number of CD entries */
        0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* size of CD */
        0x20, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* offset of CD from start of archive */
        (byte) 0xe0, 0x04, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
        /* extensible data sector */
        /* header 1 */
        0x00, 0x02,
        /* data size (16) */
        0x10, 0x00, 0x00, 0x00,
        /* data */
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        /* header 2 */
        0x00, 0x04,
        /* data size */
        0x04, 0x00, 0x00, 0x00,
        /* data */
        0x00, 0x00, 0x00, 0x00
    };

    byte[] locatorBytes = new byte[] {
        /* signature (Little Endian)*/
        0x50, 0x4b, 0x06, 0x07,
        /* number of disk with zip64 EOCD */
        0x00, 0x00, 0x00, 0x00,
        /* relative offset of the zip64 EOCD */
        0x00, 0x08, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
        /* total number of disks */
        0x00, 0x00, 0x00, 0x00
    };

    byte[] eocdBytes = new byte[] {
        /* signature (Little Endian) */
        0x50, 0x4b, 0x05, 0x06,
        /* number of the disk */
        0x00, 0x00,
        /* disk with start central directory */
        0x00, 0x00,
        /* total number of CD entries on this disk */
        0x06, 0x00,
        /* total number of CD entries */
        0x06, 0x00,
        /* size of CD */
        0x20, 0x03, 0x00, 0x00,
        /* offset of CD from start of archive (0xFFFFFFFF)*/
        -1, -1, -1, -1,
        /* comment length */
        0x00, 0x00
        /* comment (empty) */
    };

    setup(0x100000800L, zip64EocdBytes, 0, locatorBytes, eocdBytes);

    EocdGroup group = new EocdGroup(zf, fileUseMap);
    group.readRecord(getFileSize());

    assertEquals(0x100000800L, group.getRecordStart());
    assertEquals(eocdRecord.length, group.getRecordSize());
    assertEquals(0x1000004e0L, group.getDirectoryOffset());
    assertEquals(0x320L, group.getDirectorySize());
    assertEquals(6, group.getTotalDirectoryRecords());
    assertArrayEquals(new byte[0], group.getEocdComment());
    assertFalse(group.usingVersion2Header());

    assertNotNull(group.getExtensibleData());
    List<Z64SpecialPurposeData> fields = group.getExtensibleData().getFields();
    Truth.assertThat(fields).hasSize(2);

    assertEquals(0x200, fields.get(0).getHeaderId());
    assertEquals(22, fields.get(0).size());

    assertEquals(0x400, fields.get(1).getHeaderId());
    assertEquals(10, fields.get(1).size());
  }

  @Test
  public void failToFindEocd() throws IOException {
    byte[] eocdBytes = new byte[] {
        /* incorrect signature (Little Endian) */
        0x50, 0x4a, 0x05, 0x06,
        /* number of the disk */
        0x00, 0x00,
        /* disk with start central directory */
        0x00, 0x00,
        /* total number of CD entries on this disk */
        0x06, 0x00,
        /* total number of CD entries */
        0x06, 0x00,
        /* size of CD */
        0x20, 0x03, 0x00, 0x00,
        /* offset of CD from start of archive (0xFFFFFFFF)*/
        -1, -1, -1, -1,
        /* comment length */
        0x00, 0x00
        /* comment (empty) */
    };

    setup(0x4000000, eocdBytes, 0, new byte[0], new byte[0]);

    try {
      EocdGroup group = new EocdGroup(zf, fileUseMap);
      group.readRecord(getFileSize());

      fail();
    } catch (IOException e) {
      // expected.
    }
  }
}
