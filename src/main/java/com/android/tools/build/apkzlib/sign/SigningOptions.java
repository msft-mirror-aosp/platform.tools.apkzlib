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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.annotation.Nullable;

/**
 * A class that contains data to initialize SigningExtension
 */
public class SigningOptions {
    /** Key used to sign the APK. May be {@code null}. */
    private final PrivateKey key;

    /**
     * Certificates used to sign the APK. Is {@code isEmpty()} if and only if {@link #key} is {@code
     * null}.
     */
    private final ImmutableList<X509Certificate> certs;

    /** Whether signing the APK with JAR Signing Scheme (aka v1 signing) is enabled. */
    private final boolean v1SigningEnabled;

    /** Whether signing the APK with APK Signature Scheme v2 (aka v2 signing) is enabled. */
    private final boolean v2SigningEnabled;

    /** Minimum SDk version that will run the APK. */
    private final int minSdkVersion;

    public SigningOptions(
            @Nullable PrivateKey key,
            @Nullable X509Certificate cert,
            boolean v1,
            boolean v2,
            int minSdk) {
        this(key, cert == null ? ImmutableList.of() : ImmutableList.of(cert), v1, v2, minSdk);
    }

    /**
     * @param key the {@link PrivateKey} used to sign the archive, or {@code null}.
     * @param certs list of the {@link X509Certificate}s to embed in the signed APKs. The first
     *     element of the list must be the certificate associated with the private key.
     * @param v1SigningEnabled whether signing with JAR Signature Scheme (aka v1 signing) is enabled.
     * @param v2SigningEnabled whether signing with APK Signature Scheme v2 (aka v2 signing) is
     *     enabled.
     * @param minSdkVersion minimum SDK version supported
     */
    public SigningOptions(
            @Nullable PrivateKey key,
            ImmutableList<X509Certificate> certs,
            boolean v1SigningEnabled,
            boolean v2SigningEnabled,
            int minSdkVersion) {
        Preconditions.checkArgument(
                (key == null) == certs.isEmpty(),
                "Certificates list should be empty if and only if the private key is null");
        Preconditions.checkArgument(minSdkVersion >= 0, "minSdkVersion < 0");
        this.key = key;
        this.certs = certs;
        this.v1SigningEnabled = v1SigningEnabled;
        this.v2SigningEnabled = v2SigningEnabled;
        this.minSdkVersion = minSdkVersion;
    }

    @Nullable
    public PrivateKey getKey() {
        return key;
    }

    public ImmutableList<X509Certificate> getCertificates() {
        return certs;
    }

    public boolean isV1SigningEnabled() {
        return v1SigningEnabled;
    }

    public boolean isV2SigningEnabled() {
        return v2SigningEnabled;
    }

    public int getMinSdkVersion() {
        return minSdkVersion;
    }
}
