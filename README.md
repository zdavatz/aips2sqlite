aips2sqlite
===========

[![Join the chat at https://gitter.im/zdavatz/aips2sqlite](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/zdavatz/aips2sqlite?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

aips2sqlite - creates an SQLite DB from AIPS, Swissmedic, BAG and Refdata

## Requirements

Java 21+ (tested on Windows, Linux, Mac OS X)

Gradle 8.12 (included via wrapper)

## Build aips2sqlite.jar from commandline

`make aips2sqlite`

the output will be placed here: `build/libs`

## Clean the build folder

`make clean`

## Releases

Bump `CmlOptions.APP_VERSION`, rebuild the jar and commit it (the deployed hosts
run `jars/aips2sqlite.jar` straight out of the checkout, so the tracked copy is
what actually ships), then push a matching tag:

```
make aips2sqlite
cp build/libs/aips2sqlite.jar jars/aips2sqlite.jar
git commit -am "Release 1.0.4"
git tag v1.0.4 && git push origin master v1.0.4
```

The tag triggers `.github/workflows/release.yml`, which smoke-tests the
committed jar on Temurin 21 (`--help`, so it loads the main class and every
bundled dependency) and publishes a GitHub release with it attached.

Rebuilding the jar is a local step, not a CI one, because
`src/com/maxl/java/aips2sqlite/Crypto.java` is gitignored — it names the path of
the private AES key file (`Constants.DIR_CRYPTO + "/secret.txt"`) used by the
partner exports. Without it a fresh checkout does not compile: `./gradlew jar`
fails with `Symbol nicht gefunden: Crypto` at the six call sites in `FileOps`,
`GlnCodes` and `ShoppingCart{Desitin,Ibsa,Rose}`. Keep a copy of that file
alongside your checkout. The class itself holds no key — only the IV and that
path — so it can be committed if you would rather have CI compile the jar.

## Caveats

On some systems it may be necessary to increase the heap space with the Java option -Xmx, see below for an example.

**Refdata articles without a barcode.** Refdata publishes a few PHARMA articles
that carry no `<DataCarrierIdentifier>` — the Swiss Red Cross blood products
under the collective registration 99999, which appeared in August 2026. The
four loops that read that element (`RealExpertInfo`, `BaseDataParser`,
`RealPatientInfo`, `DailyDrugCosts`) skip such articles; without that guard the
run aborts with

```
NullPointerException: Cannot invoke "String.length()" because "ean_code" is null
```

which killed `--lang=de --xml` and `--smsequence` nightly from 14.08.2026 to
02.09.2026. The same upstream change broke oddb2xml, see
[oddb2xml#122](https://github.com/zdavatz/oddb2xml/issues/122).

`scripts/generate_aips_fi` publishes `output/oddb2xml_swissmedic_sequences.csv`
only after a run that succeeded and produced at least `MIN_SEQUENCE_LINES`
(default 8000) rows, so a failed run leaves the previous CSV in place instead of
removing it.

## Options

```
--alpha=<char>    generate sqlite database or xml file for meds whose title starts with 'char'
--dailydrugcosts  calculate the daily drug costs
--desitin         generate encrypted files for Desitin
--fhir            use BAG FHIR NDJSON instead of BAG Preparations XML (default ON since 01.06.2026)
--no-fhir         use the legacy BAG Preparations XML instead of FHIR NDJSON
--gln             generate csv file with Swiss gln codes
--help            print help
--indications     generate report about keywords found in section indications (folder output)
--inter			  generate drug interaction files as an sqlite database and a csv data file
--lang=<arg>      generate database with given language: 'de', 'fr', 'it' or 'en'
--nodown          do not download the aips, swissmedic, bag and refdata files
--onlydesitin     generate Desitin files only, skip the sqlite database
--onlyshop        generate shopping cart files only, skip the sqlite database
--owner=<owner>   generate sqlite database or xml file for meds whose holder starts with 'owner'
--packageparse    extract dosage information from package name
--pinfo           generate Patinfo (default is Fachinfo)
--plain           do not update the package section
--pseudo          add pseudo expert infos to db
--quiet           be extra quiet
--regnr=<number>  generate sqlite database or xml file for meds whose registration number starts with 'number'
--reports         generate parse and owner error reports (folder output)
--shop            generate encrypted files for shopping cart
--smsequence      generate swissmedic sequence csv (used by scripts/generate_aips_fi)
--stats=<user>    generate statistics for the given user
--takeda=<arg>    generate sap/gln matching file
--test            start aips2sqlite in test mode
--verbose         be extra verbose
--version         print the version information and exit
--xml             generate xml file
--zip             generate zipped versions sqlite database or xml file
--zurrose=<arg>   generate zur Rose article database or stock/like files (fulldb/atcdb/quick)
```

The partner exports (`--shop`, `--desitin`, `--takeda`, `--gln`) encrypt their
output and therefore need the private key described under "Releases" above;
the Fachinfo path (`--xml`, `--smsequence`) does not.

## BAG FHIR NDJSON source

Since 01.06.2026 the SL data (prices, SL flags, GTINs, Swissmedic numbers) comes
from BAG's FHIR NDJSON export rather than the Preparations XML; `--no-fhir`
switches back.

`AllDown.downFhirNdjson` resolves the export in two steps: BAG's resource index
(`https://epl.bag.admin.ch/api/sl/public/resources/current`) first, then the
stable per-language path

    https://epl.bag.admin.ch/static/sl/publication/fhir/foph-sl-publication-latest-<lang>.ndjson

**BAG moved the export there on 24.08.2026.** The old
`/static/fhir/foph-sl-export-latest-<lang>.ndjson` alias now answers 404 while
the dated snapshots beside it stay in place, so the move does not crash a run —
it downloads nothing and keeps the copy from the last good fetch, which is how
frozen prices can look like a healthy build. The index is no help either: it
answers a bare `"fhir": {}` (seen 26.08.2026), so the per-language path is
currently the only working source.

- The download lands in a `.part` file and replaces the real one only once its
  first line starts with `{`, so a 404 page or a truncated body cannot become
  the input. A failed download logs how old the copy it falls back on is.
- Language matters: an export carries the medicine names and limitation texts in
  one language only. An index-supplied URL is therefore used only when it names
  the language of the run.
- BAG publishes **de**, **fr** and **it**; there is no language-less default and
  no English export, so `--lang=en` reads the German one (prices, SL flags,
  GTINs and Swissmedic numbers do not depend on the language).
- A *preliminary* publication sits in parallel under
  `/static/sl/preliminary/fhir/foph-sl-preliminary-latest-<lang>.ndjson`. That is
  not the list in force and is not used.

## Download Domains

### Application (runtime data downloads)

| Domain | Description |
|--------|-------------|
| download.swissmedicinfo.ch | AIPS XML (Fachinfo/Patinfo documents) |
| www.swissmedic.ch | Authorized packages XLSX (Zugelassene Packungen) |
| swissindex.refdata.ch | Swissindex Pharma SOAP web service (pharmacode data) |
| files.refdata.ch | Refdata Articles ZIP and MedicinalDocuments AllHtml ZIP |
| api.refdata.ch | Refdata Partner SOAP web service (GLN data, requires REFDATA_API_KEY) |
| www.spezialitaetenliste.ch | BAG Spezialitätenliste XMLPublications ZIP |
| epl.bag.admin.ch | BAG FHIR NDJSON, `/static/sl/publication/fhir/foph-sl-publication-latest-<lang>.ndjson` (alternative to Preparations XML, see below) |
| www.swissdrg.org | SwissDRG Excel files |
| raw.githubusercontent.com | EPha interactions CSV, products JSON, ATC codes CSV (zdavatz/oddb2xml_files) |

### Build (Java/Gradle infrastructure)

| Domain | Description |
|--------|-------------|
| services.gradle.org | Gradle distribution download |
| repo.maven.apache.org | Maven Central repository (dependency JARs) |
| clojars.org | Clojars Maven repository (dependency JARs) |
| jitpack.io | JitPack Maven repository (GitHub-based dependency JARs) |

## Environment Variables

| Variable | Description |
|----------|-------------|
| REFDATA_API_KEY | API key for Refdata Partner SOAP service. Register at developer.refdata.ch to obtain a key. |

## Examples

Generate German SQLite database including report file:

$ java -jar aips2sqlite.jar --lang=de --verbose --reports

Generate French SQLite database, do not download any files and be extra quiet:

$ java -jar aips2sqlite.jar --lang=fr --quiet --nodown

Generate zipped German database for all med titles starting with P including a parse and section indication reports:

$ java -jar aips2sqlite.jar --lang=de --alpha=P --verbose --reports --nodown --indications --zip

Generate zipped French database and xml file for meds with registration number starting with N

$ java -jar aips2sqlite.jar --lang=fr --xml --regnr=N --verbose --nodown --zip

To increase the heap space use the option -Xmx

$ java -jar -Xmx2048m aips2sqlite.jar --lang=de --verbose --reports
