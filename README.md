# iso-codes-source-generation

Generates Java sources from [Debian's iso-codes](https://salsa.debian.org/iso-codes-team/iso-codes) JSON data.

This repo holds only the generator. The generated library lives in
[bigMemer/iso-codes-java](https://github.com/bigMemer/iso-codes-java), which is what consumers depend on and what
gets published to Maven Central. Generated code only reaches that repo through pull requests opened by this one.

## Output specification

What every generated library must look like and how it behaves, in any language, is defined in
[docs/output-spec](docs/output-spec/README.md). Each output language has its own binding document there (currently
[Java](docs/output-spec/java.md)), including a list of where the current output falls short.

## How it works

```
 source-isocodes            model                       emitter-java
 iso-codes JSON  ──────►  versioned records  ──────►  Java sources
                 parse     ──upcast──► latest  emit
                           + validate
```

The generator is a separate Gradle build in `generator/`, split into modules so the boundaries are enforced by
the compiler:

| Module            | Knows about                                         | Depends on |
|-------------------|-----------------------------------------------------|------------|
| `model`           | Our intermediate representation, nothing else       | nothing    |
| `source-isocodes` | iso-codes file names, JSON keys, salsa.debian.org   | `model`    |
| `emitter-java`    | Java naming, enums vs classes, JavaPoet             | `model`    |
| `gradle-plugin`   | Wiring one source to one emitter in a Gradle build  | all three  |

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
- *A different source of truth:* write a new `source-*` module that produces `SourceData`. Nothing else changes.

```sh
./gradlew build                                   # generator unit tests + generate/compile/test 4.20.1
./gradlew build exportOutput -PisoCodesVersion=4.7.0
```

`exportOutput` writes exactly the files the output repo tracks to `build/output/`:

```
build/output/
├── iso-codes.version          # the upstream release, e.g. 4.20.1
└── src/main/java/...          # generated sources
```

`scripts/upstream_versions.py` lists the supported upstream releases (3.67 onward; `--latest` for the newest).

## Generated API

| Standard   | Type                     | Kind  | Lookups                                          |
|------------|--------------------------|-------|--------------------------------------------------|
| ISO 3166-1 | `iso3166.Country`        | enum  | `fromAlpha2`, `fromAlpha3`, `fromNumeric`        |
| ISO 3166-2 | `iso3166.Subdivision`    | class | `fromCode`, `all()`                              |
| ISO 3166-3 | `iso3166.FormerCountry`  | enum  | `fromAlpha4`                                     |
| ISO 4217   | `iso4217.Currency`       | enum  | `fromAlpha3`, `fromNumeric`                      |
| ISO 15924  | `iso15924.Script`        | enum  | `fromAlpha4`, `fromNumeric`                      |
| ISO 639-2  | `iso639.LanguagePart2`   | enum  | `fromAlpha3`, `fromAlpha2`, `fromBibliographic`  |
| ISO 639-3  | `iso639.Language`        | class | `fromAlpha3`, `fromAlpha2`, `fromBibliographic`, `all()` |
| ISO 639-5  | `iso639.LanguageFamily`  | enum  | `fromAlpha3`                                     |

ISO 3166-2 and ISO 639-3 have thousands of entries, more than a single JVM class can hold as enum constants, so
they are plain classes split across package-private data holder classes. Required model fields return `String`;
optional ones return `Optional<String>`. The JSON `name` field is exposed as
`englishName()` because `name()` is taken by `Enum`.

## CI

- **CI** (`ci.yml`): on every push, builds and tests three representative releases and uploads each one's
  `build/output/` as a workflow artifact.
- **Propose output update** (`propose-update.yml`): run manually with an iso-codes version (blank means newest).
  It generates and tests the sources, uploads them as an artifact, then opens or updates a pull request on
  `bigMemer/iso-codes-java` from branch `iso-codes/<version>`.

  **Not wired up yet:** the PR step needs a credential for the output repo, and which mechanism to use
  (fine-grained PAT or GitHub App) hasn't been decided. Until then the `open-pr` job fails at a clearly marked
  TODO step; the `generate` job and its artifact still work.

## Licence

The generated code derives from iso-codes data, licensed under the [GNU LGPL 2.1 or later](LICENSE). This
generator uses the same licence.
