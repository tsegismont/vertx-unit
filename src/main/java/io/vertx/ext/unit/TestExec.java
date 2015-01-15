package io.vertx.ext.unit;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface TestExec {

  String description();

  void completeHandler(Handler<TestResult> handler);

}