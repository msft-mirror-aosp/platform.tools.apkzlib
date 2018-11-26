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

package com.android.tools.build.apkzlib.zfile;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.build.apkzlib.sign.SignatureTestUtils;
import com.android.tools.build.apkzlib.sign.SigningOptions;
import com.android.tools.build.apkzlib.utils.ApkZLibPair;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileOptions;
import com.android.tools.build.apkzlib.zip.compress.RandomDataInputStream;
import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ZFilesTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testSigningVeryBigZipEntries() throws Exception {
    final long FILE_SIZE = 2_200_000_000L;
    File zpath = new File(temporaryFolder.getRoot(), "a.zip");
    ApkZLibPair<PrivateKey, X509Certificate> signingData =
        SignatureTestUtils.generateSignaturePos18();
    Optional<SigningOptions> signingOptions =
        Optional.of(
            SigningOptions.create(
                signingData.v1,
                signingData.v2,
                /* v1= */ true,
                /* v2= */ true,
                /* minSdk= */ 18));

    try (ZFile zf =
        ZFiles.apk(
            zpath,
            new ZFileOptions(),
            signingOptions,
            /* builtBy= */ null,
            /* createdBy= */ null)) {
      zf.add("foo", new RandomDataInputStream(FILE_SIZE));
    }

    try (ZFile zf = ZFile.openReadOnly(zpath)) {
      StoredEntry e = zf.get("foo");
      assertThat(e.getCentralDirectoryHeader().getUncompressedSize()).isEqualTo(FILE_SIZE);
    }
  }
}
