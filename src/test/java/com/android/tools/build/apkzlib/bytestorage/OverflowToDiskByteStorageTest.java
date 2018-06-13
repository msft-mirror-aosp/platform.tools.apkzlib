package com.android.tools.build.apkzlib.bytestorage;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.build.apkzlib.zip.utils.CloseableByteSource;
import com.google.common.io.ByteSource;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class OverflowToDiskByteStorageTest {

  @Parameter(0)
  public Integer memoryCacheSize;

  private ByteStorage makeStorage() throws IOException {
    return new OverflowToDiskByteStorage(
        memoryCacheSize, TemporaryDirectory::newSystemTemporaryDirectory);
  }

  @Parameters
  public static List<Object[]> getParameters() {
    // Make sure to keep the values here synchronized with ByteStorageTest.
    return Arrays.asList(new Object[][] {{0}, {10}, {1000}});
  }

  @Test
  public void dataStaysInRamUntilItDoesntFit() throws Exception {
    Assume.assumeTrue(memoryCacheSize > 0);

    int magicSize = 7;

    try (OverflowToDiskByteStorage storage = (OverflowToDiskByteStorage) makeStorage()) {
      int total = 0;
      while (total < memoryCacheSize) {
        assertThat(storage.getMemoryBytesUsed()).isEqualTo(total);
        assertThat(storage.getMaxMemoryBytesUsed()).isEqualTo(total);
        assertThat(storage.getDiskBytesUsed()).isEqualTo(0);
        assertThat(storage.getMaxDiskBytesUsed()).isEqualTo(0);

        storage.fromSource(ByteSource.wrap(new byte[magicSize]));
        total += magicSize;
      }

      // We've went beyond memoryCacheSize by 1. Make sure the values make sense.
      assertThat(storage.getMemoryBytesUsed()).isEqualTo(total - magicSize);
      assertThat(storage.getMaxMemoryBytesUsed()).isEqualTo(total);
      assertThat(storage.getDiskBytesUsed()).isEqualTo(magicSize);
      assertThat(storage.getMaxDiskBytesUsed()).isEqualTo(magicSize);
      total += magicSize;

      storage.fromSource(ByteSource.wrap(new byte[magicSize]));
      assertThat(storage.getMemoryBytesUsed()).isEqualTo(total - 2 * magicSize);
      assertThat(storage.getMaxMemoryBytesUsed()).isEqualTo(total - magicSize);
      assertThat(storage.getDiskBytesUsed()).isEqualTo(2 * magicSize);
      assertThat(storage.getMaxDiskBytesUsed()).isEqualTo(2 * magicSize);
    }
  }

  @Test
  public void diskDataIsNotLoadedIntoRamAgain() throws Exception {
    int magicSize = 7;

    try (OverflowToDiskByteStorage storage = (OverflowToDiskByteStorage) makeStorage()) {
      int total = 0;
      List<CloseableByteSource> open = new ArrayList<>();
      while (total == 0 || total < memoryCacheSize) {
        open.add(storage.fromSource(ByteSource.wrap(new byte[magicSize])));
        total += magicSize;
      }

      // At this point we have some data on disk already.
      assertThat(storage.getDiskBytesUsed()).isEqualTo(magicSize);

      // If we close all the most recent ones, we will be left with no data being in RAM and
      // data still on disk.
      while (open.size() > 1) {
        open.remove(1).close();
      }

      assertThat(storage.getMemoryBytesUsed()).isEqualTo(0);
      assertThat(storage.getDiskBytesUsed()).isEqualTo(magicSize);
    }
  }

  @Test
  public void closingOverflowStorageDeletesDirectory() throws Exception {
    File tempDir;
    try (OverflowToDiskByteStorage storage = (OverflowToDiskByteStorage) makeStorage()) {
      tempDir = storage.diskStorage.temporaryDirectory.getDirectory();
    }

    assertThat(tempDir.exists()).isFalse();
  }
}
