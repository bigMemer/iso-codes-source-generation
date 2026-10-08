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
3. **Strict by default, lenient on request.** The ISO standards are exact about their codes. The library is exact
   by default and lets callers relax specific rules explicitly (see [§6](#6-strictness)).
4. **Nothing can fail at runtime except parsing.** Data integrity is checked when the library is generated. A
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
| **Canonical form** | A code exactly as the standard (and the model) spells it, e.g. `DE`, `deu`, `Latn`, `004`, `US-CA`. |
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

**On each standard, for each code field `f`:**

| Operation | Returns | Behaviour |
|-----------|---------|-----------|
| `from_f(input, strictness = STRICT)` | maybe an entry | The entry whose `f` matches `input` under `strictness`, or absent. Never fails for non-null input. |
| `parse_f(input, strictness = STRICT)` | an entry | Same match, but no match is a failure ([§7](#7-failure)). |
| `is_valid_f(input, strictness = STRICT)` | boolean | Whether a match exists. |

These three MUST agree. For every input and strictness:
`is_valid_f(x, s)` is true ⇔ `from_f(x, s)` is present ⇔ `parse_f(x, s)` succeeds, and when they succeed they return
the same entry.

`strictness` MUST be optional, defaulting to `STRICT`. In languages without default arguments, provide both forms
(overloads, or a separately named variant documented by the binding).

Operations exist only for code fields. Non-code fields (such as `english_name`) MUST NOT get `from_`/`parse_`
operations, because their values aren't guaranteed unique.

**On each entry:**

| Operation | Returns | Behaviour |
|-----------|---------|-----------|
| string conversion | string | The canonical primary code (`DE`, `deu`, `US-CA`). Whatever the language uses as an entry's default string form (`toString`, `__str__`, `Display`) MUST produce this. |

Round trip: for every entry `e`, `parse_<primary>(to_string(e), STRICT)` MUST return `e`.

### 5.4 Dataset information

Each library MUST expose, as constants in its root namespace:

| Concept name     | Example |
|------------------|---------|
| `source_name`    | `Debian iso-codes` |
| `source_version` | `4.20.1` |
| `source_license` | `LGPL-2.1-or-later` |

## 6. Strictness

Matching an input against a code field compares the input with each entry's canonical form. Strictness controls
which differences are tolerated. It's made of independent **components**, each with an exact setting and one
relaxed setting:

| Component         | Exact setting       | Relaxed setting      | What the relaxed setting does |
|-------------------|---------------------|----------------------|-------------------------------|
| `case`            | `exact`             | `ignore_ascii_case`  | ASCII letters match regardless of case. |
| `dashes`          | `hyphen_minus_only` | `any_dash`           | Dash-like characters in the input count as `-`. |
| `whitespace`      | `exact`             | `trim`               | Leading and trailing whitespace in the input is ignored. |
| `numeric_padding` | `exact`             | `pad_zeros`          | Numeric codes given without leading zeros are accepted. |

Two presets MUST be provided:

- **`STRICT`**: every component exact. The default everywhere.
- **`LENIENT`**: every component relaxed.

Callers MUST be able to build any other combination, e.g. "strict except for case". Strictness values MUST be
immutable. The binding chooses the mechanism (a record with enum fields, flags, a builder) but MUST keep the
component and setting names above, adapted only in case and spelling.

### 6.1 Matching algorithm

Given an input string and a strictness, a binding MUST behave as if it did the following. Implementations may
precompute or reorder steps as long as results are identical.

1. **Whitespace.** If `whitespace` is `trim`, remove leading and trailing characters with the Unicode
   `White_Space` property (this includes U+00A0 NO-BREAK SPACE). Interior whitespace is never removed.
2. **Dashes.** If `dashes` is `any_dash`, replace each of these characters with U+002D HYPHEN-MINUS:

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

   Underscores, spaces, slashes and other separators are not dashes and are never converted.
3. **Numeric padding.** If `numeric_padding` is `pad_zeros` and the field is a numeric field (`numeric`), and the
   input consists only of ASCII digits and is shorter than the field's width (3 for every current numeric field),
   left-pad it with `0` to that width. Longer inputs are not truncated, so `0004` doesn't match `004`. This
   component has no effect on other fields, including digits inside ISO 3166-2 codes (`AD-2` never matches
   `AD-02`).
4. **Case.** If `case` is `ignore_ascii_case`, compare with ASCII letters `A`–`Z` and `a`–`z` treated as equal.
   Only ASCII letters are folded. The comparison MUST NOT depend on the process locale; Turkish dotted and dotless
   `i` in particular are never equated with ASCII `i`.
5. **Compare** the result with each entry's canonical value for the field. Equal means match.

Under every strictness at most one entry can match. The generator MUST fail the build if, under `LENIENT`, two
entries' values for the same code field would become indistinguishable. (Current data has no such collisions; see
[§10](#10-implementation-status).)

The result of a successful match is always the entry. Its accessors return canonical forms, so lenient parsing
doubles as normalisation: `parse_alpha_2(" de ", LENIENT).alpha_2` is `"DE"`.

### 6.2 Examples

| Operation | Input | `STRICT` | `LENIENT` |
|-----------|-------|----------|-----------|
| `Country.from_alpha_2` | `"DE"` | DE | DE |
| `Country.from_alpha_2` | `"de"` | absent | DE |
| `Country.from_alpha_2` | `" DE\n"` | absent | DE |
| `Country.from_numeric` | `"4"` | absent | AF |
| `Country.from_numeric` | `"0004"` | absent | absent |
| `Subdivision.from_code` | `"US–CA"` (en dash) | absent | US-CA |
| `Subdivision.from_code` | `"us_ca"` | absent | absent |
| `Script.from_alpha_4` | `"latn"` | absent | Latn |
| `LanguagePart2.from_alpha_3` | `"QAA—QTZ"` (em dash) | absent | qaa-qtz |
| `LanguagePart2.from_alpha_3` | `"qab"` | absent | absent |
| `Country.from_alpha_2` | `""` | absent | absent |

`qaa-qtz` is a single entry representing the range reserved for local use. Individual codes in the range are not
entries and don't match.

## 7. Failure

There are exactly two kinds of failure, and bindings MUST keep them distinguishable.

### 7.1 Unknown code

Raised only by `parse_f` when nothing matches. It is an expected outcome caused by data, not a bug in the caller.

- Its concept name is **`UnknownCode`**, adapted to the language's convention for error types
  (`UnknownCodeException`, `UnknownCodeError`, ...).
- It MUST carry: the standard (e.g. `3166-1`), the field id (e.g. `alpha_2`), the input exactly as given (before any
  normalisation), and the strictness used.
- Its message SHOULD read `"<input>" is not a known ISO <standard> <field> code`, e.g.
  `"XX" is not a known ISO 3166-1 alpha_2 code`. Inputs SHOULD be escaped so control characters are visible.
- Where the language reports failure through return values (`Result`, `(value, error)`), `parse_f` MUST use that
  mechanism. Where it uses exceptions, `UnknownCode` MUST be a dedicated type that callers can catch without also
  catching programming errors. If the language has a conventional "bad argument" exception, `UnknownCode` SHOULD
  derive from it.

`from_f` and `is_valid_f` MUST NOT report unknown codes as failures; they return absent and false.

### 7.2 Programming errors

Misusing the API is a bug in the caller and is reported with the language's standard mechanism for invalid
arguments, never as `UnknownCode`:

- **Null input.** In languages where a string argument can be null, every operation (including `from_f` and
  `is_valid_f`) MUST reject null with the language's standard null/argument error. Null is not "an unknown code":
  `is_valid_f(null)` fails, it does not return false.
- **Null strictness**, where possible, is rejected the same way.

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

- [ ] Fail the build on lenient-match collisions ([§6.1](#61-matching-algorithm)). Not yet checked by
  `IsoCodesDataset` validation, though current data has none.
- [ ] Expose `source_license` and `source_name` from the model to emitters as dataset constants (the model has
  them; emitters don't output them yet).

Each binding document lists its own conformance gaps.

## 11. Out of scope for spec version 1

Candidates for later versions, deliberately unspecified for now:

- **Relationships** between entries: a subdivision's country and parent subdivision, ISO 639-2 ↔ 639-3
  cross-references, a former country's successors.
- **Parsing across code fields** (`Country.parse("DEU")` trying every code field). Code fields are currently
  disjoint per standard, but that isn't guaranteed.
- **Other separators** (`_`, space) in ISO 3166-2 codes.
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
6. Strictness API
7. Failure types
8. Generated file conventions (headers, documentation comments, lint cleanliness)
9. Conformance gaps: every way the current output falls short of this spec
