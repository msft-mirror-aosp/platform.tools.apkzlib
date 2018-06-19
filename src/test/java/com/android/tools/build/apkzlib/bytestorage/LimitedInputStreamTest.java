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

package com.android.tools.build.apkzlib.bytestorage;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LimitedInputStreamTest {

  private static final byte[] DATA = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

  private final InputStream input = new ByteArrayInputStream(DATA);
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();

  @Test
  public void readEmpty() throws Exception {
    ByteStreams.copy(new LimitedInputStream(input, 0), output);
    assertThat(output.toByteArray()).isEqualTo(new byte[0]);
  }

  @Test
  public void readPartial() throws Exception {
    ByteStreams.copy(new LimitedInputStream(input, 3), output);
    assertThat(output.toByteArray()).isEqualTo(new byte[] {0, 1, 2});
  }

  @Test
  public void readExact() throws Exception {
    ByteStreams.copy(new LimitedInputStream(input, DATA.length), output);
    assertThat(output.toByteArray()).isEqualTo(DATA);
  }

  @Test
  public void readBeyondLimit() throws Exception {
    ByteStreams.copy(new LimitedInputStream(input, DATA.length + 10), output);
    assertThat(output.toByteArray()).isEqualTo(DATA);
  }

  @Test
  public void readSingleEmpty() throws Exception {
    InputStream inputStream = new LimitedInputStream(input, 0);

    assertThat(inputStream.read()).isEqualTo(-1);
    assertThat(inputStream.read()).isEqualTo(-1);
  }

  @Test
  public void readSinglePartial() throws Exception {
    InputStream inputStream = new LimitedInputStream(input, 2);

    assertThat(inputStream.read()).isEqualTo(DATA[0]);
    assertThat(inputStream.read()).isEqualTo(DATA[1]);
    assertThat(inputStream.read()).isEqualTo(-1);
    assertThat(inputStream.read()).isEqualTo(-1);
  }

  @Test
  public void readSingleExact() throws Exception {
    InputStream inputStream =
        new LimitedInputStream(new ByteArrayInputStream(new byte[] {0, 1}), 2);

    assertThat(inputStream.read()).isEqualTo(DATA[0]);
    assertThat(inputStream.read()).isEqualTo(DATA[1]);
    assertThat(inputStream.read()).isEqualTo(-1);
    assertThat(inputStream.read()).isEqualTo(-1);
  }

  @Test
  public void readSingleBeyondLimit() throws Exception {
    InputStream inputStream =
        new LimitedInputStream(new ByteArrayInputStream(new byte[] {0, 1}), 4);

    assertThat(inputStream.read()).isEqualTo(DATA[0]);
    assertThat(inputStream.read()).isEqualTo(DATA[1]);
    assertThat(inputStream.read()).isEqualTo(-1);
    assertThat(inputStream.read()).isEqualTo(-1);
  }

  @Test
  public void closeDoesNotCloseUnderlying() throws Exception {
    AtomicBoolean closed = new AtomicBoolean(false);

    InputStream underlying =
        new ByteArrayInputStream(new byte[0]) {
          @Override
          public void close() {
            closed.set(true);
          }
        };

    new LimitedInputStream(underlying, 2).close();

    assertThat(closed.get()).isFalse();
  }

  @Test
  public void inputClosedReportsFalseBeforeLimitEnd() throws Exception {
    LimitedInputStream inputStream = new LimitedInputStream(input, 4);

    assertThat(inputStream.isInputFinished()).isFalse();
  }

  @Test
  public void inputClosedReportsFalseAfterLimitEndIfInputDoesNotReachEnd() throws Exception {
    LimitedInputStream inputStream = new LimitedInputStream(input, 4);
    ByteStreams.copy(inputStream, output);

    assertThat(inputStream.isInputFinished()).isFalse();
  }

  @Test
  public void inputClosedReportsFalseIfLimitIsReachedExactlyAtEof() throws Exception {
    LimitedInputStream inputStream = new LimitedInputStream(input, DATA.length);
    ByteStreams.copy(inputStream, output);

    assertThat(inputStream.isInputFinished()).isFalse();
  }

  @Test
  public void inputClosedReportsTrueIfLimitIsReachedAfterEof() throws Exception {
    LimitedInputStream inputStream = new LimitedInputStream(input, DATA.length + 1);
    ByteStreams.copy(inputStream, output);

    assertThat(inputStream.isInputFinished()).isTrue();
  }
}
