# Generated library specification

**Status:** draft, spec version 1. **Applies to:** every language this generator emits.
**Scope:** ISO 3166-1 (countries) and ISO 3166-2 (country subdivisions) only.

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
| **Canonical form** | The one spelling of a code the library outputs, defined per field in [§6.1](#61-canonical-form): `DE`, `DEU`, `004`, `US-CA`. |
| **Active / withdrawn entry** | An entry whose code ISO currently assigns, or one ISO has withdrawn. Withdrawn entries stay in the library ([§4.1](#41-withdrawn-entries)). |
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

The table is a snapshot; the model's `StandardDef`s are authoritative. Bindings MUST derive fields, code fields and
primary codes from the model, never from a hand-maintained list.

Types SHOULD be grouped under a standard-family namespace (`iso3166`) using the language's
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
   ordering (e.g. by declaration), it MUST equal source order. Withdrawn entries come after all active ones.

**Large standards.** Some languages limit how many members an enumeration can have. ISO 3166-2 has over 5,000
entries. A binding MAY use a different construct for a standard that doesn't fit (for
example a class with a private constructor and static instances), and MAY omit named members for it. It MUST still
satisfy rules 1, 2, 4 and 5 and provide every operation in [§5.3](#53-operations). The binding document MUST say
which standards are affected and why.

### 4.1 Withdrawn entries

When ISO withdraws a code, its entry is **not removed**. It stays a member of its type, marked withdrawn
([§5.1](#51-field-accessors)), so data stored under the old code still resolves to a typed value, and code that
refers to the member keeps compiling. Bindings in languages with a deprecation mechanism (Java's `@Deprecated`,
C#'s `[Obsolete]`, Rust's `#[deprecated]`) MUST apply it to withdrawn members, so consumers see a warning where they
reference one.

The library's history is best-effort: it covers codes withdrawn since its sources began recording (2015-2016 for
subdivisions; earlier for countries, via ISO 3166-3). Codes withdrawn before that are simply unknown.

**Reused codes.** ISO occasionally reassigns a withdrawn code to something else. `CS` was Czechoslovakia (withdrawn
1993), then Serbia and Montenegro (withdrawn 2006). `AI` was the French Afars and Issas and is now Anguilla. So:

- A primary code identifies **at most one entry**: the active holder if there is one, otherwise the most recently
  withdrawn holder. Earlier holders are not represented. `CS` is Serbia and Montenegro; `AI` is Anguilla.
- When a withdrawn and an active entry share a value in another code field (ISO 3166-1 `alpha_3` `ATF` belonged to
  both the withdrawn French Southern and Antarctic Territories and today's French Southern Territories), matching
  prefers the active entry ([§6.4](#64-matching-algorithm)).

### 4.2 Member names

**Member names** are derived from the entry's primary code:

1. Convert ASCII letters to upper case.
2. Replace every character that isn't an ASCII letter or digit with `_`.
3. If the result starts with a digit, prefix `_`.

So `DE` → `DE`, and a hypothetical subdivision member for `US-CA` would be `US_CA`. A binding MAY adapt this to a mandatory
language convention (and MUST then document the rule), but the result MUST be deterministic, MUST be unique within
the type (the generator fails the build otherwise), and MUST NOT change between source versions for the same code.

## 5. Fields and operations

### 5.1 Field accessors

Every field in the model's definition MUST be exposed as a read-only accessor on each entry, named after the field
id in the language's convention, except:

- The model field `name` MUST be exposed as **`english_name`**. The names are English (translations ship
  separately upstream), and `name` collides with built-in enumeration members in many languages.

Every entry MUST also expose its lifecycle:

| Accessor | Type | Meaning |
|----------|------|---------|
| `is_withdrawn` | boolean | Whether ISO has withdrawn this entry's code. |
| `assigned_earliest`, `assigned_latest` | maybe a date each | The range of days on which ISO assigned the code to this entry. |
| `withdrawn_earliest`, `withdrawn_latest` | maybe a date each | The range of days on which ISO withdrew it. Both absent for active entries. |

Dates in the library's history are known to varying precision, so each is exposed as a range of days rather than a
single date:

| Known as | `…_earliest` | `…_latest` |
|----------|--------------|------------|
| An exact day, e.g. 2006-09-26 | 2006-09-26 | 2006-09-26 |
| A year, e.g. 1993 | 1993-01-01 | 1993-12-31 |
| Only "no later than" a source release, e.g. 2021-10-27 | absent | 2021-10-27 |
| Unknown | absent | absent |

The library never collapses a range into a single guessed date. Callers who want to apply their own rules, e.g. a
grace period after withdrawal, compare their own clock against whichever end suits them.

A withdrawn entry's other fields hold their last known values.

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
| `all()` | ordered collection of entries | Every **active** entry, in source order ([§4](#4-entries-are-enumerations)). The collection MUST be immutable or a fresh copy; callers can't change the library's data through it. |
| `all_including_withdrawn()` | ordered collection of entries | As `all()`, followed by every withdrawn entry. |

**On each standard, for each code field `f`**, three operations, each in a plain and a detailed form:

| Operation | Returns | Behaviour |
|-----------|---------|-----------|
| `from_f(input, strictness = STRICT, written_at = absent, history = PESSIMISTIC)` | maybe an entry | The entry whose `f` matches `input` under `strictness` ([§6](#6-ambiguities-canonical-form-and-strictness)), or absent. If `written_at` is given, also absent when the history check fails ([§6.7](#67-checking-against-when-a-value-was-written)). Never fails for non-null input. |
| `parse_f(…same…)` | an entry | Same, but no match is an `UnknownCode` failure and a failed history check is a `MeaningChanged` failure ([§7](#7-failure)). |
| `is_valid_f(…same…)` | boolean | Whether `from_f` would return an entry. |
| `from_f_detailed(…same…)` | maybe a `Match` | Present whenever the code matches today, **even if the history check fails**, so the caller can see why. |
| `parse_f_detailed(…same…)` | a `Match` | As `from_f_detailed`, but no match is an `UnknownCode` failure. A failed history check is not a failure here; it's reported in the `Match`. |
| `is_valid_f_detailed(…same…)` | a `Validation` | As `is_valid_f`, with the details. |

The result shapes:

| Concept name | Fields | Rules |
|--------------|--------|-------|
| `Match` | `entry`: the matched entry; `relaxations`: set of `Relaxation`; `history`: maybe a `HistoryCheck` | `relaxations` is exactly the set of relaxations that were materially needed ([§6.4](#64-matching-algorithm)). Empty means the input was already canonical. `history` is present exactly when `written_at` was given. |
| `HistoryCheck` | `states`: set of `HistoryState`; `policy`: the `HistoryPolicy` applied; `passed`: boolean | See [§6.7](#67-checking-against-when-a-value-was-written). |
| `Validation` | `is_valid`: boolean; `match`: maybe a `Match` | `match` is present whenever the code matches today. `is_valid` is true when it's present and its history check, if any, passed. |

`Relaxation` ([§6.2](#62-ambiguities)), `HistoryState` and `HistoryPolicy` ([§6.7](#67-checking-against-when-a-value-was-written)) are
enumerations. Sets of them MUST be immutable and MUST iterate in declaration order.

`written_at` is a calendar date (no time, no time zone). `history` is ignored when `written_at` is absent.

All six MUST agree. For the same arguments:

- `is_valid_f` ⇔ `from_f` is present ⇔ `parse_f` succeeds ⇔ `is_valid_f_detailed(…).is_valid`;
- `from_f_detailed` is present ⇔ `parse_f_detailed` succeeds ⇔ `is_valid_f_detailed(…).match` is present
  ⇔ the code matches today, regardless of history;
- every form that returns an entry or a `Match` agrees on the entry, `relaxations` and `history`.

`strictness` MUST be optional, defaulting to `STRICT`. In languages without default arguments, provide both forms
(overloads, or a separately named variant documented by the binding).

Operations exist only for code fields. Non-code fields (such as `english_name`) MUST NOT get `from_`/`parse_`
operations, because their values aren't guaranteed unique.

### 5.4 Formatting: entry to string

Turning an entry back into a string MUST always produce the **canonical form** ([§6.1](#61-canonical-form)), which
by definition satisfies `STRICT`. There is no option to format any other way.

- **Per code field:** the field's accessor is the formatter. `e.alpha_3` returns `"DEU"`, `e.numeric` returns
  `"276"`. Should a code field ever be optional, its accessor returns absent when the entry has no such code.
- **Default string conversion:** whatever the language uses as an entry's default string form (`toString`,
  `__str__`, `Display`) MUST return the canonical primary code: `DE`, `US-CA`.
- The **member name** is an identifier, not a string form of the code. For `Country` it happens to equal the
  canonical `alpha_2`, but bindings MUST NOT rely on that: default string conversion MUST be defined as the canonical
  primary code, not as the member name ([§6.2](#62-ambiguities), `member_name`).

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
| `history_recorded_since`, per standard | the date the library's history is complete from ([§6.7](#67-checking-against-when-a-value-was-written)) |

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

In short: **upper-case ASCII letters, U+002D as the only dash, no whitespace, numeric codes always 3 digits.** ISO
3166 defines every code in upper case, so output is always upper case and there is no per-standard case to choose.

Code values are always pure ASCII. If a future source version breaks any rule in this table, the generator fails
the build; it never emits a non-canonical value.

### 6.2 Ambiguities

Every known way input can differ from a canonical form, by name. **Relaxable** ambiguities are the values of the
`Relaxation` enumeration, which every binding MUST generate with exactly these members, in this order, named per the
language's enum member convention:

| `Relaxation` member | Ambiguity | Accepted input when allowed | Canonical output |
|---------------------|-----------|-----------------------------|------------------|
| `ASCII_CASE` | Letter case | ASCII letters in either case: `de`, `De`, `us-ca` | Upper case |
| `DASH` | Dash character | Any of the dashes in [§6.4](#64-matching-algorithm) step 2 in place of U+002D: `US–CA`, `US—CA` | U+002D |
| `WHITESPACE` | Surrounding whitespace | Leading and trailing Unicode `White_Space`: ` DE`, `DE\n`, ` DE` | None |
| `NUMERIC_PADDING` | Missing leading zeros on numeric codes | Fewer than 3 digits: `4`, `04` | 3 digits: `004` |
| `WITHDRAWN` | A code that was once valid but has been withdrawn | `CS`, `IN-OR` | The withdrawn entry's own code (`CS`), unchanged |

The first four are **format** relaxations: they're about how a code was written. `WITHDRAWN` is a **lifecycle**
relaxation: it changes which entries can match at all.

**Non-relaxable** ambiguities are named so bindings agree on them, but spec version 1 never accepts them, under any
strictness:

| Name | Example inputs (never match) | Why not relaxable |
|------|------------------------------|-------------------|
| `separator` | `US_CA`, `US CA`, `US/CA`, `USCA` | Which separators to accept is an open question (§11). |
| `interior_whitespace` | `D E`, `US - CA` | Almost always a sign of a different, malformed value. |
| `excess_padding` | `0004`, `00276` | Ambiguous with a longer code; padding only ever adds zeros. |
| `numeric_sign` | `+004`, `-4` | Codes aren't numbers. |
| `non_ascii_lookalike` | `ＤＥ` (fullwidth), `DЕ` (Cyrillic Е), `٠٠٤` (Arabic-Indic digits) | Requires Unicode normalisation or confusable detection; deferred. |
| `member_name` | `US_CA` | A language identifier, not a code; it's the `separator` ambiguity in another guise. (For `Country`, member names equal `alpha_2` codes, so they match as codes, not as member names.) |
| `wrong_field` | `DEU` passed to `from_alpha_2` | Each operation matches one field (§11). |

Adding a relaxable ambiguity is a spec change: it adds a `Relaxation` member, which must then be added to every
binding.

### 6.3 Strictness

A **strictness** is the set of `Relaxation`s a caller allows. Bindings MUST provide:

- **`STRICT`**: the empty set. The default everywhere.
- **`LENIENT`**: every **format** relaxation (`ASCII_CASE`, `DASH`, `WHITESPACE`, `NUMERIC_PADDING`), but **not**
  `WITHDRAWN`. Loosening how input may be written must never quietly start accepting withdrawn codes. Callers who
  want both say so: `LENIENT` with `WITHDRAWN` added.
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
4. **Candidates.** The candidate entries are the active entries, plus the withdrawn entries if `WITHDRAWN` is
   allowed.
5. **Exact comparison.** Candidates whose canonical value for the field equals the string match.
6. **`ASCII_CASE`.** If nothing matched and `ASCII_CASE` is allowed, compare again with ASCII `A`–`Z` and `a`–`z`
   treated as equal. Only ASCII letters fold; the comparison MUST NOT depend on the process locale. If a candidate
   matches here, `ASCII_CASE` is *observed*.
7. **Selection.** If several candidates matched (only possible through reused codes, [§4.1](#41-withdrawn-entries)),
   select the active one if any, otherwise the one withdrawn most recently. If nothing matched, there's no match.
8. **`WITHDRAWN`** is *observed* if the selected entry is withdrawn.

The `relaxations` of a `Match` or `Validation` are exactly the relaxations *observed* above. Allowed but unneeded
relaxations are never reported: `" DE"` under `LENIENT` reports `{WHITESPACE}`, and `"DE"` under `LENIENT` reports
`{}`.

Two properties follow, and binding tests SHOULD check them:

- **Subset:** reported relaxations are always a subset of the strictness used.
- **Necessity and sufficiency:** matching the same input with strictness equal to exactly the reported set succeeds
  with the same entry, and removing any one member from that set makes it fail.

Under every strictness at most one entry is selected, and among active entries at most one can match. The generator MUST fail the build if, under `LENIENT`, two active
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
| `Country.from_alpha_3` | `"Deu"` | absent | DE, {`ASCII_CASE`} |
| `Subdivision.from_code` | `"US - CA"` | absent | absent (`interior_whitespace`) |
| `Country.from_alpha_2` | `"ＤＥ"` (fullwidth) | absent | absent (`non_ascii_lookalike`) |
| `Country.from_alpha_2` | `""` | absent | absent |

Withdrawn codes (`STRICT` + `WITHDRAWN` written as `STRICT+W`):

| Operation | Input | `STRICT` | `STRICT+W` → entry, relaxations | `LENIENT` | `LENIENT+W` |
|-----------|-------|----------|---------------------------------|-----------|-------------|
| `Country.from_alpha_2` | `"CS"` | absent | CS (Serbia and Montenegro, withdrawn 2006), {`WITHDRAWN`} | absent | CS, {`WITHDRAWN`} |
| `Country.from_alpha_2` | `"cs"` | absent | absent | absent | CS, {`ASCII_CASE`, `WITHDRAWN`} |
| `Country.from_alpha_2` | `"AI"` | AI (Anguilla), {} | AI (Anguilla), {} | AI, {} | AI, {} |
| `Country.from_alpha_3` | `"ATF"` | TF, {} | TF, {} (the active holder wins) | TF, {} | TF, {} |
| `Subdivision.from_code` | `"IN-OR"` | absent | IN-OR (withdrawn 2023), {`WITHDRAWN`} | absent | IN-OR, {`WITHDRAWN`} |
| `Subdivision.from_code` | `"IN-XX"` | absent | absent | absent | absent |

Relaxation sets are listed in `Relaxation` declaration order. Formatting the matched entry always gives the
canonical form: every LENIENT row above formats back to `DE`, `DEU`, `004` or `US-CA`.

### 6.6 Validating input and checking stored data (informative)

The lifecycle relaxation exists to support this pattern. It flags withdrawn codes; deciding what to do about them is
the consumer's job, and the library carries no hints about what replaced a withdrawn code.

1. **Accept new input** with `STRICT`, or `LENIENT` for messy input. Withdrawn codes are rejected.
2. **Read stored data** with `WITHDRAWN` added (`STRICT+W` or `LENIENT+W`), so records stored before a code was
   withdrawn still load as typed values.
3. **After upgrading the library**, scan stored data with a detailed operation and `WITHDRAWN` allowed:

   | Result | Meaning |
   |--------|---------|
   | Match, `WITHDRAWN` not reported | Still valid. |
   | Match, `WITHDRAWN` reported | Was valid, has since been withdrawn: needs the consumer's own migration. `withdrawn_earliest`/`withdrawn_latest` say since when, as far as known. |
   | No match | Never a known code, e.g. a typo, or a code withdrawn before the library's history begins. |

If the consumer knows when each value was stored, passing it as `written_at` also checks whether the code meant
something else at that time ([§6.7](#67-checking-against-when-a-value-was-written)). Without `written_at`, a code ISO
reassigned after the value was stored scans as valid even though its meaning changed.

### 6.7 Checking against when a value was written

`written_at` answers one question: **did this code mean something different, but valid, when it was written?**
Matching itself is unchanged (it's always against the library's current state, [§6.4](#64-matching-algorithm)); the
history check runs on the entry that matched.

#### States

Every binding MUST generate `HistoryState` with exactly these members, in this order:

| `HistoryState` | Meaning at `written_at` |
|----------------|-------------------------|
| `SAME` | The code meant the matched entry. |
| `OTHER` | The code meant a different entry, valid at the time. |
| `WITHDRAWN` | The code meant nothing: its previous holder had been withdrawn. |
| `UNASSIGNED` | The code meant nothing: it had never been assigned. |
| `UNRECORDED` | `written_at` is before the library's recorded history for this standard begins. Always reported alone. |

The check produces the **set of states the code could have been in** on `written_at`:

1. Each code has a timeline of segments: unassigned, then held by an entry, possibly withdrawn, possibly held by
   another entry, and so on. Each boundary between segments has a date range ([§5.1](#51-field-accessors)).
2. If `written_at` is before the standard's `history_recorded_since` date ([§5.5](#55-dataset-information)), the
   result is `{UNRECORDED}`.
3. Otherwise, the result is the set of states of every segment `written_at` could fall in, taking every possible
   date of every boundary within its range (keeping the boundaries in order). A segment held by the matched entry
   gives `SAME`; held by any other entry, `OTHER`; a gap after a holder, `WITHDRAWN`; before any holder,
   `UNASSIGNED`.

A single state is certain. Several states mean the dates can't tell them apart; they're always neighbouring
segments. Each pair arises from one boundary with an imprecise date:

| States | Arises when |
|--------|-------------|
| `{UNASSIGNED, SAME}` | The matched entry's assignment date is imprecise. |
| `{UNASSIGNED, OTHER}` | An earlier holder's assignment date is imprecise. |
| `{SAME, WITHDRAWN}` | The matched entry's withdrawal date is imprecise, or its assignment after a gap is. |
| `{OTHER, WITHDRAWN}` | An earlier holder's withdrawal date is imprecise. |
| `{OTHER, SAME}` | The code passed directly from another holder to the matched entry on an imprecise date. |
| `{UNASSIGNED, WITHDRAWN}` | Never alone: a holder lies between them, so its state is always in the set too. |

Wider ranges spanning several boundaries give larger sets.

#### Policy

Whether the check passes is set by a `HistoryPolicy`, which every binding MUST generate with exactly these members,
in this order. Each passes everything the previous one passes:

| `HistoryPolicy` | Passes when the states are | In words |
|-----------------|----------------------------|----------|
| `EXACT` | exactly `{SAME}` | Only a certain, unchanged meaning. |
| `STRICT` | `{SAME}` or `{UNRECORDED}` | Missing records are acceptable; anything else isn't. |
| `PESSIMISTIC` (default) | any set without `OTHER` | Fail on any possible change of meaning. |
| `OPTIMISTIC` | anything except exactly `{OTHER}` | Fail only on a certain change of meaning. |
| `PERMISSIVE` | anything | Never fail; just report. |

`PESSIMISTIC` is the default because `OTHER` is the only state that means the value silently changed meaning.
`WITHDRAWN` and `UNASSIGNED` mean the value was invalid when written, which is equally likely to be a wrong
`written_at` as wrong data, so by default they don't fail. `{UNRECORDED}` contains no `OTHER` and passes
`PESSIMISTIC` (it must, since `STRICT` passes it).

`HistoryPolicy.STRICT` is unrelated to the `STRICT` strictness preset ([§6.3](#63-strictness)); they're different
types.

#### Examples

Matching with `STRICT` plus `WITHDRAWN`, so withdrawn codes match. ✓ passes, ✗ fails.

| Input | `written_at` | States | `EXACT` | `STRICT` | `PESSIMISTIC` | `OPTIMISTIC` | `PERMISSIVE` |
|-------|--------------|--------|---|---|---|---|---|
| `DE` | 2020-01-01 | `{SAME}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `IN-OR` (withdrawn 2023-11-23) | 2020-01-01 | `{SAME}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `GT-AV` (withdrawn 2021-11-25) | 2022-03-01 | `{WITHDRAWN}` | ✗ | ✗ | ✓ | ✓ | ✓ |
| `SS` (assigned 2011-08-09) | 2005-01-01 | `{UNASSIGNED}` | ✗ | ✗ | ✓ | ✓ | ✓ |
| `CS` (Serbia and Montenegro; was Czechoslovakia until 1993) | 1990-01-01 | `{OTHER}` | ✗ | ✗ | ✗ | ✗ | ✓ |
| a withdrawn code whose withdrawal is known only as "no later than 2021-10-27" | 2020-05-01 | `{SAME, WITHDRAWN}` | ✗ | ✗ | ✓ | ✓ | ✓ |
| a code that passed directly between two holders in a known year | mid-year | `{OTHER, SAME}` | ✗ | ✗ | ✗ | ✓ | ✓ |
| any code | before `history_recorded_since` | `{UNRECORDED}` | ✗ | ✓ | ✓ | ✓ | ✓ |

The library records only *when* earlier holders held a code, nothing else about them. It reports that the meaning
changed, not what it used to be. Like all of the library's history, this is best-effort
([§4.1](#41-withdrawn-entries)).

## 7. Failure

Failures fall into two kinds, rejected data ([§7.1](#71-unknown-code), [§7.1a](#71a-meaning-changed)) and
programming errors ([§7.2](#72-programming-errors)), and bindings MUST keep them distinguishable.

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

### 7.1a Meaning changed

Raised only by `parse_f` when the code matches today but the history check fails ([§6.7](#67-checking-against-when-a-value-was-written)).

- Concept name **`MeaningChanged`**. It MUST carry everything `UnknownCode` carries, plus the matched entry,
  `written_at`, the `HistoryPolicy` and the set of `HistoryState`s.
- Message, SHOULD: `"<input>" may have meant something else on <written_at> (states: <states>)`.
- Where the language uses exceptions, `UnknownCode` and `MeaningChanged` SHOULD share a common parent type for
  "rejected code", so callers can catch both at once.

`parse_f_detailed` doesn't raise it; it returns the `Match`, whose `history` says the check failed.

### 7.2 Programming errors

Misusing the API is a bug in the caller and is reported with the language's standard mechanism for invalid
arguments, never as `UnknownCode`:

- **Null input.** In languages where a string argument can be null, every operation (including `from_` and
  `is_valid_`) MUST reject null with the language's standard null/argument error. Null is not "an unknown code":
  `is_valid_f(null)` fails, it does not return false.
- **Null strictness**, or a strictness containing null, is rejected the same way, as is a null `history` policy.

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
  source version to its newest schema. Data is not stable: entries can appear, be withdrawn or have their values
  changed between versions. Members are never removed: a withdrawn entry stays, marked withdrawn
  ([§4.1](#41-withdrawn-entries)). Consumers should treat every upgrade as
  potentially source-incompatible. The library does not follow semantic versioning.
- Every generated file MUST start with a comment holding the SPDX licence identifier of the source data and the line
  `Generated from <source name> <source version>. Do not edit.`
- Generated libraries are licensed under the source data's licence.

## 10. Implementation status

The generator side of this spec:

- [ ] Fail the build on lenient-match collisions ([§6.4](#64-matching-algorithm)). Not yet checked by
  `IsoCodesDataset` validation, though current data has none.
- [ ] Withdrawn entries and history ([§4.1](#41-withdrawn-entries)). Requires the aggregated sources in
  [sources.md](../sources.md); today's iso-codes-only generator has no withdrawn entries.
- [x] Every code value is canonical ([§6.1](#61-canonical-form)): each code field in the model has a format pattern
  enforced by validation.
- [ ] Check that every ISO 3166-2 code's prefix is an ISO 3166-1 `alpha_2` ([§6.1](#61-canonical-form)). True of all
  current data, but validation only checks the format.
- [ ] Expose `source_license` and `source_name` from the model to emitters as dataset constants (the model has
  them; emitters don't output them yet).

Each binding document lists its own conformance gaps.

## 11. Out of scope for spec version 1

Candidates for later versions, deliberately unspecified for now:

- **Relationships** between entries: a subdivision's country and parent subdivision. With the scope now limited to
  ISO 3166-1/2, this is the most likely next addition.
- **Parsing across code fields** (`Country.parse("DEU")` trying every code field). Code fields are currently
  disjoint per standard, but that isn't guaranteed.
- **More relaxations** for the non-relaxable ambiguities in [§6.2](#62-ambiguities), notably `separator`
  (`US_CA`) and `non_ascii_lookalike` (fullwidth letters).
- **Matching as of a date**, i.e. resolving a code to the holder it had at `written_at` (making earlier holders
  entries). Spec version 1 only reports that the holder changed.
- **A grace-period convenience**, e.g. "accept codes withdrawn in the last six months, relative to a date I pass
  in". Callers can already do this with the withdrawal date ranges ([§5.1](#51-field-accessors)).
- **Translated names.** iso-codes ships gettext translations; the model doesn't carry them.
- **Other standards.** ISO 3166-3 (former countries), ISO 4217, ISO 15924 and ISO 639 were in an earlier draft and
  were removed when the scope narrowed to ISO 3166-1/2.
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
