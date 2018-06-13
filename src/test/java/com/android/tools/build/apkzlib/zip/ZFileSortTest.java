/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.build.apkzlib.zip;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ZFileSortTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private File file;
  private ZFile zFile;
  private StoredEntry maryEntry;
  private long maryOffset;
  private StoredEntry andrewEntry;
  private long andrewOffset;
  private StoredEntry bethEntry;
  private long bethOffset;
  private StoredEntry peterEntry;
  private long peterOffset;

  @Before
  public final void before() throws Exception {
    file = new File(temporaryFolder.getRoot(), "a.zip");
    setupZFile(null);
  }

  @After
  public final void after() throws Exception {
    zFile.close();
  }

  /**
   * Recreates the zip file, if one already exist.
   *
   * @param options the options for the file, may be {@code null} in which case the default options
   *     will be used
   * @throws Exception failed to re-create the file
   */
  private void setupZFile(@Nullable ZFileOptions options) throws Exception {
    if (zFile != null) {
      zFile.close();
    }

    if (file.exists()) {
      assertTrue(file.delete());
    }

    if (options == null) {
      options = new ZFileOptions();
    }

    zFile = new ZFile(file, options);

    zFile.add("Mary.xml", new ByteArrayInputStream(new byte[] {1, 2, 3}));
    zFile.add("Andrew.txt", new ByteArrayInputStream(new byte[] {4, 5}));
    zFile.add("Beth.png", new ByteArrayInputStream(new byte[] {6, 7, 8, 9}));
    zFile.add("Peter.html", new ByteArrayInputStream(new byte[] {10}));
    zFile.finishAllBackgroundTasks();
  }

  private void readEntries() throws Exception {
    maryEntry = zFile.get("Mary.xml");
    assertNotNull(maryEntry);
    maryOffset = maryEntry.getCentralDirectoryHeader().getOffset();
    assertArrayEquals(new byte[] {1, 2, 3}, maryEntry.read());

    andrewEntry = zFile.get("Andrew.txt");
    assertNotNull(andrewEntry);
    andrewOffset = andrewEntry.getCentralDirectoryHeader().getOffset();
    assertArrayEquals(new byte[] {4, 5}, andrewEntry.read());

    bethEntry = zFile.get("Beth.png");
    assertNotNull(bethEntry);
    bethOffset = bethEntry.getCentralDirectoryHeader().getOffset();
    assertArrayEquals(new byte[] {6, 7, 8, 9}, bethEntry.read());

    peterEntry = zFile.get("Peter.html");
    assertNotNull(peterEntry);
    peterOffset = peterEntry.getCentralDirectoryHeader().getOffset();
    assertArrayEquals(new byte[] {10}, peterEntry.read());
  }

  @Test
  public void noSort() throws Exception {
    readEntries();

    assertEquals(-1, maryOffset);
    assertEquals(-1, andrewOffset);
    assertEquals(-1, bethOffset);
    assertEquals(-1, peterOffset);

    zFile.update();

    readEntries();

    assertTrue(maryOffset >= 0);
    assertTrue(maryOffset < andrewOffset);
    assertTrue(andrewOffset < bethOffset);
    assertTrue(bethOffset < peterOffset);
  }

  @Test
  public void sortFilesBeforeUpdate() throws Exception {
    readEntries();
    zFile.sortZipContents();

    zFile.update();

    readEntries();

    assertTrue(andrewOffset >= 0);
    assertTrue(bethOffset > andrewOffset);
    assertTrue(maryOffset > bethOffset);
    assertTrue(peterOffset > maryOffset);
  }

  @Test
  public void autoSort() throws Exception {
    ZFileOptions options = new ZFileOptions();
    options.setAutoSortFiles(true);
    setupZFile(options);

    readEntries();

    zFile.update();

    readEntries();

    assertTrue(andrewOffset >= 0);
    assertTrue(bethOffset > andrewOffset);
    assertTrue(maryOffset > bethOffset);
    assertTrue(peterOffset > maryOffset);
  }

  @Test
  public void sortFilesAfterUpdate() throws Exception {
    readEntries();

    zFile.update();

    zFile.sortZipContents();

    readEntries();

    assertEquals(-1, maryOffset);
    assertEquals(-1, andrewOffset);
    assertEquals(-1, bethOffset);
    assertEquals(-1, peterOffset);

    zFile.update();

    readEntries();

    assertTrue(andrewOffset >= 0);
    assertTrue(bethOffset > andrewOffset);
    assertTrue(maryOffset > bethOffset);
    assertTrue(peterOffset > maryOffset);
  }

  @Test
  public void sortFilesWithAlignment() throws Exception {
    zFile.close();

    ZFileOptions options = new ZFileOptions();
    options.setAlignmentRule(AlignmentRules.constantForSuffix(".xml", 1024));
    zFile = new ZFile(file, options);

    zFile.sortZipContents();
    zFile.update();

    readEntries();
    assertTrue(andrewOffset >= 0);
    assertTrue(bethOffset > andrewOffset);
    assertTrue(peterOffset > bethOffset);
    assertTrue(maryOffset > peterOffset);
  }

  @Test
  public void sortFilesOnClosedFile() throws Exception {
    zFile.close();
    zFile = new ZFile(file);
    zFile.sortZipContents();
    zFile.update();

    readEntries();

    assertTrue(andrewOffset >= 0);
    assertTrue(bethOffset > andrewOffset);
    assertTrue(maryOffset > bethOffset);
    assertTrue(peterOffset > maryOffset);
  }
}
