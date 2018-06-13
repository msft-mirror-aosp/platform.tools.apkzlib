package com.android.tools.build.apkzlib.bytestorage;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.build.apkzlib.zip.utils.CloseableDelegateByteSource;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SwitchableDelegateCloseableByteSourceTest {

  @Test
  public void switchSourcesWillSwitchStreams() throws Exception {
    try (SwitchableDelegateCloseableByteSource switchable =
        new SwitchableDelegateCloseableByteSource(
            new CloseableDelegateByteSource(ByteSource.wrap(new byte[] {1, 2, 3, 4}), 4))) {
      InputStream stream1 = switchable.openStream();
      InputStream stream2 = switchable.openStream();

      assertThat(stream1.read()).isEqualTo(1);
      assertThat(stream1.read()).isEqualTo(2);
      assertThat(stream2.read()).isEqualTo(1);

      switchable.switchSource(
          new CloseableDelegateByteSource(ByteSource.wrap(new byte[] {5, 6, 7, 8}), 4));

      assertThat(stream1.read()).isEqualTo(7);
      assertThat(stream1.read()).isEqualTo(8);
      assertThat(stream2.read()).isEqualTo(6);
      assertThat(stream2.read()).isEqualTo(7);
      assertThat(stream2.read()).isEqualTo(8);
    }
  }

  @Test
  public void switchSourceToSameIsNoOp() throws Exception {
    try (CloseableDelegateByteSource source =
            new CloseableDelegateByteSource(ByteSource.wrap(new byte[] {1, 2, 3, 4}), 4);
        SwitchableDelegateCloseableByteSource switchable =
            new SwitchableDelegateCloseableByteSource(source)) {
      InputStream stream = switchable.openStream();
      assertThat(stream.read()).isEqualTo(1);
      assertThat(stream.read()).isEqualTo(2);

      switchable.switchSource(source);
      assertThat(stream.read()).isEqualTo(3);
      assertThat(stream.read()).isEqualTo(4);
    }
  }

  @Test
  public void closingClosesDelegateAndAllStreams() throws Exception {
    AtomicBoolean delegateClosed = new AtomicBoolean(false);
    AtomicReference<SwitchableDelegateInputStream> stream = new AtomicReference<>();
    try (SwitchableDelegateCloseableByteSource switchable =
        new SwitchableDelegateCloseableByteSource(
            new CloseableDelegateByteSource(ByteSource.wrap(new byte[2]), 2) {
              @Override
              protected synchronized void innerClose() throws IOException {
                super.innerClose();
                delegateClosed.set(true);
              }
            })) {
      stream.set((SwitchableDelegateInputStream) switchable.openStream());
    }

    assertThat(delegateClosed.get()).isTrue();
    assertThat(stream.get().endOfStreamReached).isTrue();
  }

  @Test
  public void switchClosesOldDelegate() throws Exception {
    AtomicBoolean delegateClosed = new AtomicBoolean(false);
    try (SwitchableDelegateCloseableByteSource switchable =
        new SwitchableDelegateCloseableByteSource(
            new CloseableDelegateByteSource(ByteSource.wrap(new byte[2]), 2) {
              @Override
              protected synchronized void innerClose() throws IOException {
                super.innerClose();
                delegateClosed.set(true);
              }
            })) {
      switchable.switchSource(new CloseableDelegateByteSource(ByteSource.empty(), 0));
      assertThat(delegateClosed.get()).isTrue();
    }
  }

  @Test
  public void switchClosedSourceClosesNewSource() throws Exception {
    AtomicBoolean delegateClosed = new AtomicBoolean(false);
    SwitchableDelegateCloseableByteSource switchable;
    try (SwitchableDelegateCloseableByteSource switchableOpen =
        new SwitchableDelegateCloseableByteSource(
            new CloseableDelegateByteSource(ByteSource.empty(), 0))) {
      switchable = switchableOpen;
    }

    switchable.switchSource(
        new CloseableDelegateByteSource(ByteSource.empty(), 0) {
          @Override
          protected synchronized void innerClose() throws IOException {
            super.innerClose();
            delegateClosed.set(true);
          }
        });

    assertThat(delegateClosed.get()).isTrue();
  }
}
