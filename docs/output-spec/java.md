# Java binding

**Status:** partially conforming (see [§9](#9-conformance-gaps)). **Implements:**
[generated library specification](README.md), spec version 1.

This document maps the language-neutral spec onto Java. It only decides what the spec leaves to bindings; it
doesn't repeat behaviour the spec already defines.

## 1. Packaging

- **Artifact:** `com.wwwdottheinternetdotcom:iso-codes:<source version>`, e.g. `4.20.1`. A re-release for the same
  source version appends `-r<n>` starting at `-r2` (`4.20.1-r2`), which Maven orders after `4.20.1`.
- **Language level:** compiled with `--release 17`. Generated code MAY use any Java 17 feature (records, pattern
  matching for `instanceof`, switch expressions) and nothing newer.
- **Module name:** the JAR MUST declare `Automatic-Module-Name: com.wwwdottheinternetdotcom.isocodes` in its
  manifest.
- **Dependencies:** none at runtime. No annotations from third-party libraries (no JSpecify, no JetBrains
  annotations).

## 2. Naming

| Spec concept | Java |
|--------------|------|
| Root namespace | package `com.wwwdottheinternetdotcom.isocodes` (the generator's `basePackage`) |
| Standard family namespace | sub-package: `.iso3166` |
| Type | concept name as is: `Country`, `Subdivision` |
| Field `snake_case` id | `lowerCamelCase`: `alpha_2` → `alpha2`, `withdrawal_date` → `withdrawalDate` |
| `name` field | `englishName()` |
| Operation `from_f` / `parse_f` / `is_valid_f` | `fromAlpha2`, `parseAlpha2`, `isValidAlpha2` |
| Member names | exactly the spec's rule: `Country.DE` |
| Dataset constants | `IsoCodes.VERSION`, `IsoCodes.SOURCES` (`List<String>`), `IsoCodes.LICENSE` |
| Error type | `UnknownCodeException` |

`IsoCodes.VERSION` now holds the dataset version rather than the iso-codes version.

## 3. Enumerations

Standards whose entries fit in a Java `enum` MUST be generated as an `enum`. `Country` is an `enum`; the exception is:

| Standard | Why | Representation |
|----------|-----|----------------|
| ISO 3166-2 `Subdivision` (5,000+ entries) | An enum's static initialiser and constant pool exceed the class-file limits (64 KiB per method, 65,535 constants). | `public final class` |

The class representation:

- has no public or protected constructor, so consumers can't create instances;
- creates every instance exactly once, from package-private holder classes (`SubdivisionData0`,
  `SubdivisionData1`, ...) of at most 500 entries each, so `==` and `equals` agree;
- implements `equals` and `hashCode` on the primary code, consistent with identity;
- has no named members. `Subdivision.US_CA` doesn't exist; use `Subdivision.parseCode("US-CA")`. That's the spec's
  permitted exception for large standards.

Which representation a standard gets is decided by `JavaTarget` in the emitter, not by entry count at generation time,
so a standard can't silently switch between `enum` and class when upstream data grows.

`enum` types get `compareTo` and `values()` for free. Their declaration order is source order, as the spec requires.
The class types MUST NOT implement `Comparable`.

## 4. Accessors and absence

- Accessors are record-style methods without a `get` prefix: `alpha2()`, `englishName()`.
- Required fields return `String`, never `null`.
- Lifecycle: `boolean isWithdrawn()`, and `Optional<LocalDate>` for `assignedEarliest()`, `assignedLatest()`,
  `withdrawnEarliest()` and `withdrawnLatest()` (`java.time.LocalDate`), and `LocalDate recordedSince()`.
- Optional fields return `Optional<String>`, never `null` and never `Optional.of("")`.
- Fields are `private final String`. Optional fields store `null` internally, wrapped on access.

## 5. Operations

For a standard type `T` and a code field `f` (shown for `Country` and `alpha_2`):

```java
public static List<Country> all();

public static Optional<Country> fromAlpha2(String alpha2);
public static Optional<Country> fromAlpha2(String alpha2, Strictness strictness);
public static Optional<Match<Country>> fromAlpha2Detailed(String alpha2);
public static Optional<Match<Country>> fromAlpha2Detailed(String alpha2, Strictness strictness);

public static Country parseAlpha2(String alpha2);                                    // throws UnknownCodeException
public static Country parseAlpha2(String alpha2, Strictness strictness);             // throws UnknownCodeException
public static Match<Country> parseAlpha2Detailed(String alpha2);                     // throws UnknownCodeException
public static Match<Country> parseAlpha2Detailed(String alpha2, Strictness strictness); // throws UnknownCodeException

public static boolean isValidAlpha2(String alpha2);
public static boolean isValidAlpha2(String alpha2, Strictness strictness);
public static Validation<Country> isValidAlpha2Detailed(String alpha2);
public static Validation<Country> isValidAlpha2Detailed(String alpha2, Strictness strictness);
```

Every operation, plain and detailed, also has two more overloads for history checks:

```java
public static Optional<Country> fromAlpha2(String alpha2, Strictness strictness, LocalDate writtenAt);
public static Optional<Country> fromAlpha2(String alpha2, Strictness strictness, LocalDate writtenAt,
                                           HistoryPolicy history);   // default HistoryPolicy.PESSIMISTIC
```

shown here for `fromAlpha2` only. `parseAlpha2` with a `writtenAt` can also throw `MeaningChangedException`.

- The one-argument forms are equivalent to passing `Strictness.STRICT`.
- `all()` returns an unmodifiable `List` of active entries, and `allIncludingWithdrawn()` all of them (from
  `List.copyOf` or equivalent). Both exist on `enum` types as well as the class types, so the API is uniform. On
  `enum` types, `values()` remains available and, as the language defines it, includes withdrawn constants.
- Withdrawn `enum` constants carry `@Deprecated` (with `forRemoval = false`, since they're never removed) and a
  Javadoc `@deprecated` tag giving the withdrawal date as precisely as known: `@deprecated Withdrawn by ISO on
  2006-09-26.`, `… in 1993.`, `… by 2021-10-27.`, or `… (date unknown).`

### Formatting

- Each code field's accessor (`alpha2()`, `numeric()`) returns its canonical form, per spec §5.4 and §6.1.
- `toString()` returns the canonical primary code. For `Country` the default `enum` `toString()` (the constant name)
  already equals `alpha2()`, but the emitter MUST override `toString()` to return `alpha2()` explicitly, so the
  guarantee doesn't depend on the member-naming rule. `name()` still returns the constant name, as the language
  requires.

## 6. Relaxation, Strictness, Match and Validation

All four live in the root package, alongside `IsoCodes`.

```java
public enum Relaxation { ASCII_CASE, DASH, WHITESPACE, NUMERIC_PADDING, WITHDRAWN }
```

Constant order is the spec's declaration order. Relaxation sets are `Set<Relaxation>` backed by an `EnumSet`
wrapped with `Collections.unmodifiableSet`, which keeps declaration-order iteration (`Set.copyOf` doesn't, so it
MUST NOT be used).

```java
public final class Strictness {
    public static final Strictness STRICT;    // allows nothing
    public static final Strictness LENIENT;   // allows every format Relaxation, not WITHDRAWN

    public static Strictness allowing(Relaxation... relaxations);
    public static Strictness allowing(Set<Relaxation> relaxations);

    public Strictness with(Relaxation relaxation);       // returns a new Strictness
    public Strictness without(Relaxation relaxation);    // returns a new Strictness
    public boolean allows(Relaxation relaxation);
    public Set<Relaxation> allowed();                    // unmodifiable, declaration order
}
```

Usage: `Country.parseAlpha2(input, Strictness.allowing(Relaxation.ASCII_CASE))`,
`Strictness.LENIENT.without(Relaxation.NUMERIC_PADDING)`, or, for reading stored data,
`Strictness.LENIENT.with(Relaxation.WITHDRAWN)`.

`Strictness` is a final class rather than a record, so it can defensively copy its set and keep its constructor
private. It implements `equals`/`hashCode` on the allowed set and `toString` as, e.g., `Strictness[ASCII_CASE, DASH]`.

```java
public enum HistoryState { SAME, OTHER, WITHDRAWN, UNASSIGNED, UNRECORDED, PREDATES_STANDARD }
public enum HistoryPolicy { EXACT, STRICT, PESSIMISTIC, OPTIMISTIC, PERMISSIVE }

public record HistoryCheck(Set<HistoryState> states, HistoryPolicy policy, boolean passed) {}
public record Match<T>(T entry, Set<Relaxation> relaxations, Optional<HistoryCheck> history) {}
public record Validation<T>(boolean isValid, Optional<Match<T>> match) {}
```

- The records' canonical constructors reject `null` and copy sets into unmodifiable `EnumSet` views.
  `HistoryCheck` rejects a `passed` value inconsistent with `policy` and `states`; `Validation` rejects
  `isValid == true` without a passing match.
- Only the library constructs them. Their constructors are public because records require it, but callers have no
  reason to.

Implementation notes:

- ASCII case folding MUST NOT use `String.toUpperCase()`/`toLowerCase()` without `Locale.ROOT`, and SHOULD use
  explicit ASCII range checks.
- Whitespace trimming follows the spec's Unicode `White_Space` definition. `String.strip()` uses
  `Character.isWhitespace`, which differs (it excludes U+00A0, for one), so it MUST NOT be used on its own.
- The matching logic (`Relaxation`, `Strictness`, `Match`, `Validation` and a package-private matcher) is fixed code,
  not data. The emitter writes it out from a template, so the output repo still contains only generated files.

## 7. Failure

```java
public abstract sealed class CodeRejectedException extends IllegalArgumentException
        permits UnknownCodeException, MeaningChangedException {
    public String standard();       // "3166-1"
    public String field();          // "alpha_2"
    public String input();          // exactly as passed in
    public Strictness strictness();
}

public final class UnknownCodeException extends CodeRejectedException {}

public final class MeaningChangedException extends CodeRejectedException {
    public Object entry();          // the entry that matched today; Object because Java forbids generic exceptions
    public LocalDate writtenAt();
    public HistoryCheck history();
}

```

- Unchecked, extending `IllegalArgumentException` as the spec recommends. Callers catch `CodeRejectedException` for
  any rejected code, or `UnknownCodeException` / `MeaningChangedException` specifically, without catching their own
  argument bugs.
- Message: `"XX" is not a known ISO 3166-1 alpha_2 code`, with the input escaped as a Java string literal.
- A `null` input, strictness, relaxation, `writtenAt` (in the overloads that take one) or `HistoryPolicy` to any operation throws `NullPointerException` from
  `Objects.requireNonNull(value, "<parameter name>")`. Lookups MUST NOT pass `null` to `Map.get`, which would
  quietly return "absent".

## 8. Generated file conventions

- Every file starts with:
  ```java
  // SPDX-License-Identifier: LGPL-2.1-or-later
  // Generated from Debian iso-codes 4.20.1. Do not edit.
  ```
- Every public type and member has Javadoc, including `@param`, `@return` and `@throws`. `javadoc` MUST run with no
  warnings.
- Generated code MUST compile with `-Xlint:all -Werror`.
- Formatting is whatever JavaPoet produces; it isn't hand-tuned.

## 9. Conformance gaps

What the current emitter output is missing, against this document and the spec. Conformance is checked by
`src/test/java/isocodes/SpecConformanceTest.java`, which runs every example in spec §6.5.

| Gap | Spec |
|-----|------|
| No withdrawn entries, lifecycle accessors (`isWithdrawn()`, date ranges, `recordedSince()`), `allIncludingWithdrawn()`, or `@Deprecated` constants. `Relaxation.WITHDRAWN` exists but never matches anything yet. Needs the history work in docs/sources.md §9. | §4.1, §5.1 |
| No `written_at` history checks: no `HistoryState`, `HistoryPolicy`, `HistoryCheck`, `MeaningChangedException`, `writtenAt` overloads, or `history` component on `Match`. Same dependency. | §6.7, §7.1a |
| No `Automatic-Module-Name` in the JAR manifest. That's set in the output repo's build, which hasn't been updated yet. | §1 here |

Conforming: strictness and relaxations (format relaxations, `STRICT`/`LENIENT`), `from`/`parse`/`isValid` with
their detailed forms, `UnknownCodeException` (via `CodeRejectedException`), null rejection, `all()` on every type,
`toString()` as the canonical code, field accessors, and `IsoCodes.VERSION`/`SOURCES`/`LICENSE`.
