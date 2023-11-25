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
	// Input directory
	public static final String DIR_INPUT = "input";
	// Output directory
	public static final String DIR_OUTPUT = "./output/";
	// XML and XSD files to be parsed (contains DE and FR -> needs to be extracted)
	public static final String FILE_MEDICAL_INFOS_XML = "./downloads/aips_xml.xml";
	public static final String FILE_MEDICAL_INFOS_XSD = "./downloads/aips_xsd.xsd";
	// Excel file to be parsed (DE = FR)
	public static final String FILE_PACKAGES_XLS = "./downloads/swissmedic_packages_xls.xls";
	public static final String FILE_PACKAGES_XLSX = "./downloads/swissmedic_packages_xlsx.xlsx";
	// Refdata xml file to be parsed (DE != FR)
	public static final String FILE_REFDATA_PHARMA_XML = "./downloads/refdata_pharma_xml.xml";	
	public static final String FILE_REFDATA_PARTNER_XML = "./downloads/refdata_partner_xml.xml";
	/*
	public static final String FILE_REFDATA_PHARMA_DE_XML = "./downloads/refdata_pharma_de_xml.xml";
	public static final String FILE_REFDATA_PHARMA_FR_XML = "./downloads/refdata_pharma_fr_xml.xml";
	*/
	// BAG xml file to be parsed (contains DE and FR)
	public static final String FILE_PREPARATIONS_XML = "./downloads/bag_preparations_xml.xml";
	// Swiss DRG xlsx file to be parsed (DE != FR)
	public static final String FILE_SWISS_DRG_DE_XLSX = "./downloads/swiss_drg_de_xlsx.xlsx";
	public static final String FILE_SWISS_DRG_FR_XLSX = "./downloads/swiss_drg_fr_xlsx.xlsx";
	public static final String FILE_SWISS_DRG_IT_XLSX = "./downloads/swiss_drg_it_xlsx.xlsx";
	// EPha interactions file to be parsed (DE != FR)
	public static final String FILE_EPHA_INTERACTIONS_DE_CSV = "./downloads/epha_interactions_de_csv.csv";
	public static final String FILE_EPHA_INTERACTIONS_FR_CSV = "./downloads/epha_interactions_fr_csv.csv";
	public static final String FILE_EPHA_PRODUCTS_DE_JSON = "./downloads/epha_products_de_json.json";
	public static final String FILE_EPHA_PRODUCTS_FR_JSON = "./downloads/epha_products_fr_json.json";	
	public static final String FILE_EPHA_PRODUCTS_IT_JSON = "./downloads/epha_products_it_json.json";
	// EPha ATC codes file
	public static final String FILE_EPHA_ATC_CODES_CSV = "./downloads/epha_atc_codes_csv.csv";
	// GLN Codes (Personen + Betriebe)
	public static final String FILE_GLN_CODES_PEOPLE = "./downloads/gln_codes_people_xlsx.xlsx";
	public static final String FILE_GLN_CODES_COMPANIES = "./downloads/gln_codes_companies_xlsx.xlsx";
	// Files Crypto
	public static final String DIR_CRYPTO = "./input/crypto/";
	// Files Ibsa
	public static final String DIR_IBSA = "./input/ibsa/";	
	public static final String FILE_CUST_IBSA = "customer_glns.csv";
	public static final String FILE_TARG_IBSA = "targeting_glns.csv";
	public static final String FILE_MOOS_ADDR = "moos_addresses.csv";
	// Files zur Rose
	public static final String DIR_ZURROSE = "./input/zurrose/";		
	public static final String CSV_FILE_DISPO_ZR = "artikel_stamm_zurrose.csv";
	public static final String CSV_FILE_FULL_DISPO_ZR = "artikel_vollstamm_zurrose.csv";
	public static final String CSV_FILE_VOIGT_ZR = "artikel_stamm_voigt.csv";
	public static final String CSV_FILE_DIRECT_SUBST_ZR = "direct_subst_zurrose.csv";
	public static final String CSV_FILE_NOTA_ZR = "nota_zurrose.csv";
    public static final String CSV_STOCK_INFO_ZR = "rose_stock.csv";
	public static final String CSV_LIKE_DB_ZR = "like_db_zurrose.csv";
	public static final String MAP_GALENIC_CODES_ZR = "galenic_codes_map_zurrose.txt";
	// Files Desitin
	public static final String DIR_DESITIN = "./input/desitin/";		
	public static final String FILE_CUST_DESITIN = "customer_desitin.csv";
	public static final String FILE_ARTICLES_DESITIN = "artikel_desitin.csv";
	// Files Takeda
	public static final String DIR_TAKEDA = "./input/takeda/";
	public static final String FILE_SAP_TAKEDA_2015 = "Auszug_SAP_Kunden_CH_2015_09_02.xlsx";
	public static final String FILE_SAP_TAKEDA_2016 = "Auszug_SAP_Kunden_CH_2016_11_01.xlsx";
	// Files Ywesee
	public static final String DIR_YWESEE = "./input/ywesee/";
	public static final String FILE_YAML_YWESEE = "medreg_doctors.yaml";
	// ****** ATC class xls file (DE != FR) ******
	// public static final String FILE_ATC_CLASSES_XLS = "./input/wido_arz_amtl_atc_index_0113_xls.xls";	// 2013
	public static final String FILE_ATC_CLASSES_XLS = "./input/wido_arz_amtl_atc_index_0114_xls.xls";		// 2014
	public static final String FILE_WHO_ATC_CLASSES_XLS = "./input/who_atc_index_2013_xls.xls";				// 2013
	public static final String FILE_ATC_MULTI_LINGUAL_TXT = "./input/atc_codes_multi_lingual.txt";
	public static final String FILE_ATC_WITH_DDDS_XML = "./input/2016_ATC_with_DDDs.xml"; // "./input/2015_ATC_with_DDDs.xml";
	public static final String FILE_DOSAGE_FORMS_JSON = "dosage_forms.json";
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
	public static final String FILE_STOP_WORDS_IT = "./input/italian_stop_words.txt";
	
	// Version of the generated database 
	public static final String FI_DB_VERSION = "1.4.0";	
	public static final String PI_DB_VERSION = "1.1.0";
}
