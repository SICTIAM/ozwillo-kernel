/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.http.fixes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;

@RunWith(Parameterized.class)
public class CookieDateParserTest {
  @Parameters(name = "{index}: parseCookieDate({0}) = {1}")
  public static Iterable<Object[]> data() throws IOException {
    ObjectMapper mapper = new ObjectMapper(new JsonFactory().enable(JsonParser.Feature.ALLOW_COMMENTS));
    JsonNode examples = mapper.readTree(CookieDateParserTest.class.getResource("/http-state/tests/data/dates/examples.json"));
    JsonNode bsdExamples = mapper.readTree(CookieDateParserTest.class.getResource("/http-state/tests/data/dates/bsd-examples.json"));
    return FluentIterable.from(Iterables.concat(examples, bsdExamples))
        .transform(new Function<JsonNode, Object[]>() {
          @Override
          public Object[] apply(JsonNode input) {
            return new Object[] {
                input.get("test").textValue(),
                input.get("expected").textValue()
            };
          }
        });
  }

  private static final SimpleDateFormat rfc1123date;
  static {
    rfc1123date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
    rfc1123date.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  private final String input;
  private final String expected;

  public CookieDateParserTest(String input, @Nullable String expected) {
    this.input = input;
    this.expected = expected;
  }

  private static String format(Date date) {
    if (date == null) return null;
    return rfc1123date.format(date);
  }

  @Test
  public void testParseCookieDate() {
    Date actual = CookieDateParser.parseCookieDate(input);
    // XXX: do not use DateAssert#withDateFormat as it stores the format in a static field
    // also parses 'expected' rather than formatting 'actual'.
    assertThat(format(actual)).isEqualTo(expected);
  }
}
