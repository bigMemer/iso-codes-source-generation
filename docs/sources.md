# Data sources and aggregation

**Status:** design, not yet implemented. **Scope:** ISO 3166-1 and ISO 3166-2.

How the generator combines several imperfect sources into one dataset, and how changes reach the generated
libraries. The model, emitters and [output spec](output-spec/README.md) stay source-agnostic. Everything here
happens before the model sees the data.

## 1. Why aggregate

ISO is the only authority on ISO 3166, and it doesn't license its data for reproduction. Every usable source is a
second-hand copy with its own strengths, measured against each other on 2026-10-07:

| Source | Strength | Weakness | Licence |
|--------|----------|----------|---------|
| **Unicode CLDR** | Usually first to pick up changes. `BS-NP`: ISO added it 2018-11-26, CLDR committed it 2019-02-26 and released it in CLDR 35 on 2019-03-27. Records withdrawn codes with replacements and reasons. Releases on a fixed cadence. | Leaves out 19 ISO codes as a modelling choice. No subdivision type. English display names instead of ISO's names. | Unicode-3.0 |
| **Debian iso-codes** | Faithful to ISO: ISO's names, subdivision types, official names. Trusted to be **right**. | Often **stale**: `BS-NP` arrived 2021-08-27, about 33 months after ISO. | LGPL-2.1-or-later |
| **Wikidata** | Broad, sometimes has what others lack. | Noisy: duplicate items, inconsistent markers, malformed values, withdrawn codes not marked as ended. | CC0 |
| **Overrides** (this repo) | Human-reviewed corrections, each with evidence. | Manual work. | Ours |

Comparing the first release of CLDR and of iso-codes that contained each subdivision code changed in both
(2016 to 2026):

| | CLDR first | iso-codes first | Median delay of iso-codes after CLDR |
|---|---|---|---|
| Codes added (831) | 745 | 86 | 560 days |
| Codes removed (476) | 395 | 81 | 560 days |

Neither source is reliably first. iso-codes shipped the December 2023 changes (47 additions, 49 removals) about 283
days before CLDR 46 did. Aggregation has to take whichever is newer while trusting iso-codes on values.

## 2. Roles

| Role | Source | Meaning |
|------|--------|---------|
| **Backbone** | CLDR | Proposes which codes exist, and is usually the first to signal changes. |
| **Authority** | iso-codes | Wins on values it has. A code it has is confirmed to exist. |
| **Gap filler** | Wikidata | Fills fields neither of the above has. Never adds or removes codes on its own. |
| **Overrides** | This repo | Wins over everything. Used for corrections and for anything the rules below get wrong. |

## 3. Which codes exist

Each source module matches its codes by canonical code string (`US-CA`). CLDR's `usca` becomes `US-CA`. The
aggregator then decides each code's status:

| Case | Example | Result | Flag in the change report |
|------|---------|--------|---------------------------|
| In CLDR and in iso-codes | `DE-BY` | Included, **confirmed** | — |
| In CLDR only, never in any iso-codes release | a code ISO added recently, before iso-codes caught up | Included, **unconfirmed** | Yes, until iso-codes has it |
| In CLDR only, but an earlier iso-codes release had it | the 49 codes iso-codes removed in December 2023, which CLDR kept until CLDR 46 | **Withdrawn**: iso-codes is trusted to be right, and its removal is newer | Yes, until CLDR drops it |
| In iso-codes only, and CLDR marks it `overlong` | the 19: `US-PR`, `FR-NC`, `NL-AW`, `FI-01`, ... | Included, **confirmed**. CLDR's `overlong` means "we use a country-level code instead" (`US-PR` → `PR`), not "ISO withdrew it". | — |
| In iso-codes only, and CLDR marks it `deprecated` | a code ISO withdrew, which CLDR noticed first | **Withdrawn** | Yes, until iso-codes drops it |
| In iso-codes only, and absent from CLDR entirely | the 2023-24 case, where iso-codes was ahead | Included, **confirmed** | Yes, until CLDR has it |
| In Wikidata only | `NO-0501`, `XK` | Not included | Listed for review |
| In overrides | anything | Whatever the override says | — |

CLDR records 626 deprecated subdivision codes: 599 with reason `deprecated` (withdrawals) and 27 with reason
`overlong` (its modelling choice). All 19 ISO codes missing from CLDR are `overlong`, so no hand-maintained list is
needed for them.

ISO 3166-1 uses the same rules: CLDR's territory codes (excluding reserved and user-assigned ones such as `AA` and
`XK`) as backbone, iso-codes as authority.

## 4. Which value each field gets

Highest precedence first. A value only falls through to the next source when the higher one has no value for that
code.

| Field | Precedence | Notes |
|-------|------------|-------|
| Codes (`alpha_2`, `alpha_3`, `numeric`, `code`) | overrides, CLDR, iso-codes | They agree except during transitions; disagreements are flagged. |
| `name` | overrides, iso-codes, CLDR, Wikidata | iso-codes has ISO's spelling. For codes iso-codes doesn't have yet, CLDR's English name is used (decided), so `name` stays required. It may read differently ("Brussels" against ISO's "Bruxelles-Capitale, Région de"), so a fallback name is always flagged. |
| `official_name`, `common_name` (3166-1) | overrides, iso-codes, Wikidata | CLDR has no equivalent. Wikidata's "official name" (`P1448`, English value) is accepted for `official_name` (decided); its values are always flagged. |
| `type` (3166-2) | overrides, iso-codes, Wikidata | CLDR has none. Wikidata's types are its own categories ("province of Spain"), so a Wikidata value is always flagged. |
| `parent` (3166-2) | overrides, iso-codes, CLDR | CLDR has containment lists. |
| `flag` (3166-1) | derived from `alpha_2` | Not taken from any source. |

**Mixed sources.** A code that's still unconfirmed may get its name from CLDR and its type from Wikidata. That's
expected. Every such value is listed in the change report, and the entry's values converge once iso-codes catches
up.

## 5. Provenance and time

The aggregator records, for every entry and every field value:

| Attribute | Meaning |
|-----------|---------|
| `sources` | Which sources attest the value, with each source's version (`CLDR 48.2`, `iso-codes 4.20.1`, `Wikidata 2026-10-07`). |
| `known_at` | Date of the earliest source release or snapshot in which the value appeared. |
| `effective_at` | Date ISO made the change effective, if known (from ISO's change notices via the iso3166-updates change log, or an override), otherwise unknown. |

These live in the model (a new metadata layer alongside each record, not new fields on `Country`/`Subdivision`),
so a future source with its own dates fills them without model changes. Emitters don't have to expose them. They
drive the change report, and `effective_at` makes historical gaps visible rather than hidden: a value whose
`known_at` is much later than its `effective_at` reached our sources late.

## 6. From change to release

```
trigger ──► aggregate ──► model ──► emit per language ──► pull request per output repo ──► review ──► merge ──► tag
                │
                └──► change report (attached to each pull request)
```

**Triggers:**
- a new CLDR release (spring and autumn, plus point releases);
- a new iso-codes tag;
- a weekly snapshot of Wikidata and of the iso3166-updates change log. These only produce a pull request if they
  change the output, and the change log never changes it on its own, only flags it.

**Change report.** Each pull request carries a report of codes added, removed or renamed, every flagged item from
§3 and §4, and every value that came from a fallback source. Reviewers tune the result by editing overrides in
this repo, which regenerates the pull request. Generated files are never edited by hand.

**Releases** are tagged per language repo, independently, once the pull request is merged.

## 7. Versioning

The library version can no longer be one upstream's version.

- **Dataset version:** `YYYY.MM.N`, assigned by this repo when generated output changes, e.g. `2026.10.0`. `N`
  counts revisions within a month, including override-only changes.
- **Language release version:** the dataset version, plus the binding's own re-release suffix when only that
  language's packaging changes (Java: `2026.10.0-r2`).
- **Upstream versions** are recorded in the generated library's dataset information (output spec §5.5), as a list:
  `CLDR 48.2`, `iso-codes 4.20.1`, `Wikidata 2026-10-07`.

## 8. Licensing of the output

Generated libraries contain data from all four sources:

- iso-codes: LGPL-2.1-or-later
- CLDR: Unicode-3.0, which requires shipping Unicode's notice
- Wikidata: CC0, no conditions
- overrides: ours

Our own contributions (overrides, and the structure and code the emitters generate) are dual-licensed
`Apache-2.0 OR MIT`, at the consumer's choice. So the output as a whole is:

```
(Apache-2.0 OR MIT) AND LGPL-2.1-or-later AND Unicode-3.0
```

The per-file SPDX header carries that expression, and each package ships all four licence texts (Apache-2.0, MIT,
LGPL-2.1, and Unicode's notice).

## 9. History

Withdrawn codes and earlier values are kept, best-effort, as part of the dataset rather than dropped. They support
functions that help consumers migrate data (to be specified in the output spec), such as looking up a withdrawn
code's successors.

### 9.1 What is recorded

Per code:

| Attribute | Meaning |
|-----------|---------|
| `status` | `active` or `withdrawn` |
| `effective_from`, `effective_to` | When ISO introduced and withdrew the code, where known (from §5's `effective_at`) |
| `known_from`, `known_to` | When our sources first and last had the code (from §5's `known_at`) |
| `successors` | For a withdrawn code: the codes that replaced it, each with a relation: `renamed_to`, `split_into`, `merged_into`, or `promoted_to_country` (CLDR's `overlong` case, e.g. `US-PR` → `PR`). May be empty, or `unknown_within_country`, when no source says. |
| `predecessors` | The inverse, for active codes. |

Per field value: earlier values with the same interval attributes, e.g. a subdivision's previous names, types and
parents.

Missing dates and successors are explicit (`unknown`), never guessed silently.

### 9.2 Where history comes from

| Source | Gives | Depth |
|--------|-------|-------|
| iso-codes release history | First and last release containing each code and each value | 35 releases, 3.67 (2016) onward |
| CLDR release history | The same, from CLDR's side | 31 releases, CLDR 28 (2015) onward |
| CLDR alias table | Withdrawn codes with replacements and reasons: 599 subdivisions withdrawn (`deprecated`), 27 `overlong`, 27 country codes | Current release, cumulative |
| iso3166-updates change log | ISO's effective dates and change descriptions (free text), 909 entries | Back to the 1990s |
| iso-codes ISO 3166-3 data | Withdrawn country codes with withdrawal dates (31 entries) | Cumulative |
| Wikidata | End-time qualifiers on code statements | Noisy, so it only flags items for review |
| Overrides | Corrections and successor links nobody else provides | — |

Withdrawn ISO 3166-1 codes are exactly what ISO 3166-3 lists, so 3166-3 comes back as history data, not as a
separate standard type.

### 9.3 Successor links

Successors are the weakest part. Of CLDR's 599 withdrawn subdivision codes, 120 have exact replacements (including
splits like `LU-D` → five cantons). The other 479 only say "somewhere in this country" (`IN-OR` → `in?`, though
ISO renamed it to `IN-OD`). Successors are resolved in this order, each step only for codes the previous ones
didn't resolve:

1. overrides;
2. CLDR's exact replacements (120 codes);
3. renames parsed from the ISO change log. Its text names 415 of the 479 vague cases, but only 23 are phrased as a
   parseable "from X to Y";
4. a same-name match within the same country and the same source release, e.g. `KZ-AKM` "Akmolinskaja oblast'"
   becoming `KZ-11` with the same name. Always flagged for review;
5. otherwise `unknown_within_country`.

The change report lists every link resolved by steps 3 or 4, and every unresolved one, so reviewers can turn them
into overrides over time.

## 10. Impact on existing code and docs

- `SourceData`'s single `sourceName`/`sourceVersion`/`sourceLicense` becomes a list of sources. Values gain the
  §5 provenance metadata.
- New modules: `source-cldr`, `source-wikidata`, `overrides` (data plus loader), `source-aggregate`.
  `source-isocodes` keeps its role but no longer defines the library version.
- Output spec §5.5 (dataset information) and §9 (versioning) change to dataset versions and a list of upstream
  versions.
- Output spec gains withdrawn entries and history (§9 here). How withdrawn codes appear in the generated API is not
  yet specified; see open questions.
- `scripts/upstream_versions.py` and the propose-update workflow change from per-iso-codes-release to the triggers
  in §6.

## 11. Decisions and open questions

Decided:

- Our licence: `Apache-2.0 OR MIT` (§8).
- Names for codes iso-codes doesn't have yet: CLDR's English name (§4).
- Wikidata's "official name" is good enough to fill `official_name` (§4).
- Withdrawn codes are kept as best-effort history (§9), not dropped.

Open:

1. **Withdrawn codes in the API.** Matching withdrawn codes is another strictness axis, with a more granular dial
   than the on/off `Relaxation`s: for example current codes only (default), codes withdrawn since a given date,
   codes valid as of a given date, or any code ever issued. A match on a withdrawn code would report that, with its
   successors. To be designed together with the migration functions.
2. **Whether withdrawn entries are members of the enumeration** (Java: `@Deprecated` enum constants), or a separate
   history API alongside it. This depends on the same functions.
