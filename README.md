# iso-codes-source-generation

Generates Java sources for ISO 3166-1 (countries) and ISO 3166-2 (country subdivisions), combining
[Unicode CLDR](https://github.com/unicode-org/cldr) and [Debian's iso-codes](https://salsa.debian.org/iso-codes-team/iso-codes)
with reviewed overrides.

This repo holds only the generator. The generated library lives in
[bigMemer/iso-codes-java](https://github.com/bigMemer/iso-codes-java), which is what consumers depend on and what
gets published to Maven Central. Generated code only reaches that repo through pull requests opened by this one.

## Output specification

What every generated library must look like and how it behaves, in any language, is defined in
[docs/output-spec](docs/output-spec/README.md). Each output language has its own binding document there (currently
[Java](docs/output-spec/java.md)), including a list of where the current output falls short.

## Data sources

CLDR is the *backbone* (proposes which codes exist; usually first to change), iso-codes the *authority* (trusted to
be right, sometimes stale), and `overrides/iso3166.json` holds reviewed corrections, each with a reason and evidence.
The design is in [docs/sources.md](docs/sources.md). Implemented so far: CLDR, iso-codes, overrides and the
aggregation rules. Not yet: Wikidata as a gap filler, and history (withdrawn entries, `written_at`).

Source versions and the dataset version are set in `gradle.properties` (`cldrVersion`, `isoCodesVersion`,
`datasetVersion`). Every build writes `build/reports/iso-codes/aggregation.md`, listing everything aggregation
decided that a reviewer should see. If a code can't be built from the sources (e.g. a required field nobody
supplies), the build fails and the report says which override to add.

## How it works

```
 source-cldr ───────┐
 source-isocodes ───┼─► source-aggregate ──► model ──────────────► emitter-java
 overrides file ────┘   (rules, report)      upcast + validate      Java sources
```

The generator is a separate Gradle build in `generator/`, split into modules so the boundaries are enforced by
the compiler:

| Module            | Knows about                                         | Depends on |
|-------------------|-----------------------------------------------------|------------|
| `model`           | Our intermediate representation, nothing else       | nothing    |
| `source-isocodes` | iso-codes file names, JSON keys, salsa.debian.org   | `model`    |
| `source-cldr`     | CLDR's XML layout, id format and deprecation reasons| `model`    |
| `source-aggregate`| Combining sources by role (backbone, authority), overrides | `model` |
| `emitter-java`    | Java naming, enums vs classes, JavaPoet             | `model`    |
| `gradle-plugin`   | Wiring sources, aggregation and an emitter in a Gradle build | all of them |

**Model.** Each standard is a sealed interface whose nested records are numbered schema versions, e.g.
`Country.V1` (no flag) and `Country.V2` (with flag). Every version can `toLatest()`, deriving what's missing
where possible: `V1 → V2` computes the flag emoji from the alpha-2 code. `IsoCodesDataset.fromSource` upcasts
everything and validates it against each standard's `DEFINITION`: required fields, formats, uniqueness. Emitters
only ever see the newest versions, so the generated API is the same for every upstream release.

**Source.** `IsoCodesShapes` lists every JSON layout iso-codes has published, each tagged with the schema version it
parses into. A file is matched against them newest first. A file that fits none, for example because upstream added
a field, **fails the build** with a message saying what didn't match.

**When upstream changes:**
- *Same information, new layout:* add a shape in `source-isocodes` that parses into the existing model version.
- *New information:* add a model version (`V3`) with an upcaster from `V2`, point the `DEFINITION` at it, and add
  the matching shape. The emitter picks up the new field from the definition.
- *Another source:* write a `source-*` module that produces a `Contribution`, and give it a role in aggregation.
  The model and emitters don't change.

```sh
./gradlew build                     # generator unit tests + aggregate, generate, compile and test
./gradlew build exportOutput        # also assemble the files the output repo tracks
```

`exportOutput` writes exactly the files the output repo tracks to `build/output/`:

```
build/output/
├── dataset.version            # the dataset version, e.g. 2026.10.0
└── src/main/java/...          # generated sources
```


## Generated API

| Standard   | Type                     | Kind  | Lookups                                          |
|------------|--------------------------|-------|--------------------------------------------------|
| ISO 3166-1 | `iso3166.Country`        | enum  | `fromAlpha2`, `fromAlpha3`, `fromNumeric`        |
| ISO 3166-2 | `iso3166.Subdivision`    | class | `fromCode`, `all()`                              |

ISO 3166-2 has thousands of entries, more than a single JVM class can hold as enum constants, so it is a plain
class split across package-private data holder classes. Required model fields return `String`;
optional ones return `Optional<String>`. The JSON `name` field is exposed as
`englishName()` because `name()` is taken by `Enum`.

## CI

- **CI** (`ci.yml`): on every push, builds and tests with the configured source versions, and uploads the
  generated files and the aggregation report as workflow artifacts.
- **Propose output update** (`propose-update.yml`): run manually with a dataset version. It generates and tests
  the sources, uploads them with the report, then opens or updates a pull request on `bigMemer/iso-codes-java` from
  branch `dataset/<version>`, with the aggregation report as the pull request's description.

  **Not wired up yet:** the PR step needs a credential for the output repo, and which mechanism to use
  (fine-grained PAT or GitHub App) hasn't been decided. Until then the `open-pr` job fails at a clearly marked
  TODO step; the `generate` job and its artifact still work.

## Licence

Generated code is licensed `(Apache-2.0 OR MIT) AND Unicode-3.0 AND LGPL-2.1-or-later`: our own contribution
(generated structure and overrides) under Apache-2.0 or MIT at the consumer's choice, plus the licences of the CLDR
and iso-codes data it contains. See [docs/sources.md](docs/sources.md) §8.

This generator itself is licensed under the [GNU LGPL 2.1 or later](LICENSE).
