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

## Caveats

On some systems it may be necessary to increase the heap space with the Java option -Xmx, see below for an example.

## Options

```
--alpha=<char>    generate sqlite database or xml file for meds whose title starts with 'char'
--fhir            use BAG FHIR NDJSON instead of BAG Preparations XML
--help            print help
--indications     generate report about keywords found in section indications (folder output)
--inter			  generate drug interaction files as an sqlite database and a csv data file
--lang=<arg>      generate database with given language, two options are supported: 'de' and 'fr'
--nodown          do not download the aips, swissmedic, bag and refdata files
--owner=<owner>   generate sqlite database or xml file for meds whose holder starts with 'owner'
--pinfo           generate Patinfo (default is Fachinfo)
--quiet           be extra quiet
--regnr=<number>  generate sqlite database or xml file for meds whose registration number starts with 'number'
--reports         generate parse and owner error reports (folder output)
--verbose         be extra verbose
--version         print the version information and exit
--xml             generate xml file
--zip             generate zipped versions sqlite database or xml file
```

## Download Domains

### Application (runtime data downloads)

| Domain | Description |
|--------|-------------|
| download.swissmedicinfo.ch | AIPS XML (Fachinfo/Patinfo documents) |
| www.swissmedic.ch | Authorized packages XLSX (Zugelassene Packungen) |
| swissindex.refdata.ch | Swissindex Pharma SOAP web service (pharmacode data) |
| files.refdata.ch | Refdata Articles ZIP and MedicinalDocuments AllHtml ZIP |
| refdatabase.refdata.ch | Refdata Partner SOAP web service (GLN data) |
| www.spezialitaetenliste.ch | BAG Spezialitätenliste XMLPublications ZIP |
| epl.bag.admin.ch | BAG FHIR NDJSON (alternative to Preparations XML) |
| www.swissdrg.org | SwissDRG Excel files |
| raw.githubusercontent.com | EPha interactions CSV, products JSON, ATC codes CSV (zdavatz/oddb2xml_files) |

### Build (Java/Gradle infrastructure)

| Domain | Description |
|--------|-------------|
| services.gradle.org | Gradle distribution download |
| repo.maven.apache.org | Maven Central repository (dependency JARs) |
| clojars.org | Clojars Maven repository (dependency JARs) |
| jitpack.io | JitPack Maven repository (GitHub-based dependency JARs) |

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
