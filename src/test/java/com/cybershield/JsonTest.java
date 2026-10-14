package com.cybershield;

import static org.junit.jupiter.api.Assertions.*;

import com.cybershield.util.Json;
import java.util.*;
import org.junit.jupiter.api.Test;

class JsonTest {
    @Test void writesNestedStructures() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("a", 1); m.put("b", List.of(1.5, "x\"y\n")); m.put("c", new int[]{1, 2}); m.put("d", null); m.put("e", true);
        assertEquals("{\"a\":1,\"b\":[1.5,\"x\\\"y\\n\"],\"c\":[1,2],\"d\":null,\"e\":true}", Json.write(m));
    }

    @Test void roundTrips() {
        String src = "{\"name\":\"caf\\u00e9\",\"n\":[1,2.5,-3e2],\"o\":{\"k\":false,\"z\":null}}";
        Map<String, Object> o = Json.asObject(Json.parse(src));
        assertEquals("café", o.get("name"));
        assertEquals(List.of(1.0, 2.5, -300.0), o.get("n"));
        assertEquals(Json.write(o), Json.write(Json.parse(Json.write(o))));
    }

    @Test void escapesControlCharacters() {
        assertEquals("\"\\u0001\"", Json.write("\u0001"));
        assertEquals("\u0001", Json.parse("\"\\u0001\""));
    }

    @Test void nonFiniteNumbersBecomeNull() {
        assertEquals("[null,null]", Json.write(new double[]{Double.NaN, Double.POSITIVE_INFINITY}));
    }

    @Test void rejectsMalformedInput() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(Json.JsonException.class, () -> Json.parse("[1,2"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{} extra"));
        assertThrows(Json.JsonException.class, () -> Json.asObject(Json.parse("[1]")));
    }
}
