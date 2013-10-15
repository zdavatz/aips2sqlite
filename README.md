aips2sqlite
===========

aips2sqlite - creates an SQLite DB from AIPS, Swissmedic, BAG and Refdata

## Requirements

Java 1.7 (tested on Windows)

## Options

```
--alpha <arg> 	generate database with drugs whose titles start with <arg>
--help
--lang <arg>	generate database with given language, two options are supported: 'de' and 'fr'
--nodown		do not download the aips, swissmedic, bag and refdata files
--quiet			be extra quiet
--report		generate also report file
--verbose		be extra verbose
--version		print the version information and exit
--zip			generate a zipped version of the database 
```

## Examples

Generate German SQLite database including report file:

$ java -jar aips2sqlite.jar --lang=de --verbose --report

Generate French SQLite database, do not download any files and be extra quiet:

$ java -jar aips2sqlite.jar --lang=fr --quiet --nodown