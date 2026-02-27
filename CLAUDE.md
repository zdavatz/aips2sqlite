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

## Running

```bash
java -jar build/libs/aips2sqlite.jar --lang=de --verbose --reports
java -jar -Xmx2048m build/libs/aips2sqlite.jar --lang=de --verbose  # increased heap
```

Key flags: `--lang=<de|fr|it|en>`, `--nodown` (skip downloads), `--verbose`, `--quiet`, `--xml`, `--zip`, `--reports`, `--inter` (drug interactions), `--pinfo` (patient info instead of Fachinfo).

## Architecture

**Pipeline: Download → Parse → Transform → Output**

1. **`Aips2Sqlite.java`** — Main entry point. Parses CLI options via Commons CLI, orchestrates the full pipeline.
2. **`AllDown.java`** — Downloads data files from Swiss sources (AIPS XML, Swissmedic XLS/XLSX, BAG XML, Refdata XML, EPha CSV, GLN XLSX, Swiss DRG).
3. **`RealExpertInfo.java`** / **`RealPatientInfo.java`** — Parse AIPS Fachinfo and Patinfo respectively. Core business logic for extracting medication data from XML/HTML.
4. **`SqlDatabase.java`** — SQLite database operations. Creates `amiko_db_full_idx_<lang>.db` with tables: `amikodb` (medications), `productdb` (articles), `android_metadata`.
5. **`Interactions.java`** — Drug interaction processing from EPha data, generates interaction SQLite DB and CSV.

**Data models:** `Medication` (id, title, auth, atccode, content HTML, pack_info), `Article` (ean_code, pharma_code, pricing, stock), `Product` (shopping cart data).

**Partner-specific generators:** `ShoppingCartIbsa`, `ShoppingCartDesitin`, `ShoppingCartRose`, `TakedaParse` — each produces encrypted/specialized output for specific pharmacy partners.

**Supporting classes:** `Constants.java` (all file paths), `CmlOptions.java` (global config flags), `HtmlUtils.java` (HTML sanitization), `FileOps.java` (I/O + AES encryption), `ExcelOps.java` (POI-based Excel parsing), `BaseDataParser.java` (XML/Excel parsing base).

## Package Structure

- `src/com/maxl/java/aips2sqlite/` — All application code (~43 classes)
- `src/com/maxl/java/aips2sqlite/refdata/` — Generated JAXB classes for Refdata XML binding
- `src/com/maxl/java/shared/` — Shared utilities

## Key Technical Details

- Java 21+, Gradle 8.12 (via wrapper), fat JAR with all dependencies
- Uses JAXB for XML parsing, Apache POI for Excel, jsoup (forked) for HTML, Jackson for JSON
- SQLite via `org.xerial:sqlite-jdbc`
- Input files downloaded to `downloads/`, outputs written to `output/`
- Custom input configs in `input/`
- Tests use JUnit 4
