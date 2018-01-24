package io.vertx.ext.unit.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TestContextImpl implements TestContext {

  private static final AtomicInteger threadCount = new AtomicInteger(0);

  private final Map<String, Object> attributes;
  private final Handler<Throwable> unhandledFailureHandler;
  private Step current;

  /**
   * A step in the test.
   */
  private class Step {

    private final Handler<Throwable> endHandler;
    private final LinkedList<AsyncImpl> asyncs = new LinkedList<>();
    private boolean running = true;
    private boolean complete;
    private Throwable failure;

    public Step(Handler<Throwable> endHandler, Throwable failure) {
      this.endHandler = endHandler;
      this.failure = failure;
    }

    private void tryEnd() {
      List<AsyncImpl> copy;
      boolean end = false;
      synchronized (this) {
        if ((asyncs.isEmpty() || failure != null) && !complete && !running) {
          complete = true;
          end = true;
        }
        if (end) {
          // Stack contention to avoid CME.
          copy = new ArrayList<>(asyncs);
          asyncs.clear();
          synchronized (TestContextImpl.this) {
            current = null;
          }
          this.notify();
        } else {
          copy = Collections.emptyList();
        }
      }
      if (end) {
        for (AsyncImpl a : copy) {
          a.release();
        }
        endHandler.handle(failure);
      }
    }

    private boolean failed(Throwable t) {
      synchronized (this) {
        if (complete) {
          return false;
        }
        if (failure == null) {
          failure = t;
        }
      }
      tryEnd();
      return true;
    }

    private AsyncImpl async(int count, boolean strict) {
      synchronized (this) {
        if (!complete) {
          AsyncImpl async = new AsyncImpl(count, strict);
          if (failure == null) {
            asyncs.add(async);
          }
          return async;
        } else {
          throw new IllegalStateException("Test already completed");
        }
      }
    }

    private void run(long timeout, Handler<TestContext> test) {
      if (timeout > 0) {
        Runnable cancel = () -> {
          try {
            synchronized (this) {
              if (complete) {
                return;
              }
              wait(timeout);
              if (complete) {
                return;
              }
              running = false;
            }
            failed(new TimeoutException());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };
        Thread timeoutThread = new Thread(cancel);
        timeoutThread.setName("vert.x-unit-timeout-thread-" + threadCount.incrementAndGet());
        timeoutThread.start();
      }
      try {
        test.handle(TestContextImpl.this);
      } catch (Throwable t) {
        failed(t);
      } finally {
        synchronized (this) {
          running = false;
        }
        tryEnd();
      }
    }

    class AsyncImpl extends CompletionImpl<Void> implements Async {

      private final int initialCount;
      private final AtomicInteger current;
      private final boolean strict;

      public AsyncImpl(int initialCount, boolean strict) {
        this.initialCount = initialCount;
        this.strict = strict;
        this.current = new AtomicInteger(initialCount);
      }

      @Override
      public int count() {
        return current.get();
      }

      @Override
      public void countDown() {
        int oldValue, newValue;
        do {
          oldValue = current.get();
          if (oldValue == 0) {
            newValue = 0;
            if (strict) {
              String msg;
              if (initialCount == 1) {
                msg = "Countdown invoked more than once";
              } else if (initialCount == 2) {
                msg = "Countdown invoked more than twice";
              } else {
                msg = "Countdown invoked more than " + initialCount + " times";
              }
              throw new IllegalStateException(msg);
            }
          } else {
            newValue = oldValue - 1;
          }
        } while (!current.compareAndSet(oldValue, newValue));
        if (newValue == 0) {
          completable.complete(null);
          internalComplete();
        }
      }

      @Override
      public void complete() {
        int value = current.getAndSet(0);
        if (value > 0) {
          completable.complete(null);
          internalComplete();
        } else {
          throw new IllegalStateException("The Async complete method has been called more than " + initialCount + " times, check your test.");
        }
      }

      private void release() {
        if (Step.this.failure != null) {
          completable.completeExceptionally(Step.this.failure);
        } else {
          completable.complete(null);
        }
      }

      void internalComplete() {
        boolean complete;
        synchronized (Step.this) {
          complete = asyncs.remove(this);
        }
        if (complete) {
          tryEnd();
        }
        release();
      }
    }
  }

  public TestContextImpl(Map<String, Object> attributes, Handler<Throwable> unhandledFailureHandler) {
    this.attributes = attributes;
    this.unhandledFailureHandler = unhandledFailureHandler;
  }

  @Override
  public synchronized <T> T get(String key) {
    return (T) attributes.get(key);
  }

  @Override
  public synchronized <T> T put(String key, Object value) {
    if (value != null) {
      return (T) attributes.put(key, value);
    } else {
      return (T) attributes.remove(key);
    }
  }

  @Override
  public synchronized <T> T remove(String key) {
    return (T) attributes.remove(key);
  }

  public void run(Throwable failed, long timeout, Handler<TestContext> test, Handler<Throwable> endHandler) {
    Step step;
    synchronized (this) {
      if (current != null) {
        throw new IllegalStateException("Wrong status");
      }
      current = step = new Step(endHandler, failed);
    }
    step.run(timeout, test);
  }

  public void failed(Throwable t) {
    boolean reported;
    synchronized (this) {
      reported = current != null && current.failed(t);
    }
    if (!reported && unhandledFailureHandler != null) {
      unhandledFailureHandler.handle(t);
    }
  }

  @Override
  public Async async() {
    return async(1);
  }

  @Override
  public Async async(int count) {
    return async(count, false);
  }

  @Override
  public Async async(int count, boolean strict) {
    if (count < 1) {
      throw new IllegalArgumentException("Async completion count must be > 0");
    }
    synchronized (this) {
      if (current != null) {
        return current.async(count, strict);
      } else {
        throw new IllegalStateException();
      }
    }
  }

  @Override
  public TestContext assertNull(Object expected) {
    return assertNull(expected, null);
  }

  @Override
  public TestContext assertNull(Object expected, String message) {
    if (expected != null) {
      throw reportAssertionError(formatMessage(message, "Expected null"));
    }
    return this;
  }

  @Override
  public TestContext assertNotNull(Object expected) {
    return assertNotNull(expected, null);
  }

  @Override
  public TestContext assertNotNull(Object expected, String message) {
    if (expected == null) {
      throw reportAssertionError(formatMessage(message, "Expected not null"));
    }
    return this;
  }

  @Override
  public TestContext assertTrue(boolean condition, String message) {
    if (!condition) {
      throw reportAssertionError(formatMessage(message, "Expected true"));
    }
    return this;
  }

  public TestContext assertTrue(boolean condition) {
    return assertTrue(condition, null);
  }

  @Override
  public TestContext assertFalse(boolean condition) {
    return assertFalse(condition, null);
  }

  @Override
  public TestContext assertFalse(boolean condition, String message) {
    if (condition) {
      throw reportAssertionError(formatMessage(message, "Expected false"));
    }
    return this;
  }

  @Override
  public void fail() {
    fail((String) null);
  }

  public void fail(String message) {
    throw reportAssertionError(message != null ? message : "Test failed");
  }

  public void fail(Throwable cause) {
    failed(cause);
    Helper.uncheckedThrow(cause);
  }

  @Override
  public Handler<Throwable> exceptionHandler() {
    return this::failed;
  }

  @Override
  public TestContext assertEquals(Object expected, Object actual) {
    return assertEquals(expected, actual, null);
  }

  @Override
  public TestContext assertEquals(Object expected, Object actual, String message) {
    if (actual == null) {
      if (expected != null) {
        throw reportAssertionError(formatMessage(message, "Expected " + expected + " got null"));
      }
    } else {
      if (expected == null) {
        throw reportAssertionError(formatMessage(message, "Expected null instead of " + actual));
      } else if (!expected.equals(actual)) {
        throw reportAssertionError(formatMessage(message, "Not equals : " + expected + " != " + actual));
      }
    }
    return this;
  }

  @Override
  public TestContext assertInRange(double expected, double actual, double delta) {
    return assertInRange(expected, actual, delta, null);
  }

  @Override
  public TestContext assertInRange(double expected, double actual, double delta, String message) {
    if (Double.compare(expected, actual) != 0 && Math.abs((actual - expected)) > delta) {
      throw reportAssertionError(formatMessage(message, "Expected " + actual + " to belong to [" +
          (expected - delta) + "," + (expected + delta) + "]"));
    }
    return this;
  }

  @Override
  public TestContext assertNotEquals(Object first, Object second, String message) {
    if (first == null) {
      if (second == null) {
        throw reportAssertionError(formatMessage(message, "Expected null != null"));
      }
    } else {
      if (first.equals(second)) {
        throw reportAssertionError(formatMessage(message, "Expected different values " + first + " != " + second));
      }
    }
    return this;
  }

  @Override
  public TestContext verify(Handler<Void> block) {
    try {
      block.handle(null);
    } catch (Throwable t) {
      fail(t);
    }
    return this;
  }

  @Override
  public <T> Handler<AsyncResult<T>> asyncAssertSuccess() {
    return asyncAssertSuccess(result -> {
    });
  }

  @Override
  public <T> Handler<AsyncResult<T>> asyncAssertSuccess(Handler<T> resultHandler) {
    Async async = async();
    return ar -> {
      if (ar.succeeded()) {
        T result = ar.result();
        try {
          resultHandler.handle(result);
          async.complete();
        } catch (Throwable e) {
          failed(e);
        }
      } else {
        failed(ar.cause());
      }
    };
  }

  @Override
  public <T> Handler<AsyncResult<T>> asyncAssertFailure() {
    return asyncAssertFailure(cause -> {
    });
  }

  @Override
  public <T> Handler<AsyncResult<T>> asyncAssertFailure(Handler<Throwable> causeHandler) {
    Async async = async();
    return ar -> {
      if (ar.failed()) {
        Throwable result = ar.cause();
        try {
          causeHandler.handle(result);
          async.complete();
        } catch (Throwable e) {
          failed(e);
        }
      } else {
        reportAssertionError("Was expecting a failure instead of of success");
      }
    };
  }

  @Override
  public TestContext assertNotEquals(Object first, Object second) {
    return assertNotEquals(first, second, null);
  }

  /**
   * Create and report an assertion error, the returned throwable can be thrown to change
   * the control flow.
   *
   * @return an assertion error to eventually throw
   */
  private AssertionError reportAssertionError(String message) {
    AssertionError err = new AssertionError(message);
    failed(err);
    return err;
  }

  private static String formatMessage(String providedMessage, String defaultMessage) {
    return providedMessage == null ? defaultMessage : (providedMessage + ". " + defaultMessage);
  }
}
