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
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ChunkBasedByteStorageTest {

  @Test
  public void createFromSource() throws IOException {
    final int MORE_THAN_20MB = 21*1024*1024;
    ByteSource source = ByteSource.wrap(new byte[MORE_THAN_20MB]);

    try (ChunkBasedByteStorage storage = new ChunkBasedByteStorage(new InMemoryByteStorageFactory().create());
      CloseableByteSource bs = storage.fromSource(source)) {
      assertThat(bs.size()).isEqualTo(MORE_THAN_20MB);
    }
  }
}
