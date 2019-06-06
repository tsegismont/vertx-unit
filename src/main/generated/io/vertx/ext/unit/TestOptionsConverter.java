package io.vertx.ext.unit;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Converter and Codec for {@link io.vertx.ext.unit.TestOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.ext.unit.TestOptions} original class using Vert.x codegen.
 */
public class TestOptionsConverter implements JsonCodec<TestOptions, JsonObject> {

  public static final TestOptionsConverter INSTANCE = new TestOptionsConverter();

  @Override public JsonObject encode(TestOptions value) { return (value != null) ? value.toJson() : null; }

  @Override public TestOptions decode(JsonObject value) { return (value != null) ? new TestOptions(value) : null; }

  @Override public Class<TestOptions> getTargetClass() { return TestOptions.class; }

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, TestOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "timeout":
          if (member.getValue() instanceof Number) {
            obj.setTimeout(((Number)member.getValue()).longValue());
          }
          break;
        case "useEventLoop":
          if (member.getValue() instanceof Boolean) {
            obj.setUseEventLoop((Boolean)member.getValue());
          }
          break;
      }
    }
  }

  public static void toJson(TestOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(TestOptions obj, java.util.Map<String, Object> json) {
    json.put("timeout", obj.getTimeout());
    if (obj.isUseEventLoop() != null) {
      json.put("useEventLoop", obj.isUseEventLoop());
    }
  }
}
