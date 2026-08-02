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

## Running

```bash
java -jar build/libs/aips2sqlite.jar --lang=de --verbose --reports
java -jar -Xmx2048m build/libs/aips2sqlite.jar --lang=de --verbose  # increased heap
```

Key flags: `--lang=<de|fr|it|en>`, `--nodown` (skip downloads), `--verbose`, `--quiet`, `--xml`, `--zip`, `--reports`, `--inter` (drug interactions), `--pinfo` (patient info instead of Fachinfo), `--fhir` (use BAG FHIR NDJSON instead of BAG Preparations XML; default ON since 01.06.2026), `--no-fhir` (opt out, use the legacy BAG Preparations XML).

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

**FHIR support:** `BagFhirParser.java` — Parses BAG FHIR NDJSON file (alternative to Preparations XML). Downloads from `epl.bag.admin.ch`, extracts prices, SL flags, GTIN, Swissmedic numbers from FHIR Bundle resources (MedicinalProductDefinition, RegulatedAuthorization, PackagedProductDefinition). `AllDown.downFhirNdjson` resolves the export in two steps: BAG's resource index (`/api/sl/public/resources/current`, field `fhir.fileUrl`) first, then the stable per-language path `/static/fhir/foph-sl-export-latest-<lang>.ndjson`. That fallback is not theoretical — the index has been reporting `"fhir": {"fileUrl": null}` (seen 2026-08-01), and Jackson's `asText()` on a JSON null yields the *string* `"null"`, so the old code requested `/static/null`, got a 404, and the run parsed 0 preparations: no SL flags, no prices, no visible failure. The download now lands in a `.part` file that replaces the real one only once its first line starts with `{`, and a failed download logs the age of the copy it falls back on — otherwise a stale file still reports a healthy preparation count and frozen prices look like a successful build. Language matters for the fallback because each export carries names and limitation texts in one language only.

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
