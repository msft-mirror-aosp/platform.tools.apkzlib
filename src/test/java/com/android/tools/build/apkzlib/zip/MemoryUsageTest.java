package com.android.tools.build.apkzlib.zip;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.build.apkzlib.bytestorage.ByteStorage;
import com.android.tools.build.apkzlib.bytestorage.ByteStorageFactory;
import com.android.tools.build.apkzlib.bytestorage.ChunkBasedByteStorage;
import com.android.tools.build.apkzlib.bytestorage.ChunkBasedByteStorageFactory;
import com.android.tools.build.apkzlib.bytestorage.InMemoryByteStorage;
import com.android.tools.build.apkzlib.bytestorage.InMemoryByteStorageFactory;
import com.android.tools.build.apkzlib.bytestorage.OverflowToDiskByteStorage;
import com.android.tools.build.apkzlib.bytestorage.OverflowToDiskByteStorageFactory;
import com.android.tools.build.apkzlib.bytestorage.TemporaryDirectoryFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MemoryUsageTest {

  private static final byte[] VERY_COMPRESSIBLE_DATA = new byte[10000];
  private static final byte[] VERY_COMPRESSIBLE_DATA_DEFLATED = deflate(VERY_COMPRESSIBLE_DATA);
  private static final byte[] NOT_COMPRESSIBLE_DATA = randomData(11000);
  private static final byte[] NOT_COMPRESSIBLE_DATA_DEFLATED = deflate(NOT_COMPRESSIBLE_DATA);

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static byte[] deflate(byte[] input) {
    Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
    try (DeflaterOutputStream output = new DeflaterOutputStream(bytesOut, deflater)) {
      output.write(input);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    deflater.end();
    return bytesOut.toByteArray();
  }

  private static byte[] randomData(int size) {
    byte[] data = new byte[size];
    new Random(0).nextBytes(data);
    return data;
  }

  private ByteStorage buildZipAndGetStorage(ByteStorageFactory storageFactory) throws Exception {
    File zip = new File(temporaryFolder.getRoot(), "a.zip");

    ZFileOptions options = new ZFileOptions();
    options.setStorageFactory(storageFactory);
    ByteStorage storage;
    try (ZFile zf = new ZFile(zip, options)) {
      zf.add("very-compressible", new ByteArrayInputStream(VERY_COMPRESSIBLE_DATA));
      zf.add("very-compressible-stored", new ByteArrayInputStream(VERY_COMPRESSIBLE_DATA), false);
      zf.add("not-compressible", new ByteArrayInputStream(NOT_COMPRESSIBLE_DATA));
      zf.add("not-compressible-stored", new ByteArrayInputStream(NOT_COMPRESSIBLE_DATA), false);
      storage = zf.getStorage();
    }

    try (ZFile zf = new ZFile(zip)) {
      assertThat(
              zf.get("very-compressible")
                  .getCentralDirectoryHeader()
                  .getCompressionInfoWithWait()
                  .getCompressedSize())
          .isEqualTo(VERY_COMPRESSIBLE_DATA_DEFLATED.length);
      assertThat(
              zf.get("not-compressible")
                  .getCentralDirectoryHeader()
                  .getCompressionInfoWithWait()
                  .getMethod())
          .isEqualTo(CompressionMethod.STORE);
    }

    return storage;
  }

  @Test
  public void memoryStorageUsage() throws Exception {
    InMemoryByteStorage storage =
        (InMemoryByteStorage) buildZipAndGetStorage(new InMemoryByteStorageFactory());

    long remaining = storage.getBytesUsed();
    long max = storage.getMaxBytesUsed();

    assertThat(remaining).isEqualTo(0);

    // Why this value?
    // Contribution  | Step
    // +VCD          | 1. VCD added (source data in memory).
    // +VCD deflated | 2. VCD compressed (deflated data in memory).
    // +VCD          | 3. VCD added without compression (source data in memory).
    // +NCD          | 4. NCD added (source data in memory).
    // +NCD deflated | 5. NCD compressed (deflated data in memory, larger than NCD).
    //               | 6. NCD compressed is discarded because it is larger than NCD and NCD is
    //               | stored without compression.
    //               | 7. NCD added without compression (source data in memory), but it is smaller
    //               | than NCD compressed so it doesn't increase maximum size.
    assertThat(max)
        .isEqualTo(
            2 * VERY_COMPRESSIBLE_DATA.length
                + VERY_COMPRESSIBLE_DATA_DEFLATED.length
                + NOT_COMPRESSIBLE_DATA.length
                + NOT_COMPRESSIBLE_DATA_DEFLATED.length);
  }

  @Test
  public void overflowToDiskStorageUsage() throws Exception {
    OverflowToDiskByteStorage storage =
        (OverflowToDiskByteStorage)
            buildZipAndGetStorage(
                new OverflowToDiskByteStorageFactory(
                    1000L, TemporaryDirectoryFactory.fixed(temporaryFolder.newFolder())));

    long remaining = storage.getBytesUsed();
    long max = storage.getMaxBytesUsed();

    long maxMemory = storage.getMaxMemoryBytesUsed();
    long maxDisk = storage.getMaxDiskBytesUsed();

    assertThat(remaining).isEqualTo(0);

    // Why this value?
    // Contribution                  | Step
    // max           | maxMemory     | maxDisk       |
    // +VCD          | +VCD          |               | 1. VCD added (source data in memory).
    //               |               | +VCD          | [memory in use (VCD) greater than limit, so
    //               |               |               | VCD overflows to disk.]
    // +VCD deflated |               |               | 2. VCD compressed (deflated data in memory).
    //               |               |               | Does not increase maxMemory because VCD
    //               |               |               | compressed < VCD
    // +VCD          |               |               | 3. VCD added without compression (source data
    //               |               |               | in memory). Does not increase maxMemory
    //               |               |               | because the initial VCD was already removed
    //               |               |               | due to overflow.
    //               |               | +VCD          | [memory in use (VCD + VCD deflated) greater
    //               |               | +VCD deflated | than limit so VCD and VCD deflated overflow
    //               |               |               | to disk.]
    // +NCD          | +NCD - VCD    |               | 4. NCD added (source data in memory). We
    //               |               |               | increase memory usage by (NCD - VCD) because
    //               |               |               | NCD > VCD.
    //               |               | +NCD          | [memory in use (NCD) greater than limit so
    //               |               | -NCD%(VCD/4)  | NCD overflows to disk except for the last
    //               |               |               | chunk that stays in memory.]
    // +NCD deflated | -NCD          | +NCD%(VCD/4)  | 5. NCD compressed (deflated data in memory,
    //               | +NCD deflated |               | larger than NCD).
    //               |               | +NCD deflated | [memory in use (NCD deflated) greater than
    //               |               | -NCD deflated | limit so NCD overflows to disk.]
    //               |               | %(VCD/4)      |
    //               |               |               | 6. NCD compressed is discarded because it is
    //               |               |               | larger than NCD and NCD is stored without
    //               |               |               | compression.
    //               |               |               | 7. NCD added without compression (source data
    //               |               |               | in memory), but it is smaller than NCD
    //               |               |               | compressed so it doesn't increase maximum
    //               |               |               | size.

    assertThat(max)
        .isEqualTo(
            2 * VERY_COMPRESSIBLE_DATA.length
                + VERY_COMPRESSIBLE_DATA_DEFLATED.length
                + NOT_COMPRESSIBLE_DATA.length
                + NOT_COMPRESSIBLE_DATA_DEFLATED.length);
    assertThat(maxMemory).isEqualTo(NOT_COMPRESSIBLE_DATA_DEFLATED.length);
    assertThat(maxDisk)
        .isEqualTo(
            2 * VERY_COMPRESSIBLE_DATA.length
                + VERY_COMPRESSIBLE_DATA_DEFLATED.length
                + NOT_COMPRESSIBLE_DATA.length
                + NOT_COMPRESSIBLE_DATA_DEFLATED.length);
  }

  @Test
  public void chunkedOverflowToDiskStorageUsage() throws Exception {
    OverflowToDiskByteStorage storage =
        (OverflowToDiskByteStorage)
            ((ChunkBasedByteStorage)
                    buildZipAndGetStorage(
                        new ChunkBasedByteStorageFactory(
                            new OverflowToDiskByteStorageFactory(
                                1000L,
                                TemporaryDirectoryFactory.fixed(temporaryFolder.newFolder())),
                            (long) VERY_COMPRESSIBLE_DATA.length / 4)))
                .getDelegate();

    long remaining = storage.getBytesUsed();
    long max = storage.getMaxBytesUsed();

    long maxMemory = storage.getMaxMemoryBytesUsed();
    long maxDisk = storage.getMaxDiskBytesUsed();

    assertThat(remaining).isEqualTo(0);

    // Why this value?
    // Contribution                  | Step
    // max           | maxMemory     | maxDisk       |
    // +VCD          | +VCD/4        | +VCD          | 1. VCD added (source data in memory). As
    //               |               |               | the next 1/4 is added (chunk limit), the
    //               |               |               | previous one is flushed to disk because each
    //               |               |               | block exceeds the overflow threshold.
    // +VCD deflated |               |               | 2. VCD compressed (deflated data in memory).
    //               |               |               | Does not increase maxMemory because VCD
    //               |               |               | compressed < VCD
    // +VCD          | +VCD deflated | +VCD deflated | 3. VCD added without compression (source data
    //               |               | +VCD          | in memory). Because VCD deflated is already
    //               |               |               | in memory, when the first chunk (VCD/4) is
    //               |               |               | added, the total memory increases.
    //               |               |               | because the initial parts of VCD were already
    //               |               |               | removed due to overflow.
    // +NCD          |               | +NCD          | 4. NCD added (source data is all flushed to
    //               |               | -NCD%(VCD/4)  | disk except for the last remaining chunk,
    //               |               |               | which is larger than VCD deflated.
    // +NCD deflated | -VCD deflated | +NCD%(VCD/4)  | 5. NCD compressed added. maxMemory is
    //               | +NCD%(VCD/4)  | +NCD deflated | increased because NCD%(VCD/4) > VCD deflated
    //               |               |               | and that amount of memory is added to the
    //               |               |               | first chunk allocated from NCD deflated.
    //               |               |               | Since the last chunk of NCD deflated is
    //               |               |               | greater than the overflow threshold, all data
    //               |               |               | is written to disk.
    //               |               |               | 6. NCD compressed is discarded because it is
    //               |               |               | larger than NCD and NCD is stored without
    //               |               |               | compression.
    //               |               |               | 7. NCD added without compression (source data
    //               |               |               | in memory), but it is smaller than NCD
    //               |               |               | compressed so it doesn't increase maximum
    //               |               |               | size.

    assertThat(max)
        .isEqualTo(
            2 * VERY_COMPRESSIBLE_DATA.length
                + VERY_COMPRESSIBLE_DATA_DEFLATED.length
                + NOT_COMPRESSIBLE_DATA.length
                + NOT_COMPRESSIBLE_DATA_DEFLATED.length);
    assertThat(maxMemory)
        .isEqualTo(
            VERY_COMPRESSIBLE_DATA.length / 4
                + (NOT_COMPRESSIBLE_DATA.length % (VERY_COMPRESSIBLE_DATA.length / 4)));
    assertThat(maxDisk)
        .isEqualTo(
            2 * VERY_COMPRESSIBLE_DATA.length
                + VERY_COMPRESSIBLE_DATA_DEFLATED.length
                + NOT_COMPRESSIBLE_DATA.length
                + NOT_COMPRESSIBLE_DATA_DEFLATED.length);
  }
}
