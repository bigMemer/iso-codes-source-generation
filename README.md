# iso-codes-source-generation

Generates Java sources from [Debian's iso-codes](https://salsa.debian.org/iso-codes-team/iso-codes) JSON data.

This repo holds only the generator. The generated library lives in
[bigMemer/iso-codes-java](https://github.com/bigMemer/iso-codes-java), which is what consumers depend on and what
gets published to Maven Central. Generated code only reaches that repo through pull requests opened by this one.

## How it works

`buildSrc/` contains a Gradle task that downloads one iso-codes release from salsa.debian.org and writes Java
sources for it. The build then compiles those sources, runs `src/test` against them and builds their Javadoc, so a
generator bug fails here instead of in the output repo.

```sh
./gradlew build                                   # generate + test the isoCodesVersion in gradle.properties
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
they are plain classes split across package-private data holder classes. A field present on every entry returns
`String`; one only some entries have returns `Optional<String>`. The JSON `name` field is exposed as
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
