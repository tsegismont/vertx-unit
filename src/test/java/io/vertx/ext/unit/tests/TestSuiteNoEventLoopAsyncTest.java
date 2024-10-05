package io.vertx.ext.unit.tests;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TestSuiteNoEventLoopAsyncTest extends TestSuiteTestBase {

  public TestSuiteNoEventLoopAsyncTest() {
    super();
    getRunner = testsuite -> testsuite.runner();
    run = (runner) -> new Thread() {
      @Override
      public void run() {
        runner.setUseEventLoop(false).run();
      }
    }.start();
    operateOnAsync = (async,action) -> action.accept(async);
  }

  @Override
  protected boolean checkTest(TestContext test) {
    return Vertx.currentContext() == null;
  }
}
