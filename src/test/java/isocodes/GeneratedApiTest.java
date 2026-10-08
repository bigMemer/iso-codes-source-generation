package isocodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests against the generated API. Uses reflection because the base package is configurable and the set of
 * accessors varies between iso-codes releases; these checks only rely on data stable across all supported releases.
 */
class GeneratedApiTest {

    private static final String BASE = System.getProperty("basePackage");

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(BASE + "." + name);
    }

    private static Object lookup(String type, String method, String value) throws Exception {
        Optional<?> result = (Optional<?>) type(type).getMethod(method, String.class).invoke(null, value);
        assertTrue(result.isPresent(), type + "." + method + "(" + value + ")");
        return result.get();
    }

    private static Object call(Object target, String method) throws Exception {
        Method m = target.getClass().getMethod(method);
        Object value = m.invoke(target);
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    @Test
    void countries() throws Exception {
        Object germany = lookup("iso3166.Country", "fromAlpha3", "DEU");
        assertEquals("DE", ((Enum<?>) germany).name());
        assertEquals("276", call(germany, "numeric"));
        assertEquals("Germany", call(germany, "englishName"));
        assertEquals(germany, lookup("iso3166.Country", "fromNumeric", "276"));
        assertTrue(type("iso3166.Country").getEnumConstants().length > 240);
    }

    @Test
    void subdivisions() throws Exception {
        Object california = lookup("iso3166.Subdivision", "fromCode", "US-CA");
        assertEquals("California", call(california, "englishName"));
        assertEquals("US-CA", california.toString());
        List<?> all = (List<?>) type("iso3166.Subdivision").getMethod("all").invoke(null);
        assertTrue(all.size() > 4000);
    }
}
