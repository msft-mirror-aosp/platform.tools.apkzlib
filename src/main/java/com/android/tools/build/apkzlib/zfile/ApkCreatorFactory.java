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

package com.android.tools.build.apkzlib.zfile;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.tools.build.apkzlib.sign.SigningOptions;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import java.io.File;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Factory that creates instances of {@link ApkCreator}. */
public interface ApkCreatorFactory {

  /**
   * Creates an {@link ApkCreator} with a given output location, and signing information.
   *
   * @param creationData the information to create the APK
   */
  ApkCreator make(CreationData creationData);

  /**
   * Data structure with the required information to initiate the creation of an APK. See {@link
   * ApkCreatorFactory#make(CreationData)}.
   */
  class CreationData {

    /**
     * The path where the APK should be located. May already exist or not (if it does, then the APK
     * may be updated instead of created).
     */
    private final File apkPath;

    /** Data used to sign the APK */
    private final Optional<SigningOptions> signingOptions;

    /** Built-by information for the APK, if any. */
    @Nullable private final String builtBy;

    /** Created-by information for the APK, if any. */
    @Nullable private final String createdBy;

    /** How should native libraries be packaged? */
    private final NativeLibrariesPackagingMode nativeLibrariesPackagingMode;

    /** Predicate identifying paths that should not be compressed. */
    private final Predicate<String> noCompressPredicate;

    /**
     * @param apkPath the path where the APK should be located. May already exist or not (if it
     *     does, then the APK may be updated instead of created)
     * @param builtBy built-by information for the APK, if any; if {@code null} then the default
     *     should be used
     * @param createdBy created-by information for the APK, if any; if {@code null} then the default
     *     should be used
     * @param nativeLibrariesPackagingMode packaging mode for native libraries
     * @param noCompressPredicate predicate to decide which file paths should be uncompressed;
     *     returns {@code true} for files that should not be compressed
     */
    public CreationData(
        File apkPath,
        @Nonnull Optional<SigningOptions> signingOptions,
        @Nullable String builtBy,
        @Nullable String createdBy,
        NativeLibrariesPackagingMode nativeLibrariesPackagingMode,
        Predicate<String> noCompressPredicate) {
      this.apkPath = apkPath;
      this.signingOptions = signingOptions;
      this.builtBy = builtBy;
      this.createdBy = createdBy;
      this.nativeLibrariesPackagingMode = checkNotNull(nativeLibrariesPackagingMode);
      this.noCompressPredicate = checkNotNull(noCompressPredicate);
    }

    /**
     * Obtains the path where the APK should be located. If the path already exists, then the APK
     * may be updated instead of re-created.
     *
     * @return the path that may already exist or not
     */
    public File getApkPath() {
      return apkPath;
    }

    /**
     * Obtains the data used to sign the APK.
     *
     * @return the SigningOptions
     */
    @Nonnull
    public Optional<SigningOptions> getSigningOptions() {
      return signingOptions;
    }

    /**
     * Obtains the "built-by" text for the APK.
     *
     * @return the text or {@code null} if the default should be used
     */
    @Nullable
    public String getBuiltBy() {
      return builtBy;
    }

    /**
     * Obtains the "created-by" text for the APK.
     *
     * @return the text or {@code null} if the default should be used
     */
    @Nullable
    public String getCreatedBy() {
      return createdBy;
    }

    /** Returns the packaging policy that the {@link ApkCreator} should use for native libraries. */
    public NativeLibrariesPackagingMode getNativeLibrariesPackagingMode() {
      return nativeLibrariesPackagingMode;
    }

    /** Returns the predicate to decide which file paths should be uncompressed. */
    public Predicate<String> getNoCompressPredicate() {
      return noCompressPredicate;
    }
  }
}
