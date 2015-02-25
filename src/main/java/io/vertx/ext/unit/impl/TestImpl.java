package io.vertx.ext.unit.impl;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.Test;
import org.junit.Assert;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
* @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
*/
class TestImpl implements Test {

  class AsyncImpl implements Async {

    private final AtomicBoolean completeCalled = new AtomicBoolean();

    @Override
    public void complete() {
      if (!completeCalled.compareAndSet(false, true)) {
        throw new IllegalStateException("The Async complete method cannot be called more than one time, check your test.");
      }
      internalComplete();
    }

    void internalComplete() {
      boolean complete;
      synchronized (TestImpl.this) {
        complete = asyncs.remove(this);
      }
      if (complete) {
        tryEnd();
      }
    }
  }

  private final InvokeTask invokeTask;
  private final Context context;
  private boolean completed;
  private Throwable failed;
  private long time;
  private final LinkedList<AsyncImpl> asyncs = new LinkedList<>();

  public TestImpl(InvokeTask invokeTask, Context context, long time, Throwable failed) {
    this.invokeTask = invokeTask;
    this.context = context;
    this.failed = failed;
    this.time = time - System.currentTimeMillis();
  }

  private void tryEnd() {
    boolean run = false;
    synchronized (this) {
      if (asyncs.isEmpty() && !completed) {
        completed = true;
        run = true;
        time += System.currentTimeMillis();
      }
    }
    if (run) {
      context.run(invokeTask.next, new Result(time, failed));
    }
  }

  void failed(Throwable t) {
    synchronized (this) {
      if (!completed) {
        failed = t;
      }
    }
    while (true) {
      AsyncImpl async;
      synchronized (this) {
        async = asyncs.peekFirst();
      }
      if (async == null) {
        break;
      } else {
        async.internalComplete();
      }
    }
  }

  @Override
  public Vertx vertx() {
    return context.vertx();
  }

  @Override
  public AsyncImpl async() {
    synchronized (this) {
      if (!completed) {
        AsyncImpl async = new AsyncImpl();
        asyncs.add(async);
        return async;
      } else {
        throw new IllegalStateException("Test already completed");
      }
    }
  }

  @Override
  public Test assertTrue(boolean condition, String message) {
    try {
      Assert.assertTrue(message, condition);
      return this;
    } catch (AssertionError err) {
      failed(err);
      throw err;
    }
  }

  public Test assertTrue(boolean condition) {
    try {
      Assert.assertTrue(condition);
      return this;
    } catch (AssertionError err) {
      failed(err);
      throw err;
    }
  }

  public void fail(String message) {
    try {
      Assert.fail(message);
    } catch (AssertionError err) {
      failed(err);
      throw err;
    }
  }

  @Override
  public Test assertFalse(boolean condition) {
    try {
      Assert.assertFalse(condition);
      return this;
    } catch (AssertionError err) {
      failed(err);
      throw err;
    }
  }

  @Override
  public Test assertFalse(boolean condition, String message) {
    try {
      Assert.assertFalse(message, condition);
      return this;
    } catch (AssertionError err) {
      failed(err);
      throw err;
    }
  }

  @Override
  public void fail() {
    try {
      Assert.fail();
    } catch (AssertionError err) {
      failed(err);
      throw err;
    }
  }

  @Override
  public Test assertEquals(Object expected, Object actual) {
    try {
      Assert.assertEquals(expected, actual);
      return this;
    } catch (AssertionError err) {
      failed(err);
      throw err;
    }
  }

  @Override
  public Test assertEquals(Object expected, Object actual, String message) {
    try {
      Assert.assertEquals(message, expected, actual);
      return this;
    } catch (AssertionError err) {
      failed(err);
      throw err;
    }
  }

  @Override
  public Test assertInRange(double expected, double actual, double delta) {
    try {
      Assert.assertEquals(expected, actual, delta);
      return this;
    } catch (AssertionError err) {
      failed(err);
      throw err;
    }
  }

  @Override
  public Test assertInRange(double expected, double actual, double delta, String message) {
    try {
      Assert.assertEquals(message, expected, actual, delta);
      return this;
    } catch (AssertionError err) {
      failed(err);
      throw err;
    }
  }

  @Override
  public Test assertNotEquals(Object first, Object second, String message) {
    try {
      Assert.assertNotEquals(message, first, second);
      return this;
    } catch (AssertionError err) {
      failed(err);
      throw err;
    }
  }

  @Override
  public Test assertNotEquals(Object first, Object second) {
    try {
      Assert.assertNotEquals(first, second);
      return this;
    } catch (AssertionError err) {
      failed(err);
      throw err;
    }
  }
}
