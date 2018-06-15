package com.android.tools.build.apkzlib.zip;

import com.android.tools.build.apkzlib.bytestorage.ByteStorageFactory;
import com.android.tools.build.apkzlib.bytestorage.InMemoryByteStorageFactory;
import com.android.tools.build.apkzlib.bytestorage.OverflowToDiskByteStorageFactory;
import com.android.tools.build.apkzlib.bytestorage.TemporaryDirectoryFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Random;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PerformanceTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private void buildTestZip(
      ByteStorageFactory factory,
      SmallMediumLargeCount compressibleFiles,
      SmallMediumLargeCount nonCompressibleFiles)
      throws Exception {
    File zip = new File(temporaryFolder.newFolder(), "a.zip");
    ZFileOptions options = new ZFileOptions();
    options.setStorageFactory(factory);

    byte[] wiki = ZipTestUtils.rsrcBytes("text-files/wikipedia.html");
    int wikiAppends = 20 * 1024 * 1024 / wiki.length;
    byte[] wikiCopies = new byte[wikiAppends * wiki.length];
    for (int i = 0; i < wikiAppends; i++) {
      System.arraycopy(wiki, 0, wikiCopies, i * wiki.length, wiki.length);
    }

    byte[] smallNonCompressible = new byte[50];
    byte[] mediumNonCompressible = new byte[3000];
    byte[] largeNonCompressible = new byte[40000];

    Random random = new Random();
    random.nextBytes(smallNonCompressible);
    random.nextBytes(mediumNonCompressible);
    random.nextBytes(largeNonCompressible);

    try (ZFile zf = new ZFile(zip, options)) {
      for (int i = 0; i < compressibleFiles.smallCount; i++) {
        zf.add("scf-" + i, new ByteArrayInputStream(new byte[100]));
      }

      for (int i = 0; i < compressibleFiles.mediumCount; i++) {
        zf.add("mcf-" + i, new ByteArrayInputStream(wiki));
      }

      for (int i = 0; i < compressibleFiles.largeCount; i++) {
        zf.add("lcf-" + i, new ByteArrayInputStream(wikiCopies));
      }

      for (int i = 0; i < nonCompressibleFiles.smallCount; i++) {
        zf.add("snf-" + i, new ByteArrayInputStream(smallNonCompressible));
      }

      for (int i = 0; i < nonCompressibleFiles.mediumCount; i++) {
        zf.add("mnf-" + i, new ByteArrayInputStream(mediumNonCompressible));
      }

      for (int i = 0; i < nonCompressibleFiles.largeCount; i++) {
        zf.add("lnf-" + i, new ByteArrayInputStream(largeNonCompressible));
      }
    }
  }

  private long benchmark(
      ByteStorageFactory factory,
      SmallMediumLargeCount compressible,
      SmallMediumLargeCount nonCompressible)
      throws Exception {
    long nanos = System.nanoTime();
    buildTestZip(factory, compressible, nonCompressible);
    return (System.nanoTime() - nanos) / 1_000_000;
  }

  private void compare(SmallMediumLargeCount compressible, SmallMediumLargeCount nonCompressible)
      throws Exception {
    long memory = benchmark(new InMemoryByteStorageFactory(), compressible, nonCompressible);
    long disk10B =
        benchmark(
            new OverflowToDiskByteStorageFactory(
                10L, TemporaryDirectoryFactory.fixed(temporaryFolder.newFolder())),
            compressible,
            nonCompressible);
    long disk10KB =
        benchmark(
            new OverflowToDiskByteStorageFactory(
                10L * 1024, TemporaryDirectoryFactory.fixed(temporaryFolder.newFolder())),
            compressible,
            nonCompressible);
    long disk10MB =
        benchmark(
            new OverflowToDiskByteStorageFactory(
                10L * 1024 * 1024, TemporaryDirectoryFactory.fixed(temporaryFolder.newFolder())),
            compressible,
            nonCompressible);
    long disk100MB =
        benchmark(
            new OverflowToDiskByteStorageFactory(
                100L * 1024 * 1024, TemporaryDirectoryFactory.fixed(temporaryFolder.newFolder())),
            compressible,
            nonCompressible);

    System.out.println(
        compressible.smallCount
            + "/"
            + compressible.mediumCount
            + "/"
            + compressible.largeCount
            + "-"
            + nonCompressible.smallCount
            + "/"
            + nonCompressible.mediumCount
            + "/"
            + nonCompressible.largeCount
            + " "
            + memory
            + " "
            + disk10B
            + " "
            + disk10KB
            + " "
            + disk10MB
            + " "
            + disk100MB);
  }

  @Ignore("This test is only useful when run manually to check the results.")
  @Test
  public void comparisonTest() throws Exception {
    SmallMediumLargeCount smallCompressible = new SmallMediumLargeCount(10, 2, 0);
    SmallMediumLargeCount smallNonCompressible = new SmallMediumLargeCount(10, 2, 0);
    SmallMediumLargeCount mediumCompressible = new SmallMediumLargeCount(1000, 20, 3);
    SmallMediumLargeCount mediumNonCompressible = new SmallMediumLargeCount(1000, 20, 3);
    SmallMediumLargeCount largeCompressible = new SmallMediumLargeCount(1000, 60, 10);
    SmallMediumLargeCount largeNonCompressible = new SmallMediumLargeCount(1000, 60, 10);

    compare(smallCompressible, smallNonCompressible);
    compare(mediumCompressible, mediumNonCompressible);
    compare(largeCompressible, largeNonCompressible);
  }

  private static class SmallMediumLargeCount {
    final int smallCount;
    final int mediumCount;
    final int largeCount;

    SmallMediumLargeCount(int smallCount, int mediumCount, int largeCount) {
      this.smallCount = smallCount;
      this.mediumCount = mediumCount;
      this.largeCount = largeCount;
    }
  }
}
