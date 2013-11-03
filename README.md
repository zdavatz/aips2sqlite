aips2sqlite
===========

aips2sqlite - creates an SQLite DB from AIPS, Swissmedic, BAG and Refdata

## Requirements

Java 1.7 (tested on Windows, Linux, Mac OS X)

## Caveats

On some systems it may be necessary to increase the heap space with the Java option -Xmx, see below for an example.

## Options

```
--help            print help
--version         print the version information and exit
--quiet           be extra quiet
--verbose         be extra verbose
--nodown          do not download the aips, swissmedic, bag and refdata files
--lang=<arg>      generate database with given language, two options are supported: 'de' and 'fr'
--alpha=<char>    generate sqlite database or xml file for meds whose title starts with 'char'
--regnr=<number>  generate sqlite database or xml file for meds whose registration number starts with 'number'
--xml             generate xml file 
--zip             generate zipped versions sqlite database or xml file 
--report          generate parse error report (folder output)
--indications     generate report about keywords found in section indications (folder output)
```

## Examples

Generate German SQLite database including report file:

$ java -jar aips2sqlite.jar --lang=de --verbose --report

Generate French SQLite database, do not download any files and be extra quiet:

$ java -jar aips2sqlite.jar --lang=fr --quiet --nodown

Generate zipped German database for all med titles starting with P including a parse and section indication reports:

$ java -jar aips2sqlite.jar --lang=de --alpha=P --verbose --report --nodown --indications --zip

Generate zipped French database and xml file for meds with registration number starting with N

$ java -jar aips2sqlite.jar --lang=fr --xml --regnr=N --verbose --nodown --zip

To increase the heap space use the option -Xmx

$ java -jar -Xmx2048m aips2sqlite.jar --lang=de --verbose --report
