package com.android.tools.build.apkzlib.bytestorage;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.io.FileOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TemporaryFileTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void preservesProvidedFile() throws Exception {
    File file = temporaryFolder.newFile();

    try (TemporaryFile temporaryFile = new TemporaryFile(file)) {
      assertThat(temporaryFile.getFile()).isSameInstanceAs(file);
    }
  }

  @Test
  public void deletesFileOnClose() throws Exception {
    File file = temporaryFolder.newFile();

    try (TemporaryFile temporaryFile = new TemporaryFile(file);
        FileOutputStream contents = new FileOutputStream(temporaryFile.getFile())) {
      contents.write(new byte[5]);
    }

    assertThat(file.exists()).isFalse();
  }

  @Test
  public void deletesEmptyDirectoryOnClose() throws Exception {
    File file = temporaryFolder.newFile();

    try (TemporaryFile temporaryFile = new TemporaryFile(file)) {
      assertThat(temporaryFile.getFile().delete()).isTrue();
      assertThat(temporaryFile.getFile().mkdir()).isTrue();
    }

    assertThat(file.exists()).isFalse();
  }

  @Test
  public void deletesNonEmptyDirectoryOnClose() throws Exception {
    File file = temporaryFolder.newFile();

    try (TemporaryFile temporaryFile = new TemporaryFile(file)) {
      assertThat(temporaryFile.getFile().delete()).isTrue();
      assertThat(temporaryFile.getFile().mkdir()).isTrue();

      File file1 = new File(temporaryFile.getFile(), "foo");
      try (FileOutputStream file1Contents = new FileOutputStream(file1)) {
        file1Contents.write(new byte[5]);
      }

      File dir1 = new File(temporaryFile.getFile(), "bar");
      assertThat(dir1.mkdir()).isTrue();

      File file2 = new File(dir1, "bazz");
      try (FileOutputStream file2Contents = new FileOutputStream(file2)) {
        file2Contents.write(new byte[5]);
      }
    }

    assertThat(file.exists()).isFalse();
  }

  @Test
  public void okIfFileDoesNotExistOnClose() throws Exception {
    File file = temporaryFolder.newFile();

    try (TemporaryFile temporaryFile = new TemporaryFile(file)) {
      assertThat(file.delete()).isTrue();
    }
  }

  @Test
  public void fileNotDeletedIfNotClosed() throws Exception {
    File file = temporaryFolder.newFile();

    try (TemporaryFile temporaryFile = new TemporaryFile(file)) {
      assertThat(file.isFile()).isTrue();
    }
  }
}
