package com.android.tools.build.apkzlib.bytestorage;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LruTrackerTest {

  @Test
  public void firstObjectIsPosition0() {
    LruTracker<Object> tracker = new LruTracker<>();
    Object object = new Object();
    tracker.track(object);
    assertThat(tracker.positionOf(object)).isEqualTo(0);
  }

  @Test
  public void secondObjectIsPosition0() {
    LruTracker<Object> tracker = new LruTracker<>();
    Object object1 = new Object();
    tracker.track(object1);
    Object object2 = new Object();
    tracker.track(object2);
    assertThat(tracker.positionOf(object2)).isEqualTo(0);
    assertThat(tracker.positionOf(object1)).isEqualTo(1);
  }

  @Test
  public void accessObjectMovesItToPosition0() {
    LruTracker<Object> tracker = new LruTracker<>();
    Object object1 = new Object();
    tracker.track(object1);
    Object object2 = new Object();
    tracker.track(object2);
    Object object3 = new Object();
    tracker.track(object3);
    tracker.access(object1);
    assertThat(tracker.positionOf(object1)).isEqualTo(0);
  }

  @Test
  public void untrackingMovesObjectsUp() {
    LruTracker<Object> tracker = new LruTracker<>();
    Object object1 = new Object();
    tracker.track(object1);
    Object object2 = new Object();
    tracker.track(object2);
    Object object3 = new Object();
    tracker.track(object3);
    tracker.untrack(object2);
    assertThat(tracker.positionOf(object3)).isEqualTo(0);
    assertThat(tracker.positionOf(object1)).isEqualTo(1);
  }

  @Test
  public void lastWithoutAnyObjectsIsEmpty() {
    LruTracker<Object> tracker = new LruTracker<>();
    assertThat(tracker.last()).isNull();
  }

  @Test
  public void lastWithOneObjectReturnsThatObject() {
    LruTracker<Object> tracker = new LruTracker<>();
    Object object1 = new Object();
    tracker.track(object1);
    assertThat(tracker.last()).isEqualTo(object1);
  }

  @Test
  public void lastWithTwoObjectsReturnsPosition1() {
    LruTracker<Object> tracker = new LruTracker<>();
    Object object1 = new Object();
    tracker.track(object1);
    Object object2 = new Object();
    tracker.track(object2);
    assertThat(tracker.last()).isEqualTo(object1);
  }

  @Test
  public void cannotTrackTwice() {
    LruTracker<Object> tracker = new LruTracker<>();
    Object obj = new Object();
    tracker.track(obj);
    try {
      tracker.track(obj);
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }
  }

  @Test
  public void cannotUnrackTwice() {
    LruTracker<Object> tracker = new LruTracker<>();
    Object obj = new Object();
    tracker.track(obj);
    tracker.untrack(obj);
    try {
      tracker.untrack(obj);
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }
  }

  @Test
  public void cannotUnrackIfNotTracked() {
    LruTracker<Object> tracker = new LruTracker<>();
    Object obj = new Object();
    try {
      tracker.untrack(obj);
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }
  }

  @Test
  public void cannotPositionIfNotTracked() {
    LruTracker<Object> tracker = new LruTracker<>();
    Object obj = new Object();
    tracker.track(obj);
    tracker.untrack(obj);
    try {
      tracker.positionOf(obj);
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }
  }

  @Test
  public void cannotAccessIfNotTracked() {
    LruTracker<Object> tracker = new LruTracker<>();
    Object obj = new Object();
    tracker.track(obj);
    tracker.untrack(obj);
    try {
      tracker.access(obj);
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }
  }
}
