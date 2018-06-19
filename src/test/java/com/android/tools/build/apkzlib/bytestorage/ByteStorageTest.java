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

package com.android.tools.build.apkzlib.bytestorage;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.build.apkzlib.zip.utils.CloseableByteSource;
import com.google.common.io.ByteSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ByteStorageTest {

  @Parameter public ByteStorage storage;

  @Parameters
  public static List<Object[]> getParameters() throws Exception {
    return Arrays.asList(
        new Object[][] {
          {new InMemoryByteStorage()},
          {new TemporaryDirectoryStorage(TemporaryDirectory::newSystemTemporaryDirectory)},
          // Make sure to keep the values here synchronized with OverflowToDiskByteStorageTest.
          {new OverflowToDiskByteStorage(0, TemporaryDirectory::newSystemTemporaryDirectory)},
          {new OverflowToDiskByteStorage(10, TemporaryDirectory::newSystemTemporaryDirectory)},
          {new OverflowToDiskByteStorage(1000, TemporaryDirectory::newSystemTemporaryDirectory)},
          {new ChunkBasedByteStorage(10, new InMemoryByteStorage())},
          {new ChunkBasedByteStorage(1000, new InMemoryByteStorage())},
          {
            new ChunkBasedByteStorage(
                10,
                new OverflowToDiskByteStorage(10, TemporaryDirectory::newSystemTemporaryDirectory))
          },
          {
            new ChunkBasedByteStorage(
                1000,
                new OverflowToDiskByteStorage(10, TemporaryDirectory::newSystemTemporaryDirectory))
          },
          {
            new ChunkBasedByteStorage(
                10,
                new OverflowToDiskByteStorage(
                    1000, TemporaryDirectory::newSystemTemporaryDirectory))
          },
          {
            new ChunkBasedByteStorage(
                1000,
                new OverflowToDiskByteStorage(
                    1000, TemporaryDirectory::newSystemTemporaryDirectory))
          },
        });
  }

  @Test
  public void createFromInputStream() throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[] {1, 2, 3});

    try (CloseableByteSource bs = storage.fromStream(inputStream)) {
      assertThat(bs.size()).isEqualTo(3);

      InputStream sourceStream = bs.openStream();
      assertThat(sourceStream.read()).isEqualTo(1);
      assertThat(sourceStream.read()).isEqualTo(2);
      assertThat(sourceStream.read()).isEqualTo(3);
      assertThat(sourceStream.read()).isEqualTo(-1);
    }
  }

  @Test
  public void createFromByteArrayOutputStream() throws IOException {
    CloseableByteSourceFromOutputStreamBuilder bsBuilder = storage.makeBuilder();
    bsBuilder.write(1);
    bsBuilder.write(2);
    bsBuilder.write(3);

    try (CloseableByteSource bs = bsBuilder.build()) {
      assertThat(bs.size()).isEqualTo(3);

      InputStream sourceStream = bs.openStream();
      assertThat(sourceStream.read()).isEqualTo(1);
      assertThat(sourceStream.read()).isEqualTo(2);
      assertThat(sourceStream.read()).isEqualTo(3);
      assertThat(sourceStream.read()).isEqualTo(-1);
    }
  }

  @Test
  public void createFromByteSource() throws Exception {
    byte[] array = new byte[] {1, 2, 3};
    ByteSource source = ByteSource.wrap(array);

    try (CloseableByteSource bs = storage.fromSource(source)) {
      assertThat(bs.size()).isEqualTo(3);

      InputStream sourceStream = bs.openStream();
      assertThat(sourceStream.read()).isEqualTo(1);
      assertThat(sourceStream.read()).isEqualTo(2);
      assertThat(sourceStream.read()).isEqualTo(3);
      assertThat(sourceStream.read()).isEqualTo(-1);
    }
  }

  @Test
  public void tracksCurrentUsage() throws Exception {
    assertThat(storage.getBytesUsed()).isEqualTo(0);

    CloseableByteSource bs0 = storage.fromSource(ByteSource.wrap(new byte[10]));
    assertThat(storage.getBytesUsed()).isEqualTo(10);

    CloseableByteSource bs1 = storage.fromSource(ByteSource.wrap(new byte[15]));
    assertThat(storage.getBytesUsed()).isEqualTo(25);

    bs0.close();
    assertThat(storage.getBytesUsed()).isEqualTo(15);

    bs1.close();
  }

  @Test
  public void tracksMaxUsage() throws Exception {
    assertThat(storage.getMaxBytesUsed()).isEqualTo(0);

    CloseableByteSource bs0 = storage.fromSource(ByteSource.wrap(new byte[10]));
    assertThat(storage.getMaxBytesUsed()).isEqualTo(10);

    CloseableByteSource bs1 = storage.fromSource(ByteSource.wrap(new byte[15]));
    assertThat(storage.getMaxBytesUsed()).isEqualTo(25);

    bs0.close();
    assertThat(storage.getMaxBytesUsed()).isEqualTo(25);

    bs1.close();
  }
}
