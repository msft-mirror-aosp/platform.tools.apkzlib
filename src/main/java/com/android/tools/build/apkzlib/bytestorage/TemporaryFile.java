package com.android.tools.build.apkzlib.bytestorage;

import com.google.common.base.Preconditions;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * A temporary file or directory. Wraps a file or directory and deletes it (recursively, if it is a
 * directory) when closed.
 */
public class TemporaryFile implements Closeable {

  /** Has the file or directory represented by {@link #file} been deleted? */
  private boolean deleted;

  /**
   * The file or directory that will be deleted on close. May no longer exist if {@link #deleted} is
   * {@code true}.
   */
  private final File file;

  /**
   * Creates a new wrapper around the given file. The file or directory {@code file} will be deleted
   * (recursively, if it is a directory) on close.
   */
  public TemporaryFile(File file) {
    deleted = false;
    this.file = file;
  }

  /** Obtains the file or directory this temporary file refers to. */
  public File getFile() {
    Preconditions.checkState(!deleted, "File already deleted");
    return file;
  }

  @Override
  public void close() throws IOException {
    if (deleted) {
      return;
    }

    deleted = true;

    deleteFile(file);
  }

  /** Deletes a file or directory if it exists. */
  private void deleteFile(File file) throws IOException {
    if (file.exists()) {
      // Technically there may be a race condition because deleteRecursively doesn't allow
      // deleting non-existent paths and we could delete file between the exists() call and
      // this one.
      MoreFiles.deleteRecursively(file.toPath(), RecursiveDeleteOption.ALLOW_INSECURE);
    }
  }
}
