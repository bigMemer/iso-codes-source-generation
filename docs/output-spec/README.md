# Generated library specification

**Status:** draft, spec version 1. **Applies to:** every language this generator emits.

This document defines what a generated library looks like and how it behaves, independently of any programming
language. Each language has its own document (see [Language bindings](#language-bindings)) that maps these
concepts onto that language's idioms. When a binding document and this one disagree, this one wins, and the binding
document has a bug.

The key words MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are to be read as described in
[RFC 2119](https://www.rfc-editor.org/rfc/rfc2119).

## 1. Goals

1. **The same library in every language.** Someone who knows the Java library should be able to guess the Python
   one: the same types, the same members, the same operations, the same edge-case behaviour. Only spelling and
   idiom differ.
2. **Codes are data, not strings.** Consumers work with typed values (`Country.DE`) and reach for strings only at
   their system's edges, through parsing.
3. **Strict by default, lenient on request, and honest about it.** The ISO standards are exact about their codes.
   The library is exact by default, lets callers allow specific named relaxations, and can report exactly which
   relaxations a match needed (see [§6](#6-ambiguities-canonical-form-and-strictness)).
4. **One canonical output.** Every implementation formats a given entry to the same string, and that string is
   always accepted strictly.
5. **Nothing can fail at runtime except parsing.** Data integrity is checked when the library is generated. A
   generated library performs no I/O, has no failure modes on load, and only reports errors when asked to parse
   input that doesn't match.

## 2. Terminology

| Term | Meaning |
|------|---------|
| **Standard** | One ISO code list, e.g. ISO 3166-1. Each standard becomes one type. |
| **Entry** | One element of a standard, e.g. Germany in ISO 3166-1. |
| **Field** | A property every entry of a standard may have, e.g. `alpha_2`, `name`. Fields come from the model's `StandardDef`. |
| **Required / optional field** | A required field is present on every entry. An optional field may be absent on some. |
| **Code field** | A field whose values are unique across a standard's entries (`StandardDef.uniqueFields`), e.g. `alpha_2`, `alpha_3`, `numeric`. Only code fields can be parsed. |
| **Primary code** | The one required code field that identifies an entry (`StandardDef.primaryKey`). |
| **Canonical form** | The one spelling of a code the library outputs, defined per field in [§6.1](#61-canonical-form): `DE`, `deu`, `Latn`, `004`, `US-CA`. |
| **Ambiguity** | A named way input can differ from canonical form, e.g. letter case ([§6.2](#62-ambiguities)). |
| **Relaxation** | A relaxable ambiguity, as a member of the `Relaxation` enumeration. |
| **Strictness** | The set of relaxations a caller allows for one operation ([§6.3](#63-strictness)). |
| **Binding** | The generated library for one language, plus its document in this directory. |

Field ids are given in the model's `snake_case` (`alpha_2`). Bindings convert them to their own naming convention
(`alpha2`, `Alpha2`, ...).

## 3. Standards and type names

Every binding MUST generate exactly these types, named with the concept name adapted to the language's type naming
convention:

| Standard   | Concept name     | Primary code | Code fields                              |
|------------|------------------|--------------|------------------------------------------|
| ISO 3166-1 | `Country`        | `alpha_2`    | `alpha_2`, `alpha_3`, `numeric`          |
| ISO 3166-2 | `Subdivision`    | `code`       | `code`                                   |
| ISO 3166-3 | `FormerCountry`  | `alpha_4`    | `alpha_4`                                |
| ISO 4217   | `Currency`       | `alpha_3`    | `alpha_3`, `numeric`                     |
| ISO 15924  | `Script`         | `alpha_4`    | `alpha_4`, `numeric`                     |
| ISO 639-2  | `LanguagePart2`  | `alpha_3`    | `alpha_3`, `alpha_2`, `bibliographic`    |
| ISO 639-3  | `Language`       | `alpha_3`    | `alpha_3`, `alpha_2`, `bibliographic`    |
| ISO 639-5  | `LanguageFamily` | `alpha_3`    | `alpha_3`                                |

The table is a snapshot; the model's `StandardDef`s are authoritative. Bindings MUST derive fields, code fields and
primary codes from the model, never from a hand-maintained list.

Types SHOULD be grouped by standard family (`iso3166`, `iso4217`, `iso15924`, `iso639`) using the language's
namespacing mechanism.

## 4. Entries are enumerations

Every standard MUST be exposed as an **enumeration**: a closed set of values, all known when the library was
generated. Concretely:

1. **Closed.** Consumers MUST NOT be able to create new entries. If the language allows construction, constructors
   MUST NOT be public.
2. **Singletons.** Each entry exists exactly once. In languages with reference identity, two references to the same
   entry MUST be identical, so identity and equality agree.
3. **Named members.** Each entry MUST be reachable as a named member of its type, e.g. `Country.DE`, except where a
   binding documents that the language cannot support that many members (see below).
4. **Hashable and comparable for equality.** Entries MUST be usable as keys in the language's standard map/set
   types.
5. **Source order.** Iterating a standard (`all`, [§5.3](#53-operations)) MUST yield entries in the order the
   model holds them, which is the source's order. Bindings MUST NOT sort. Note that source order is not alphabetical
   by primary code; ISO 3166-1, for example, is ordered by `alpha_3`. If the language gives enumerations a natural
   ordering (e.g. by declaration), it MUST equal source order.

**Large standards.** Some languages limit how many members an enumeration can have. ISO 3166-2 has over 5,000
entries and ISO 639-3 has over 7,900. A binding MAY use a different construct for a standard that doesn't fit (for
example a class with a private constructor and static instances), and MAY omit named members for it. It MUST still
satisfy rules 1, 2, 4 and 5 and provide every operation in [§5.3](#53-operations). The binding document MUST say
which standards are affected and why.

**Member names** are derived from the entry's primary code:

1. Convert ASCII letters to upper case.
2. Replace every character that isn't an ASCII letter or digit with `_`.
3. If the result starts with a digit, prefix `_`.

So `DE` → `DE`, `deu` → `DEU`, `Latn` → `LATN`, `qaa-qtz` → `QAA_QTZ`. A binding MAY adapt this to a mandatory
language convention (and MUST then document the rule), but the result MUST be deterministic, MUST be unique within
the type (the generator fails the build otherwise), and MUST NOT change between source versions for the same code.

## 5. Fields and operations

### 5.1 Field accessors

Every field in the model's definition MUST be exposed as a read-only accessor on each entry, named after the field
id in the language's convention, except:

- The model field `name` MUST be exposed as **`english_name`**. The names are English (translations ship
  separately upstream), and `name` collides with built-in enumeration members in many languages.

Accessor values are strings. All codes, including numeric ones, MUST be exposed as strings in canonical form, so
`numeric` for Afghanistan is `"004"`, not `4`. A binding MAY add integer conveniences, but only in addition to the
string accessor.

### 5.2 Absence

- A **required** field's accessor MUST always return a value. It MUST NOT return the language's null, an empty
  string or any other placeholder.
- An **optional** field's accessor MUST return the language's idiomatic "maybe a value" type: an option or
  optional type where the language has one (`Optional<String>`, `Option<String>`), otherwise a nullable type marked
  as such in the type system or its standard annotations (`String?`, `str | None`). Absence MUST NOT be
  represented by an empty string.
- Every present value is non-empty and matches the field's pattern in the model. The generator guarantees this.

### 5.3 Operations

Names below are concept names. Bindings spell them in their own convention (`from_alpha_2` → `fromAlpha2`,
`FromAlpha2`, `from_alpha_2`, ...).

**On each standard:**

| Operation | Returns | Behaviour |
|-----------|---------|-----------|
| `all()` | ordered collection of entries | Every entry in source order ([§4](#4-entries-are-enumerations)). The collection MUST be immutable or a fresh copy; callers can't change the library's data through it. |

**On each standard, for each code field `f`**, three operations, each in a plain and a detailed form:

| Operation | Returns | Behaviour |
|-----------|---------|-----------|
| `from_f(input, strictness = STRICT)` | maybe an entry | The entry whose `f` matches `input` under `strictness` ([§6](#6-ambiguities-canonical-form-and-strictness)), or absent. Never fails for non-null input. |
| `parse_f(input, strictness = STRICT)` | an entry | Same match, but no match is a failure ([§7](#7-failure)). |
| `is_valid_f(input, strictness = STRICT)` | boolean | Whether a match exists. |
| `from_f_detailed(input, strictness = STRICT)` | maybe a `Match` | As `from_f`, plus the relaxations the match needed. |
| `parse_f_detailed(input, strictness = STRICT)` | a `Match` | As `parse_f`, plus the relaxations the match needed. |
| `is_valid_f_detailed(input, strictness = STRICT)` | a `Validation` | As `is_valid_f`, plus the relaxations the match needed. |

The result shapes:

| Concept name | Fields | Rules |
|--------------|--------|-------|
| `Match` | `entry`: the matched entry; `relaxations`: set of `Relaxation` | `relaxations` is exactly the set of relaxations that were materially needed ([§6.4](#64-matching-algorithm)). Empty means the input was already canonical. |
| `Validation` | `is_valid`: boolean; `relaxations`: set of `Relaxation` | When `is_valid` is false, `relaxations` is empty. |

`Relaxation` is an enumeration ([§6.2](#62-ambiguities)). Relaxation sets MUST be immutable and MUST iterate in the
declaration order of `Relaxation`.

All six MUST agree. For every input `x` and strictness `s`:

- `is_valid_f(x, s)` ⇔ `from_f(x, s)` is present ⇔ `parse_f(x, s)` succeeds ⇔ `is_valid_f_detailed(x, s).is_valid`
  ⇔ `from_f_detailed(x, s)` is present ⇔ `parse_f_detailed(x, s)` succeeds;
- when they succeed, they agree on the entry, and all three detailed forms report the same `relaxations`.

`strictness` MUST be optional, defaulting to `STRICT`. In languages without default arguments, provide both forms
(overloads, or a separately named variant documented by the binding).

Operations exist only for code fields. Non-code fields (such as `english_name`) MUST NOT get `from_`/`parse_`
operations, because their values aren't guaranteed unique.

### 5.4 Formatting: entry to string

Turning an entry back into a string MUST always produce the **canonical form** ([§6.1](#61-canonical-form)), which
by definition satisfies `STRICT`. There is no option to format any other way.

- **Per code field:** the field's accessor is the formatter. `e.alpha_3` returns `"DEU"`, `e.numeric` returns
  `"276"`. An optional code field (e.g. `alpha_2` on ISO 639-3) returns absent when the entry has no such code.
- **Default string conversion:** whatever the language uses as an entry's default string form (`toString`,
  `__str__`, `Display`) MUST return the canonical primary code: `DE`, `deu`, `Latn`, `US-CA`.
- The **member name** (`QAA_QTZ`, `LATN`) is an identifier, not a string form of the code. It MUST NOT be what
  default string conversion returns, and it doesn't parse ([§6.2](#62-ambiguities), `member_name`).

Round trips, for every entry `e`, every code field `f` with a value on `e`, and every strictness `s`:

- `parse_f(e.f, s)` returns `e`;
- `parse_f_detailed(e.f, s).relaxations` is empty;
- `parse_<primary>(to_string(e), STRICT)` returns `e`.

### 5.5 Dataset information

Each library MUST expose, as constants in its root namespace:

| Concept name     | Example |
|------------------|---------|
| `source_name`    | `Debian iso-codes` |
| `source_version` | `4.20.1` |
| `source_license` | `LGPL-2.1-or-later` |

## 6. Ambiguities, canonical form and strictness

Real-world input differs from the standard's spelling in predictable ways. This section names every such way
(an **ambiguity**), fixes the one canonical spelling the library outputs, and says which ambiguities callers may
choose to accept.

### 6.1 Canonical form

Every code value has exactly one canonical spelling. Bindings MUST output only canonical forms, and the generator
guarantees every value in the model is canonical: each code field has a format pattern that validation enforces at
generation time.

| Standard | Code field | Canonical form | Example |
|----------|------------|----------------|---------|
| ISO 3166-1 | `alpha_2` | 2 upper-case ASCII letters | `DE` |
| ISO 3166-1 | `alpha_3` | 3 upper-case ASCII letters | `DEU` |
| ISO 3166-1 | `numeric` | 3 ASCII digits, zero-padded | `004` |
| ISO 3166-2 | `code` | country `alpha_2`, U+002D HYPHEN-MINUS, then 1–3 upper-case ASCII letters or digits | `US-CA`, `AD-02` |
| ISO 3166-3 | `alpha_4` | 4 upper-case ASCII letters | `BUMM` |
| ISO 4217 | `alpha_3` | 3 upper-case ASCII letters | `EUR` |
| ISO 4217 | `numeric` | 3 ASCII digits, zero-padded | `978` |
| ISO 15924 | `alpha_4` | 1 upper-case then 3 lower-case ASCII letters (title case) | `Latn` |
| ISO 15924 | `numeric` | 3 ASCII digits, zero-padded | `215` |
| ISO 639-2 | `alpha_3` | 3 lower-case ASCII letters; the reserved range is two such codes joined by U+002D | `deu`, `qaa-qtz` |
| ISO 639-2, 639-3 | `alpha_2` | 2 lower-case ASCII letters | `de` |
| ISO 639-2, 639-3 | `bibliographic` | 3 lower-case ASCII letters | `ger` |
| ISO 639-3, 639-5 | `alpha_3` | 3 lower-case ASCII letters | `deu`, `gem` |

In short: **each standard's own letter case, U+002D as the only dash, no whitespace, numeric codes always 3 digits.**
The case differs by standard because the ISO standards themselves differ (country and currency codes are upper case,
language codes lower case, script codes title case). Bindings MUST NOT normalise output to a single case.

Code values are always pure ASCII. If a future source version breaks any rule in this table, the generator fails
the build; it never emits a non-canonical value.

### 6.2 Ambiguities

Every known way input can differ from a canonical form, by name. **Relaxable** ambiguities are the values of the
`Relaxation` enumeration, which every binding MUST generate with exactly these members, in this order, named per the
language's enum member convention:

| `Relaxation` member | Ambiguity | Accepted input when allowed | Canonical output |
|---------------------|-----------|-----------------------------|------------------|
| `ASCII_CASE` | Letter case | ASCII letters in either case: `de`, `De`, `LATN` | The standard's case (§6.1) |
| `DASH` | Dash character | Any of the dashes in [§6.4](#64-matching-algorithm) step 2 in place of U+002D: `US–CA`, `US—CA` | U+002D |
| `WHITESPACE` | Surrounding whitespace | Leading and trailing Unicode `White_Space`: ` DE`, `DE\n`, ` DE` | None |
| `NUMERIC_PADDING` | Missing leading zeros on numeric codes | Fewer than 3 digits: `4`, `04` | 3 digits: `004` |

**Non-relaxable** ambiguities are named so bindings agree on them, but spec version 1 never accepts them, under any
strictness:

| Name | Example inputs (never match) | Why not relaxable |
|------|------------------------------|-------------------|
| `separator` | `US_CA`, `US CA`, `US/CA`, `USCA` | Which separators to accept is an open question (§11). |
| `interior_whitespace` | `D E`, `US - CA` | Almost always a sign of a different, malformed value. |
| `excess_padding` | `0004`, `00276` | Ambiguous with a longer code; padding only ever adds zeros. |
| `numeric_sign` | `+004`, `-4` | Codes aren't numbers. |
| `non_ascii_lookalike` | `ＤＥ` (fullwidth), `DЕ` (Cyrillic Е), `٠٠٤` (Arabic-Indic digits) | Requires Unicode normalisation or confusable detection; deferred. |
| `member_name` | `QAA_QTZ`, `LATN` (for `Latn`, where case is exact) | A language identifier, not a code. With `ASCII_CASE` allowed, `LATN` matches because of the case relaxation, not because it's a member name. |
| `wrong_field` | `DEU` passed to `from_alpha_2` | Each operation matches one field (§11). |
| `range_member` | `qab` for the `qaa-qtz` entry | The range entry stands for itself; its members aren't entries. |

Adding a relaxable ambiguity is a spec change: it adds a `Relaxation` member, which must then be added to every
binding.

### 6.3 Strictness

A **strictness** is the set of `Relaxation`s a caller allows. Bindings MUST provide:

- **`STRICT`**: the empty set. The default everywhere.
- **`LENIENT`**: every `Relaxation`.
- A way to build any other set, e.g. "strict except `ASCII_CASE`".

Strictness values MUST be immutable. The binding chooses the mechanism (a set type, a wrapper with a builder,
flags) but the members MUST be the `Relaxation` values themselves, so the same enumeration both configures matching
and reports what matching did.

### 6.4 Matching algorithm

Given an input string, a code field and a strictness, a binding MUST behave as if it did the following.
Implementations may precompute or reorder steps as long as both the matched entry and the observed relaxations are
identical.

1. **`WHITESPACE`.** If allowed, remove leading and trailing characters with the Unicode `White_Space` property.
   *Observed* if at least one character was removed.
2. **`DASH`.** If allowed, replace each of these characters with U+002D HYPHEN-MINUS. *Observed* if at least one
   character was replaced.

   | Code point | Name |
   |------------|------|
   | U+2010 | HYPHEN |
   | U+2011 | NON-BREAKING HYPHEN |
   | U+2012 | FIGURE DASH |
   | U+2013 | EN DASH |
   | U+2014 | EM DASH |
   | U+2015 | HORIZONTAL BAR |
   | U+2212 | MINUS SIGN |
   | U+FE58 | SMALL EM DASH |
   | U+FE63 | SMALL HYPHEN-MINUS |
   | U+FF0D | FULLWIDTH HYPHEN-MINUS |

3. **`NUMERIC_PADDING`.** If allowed, and the field is a numeric field (`numeric`), and the string is 1 or 2 ASCII
   digits, left-pad it with `0` to 3 digits. *Observed* if at least one zero was added. Has no effect on other
   fields, including digits inside ISO 3166-2 codes (`AD-2` never matches `AD-02`).
4. **Exact comparison.** If the string equals an entry's canonical value for the field, that entry matches.
5. **`ASCII_CASE`.** Otherwise, if allowed, compare again with ASCII `A`–`Z` and `a`–`z` treated as equal. Only
   ASCII letters fold; the comparison MUST NOT depend on the process locale. If an entry matches here,
   `ASCII_CASE` is *observed*.
6. Otherwise nothing matches.

The `relaxations` of a `Match` or `Validation` are exactly the relaxations *observed* above. Allowed but unneeded
relaxations are never reported: `" DE"` under `LENIENT` reports `{WHITESPACE}`, and `"DE"` under `LENIENT` reports
`{}`.

Two properties follow, and binding tests SHOULD check them:

- **Subset:** reported relaxations are always a subset of the strictness used.
- **Necessity and sufficiency:** matching the same input with strictness equal to exactly the reported set succeeds
  with the same entry, and removing any one member from that set makes it fail.

Under every strictness at most one entry can match. The generator MUST fail the build if, under `LENIENT`, two
entries' values for the same code field would become indistinguishable. Current data has no such collisions
(see [§10](#10-implementation-status)).

### 6.5 Examples

| Operation | Input | `STRICT` | `LENIENT` → entry, relaxations |
|-----------|-------|----------|--------------------------------|
| `Country.from_alpha_2` | `"DE"` | DE | DE, {} |
| `Country.from_alpha_2` | `"de"` | absent | DE, {`ASCII_CASE`} |
| `Country.from_alpha_2` | `" de\n"` | absent | DE, {`ASCII_CASE`, `WHITESPACE`} |
| `Country.from_numeric` | `"4"` | absent | AF, {`NUMERIC_PADDING`} |
| `Country.from_numeric` | `" 04 "` | absent | AF, {`WHITESPACE`, `NUMERIC_PADDING`} |
| `Country.from_numeric` | `"0004"` | absent | absent (`excess_padding`) |
| `Subdivision.from_code` | `"US–CA"` (en dash) | absent | US-CA, {`DASH`} |
| `Subdivision.from_code` | `"us—ca"` (em dash) | absent | US-CA, {`ASCII_CASE`, `DASH`} |
| `Subdivision.from_code` | `"US_CA"` | absent | absent (`separator`) |
| `Script.from_alpha_4` | `"LATN"` | absent | Latn, {`ASCII_CASE`} |
| `LanguagePart2.from_alpha_3` | `"QAA—QTZ"` (em dash) | absent | qaa-qtz, {`ASCII_CASE`, `DASH`} |
| `LanguagePart2.from_alpha_3` | `"qab"` | absent | absent (`range_member`) |
| `Country.from_alpha_2` | `"ＤＥ"` (fullwidth) | absent | absent (`non_ascii_lookalike`) |
| `Country.from_alpha_2` | `""` | absent | absent |

Relaxation sets are listed in `Relaxation` declaration order. Formatting the matched entry always gives the
canonical form: every LENIENT row above formats back to `DE`, `004`, `US-CA`, `Latn` or `qaa-qtz`.

## 7. Failure

There are exactly two kinds of failure, and bindings MUST keep them distinguishable.

### 7.1 Unknown code

Raised only by `parse_f` and `parse_f_detailed` when nothing matches. It is an expected outcome caused by data, not
a bug in the caller.

- Its concept name is **`UnknownCode`**, adapted to the language's convention for error types
  (`UnknownCodeException`, `UnknownCodeError`, ...).
- It MUST carry: the standard (e.g. `3166-1`), the field id (e.g. `alpha_2`), the input exactly as given (before any
  normalisation), and the strictness used.
- Its message SHOULD read `"<input>" is not a known ISO <standard> <field> code`, e.g.
  `"XX" is not a known ISO 3166-1 alpha_2 code`. Inputs SHOULD be escaped so control characters are visible.
- Where the language reports failure through return values (`Result`, `(value, error)`), the parse operations MUST
  use that mechanism. Where it uses exceptions, `UnknownCode` MUST be a dedicated type that callers can catch without
  also catching programming errors. If the language has a conventional "bad argument" exception, `UnknownCode`
  SHOULD derive from it.

The `from_` and `is_valid_` operations MUST NOT report unknown codes as failures; they return absent and false.

### 7.2 Programming errors

Misusing the API is a bug in the caller and is reported with the language's standard mechanism for invalid
arguments, never as `UnknownCode`:

- **Null input.** In languages where a string argument can be null, every operation (including `from_` and
  `is_valid_`) MUST reject null with the language's standard null/argument error. Null is not "an unknown code":
  `is_valid_f(null)` fails, it does not return false.
- **Null strictness**, or a strictness containing null, is rejected the same way.

Any other string, including empty, whitespace-only, very long or non-ASCII strings, is valid input that simply may
not match.

### 7.3 Everything else

- Loading the library and calling accessors, `all()` and string conversion MUST NOT fail.
- The library MUST NOT perform I/O, read environment variables or system properties, or depend on locale or time
  zone.

## 8. Runtime properties

- **Immutable and thread-safe.** All entries, collections and strictness values are immutable. Every operation is
  safe to call concurrently without external synchronisation.
- **Pure.** Operations have no side effects and return the same result for the same arguments.
- **Fast lookups.** `from_f`, `parse_f` and `is_valid_f` SHOULD run in amortised constant time, and MUST NOT be
  slower than logarithmic in the number of entries. Initialisation MAY be lazy.
- **No runtime dependencies** beyond the language's standard library.

## 9. Packaging and versioning

- The library's version MUST equal the source version it was generated from (`4.20.1`). If a library has to be
  re-released for the same source version, the binding document defines a suffix scheme.
- The API shape (types, fields, operations) is stable across source versions, because the model upcasts every
  source version to its newest schema. Data is not stable: entries can appear, disappear or be renamed between
  source versions, and removing an entry removes its named member. Consumers should treat every upgrade as
  potentially source-incompatible. The library does not follow semantic versioning.
- Every generated file MUST start with a comment holding the SPDX licence identifier of the source data and the line
  `Generated from <source name> <source version>. Do not edit.`
- Generated libraries are licensed under the source data's licence.

## 10. Implementation status

The generator side of this spec:

- [ ] Fail the build on lenient-match collisions ([§6.4](#64-matching-algorithm)). Not yet checked by
  `IsoCodesDataset` validation, though current data has none.
- [x] Every code value is canonical ([§6.1](#61-canonical-form)): each code field in the model has a format pattern
  enforced by validation.
- [ ] Check that every ISO 3166-2 code's prefix is an ISO 3166-1 `alpha_2` ([§6.1](#61-canonical-form)). True of all
  current data, but validation only checks the format.
- [ ] Expose `source_license` and `source_name` from the model to emitters as dataset constants (the model has
  them; emitters don't output them yet).

Each binding document lists its own conformance gaps.

## 11. Out of scope for spec version 1

Candidates for later versions, deliberately unspecified for now:

- **Relationships** between entries: a subdivision's country and parent subdivision, ISO 639-2 ↔ 639-3
  cross-references, a former country's successors.
- **Parsing across code fields** (`Country.parse("DEU")` trying every code field). Code fields are currently
  disjoint per standard, but that isn't guaranteed.
- **More relaxations** for the non-relaxable ambiguities in [§6.2](#62-ambiguities), notably `separator`
  (`US_CA`) and `non_ascii_lookalike` (fullwidth letters).
- **Translated names.** iso-codes ships gettext translations; the model doesn't carry them.
- **Shared conformance test vectors**: a language-neutral file of inputs and expected results that every binding's
  tests run against. Strongly recommended before a second binding exists.

## Language bindings

| Language | Document | Status |
|----------|----------|--------|
| Java | [java.md](java.md) | Partially conforming |

A binding document MUST cover, in this order:

1. Packaging (artifact coordinates, minimum language version, module naming)
2. Type and member naming, including any deviation from [§4](#4-entries-are-enumerations)'s member names
3. How enumerations are represented, and which standards use an alternative construct
4. Field accessors and absence
5. Operations, with exact signatures
6. `Relaxation`, strictness, `Match` and `Validation` APIs
7. Failure types
8. Generated file conventions (headers, documentation comments, lint cleanliness)
9. Conformance gaps: every way the current output falls short of this spec
