package com.android.tools.build.apkzlib.bytestorage;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.io.ByteSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SwitchableDelegateInputStreamTest {

  @Test
  public void readFromSingleStream() throws Exception {
    ByteSource bytes = ByteSource.wrap(new byte[] {1, 2, 3, 4});

    try (SwitchableDelegateInputStream switchable =
        new SwitchableDelegateInputStream(bytes.openStream())) {
      assertThat(switchable.read()).isEqualTo(1);

      byte[] more = new byte[2];
      int r = switchable.read(more);
      assertThat(r).isEqualTo(2);
      assertThat(Arrays.equals(more, new byte[] {2, 3})).isTrue();
      r = switchable.read(more, 0, 2);
      assertThat(r).isEqualTo(1);
      assertThat(Arrays.equals(more, new byte[] {4, 3})).isTrue();

      r = switchable.read(more);
      assertThat(r).isEqualTo(-1);
    }
  }

  @Test
  public void closingClosesCurrentStream() throws Exception {
    AtomicBoolean closed = new AtomicBoolean(false);

    InputStream stream =
        new ByteArrayInputStream(new byte[2]) {
          @Override
          public void close() throws IOException {
            super.close();
            closed.set(true);
          }
        };

    try (SwitchableDelegateInputStream switchable = new SwitchableDelegateInputStream(stream)) {
      // Nothing to do.
    }

    assertThat(closed.get()).isTrue();
  }

  @Test
  public void switchingMidRead() throws Exception {
    ByteSource bytes1 = ByteSource.wrap(new byte[] {1, 2, 3, 4});
    ByteSource bytes2 = ByteSource.wrap(new byte[] {5, 6, 7, 8});

    try (SwitchableDelegateInputStream switchable =
        new SwitchableDelegateInputStream(bytes1.openStream())) {
      assertThat(switchable.read()).isEqualTo(1);
      assertThat(switchable.read()).isEqualTo(2);

      switchable.switchStream(bytes2.openStream());
      assertThat(switchable.read()).isEqualTo(7);
      assertThat(switchable.read()).isEqualTo(8);
    }
  }

  @Test
  public void switchingClosesOldStream() throws Exception {
    AtomicBoolean closed = new AtomicBoolean(false);
    ByteArrayInputStream oldStream =
        new ByteArrayInputStream(new byte[10]) {
          @Override
          public void close() throws IOException {
            closed.set(true);
            super.close();
          }
        };

    try (SwitchableDelegateInputStream switchable = new SwitchableDelegateInputStream(oldStream)) {
      switchable.switchStream(new ByteArrayInputStream(new byte[10]));
      assertThat(closed.get()).isTrue();
    }
  }

  @Test
  public void markNotSupported() throws Exception {
    ByteArrayInputStream bytes = new ByteArrayInputStream(new byte[10]);
    assertThat(bytes.markSupported()).isTrue();

    try (SwitchableDelegateInputStream switchable = new SwitchableDelegateInputStream(bytes)) {
      assertThat(switchable.markSupported()).isFalse();
      switchable.mark(1);
      try {
        switchable.reset();
        fail();
      } catch (IOException e) {
        // Expectd.
      }
    }
  }

  @Test
  public void switchToItself() throws Exception {
    ByteArrayInputStream bytes = new ByteArrayInputStream(new byte[] {1, 2, 3, 4});

    try (SwitchableDelegateInputStream switchable = new SwitchableDelegateInputStream(bytes)) {
      assertThat(switchable.read()).isEqualTo(1);
      assertThat(switchable.read()).isEqualTo(2);
      switchable.switchStream(bytes);
      assertThat(switchable.read()).isEqualTo(3);
      assertThat(switchable.read()).isEqualTo(4);
    }
  }
}
