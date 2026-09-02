# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

aips2sqlite is a Java ETL application that downloads pharmaceutical data from Swiss health authorities (AIPS/swissmedicinfo.ch, Swissmedic, BAG, Refdata, EPha) and generates SQLite databases, XML files, and various reports. Licensed under GPL v3.

## Build Commands

```bash
make aips2sqlite          # Build fat JAR (output: build/libs/aips2sqlite.jar)
make clean                # Clean build folder
./gradlew jar             # Direct Gradle build
```

**A fresh checkout does not compile.** `src/com/maxl/java/aips2sqlite/Crypto.java` is gitignored (it names the private AES key file, `Constants.DIR_CRYPTO + "/secret.txt"`, that the partner exports encrypt with), so the build fails with 12 × `Symbol nicht gefunden: Crypto` at the six call sites in `FileOps`, `GlnCodes` and `ShoppingCart{Desitin,Ibsa,Rose}`. Restore the file from a private copy before building — it is not recoverable from the repo, only from the `Crypto.class` inside the tracked jar. Nothing on the Fachinfo/`--xml`/`--smsequence` path uses it, but it still has to compile. This is also why `.github/workflows/release.yml` publishes the **tracked** `jars/aips2sqlite.jar` instead of compiling in CI. Note that when `secret.txt` is absent `Crypto` silently falls back to a **randomly generated** key, so partner exports produced without it are undecryptable rather than failing loudly.

The deployed host (mediupdatexml.oddb.org) never compiles: `scripts/generate_aips_fi` does `git pull` and runs `jars/aips2sqlite.jar` from the checkout, so **a code change only reaches production once the rebuilt jar is committed**.

**Barcode-less Refdata articles (02.09.2026 onwards).** Refdata publishes a few PHARMA articles with no `<DataCarrierIdentifier>` — the Swiss Red Cross blood products under the collective registration 99999 (`Erythrozytenkonzentrat (EK)`, `Plasma INTERCEPT®`, `Plasma FGPq`, `Thrombozytenkonzentrat Intercept®`, all under `CH-7601002120890-9999900`), which appeared in **August 2026**. `RealExpertInfo.extractPackageInfo` read that optional element into `ean_code` and then called `ean_code.length()` unguarded, so the `--lang=de --xml` step died with `NullPointerException: Cannot invoke "String.length()" because "ean_code" is null` right after `Unmarshalling Refdatabase for de`. Such articles are now skipped. **This is the same upstream data change that broke oddb2xml** (`nil.size` in `RefdataExtractor#to_hash`, [oddb2xml #122](https://github.com/zdavatz/oddb2xml/issues/122), fixed in 3.0.33) — if one of the two starts failing on Refdata input, check the other.

**Why that crash silently produced an HTTP 404 for twenty days.** `scripts/generate_aips_fi` used to `rm -f output/oddb2xml_swissmedic_sequences.csv` as its *first* action and regenerate it only after all three java steps, under `set -e`. So the NPE in step 1 left the published CSV deleted rather than stale, and `https://mediupdatexml.oddb.org/aips2sqlite/oddb2xml_swissmedic_sequences.csv` — a URL **HIN links directly** from `www.hin.ch/de/services/mediupdate-xml.cfm#section_2` — answered 404 every night from 14.08.2026 until 02.09.2026, with the cron job reporting nothing (no MTA on the host, so `MAILTO` output goes nowhere). The script now removes only the intermediate `swiss_medic_sequences.csv`, checks the result has at least `MIN_SEQUENCE_LINES` (default 8'000; a healthy run is ~10'400) and publishes by `mv` only then — the previous CSV survives a failed run. The last-good output timestamps in `jars/output/` are the fastest way to spot this: a fresh `amiko_db_full_idx_de.db` of a few **kB** next to a months-old `amiko_db_full_idx_fr.db` means step 1 died.

## Running

```bash
java -jar build/libs/aips2sqlite.jar --lang=de --verbose --reports
java -jar -Xmx2048m build/libs/aips2sqlite.jar --lang=de --verbose  # increased heap
```

Key flags: `--lang=<de|fr|it|en>`, `--nodown` (skip downloads), `--verbose`, `--quiet`, `--xml`, `--zip`, `--reports`, `--inter` (drug interactions), `--pinfo` (patient info instead of Fachinfo), `--fhir` (use BAG FHIR NDJSON instead of BAG Preparations XML; default ON since 01.06.2026), `--no-fhir` (opt out, use the legacy BAG Preparations XML), `--smsequence` (swissmedic sequence CSV — what `scripts/generate_aips_fi` runs third). README's "Options" section lists all 31 flags, including the partner exports (`--shop`, `--desitin`, `--takeda`, `--gln`, `--zurrose`) that need `Crypto.java`.

**Environment variable:** `REFDATA_API_KEY` must be set for Refdata Partner downloads (register at developer.refdata.ch).

## Architecture

**Pipeline: Download → Parse → Transform → Output**

1. **`Aips2Sqlite.java`** — Main entry point. Parses CLI options via Commons CLI, orchestrates the full pipeline.
2. **`AllDown.java`** — Downloads data files from Swiss sources (AIPS XML, Swissmedic XLS/XLSX, BAG XML, Refdata XML, EPha CSV, GLN XLSX, Swiss DRG).
3. **`RealExpertInfo.java`** / **`RealPatientInfo.java`** — Parse AIPS Fachinfo and Patinfo respectively. Core business logic for extracting medication data from XML/HTML.
4. **`SqlDatabase.java`** — SQLite database operations. Creates `amiko_db_full_idx_<lang>.db` with tables: `amikodb` (medications), `productdb` (articles), `android_metadata`.
5. **`Interactions.java`** — Drug interaction processing from EPha data, generates interaction SQLite DB and CSV.

**Data models:** `Medication` (id, title, auth, atccode, content HTML, pack_info), `Article` (ean_code, pharma_code, pricing, stock), `Product` (shopping cart data).

**Partner-specific generators:** `ShoppingCartIbsa`, `ShoppingCartDesitin`, `ShoppingCartRose`, `TakedaParse` — each produces encrypted/specialized output for specific pharmacy partners.

**FHIR support:** `BagFhirParser.java` — Parses BAG FHIR NDJSON file (alternative to Preparations XML). Downloads from `epl.bag.admin.ch`, extracts prices, SL flags, GTIN, Swissmedic numbers from FHIR Bundle resources (MedicinalProductDefinition, RegulatedAuthorization, PackagedProductDefinition). `AllDown.downFhirNdjson` resolves the export in two steps: BAG's resource index (`/api/sl/public/resources/current`, field `fhir.fileUrl`) first, then the stable per-language path `/static/sl/publication/fhir/foph-sl-publication-latest-<lang>.ndjson`. That fallback is not theoretical — the index has been reporting `"fhir": {"fileUrl": null}` (seen 2026-08-01), and Jackson's `asText()` on a JSON null yields the *string* `"null"`, so the old code requested `/static/null`, got a 404, and the run parsed 0 preparations: no SL flags, no prices, no visible failure. The download now lands in a `.part` file that replaces the real one only once its first line starts with `{`, and a failed download logs the age of the copy it falls back on — otherwise a stale file still reports a healthy preparation count and frozen prices look like a successful build. Language matters for the fallback because each export carries names and limitation texts in one language only; BAG publishes `de`, `fr` and `it` only, so an `--lang=en` run reads the German export (prices, SL flags, GTINs and Swissmedic numbers are language-independent). **BAG moved the export on 24.08.2026** and announced it the next evening: the old `/static/fhir/foph-sl-export-latest-<lang>.ndjson` alias answers 404 while the dated snapshots beside it stay in place, so the move shows up as a run that downloads nothing rather than as a crash — and since the move the resource index answers a bare `"fhir": {}` (seen 2026-08-26), i.e. the per-language path is currently the only working source. A *preliminary* publication sits in parallel under `/static/sl/preliminary/fhir/foph-sl-preliminary-latest-<lang>.ndjson`; that is not the list in force and is not used. An index-supplied URL is only trusted when it names the language of the run, and may be absolute or relative.

**Supporting classes:** `Constants.java` (all file paths), `CmlOptions.java` (global config flags), `HtmlUtils.java` (HTML sanitization), `FileOps.java` (I/O + AES encryption), `ExcelOps.java` (POI-based Excel parsing), `BaseDataParser.java` (XML/Excel parsing base).

## Package Structure

- `src/com/maxl/java/aips2sqlite/` — All application code (~43 classes)
- `src/com/maxl/java/aips2sqlite/refdata/` — Generated JAXB classes for Refdata XML binding
- `src/com/maxl/java/shared/` — Shared utilities

## Download Domains

See `README.md` "Download Domains" section for the full list of domains the application and build system connect to.

## Key Technical Details

- Java 21+, Gradle 8.12 (via wrapper), fat JAR with all dependencies
- Uses JAXB for XML parsing, Apache POI for Excel, jsoup (forked) for HTML, Jackson for JSON
- SQLite via `org.xerial:sqlite-jdbc`
- Input files downloaded to `downloads/`, outputs written to `output/`
- Custom input configs in `input/`
- Tests use JUnit 4
