package isocodes;

import static com.wwwdottheinternetdotcom.isocodes.Relaxation.ASCII_CASE;
import static com.wwwdottheinternetdotcom.isocodes.Relaxation.DASH;
import static com.wwwdottheinternetdotcom.isocodes.Relaxation.NUMERIC_PADDING;
import static com.wwwdottheinternetdotcom.isocodes.Relaxation.WHITESPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wwwdottheinternetdotcom.isocodes.Match;
import com.wwwdottheinternetdotcom.isocodes.Relaxation;
import com.wwwdottheinternetdotcom.isocodes.Strictness;
import com.wwwdottheinternetdotcom.isocodes.UnknownCodeException;
import com.wwwdottheinternetdotcom.isocodes.Validation;
import com.wwwdottheinternetdotcom.isocodes.iso3166.Country;
import com.wwwdottheinternetdotcom.isocodes.iso3166.Subdivision;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Checks the generated Java library against docs/output-spec (§ numbers refer to it). */
class SpecConformanceTest {

    private static final BiFunction<String, Strictness, Optional<Match<Country>>> ALPHA_2 = Country::fromAlpha2Detailed;
    private static final BiFunction<String, Strictness, Optional<Match<Country>>> ALPHA_3 = Country::fromAlpha3Detailed;
    private static final BiFunction<String, Strictness, Optional<Match<Country>>> NUMERIC = Country::fromNumericDetailed;
    private static final BiFunction<String, Strictness, Optional<Match<Subdivision>>> CODE = Subdivision::fromCodeDetailed;

    /** §6.5: operation, input, expected canonical code under LENIENT (null = absent), relaxations. */
    static Stream<Arguments> examples() {
        return Stream.of(
                Arguments.of(ALPHA_2, "DE", "DE", Set.of()),
                Arguments.of(ALPHA_2, "de", "DE", Set.of(ASCII_CASE)),
                Arguments.of(ALPHA_2, " de\n", "DE", Set.of(ASCII_CASE, WHITESPACE)),
                Arguments.of(NUMERIC, "4", "AF", Set.of(NUMERIC_PADDING)),
                Arguments.of(NUMERIC, " 04 ", "AF", Set.of(WHITESPACE, NUMERIC_PADDING)),
                Arguments.of(NUMERIC, "0004", null, Set.of()),
                Arguments.of(CODE, "US–CA", "US-CA", Set.of(DASH)),
                Arguments.of(CODE, "us—ca", "US-CA", Set.of(ASCII_CASE, DASH)),
                Arguments.of(CODE, "US_CA", null, Set.of()),
                Arguments.of(ALPHA_3, "Deu", "DE", Set.of(ASCII_CASE)),
                Arguments.of(CODE, "US - CA", null, Set.of()),
                Arguments.of(ALPHA_2, "ＤＥ", null, Set.of()),
                Arguments.of(ALPHA_2, "", null, Set.of()),
                Arguments.of(ALPHA_2, " DE", "DE", Set.of(WHITESPACE)),
                Arguments.of(CODE, "AD-2", null, Set.of()));
    }

    @ParameterizedTest
    @MethodSource("examples")
    <T> void lenientExamples(BiFunction<String, Strictness, Optional<Match<T>>> op, String input, String expected,
            Set<Relaxation> relaxations) {
        Optional<Match<T>> match = op.apply(input, Strictness.LENIENT);
        assertEquals(Optional.ofNullable(expected), match.map(m -> m.entry().toString()), input);
        match.ifPresent(m -> assertEquals(relaxations, m.relaxations(), input));
        // Under STRICT only canonical input matches, with no relaxations.
        Optional<Match<T>> strict = op.apply(input, Strictness.STRICT);
        assertEquals(match.isPresent() && relaxations.isEmpty(), strict.isPresent(), input);
        strict.ifPresent(m -> assertEquals(Set.of(), m.relaxations()));
    }

    @ParameterizedTest
    @MethodSource("examples")
    <T> void reportedRelaxationsAreNecessaryAndSufficient(BiFunction<String, Strictness, Optional<Match<T>>> op,
            String input, String expected, Set<Relaxation> relaxations) {
        if (expected == null) {
            return;
        }
        Strictness exactly = Strictness.allowing(relaxations);
        assertTrue(op.apply(input, exactly).isPresent(), "sufficient: " + input);
        for (Relaxation r : relaxations) {
            assertFalse(op.apply(input, exactly.without(r)).isPresent(), "necessary: " + r + " for " + input);
        }
    }

    @Test
    void allSixOperationsAgree() {
        for (String input : List.of("de", "DE", "XX", "", " de ")) {
            for (Strictness s : List.of(Strictness.STRICT, Strictness.LENIENT)) {
                Optional<Country> from = Country.fromAlpha2(input, s);
                Optional<Match<Country>> detailed = Country.fromAlpha2Detailed(input, s);
                Validation<Country> validation = Country.isValidAlpha2Detailed(input, s);
                assertEquals(from.isPresent(), Country.isValidAlpha2(input, s));
                assertEquals(from.isPresent(), validation.isValid());
                assertEquals(detailed, validation.match());
                assertEquals(from, detailed.map(Match::entry));
                if (from.isPresent()) {
                    assertSame(from.get(), Country.parseAlpha2(input, s));
                    assertEquals(detailed.get(), Country.parseAlpha2Detailed(input, s));
                } else {
                    assertThrows(UnknownCodeException.class, () -> Country.parseAlpha2(input, s));
                }
            }
        }
    }

    @Test
    void unknownCodeCarriesItsContext() {
        UnknownCodeException e = assertThrows(UnknownCodeException.class,
                () -> Country.parseAlpha2("X\nX", Strictness.LENIENT));
        assertEquals("3166-1", e.standard());
        assertEquals("alpha_2", e.field());
        assertEquals("X\nX", e.input());
        assertEquals(Strictness.LENIENT, e.strictness());
        assertEquals("\"X\\nX\" is not a known ISO 3166-1 alpha_2 code", e.getMessage());
        assertTrue(e instanceof IllegalArgumentException);
    }

    @Test
    void nullIsAProgrammingErrorEverywhere() {
        assertThrows(NullPointerException.class, () -> Country.fromAlpha2(null));
        assertThrows(NullPointerException.class, () -> Country.isValidAlpha2(null));
        assertThrows(NullPointerException.class, () -> Country.isValidAlpha2Detailed(null));
        assertThrows(NullPointerException.class, () -> Country.parseAlpha2(null));
        assertThrows(NullPointerException.class, () -> Subdivision.fromCode("US-CA", null));
        assertThrows(NullPointerException.class, () -> Strictness.allowing((Relaxation) null));
    }

    @Test
    void everyEntryFormatsToItsCanonicalCodeAndRoundTrips() {
        for (Country country : Country.all()) {
            assertEquals(country.alpha2(), country.toString());
            assertSame(country, Country.parseAlpha2(country.toString()));
            assertSame(country, Country.parseAlpha3(country.alpha3()));
            assertSame(country, Country.parseNumeric(country.numeric()));
            assertEquals(Set.of(), Country.parseAlpha2Detailed(country.alpha2(), Strictness.LENIENT).relaxations());
        }
        for (Subdivision subdivision : Subdivision.all()) {
            assertSame(subdivision, Subdivision.parseCode(subdivision.toString()));
        }
        assertEquals(Country.values().length, Country.all().size());
    }

    @Test
    void lenientExcludesWithdrawnAndStrictnessIsAnImmutableSet() {
        assertEquals(EnumSet.of(ASCII_CASE, DASH, WHITESPACE, NUMERIC_PADDING), Strictness.LENIENT.allowed());
        assertFalse(Strictness.LENIENT.allows(Relaxation.WITHDRAWN));
        assertTrue(Strictness.LENIENT.with(Relaxation.WITHDRAWN).allows(Relaxation.WITHDRAWN));
        assertFalse(Strictness.LENIENT.allows(Relaxation.WITHDRAWN), "with() returns a new value");
        assertEquals(Strictness.STRICT, Strictness.allowing());
        assertThrows(UnsupportedOperationException.class, () -> Strictness.STRICT.allowed().add(ASCII_CASE));
        assertEquals(List.of(ASCII_CASE, DASH, WHITESPACE, NUMERIC_PADDING), List.copyOf(Strictness.LENIENT.allowed()));
    }

    @Test
    void caseFoldingIsAsciiOnly() {
        // Dotless i (U+0131) must never fold to ASCII I, whatever the default locale.
        assertFalse(Country.fromAlpha2("ın", Strictness.LENIENT).isPresent());
        assertEquals("IN", Country.parseAlpha2("in", Strictness.LENIENT).toString());
    }
}
