package com.android.tools.build.apkzlib.bytestorage;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.build.apkzlib.zip.utils.CloseableByteSource;
import com.google.common.io.ByteSource;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TemporaryDirectoryStorageTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void createsFiles() throws Exception {
    try (TemporaryDirectory tempDir = TemporaryDirectory.newSystemTemporaryDirectory()) {
      File tempDirFile = tempDir.newFile().getParentFile();

      TemporaryDirectoryStorage storage =
          new TemporaryDirectoryStorage(TemporaryDirectoryFactory.fixed(tempDirFile));
      try (CloseableByteSource source = storage.fromSource(ByteSource.wrap(new byte[10]))) {
        assertThat(tempDirFile.isDirectory()).isTrue();
        assertThat(tempDirFile.listFiles()).isNotEmpty();
      }
    }
  }

  @Test
  public void deletesFilesOnClose() throws Exception {
    try (TemporaryDirectory tempDir = TemporaryDirectory.newSystemTemporaryDirectory()) {
      File tempDirFile = tempDir.getDirectory();

      TemporaryDirectoryStorage storage =
          new TemporaryDirectoryStorage(TemporaryDirectoryFactory.fixed(tempDirFile));
      try (CloseableByteSource source = storage.fromSource(ByteSource.wrap(new byte[10]))) {
        assertThat(tempDirFile.listFiles()).isNotEmpty();
      }

      assertThat(tempDirFile.isDirectory()).isTrue();
      assertThat(tempDirFile.listFiles()).isEmpty();
    }
  }

  @Test
  public void closingTemporaryDirectoryStorageDeletesDirectory() throws Exception {
    File tempDirFile;
    try (TemporaryDirectory tempDir = TemporaryDirectory.newSystemTemporaryDirectory()) {
      tempDirFile = tempDir.getDirectory();
    }

    assertThat(tempDirFile.exists()).isFalse();
  }
}
