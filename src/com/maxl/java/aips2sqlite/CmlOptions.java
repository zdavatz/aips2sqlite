/*
Copyright (c) 2014 Max Lungarella

This file is part of Aips2SQLite.

Aips2SQLite is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.maxl.java.aips2sqlite;

public class CmlOptions {
	
	// Set by command line options (default values)
	public static String APP_VERSION = "1.0.0";
	public static String DB_LANGUAGE = "";	
	public static boolean SHOW_ERRORS = false;
	public static boolean SHOW_LOGS = true;
	public static boolean DOWNLOAD_ALL = true;
	public static boolean XML_FILE = false;
	public static boolean ZIP_BIG_FILES = false;
	public static boolean GENERATE_REPORTS = false;
	public static boolean INDICATIONS_REPORT = false;
	public static boolean ADD_PSEUDO_FI = false;
	public static boolean ADD_INTERACTIONS = false;
	public static boolean GLN_CODES = false;
	public static boolean GENERATE_PI = false;
	public static boolean PLAIN = false;
	public static boolean SHOPPING_CART = false;
	public static boolean ONLY_SHOPPING_CART = false;
	public static boolean TAKEDA_SAP = false;
	public static boolean DESITIN_DB = false;
	public static boolean ONLY_DESITIN_DB = false;
	public static boolean DAILY_DRUG_COSTS = false;
	public static boolean SWISS_MEDIC_SEQUENCE = false;
	public static boolean PACKAGE_PARSE = false;
	public static String ZUR_ROSE_DB = "";
	public static String TAKEDA_RANGE = "";
	public static String STATS = "";
	public static String OPT_MED_TITLE = "";
	public static String OPT_MED_REGNR = "";
	public static String OPT_MED_OWNER = "";
}
