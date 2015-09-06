aips2sqlite
===========

[![Join the chat at https://gitter.im/zdavatz/aips2sqlite](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/zdavatz/aips2sqlite?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

aips2sqlite - creates an SQLite DB from AIPS, Swissmedic, BAG and Refdata

## Requirements

Java 1.7 (tested on Windows, Linux, Mac OS X)

## Caveats

On some systems it may be necessary to increase the heap space with the Java option -Xmx, see below for an example.

## Options

```
--alpha=<char>    generate sqlite database or xml file for meds whose title starts with 'char'
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
