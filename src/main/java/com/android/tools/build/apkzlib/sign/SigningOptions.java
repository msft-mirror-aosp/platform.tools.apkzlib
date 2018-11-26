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

package com.android.tools.build.apkzlib.sign;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.annotation.Nonnull;

/**
 * A class that contains data to initialize SigningExtension
 */
@AutoValue
public abstract class SigningOptions {

    /**
     * Static method to create {@link SigningOptions} object
     *
     * @param key the {@link PrivateKey} used to sign the archive, or {@code null}.
     * @param cert the {@link X509Certificate} associated with the private key, or {@code null}.
     * @param v1 whether signing with JAR Signature Scheme (aka v1 signing) is enabled.
     * @param v2 whether signing with APK Signature Scheme v2 (aka v2 signing) is
     *     enabled.
     * @param minSdk minimum SDK version supported
     *
     * Returns a new instance of {@link SigningOptions}
     */
    public static SigningOptions create(
            @Nonnull PrivateKey key,
            @Nonnull X509Certificate cert,
            boolean v1,
            boolean v2,
            int minSdk) {
        return create(
                key, ImmutableList.of(cert), v1, v2, minSdk);
    }

    /**
     * Static method to create {@link SigningOptions} object
     *
     * @param key the {@link PrivateKey} used to sign the archive.
     * @param certs list of the {@link X509Certificate}s to embed in the signed APKs. The first
     *     element of the list must be the certificate associated with the private key.
     * @param v1 whether signing with JAR Signature Scheme (aka v1 signing) is enabled.
     * @param v2 whether signing with APK Signature Scheme v2 (aka v2 signing) is
     *     enabled.
     * @param minSdk minimum SDK version supported
     *
     * Returns a new instance of {@link SigningOptions}
     */
    public static SigningOptions create(
            @Nonnull PrivateKey key,
            @Nonnull ImmutableList<X509Certificate> certs,
            boolean v1,
            boolean v2,
            int minSdk) {
        Preconditions.checkArgument(minSdk >= 0, "minSdkVersion < 0");
        Preconditions.checkArgument(
                !certs.isEmpty(), "There should be at least one certificate in SigningOptions");
        return new AutoValue_SigningOptions(key, certs, v1, v2, minSdk);
    }

    public abstract PrivateKey getKey();

    public abstract ImmutableList<X509Certificate> getCertificates();

    public abstract boolean isV1SigningEnabled();

    public abstract boolean isV2SigningEnabled();

    public abstract int getMinSdkVersion();
}
