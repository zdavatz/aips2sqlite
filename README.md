aips2sqlite
===========

aips2sqlite - creates an SQLite DB from AIPS, Swissmedic, BAG and Refdata

## Requirements

Java 1.7 (tested on Windows, Linux, Mac OS X)

## Caveats

On some systems it may be necessary to increase the heap space with the Java option -Xmx, see below for an example.

## Options

```
--alpha <arg> generate database with drugs whose titles start with <arg>

--help

--lang <arg>		generate database with given language, two options are supported: 'de' and 'fr'
--nodown		  	do not download the aips, swissmedic, bag and refdata files
--quiet			  	be extra quiet
--report		  	generate parse error report (folder output)
--verbose		  	be extra verbose
--version		  	print the version information and exit
--zip			    generate zipped version of the database 
--indications 		generate report about keywords found in section indications (folder output)
```

## Examples

Generate German SQLite database including report file:

$ java -jar aips2sqlite.jar --lang=de --verbose --report

Generate French SQLite database, do not download any files and be extra quiet:

$ java -jar aips2sqlite.jar --lang=fr --quiet --nodown

To increase the heap space use the option -Xmx

$ java -jar -Xmx2048m aips2sqlite.jar --lang=de --verbose --report
