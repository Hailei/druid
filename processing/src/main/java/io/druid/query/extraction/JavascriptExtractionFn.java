/*
 * Druid - a distributed column store.
 * Copyright 2012 - 2015 Metamarkets Group Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.druid.query.extraction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.metamx.common.StringUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ScriptableObject;

import java.nio.ByteBuffer;

public class JavascriptExtractionFn implements ExtractionFn
{
  private static Function<Object, String> compile(String function)
  {
    final ContextFactory contextFactory = ContextFactory.getGlobal();
    final Context context = contextFactory.enterContext();
    context.setOptimizationLevel(9);

    final ScriptableObject scope = context.initStandardObjects();

    final org.mozilla.javascript.Function fn = context.compileFunction(scope, function, "fn", 1, null);
    Context.exit();


    return new Function<Object, String>()
    {
      public String apply(Object input)
      {
        // ideally we need a close() function to discard the context once it is not used anymore
        Context cx = Context.getCurrentContext();
        if (cx == null) {
          cx = contextFactory.enterContext();
        }

        final Object res = fn.call(cx, scope, scope, new Object[]{input});
        return res != null ? Context.toString(res) : null;
      }
    };
  }

  private static final byte CACHE_TYPE_ID = 0x4;

  private final String function;
  private final Function<Object, String> fn;
  private final boolean injective;

  @JsonCreator
  public JavascriptExtractionFn(
      @JsonProperty("function") String function,
      @JsonProperty("injective") boolean injective
  )
  {
    Preconditions.checkNotNull(function, "function must not be null");

    this.function = function;
    this.fn = compile(function);
    this.injective = injective;
  }

  @JsonProperty
  public String getFunction()
  {
    return function;
  }

  @JsonProperty
  public boolean isInjective()
  {
    return this.injective;
  }

  @Override
  public byte[] getCacheKey()
  {
    byte[] bytes = StringUtils.toUtf8(function);
    return ByteBuffer.allocate(1 + bytes.length)
                     .put(CACHE_TYPE_ID)
                     .put(bytes)
                     .array();
  }

  @Override
  public String apply(Object value)
  {
    return Strings.emptyToNull(fn.apply(value));
  }

  @Override
  public String apply(String value)
  {
    return this.apply((Object) value);
  }

  @Override
  public String apply(long value)
  {
    return this.apply((Long) value);
  }

  @Override
  public boolean preservesOrdering()
  {
    return false;
  }

  @Override
  public ExtractionType getExtractionType()
  {
    return injective ? ExtractionType.ONE_TO_ONE : ExtractionType.MANY_TO_ONE;
  }

  @Override
  public String toString()
  {
    return "JavascriptDimExtractionFn{" +
           "function='" + function + '\'' +
           '}';
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    JavascriptExtractionFn that = (JavascriptExtractionFn) o;

    if (!function.equals(that.function)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode()
  {
    return function.hashCode();
  }
}
