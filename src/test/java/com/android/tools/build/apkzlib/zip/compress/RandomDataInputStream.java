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

package com.android.tools.build.apkzlib.zip.compress;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * A helper class simulating a stream of random data
 * Can be used to simulate large incompressible data stream (file) in tests
 */
class RandomDataInputStream extends InputStream {
    private static final int BUFF_SIZE = 8096;
    private final Random rand;
    private final long size;
    private final byte[] buff;

    private long bytesRead;

    public RandomDataInputStream(long size) {
        this.rand = new Random();
        this.size = size;
        this.bytesRead = 0;
        this.buff = new byte[BUFF_SIZE];
    }

    @Override
    public int read() throws IOException {
        if (bytesRead == size) {
            return -1;
        }

        int i = (int)(bytesRead % BUFF_SIZE);
        if (i == 0) {
            rand.nextBytes(buff);
        }
        ++bytesRead;

        return buff[i] + 128;
    }
}
