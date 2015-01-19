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

public class Constants {
	// Download directory
	public static final String DIR_DOWNLOAD = "./downloads/";
	// Output directory
	public static final String DIR_OUTPUT = "./output/";
	// XML and XSD files to be parsed (contains DE and FR -> needs to be extracted)
	public static final String FILE_MEDICAL_INFOS_XML = "./downloads/aips_xml.xml";
	public static final String FILE_MEDICAL_INFOS_XSD = "./downloads/aips_xsd.xsd";
	// Excel file to be parsed (DE = FR)
	public static final String FILE_PACKAGES_XLS = "./downloads/swissmedic_packages_xls.xls";
	public static final String FILE_PACKAGES_XLSX = "./downloads/swissmedic_packages_xlsx.xlsx";
	// Refdata xml file to be parsed (DE != FR)
	public static final String FILE_REFDATA_PHARMA_DE_XML = "./downloads/refdata_pharma_de_xml.xml";
	public static final String FILE_REFDATA_PHARMA_FR_XML = "./downloads/refdata_pharma_fr_xml.xml";
	// BAG xml file to be parsed (contains DE and FR)
	public static final String FILE_PREPARATIONS_XML = "./downloads/bag_preparations_xml.xml";
	// Swiss DRG xlsx file to be parsed (DE != FR)
	public static final String FILE_SWISS_DRG_DE_XLSX = "./downloads/swiss_drg_de_xlsx.xlsx";
	public static final String FILE_SWISS_DRG_FR_XLSX = "./downloads/swiss_drg_fr_xlsx.xlsx";
	// EPha interactions file to be parsed (DE != FR)
	public static final String FILE_EPHA_INTERACTIONS_DE_CSV = "./downloads/epha_interactions_de_csv.csv";
	public static final String FILE_EPHA_INTERACTIONS_FR_CSV = "./downloads/epha_interactions_fr_csv.csv";
	public static final String FILE_EPHA_PRODUCTS_DE_JSON = "./downloads/epha_products_de_json.json";
	public static final String FILE_EPHA_PRODUCTS_FR_JSON = "./downloads/epha_products_fr_json.json";	
	// GLN Codes (Personen + Betriebe)
	public static final String FILE_GLN_CODES_PEOPLE = "./downloads/gln_codes_people_xlsx.xlsx";
	public static final String FILE_GLN_CODES_COMPANIES = "./downloads/gln_codes_companies_xlsx.xlsx";
	// File IBSA
	public static final String FILE_MOOSBERGER = "moosberger_glns.csv";
	public static final String FILE_TARGETING = "targeting_glns.csv";
	// "Shopping" files
	public static final String DIR_SHOPPING = "./input/shop/";
	// ****** ATC class xls file (DE != FR) ******
	// public static final String FILE_ATC_CLASSES_XLS = "./input/wido_arz_amtl_atc_index_0113_xls.xls";	// 2013
	public static final String FILE_ATC_CLASSES_XLS = "./input/wido_arz_amtl_atc_index_0114_xls.xls";		// 2014
	public static final String FILE_WHO_ATC_CLASSES_XLS = "./input/who_atc_index_2013_xls.xls";				// 2013
	public static final String FILE_ATC_MULTI_LINGUAL_TXT = "./input/atc_codes_multi_lingual.txt";
	// CSS style sheets
	public static final String FILE_STYLE_CSS_BASE = "./css/amiko_stylesheet_";
	public static final String FILE_REPORT_CSS_BASE = "./css/report_stylesheet";
	// ****** Drug interactions file ******
	public static final String FILE_INTERACTIONS_BASE = "./output/drug_interactions_";
	// ****** Parse reports (DE != FR) ******
	public static final String FILE_PARSE_REPORT = "./output/parse_report";
	public static final String FILE_OWNER_REPORT = "./output/owner_report";
	public static final String FILE_INDICATIONS_REPORT = "./output/indications_report";
	public static final String FILE_INTERACTIONS_REPORT = "./output/interactions_report";
	// ****** XML file ******
	public static final String FI_FILE_XML_BASE = "./output/fis/";
	public static final String PI_FILE_XML_BASE = "./output/pis/";
	// ****** Stop words (DE != FR) ******
	public static final String FILE_STOP_WORDS_DE = "./input/german_stop_words.txt";
	public static final String FILE_STOP_WORDS_FR = "./input/french_stop_words.txt";
	
	// Version of the generated database 
	public static final String FI_DB_VERSION = "1.4.0";	
	public static final String PI_DB_VERSION = "1.1.0";
}
