/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.build.apkzlib.zip.utils;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LittleEndianUtilsTest {
  @Test
  public void read2Le() throws Exception {
    assertEquals(0x0102, LittleEndianUtils.readUnsigned2Le(ByteBuffer.wrap(new byte[] {2, 1})));
    assertEquals(
        0xfedc,
        LittleEndianUtils.readUnsigned2Le(ByteBuffer.wrap(new byte[] {(byte) 0xdc, (byte) 0xfe})));
  }

  @Test
  public void write2Le() throws Exception {
    ByteBuffer out = ByteBuffer.allocate(2);
    LittleEndianUtils.writeUnsigned2Le(out, 0x0102);
    assertArrayEquals(new byte[] {2, 1}, out.array());

    out = ByteBuffer.allocate(2);
    LittleEndianUtils.writeUnsigned2Le(out, 0xfedc);
    assertArrayEquals(new byte[] {(byte) 0xdc, (byte) 0xfe}, out.array());
  }

  @Test
  public void readWrite2Le() throws Exception {
    Random r = new Random();

    int range = 0x0000ffff;

    final int COUNT = 1000;
    int[] data = new int[COUNT];
    for (int i = 0; i < data.length; i++) {
      data[i] = r.nextInt(range);
    }

    ByteBuffer out = ByteBuffer.allocate(COUNT * 2);
    for (int d : data) {
      LittleEndianUtils.writeUnsigned2Le(out, d);
    }

    ByteBuffer in = ByteBuffer.wrap(out.array());
    for (int i = 0; i < data.length; i++) {
      assertEquals(data[i], LittleEndianUtils.readUnsigned2Le(in));
    }
  }

  @Test
  public void read4Le() throws Exception {
    assertEquals(
        0x01020304, LittleEndianUtils.readUnsigned4Le(ByteBuffer.wrap(new byte[] {4, 3, 2, 1})));
    assertEquals(
        0xfedcba98L,
        LittleEndianUtils.readUnsigned4Le(
            ByteBuffer.wrap(new byte[] {(byte) 0x98, (byte) 0xba, (byte) 0xdc, (byte) 0xfe})));
  }

  @Test
  public void write4Le() throws Exception {
    ByteBuffer out = ByteBuffer.allocate(4);
    LittleEndianUtils.writeUnsigned4Le(out, 0x01020304);
    assertArrayEquals(new byte[] {4, 3, 2, 1}, out.array());

    out = ByteBuffer.allocate(4);
    LittleEndianUtils.writeUnsigned4Le(out, 0xfedcba98L);
    assertArrayEquals(new byte[] {(byte) 0x98, (byte) 0xba, (byte) 0xdc, (byte) 0xfe}, out.array());
  }

  @Test
  public void readWrite4Le() throws Exception {
    Random r = new Random();

    final int COUNT = 1000;
    long[] data = new long[COUNT];
    for (int i = 0; i < data.length; i++) {
      do {
        data[i] = r.nextInt() - (long) Integer.MIN_VALUE;
      } while (data[i] < 0);
    }

    ByteBuffer out = ByteBuffer.allocate(COUNT * 4);
    for (long d : data) {
      LittleEndianUtils.writeUnsigned4Le(out, d);
    }

    ByteBuffer in = ByteBuffer.wrap(out.array());
    for (int i = 0; i < data.length; i++) {
      assertEquals(data[i], LittleEndianUtils.readUnsigned4Le(in));
    }
  }

  @Test
  public void read8Le() throws Exception {
    ByteBuffer in = ByteBuffer.wrap(new byte[] {8, 7, 6, 5, 4, 3, 2, 1});
    in.order(ByteOrder.BIG_ENDIAN);
    assertEquals(
        0x0102030405060708L,
        LittleEndianUtils.readUnsigned8Le(in));
    assertEquals(ByteOrder.BIG_ENDIAN, in.order());
    assertEquals(
        0xf0e0d0c0b0a09080L,
        LittleEndianUtils.readUnsigned8Le(
            ByteBuffer.wrap(new byte[] {(byte) 0x80, (byte) 0x90, (byte) 0xa0, (byte) 0xb0,
                (byte) 0xc0, (byte) 0xd0, (byte) 0xe0, (byte) 0xf0})));
  }

  @Test
  public void write8Le() throws Exception {
    ByteBuffer out = ByteBuffer.allocate(8);
    // ensure that endianness doesn't matter.
    out.order(ByteOrder.BIG_ENDIAN);
    LittleEndianUtils.writeUnsigned8Le(out, 0x0102030405060708L);
    assertArrayEquals(new byte[] {8, 7, 6, 5, 4, 3, 2, 1}, out.array());

    out = ByteBuffer.allocate(8);
    LittleEndianUtils.writeUnsigned8Le(out, 0x8f2e3d4c5b6a7988L);
    assertArrayEquals(
        new byte[] {(byte) 0x88, (byte) 0x79, (byte) 0x6a, (byte) 0x5b,
            (byte) 0x4c, (byte) 0x3d, (byte) 0x2e, (byte) 0x8f},
        out.array());
    assertEquals(ByteOrder.BIG_ENDIAN, out.order());
  }

  @Test
  public void readWrite8Le() throws Exception {
    Random r = new Random();
    final int COUNT = 1000;
    long[] data = new long[COUNT];
    for (int i = 0; i < COUNT; ++i) {
      data[i] = r.nextLong();
    }

    ByteBuffer out = ByteBuffer.allocate(COUNT * 8);
    for (long d : data) {
      LittleEndianUtils.writeUnsigned8Le(out, d);
    }

    ByteBuffer in = ByteBuffer.wrap(out.array());
    for (int i = 0; i < COUNT; ++i) {
      assertEquals(data[i], LittleEndianUtils.readUnsigned8Le(in));
    }
  }

  @Test
  public void read8LeOverflow() throws Exception {
    byte[] bytes = new byte[]{0x49, (byte) 0xa2, (byte) 0x93, 0x3b, 0x51, 0x22, 0x18};
    ByteBuffer in = ByteBuffer.wrap(bytes);
    try {
      LittleEndianUtils.readUnsigned8Le(in);
      fail();
    } catch (EOFException e) {
      // expected...
    }
  }
}
