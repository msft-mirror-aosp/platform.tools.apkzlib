package com.android.tools.build.apkzlib.bytestorage;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TemporaryDirectoryTest {

  @Test
  public void newFileReturnsDifferentFiles() throws Exception {
    try (TemporaryDirectory temporaryDirectory = TemporaryDirectory.newSystemTemporaryDirectory()) {
      File f0 = temporaryDirectory.newFile();
      File f1 = temporaryDirectory.newFile();

      assertThat(f0.getName()).isNotEqualTo(f1.getName());
    }
  }

  @Test
  public void newFileReturnsExistentingFiles() throws Exception {
    try (TemporaryDirectory temporaryDirectory = TemporaryDirectory.newSystemTemporaryDirectory()) {
      File file = temporaryDirectory.newFile();
      assertThat(file.exists()).isTrue();
    }
  }

  @Test
  public void allFilesAndDirectoriesDeletedOnClose() throws Exception {
    File file;
    File dir;
    File subFile;

    try (TemporaryDirectory temporaryDirectory = TemporaryDirectory.newSystemTemporaryDirectory()) {
      file = temporaryDirectory.newFile();
      try (FileOutputStream contents = new FileOutputStream(file)) {
        contents.write(new byte[10]);
      }

      dir = temporaryDirectory.newFile();
      assertThat(dir.delete()).isTrue();
      assertThat(dir.mkdir()).isTrue();

      subFile = new File(dir, "def");
      try (FileOutputStream contents = new FileOutputStream(subFile)) {
        contents.write(new byte[10]);
      }
    }

    assertThat(file.exists()).isFalse();
    assertThat(dir.exists()).isFalse();
    assertThat(subFile.exists()).isFalse();
  }
}
