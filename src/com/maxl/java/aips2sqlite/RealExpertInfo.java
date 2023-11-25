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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import org.apache.commons.io.FileUtils;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities.EscapeMode;

import com.aliasi.spell.JaccardDistance;
import com.aliasi.tokenizer.IndoEuropeanTokenizerFactory;
import com.aliasi.tokenizer.TokenizerFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxl.java.aips2sqlite.Preparations.Preparation;
import com.opencsv.CSVReader;

public class RealExpertInfo {

	// Main sqlite database
	SqlDatabase m_sql_db;

	List<MedicalInformations.MedicalInformation> m_med_list = null;

	// Map to list with all the relevant information
	// HashMap is faster, but TreeMap is sort by the key :)
	private static Map<String, ArrayList<String>> m_package_info;

	// Map to Swiss DRG: atc code -> (dosage class, price)
	private static Map<String, ArrayList<String>> m_swiss_drg_info;
	private static Map<String, String> m_swiss_drg_footnote;

	// Map to string of atc classes, key is the ATC-code or any of its substrings
	private static Map<String, String> m_atc_map;

	// Map from swissmedic no.5 to atc codes
	private static Map<String, String> m_smn5_atc_map;

	// Map to string of additional info, key is the SwissmedicNo5
	private static Map<String, String> m_add_info_map;

	// Packages string used for "shopping" purposes (will contain ean code, pharma codes, prices etc.)
	private List<String> m_list_of_packages = null;

	// List of ean codes
	private List<String> m_list_of_eancodes = null;

	// Map of swissmedicno5 to list of names
	private HashMap<String, ArrayList<String>> m_smn5_to_list_of_names = null;

	// Map of products
	private Map<String, Product> m_map_products = null;

	// Map of eancodes to owner
	private Map<String, String> m_map_eancode_to_owner = null;

	// Package section string
	private static String m_pack_info_str = "";

	// Stop word hashset
	HashSet<String> m_stop_words_hash = null;
	/*
	 * Constructors
	 */
	public RealExpertInfo(SqlDatabase sql_db,
			List<MedicalInformations.MedicalInformation> med_list,
			Map<String, Product> map_products) {
		m_sql_db = sql_db;
		m_med_list = med_list;
		m_map_products = map_products;

		// Initialize maps and lists
		m_package_info = new TreeMap<String, ArrayList<String>>();
		m_swiss_drg_info = new TreeMap<String, ArrayList<String>>();
		m_swiss_drg_footnote = new TreeMap<String, String>();
		m_atc_map = new TreeMap<String, String>();
		m_smn5_atc_map = new TreeMap<String, String>();
		m_add_info_map = new TreeMap<String, String>();
		m_list_of_packages = new ArrayList<String>();
		m_list_of_eancodes = new ArrayList<String>();
		m_map_eancode_to_owner = new HashMap<String, String>();
	}

	/*
	 * Getters / setters
	 */
	public void setMedList(List<MedicalInformations.MedicalInformation> med_list) {
		m_med_list = med_list;
	}

	public List<MedicalInformations.MedicalInformation> getMedList() {
		return m_med_list;
	}

	/**
	 * Extracts all stop words from the stop word text file, used to generate "therapy index"
	 */
	private void getStopWords() {
		// Read stop words as String
		String stopWords_str = null;
		if (CmlOptions.DB_LANGUAGE.equals("de"))
			stopWords_str = FileOps.readFromFile(Constants.FILE_STOP_WORDS_DE);
		else if (CmlOptions.DB_LANGUAGE.equals("fr"))
			stopWords_str = FileOps.readFromFile(Constants.FILE_STOP_WORDS_FR);
		else if (CmlOptions.DB_LANGUAGE.equals("it"))
			stopWords_str = FileOps.readFromFile(Constants.FILE_STOP_WORDS_IT);
		// Create stop word hash set!
		if (stopWords_str!=null) {
			List<String> sw = Arrays.asList(stopWords_str.split("\n"));
			m_stop_words_hash = new HashSet<String>();
			for (String w : sw) {
				m_stop_words_hash.add(w.trim().toLowerCase());
			}
		}
	}

	/**
	 * Extracts package info from Swissmedic package Excel file
	 */
	private void extractPackageInfo() {
		try {
			long startTime = System.currentTimeMillis();
			if (CmlOptions.SHOW_LOGS)
				System.out.print("- Processing packages xlsx... ");
			// Load Swissmedic xls file
			FileInputStream packages_file = new FileInputStream(Constants.FILE_PACKAGES_XLSX);
			// Get workbook instance for XLSX file (XSSF = Horrible SpreadSheet Format)
			XSSFWorkbook packages_workbook = new XSSFWorkbook(packages_file);
			// Get first sheet from workbook
			XSSFSheet packages_sheet = packages_workbook.getSheetAt(0);

			/*
			if (SHOW_LOGS)
				System.out.print("- Processing packages xls... ");
			// Load Swissmedic xls file
			FileInputStream packages_file = new FileInputStream(FILE_PACKAGES_XLS);
			// Get workbook instance for XLS file (HSSF = Horrible SpreadSheet Format)
			HSSFWorkbook packages_workbook = new HSSFWorkbook(packages_file);
			// Get first sheet from workbook
			HSSFSheet packages_sheet = packages_workbook.getSheetAt(0);
			*/
			// Iterate through all rows of first sheet
			Iterator<Row> rowIterator = packages_sheet.iterator();

			int num_rows = 0;
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				if (num_rows > 5) {
					String swissmedic_no5 = ""; // SwissmedicNo5 registration number (5 digits)
					String sequence_name = "";
					String package_id = "";
					String swissmedic_no8 = ""; // SwissmedicNo8 = SwissmedicNo5 + Package id (8 digits)
					String heilmittel_code = "";
					String package_size = "";
					String package_unit = "";
					String swissmedic_cat = "";
					String application_area = "";
					String public_price = "";
					String exfactory_price = "";
					String therapeutic_index = "";
					String withdrawn_str = "";
					String speciality_str = "";
					String plimitation_str = "";
					String add_info_str = ""; 	// Contains additional information separated by ;
					String ean_code_str = "";
					String pharma_code_str = "";
					String owner_str = "";

					// 0: Zulassungsnummer, 1: Dosisstärkenummer, 2: Präparatebezeichnung, 3: Zulassunginhaberin, 4: Heilmittelcode, 5: IT-Nummer, 6: ATC-Code
					// 7: Erstzulassung Präparat, 8: Zulassungsdatum Sequenz, 9: Gültigkeitsdatum, 10: Packungscode, 11: Packungsgrösse
					// 12: Einheit, 13: Abgabekategorie Packung, 14: Abgabekategorie Dosisstärke, 15: Abgabekategorie Präparat,
					// 16: Wirkstoff, 17: Zusammensetzung, 18: Anwendungsgebiet Präparat, 19: Anwendungsgebiet Dosisstärke, 20: Gentechnisch hergestellte Wirkstoffe
					// 21: Kategorie bei Insulinen, 22: Betäubungsmittelhaltigen Präparaten

					// @cybermax: 15.10.2013 - work around for Excel cells of type "Special" (cell0 and cell10)
					if (row.getCell(0) != null)
						swissmedic_no5 = String.format("%05d", (int)(row.getCell(0).getNumericCellValue()));	// Swissmedic registration number (5 digits)
					if (row.getCell(2) != null)
						sequence_name = ExcelOps.getCellValue(row.getCell(2)); 		// Sequence name
					if (row.getCell(3) != null)
						owner_str = ExcelOps.getCellValue(row.getCell(3));				// Owner
					if (row.getCell(4) != null)
						heilmittel_code = ExcelOps.getCellValue(row.getCell(4));	// Heilmittelcode
					if (row.getCell(11) != null) {
						package_size = ExcelOps.getCellValue(row.getCell(11));    // Packungsgr?sse
						// Numeric and floating, remove trailing zeros (.00)
						package_size = package_size.replaceAll("\\.00", "");
					}
					if (row.getCell(12) != null)
						package_unit = ExcelOps.getCellValue(row.getCell(12));		// Einheit
					if (row.getCell(13) != null)
						swissmedic_cat = ExcelOps.getCellValue(row.getCell(13));	// Abgabekategorie Packung
					if (row.getCell(18) != null)
						application_area = ExcelOps.getCellValue(row.getCell(18));	// Anwendungsgebiet Präparat
					if (row.getCell(10) != null) {
						package_id = String.format("%03d", (int)(row.getCell(10).getNumericCellValue()));	// Verpackungs ID
						swissmedic_no8 = swissmedic_no5 + package_id;
						// Fill in row
						ArrayList<String> pack = new ArrayList<String>();
						pack.add(swissmedic_no5); 	// 0
						pack.add(sequence_name); 	// 1
						pack.add(heilmittel_code); 	// 2
						pack.add(package_size); 	// 3
						pack.add(package_unit); 	// 4
						pack.add(swissmedic_cat); 	// 5
						if (!application_area.isEmpty())
							pack.add(application_area + " (Swissmedic);"); // 6 = swissmedic + bag
						else
							pack.add("");
						pack.add(public_price); 	// 7
						pack.add(exfactory_price); 	// 8
						pack.add(therapeutic_index);// 9
						// By default the meds are "ausser Handel"
						if (CmlOptions.DB_LANGUAGE.equals("de"))
							withdrawn_str = "a.H.";	// ausser Handel
						else if (CmlOptions.DB_LANGUAGE.equals("fr"))
							withdrawn_str = "p.c.";	//
						else if (CmlOptions.DB_LANGUAGE.equals("it"))
							withdrawn_str = "f.c.";	// fuori commercio
						pack.add(withdrawn_str); 	// 10
						pack.add(speciality_str); 	// 11
						pack.add(plimitation_str); 	// 12
						pack.add(add_info_str); 	// 13
						// 22.03.2014: EAN-13 barcodes - initialization - check digit is missing!
						ean_code_str = "7680" + swissmedic_no8;
						pack.add(ean_code_str); 	// 14
						pack.add(pharma_code_str);	// 15
						pack.add(sequence_name);	// 16
						pack.add(owner_str);		// 17

						m_package_info.put(swissmedic_no8, pack);
					}
				}
				num_rows++;
			}
			long stopTime = System.currentTimeMillis();
			if (CmlOptions.SHOW_LOGS) {
				System.out.println((m_package_info.size() + 1) + " packages in "
						+ (stopTime - startTime) / 1000.0f + " sec");
			}
			startTime = System.currentTimeMillis();

			if (CmlOptions.SHOW_LOGS)
				System.out.print("- Processing atc classes xls... ");
			if (CmlOptions.DB_LANGUAGE.equals("de")) {
				/*
				// Load ATC classes xls file
				FileInputStream atc_classes_file = new FileInputStream(Constants.FILE_ATC_CLASSES_XLS);
				// Get workbook instance for XLS file (HSSF = Horrible SpreadSheet Format)
				HSSFWorkbook atc_classes_workbook = new HSSFWorkbook(atc_classes_file);
				// Get first sheet from workbook
				// HSSFSheet atc_classes_sheet = atc_classes_workbook.getSheetAt(1);	// --> 2013 file
				HSSFSheet atc_classes_sheet = atc_classes_workbook.getSheetAt(0);		// --> 2014 file
				// Iterate through all rows of first sheet
				rowIterator = atc_classes_sheet.iterator();

				num_rows = 0;
				while (rowIterator.hasNext()) {
					Row row = rowIterator.next();
					if (num_rows>2) {
						String atc_code = "";
						String atc_class = "";
						if (row.getCell(0)!=null) {
							atc_code = row.getCell(0).getStringCellValue().replaceAll("\\s", "");
						}
						if (row.getCell(2)!=null) {
							atc_class = row.getCell(2).getStringCellValue();
						}
						// Build a full map atc code to atc class
						if (atc_code.length()>0) {
							m_atc_map.put(atc_code, atc_class);
						}
					}
					num_rows++;
				}
				*/
				CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(Constants.FILE_EPHA_ATC_CODES_CSV), "UTF-8"));
				List<String[]> myEntries = reader.readAll();
				num_rows = myEntries.size();
				for (String[] s : myEntries) {
					if (s.length>2) {
						String atc_code = s[0];
						String atc_class = s[1];
						m_atc_map.put(atc_code, atc_class);
					}
				}
				reader.close();
			} else if (CmlOptions.DB_LANGUAGE.equals("fr") || CmlOptions.DB_LANGUAGE.equals("it")) {
				// Load ATC classes xls file
				FileInputStream atc_classes_file = new FileInputStream(Constants.FILE_WHO_ATC_CLASSES_XLS);
				// Get workbook instance for XLS file (HSSF = Horrible SpreadSheet Format)
				HSSFWorkbook atc_classes_workbook = new HSSFWorkbook(atc_classes_file);
				// Get first sheet from workbook
				HSSFSheet atc_classes_sheet = atc_classes_workbook.getSheetAt(0);		// --> 2014 file
				// Iterate through all rows of first sheet
				rowIterator = atc_classes_sheet.iterator();

				num_rows = 0;
				while (rowIterator.hasNext()) {
					Row row = rowIterator.next();
					if (num_rows>0) {
						String atc_code = "";
						String atc_class = "";
						if (row.getCell(1)!=null) {
							atc_code = row.getCell(1).getStringCellValue();
							if (atc_code.length()>0) {
								// Extract L5 and below
								if (atc_code.length()<6 && row.getCell(2)!=null) {
									atc_class = row.getCell(2).getStringCellValue();
									// Build a full map atc code to atc class
									m_atc_map.put(atc_code, atc_class);
								// Extract L7
								} else if (atc_code.length()==7 && row.getCell(4)!=null) {
									atc_class = row.getCell(4).getStringCellValue();
									m_atc_map.put(atc_code, atc_class);
								}
							}
						}
					}
					num_rows++;
				}

				// Load multilingual ATC classes txt file, replace English with French
				String atc_classes_multi = FileOps.readFromFile(Constants.FILE_ATC_MULTI_LINGUAL_TXT);
				// Loop through all lines
				Scanner scanner = new Scanner(atc_classes_multi);
				while (scanner.hasNextLine()) {
					String line = scanner.nextLine();
					List<String> atc_class = Arrays.asList(line.split(": "));
					String atc_code = atc_class.get(0);
					String[] atc_classes_str = atc_class.get(1).split(";");
					String atc_class_french = atc_classes_str[1].trim();
					// Replaces atc code...
					m_atc_map.put(atc_code, atc_class_french);
				}
				scanner.close();
			}
			stopTime = System.currentTimeMillis();
			if (CmlOptions.SHOW_LOGS)
				System.out.println((m_atc_map.size() + 1) + " classes in " + (stopTime - startTime) / 1000.0f + " sec");

			// Load Refdata xml file
			File refdata_xml_file = new File(Constants.FILE_REFDATA_PHARMA_XML);
			FileInputStream refdata_fis = new FileInputStream(refdata_xml_file);

			startTime = System.currentTimeMillis();
			if (CmlOptions.SHOW_LOGS)
				System.out.print("- Unmarshalling Refdatabase for " + CmlOptions.DB_LANGUAGE + "... ");

			JAXBContext context = JAXBContext.newInstance(Refdata.class);
			Unmarshaller um = context.createUnmarshaller();
			Refdata refdataPharma = (Refdata) um.unmarshal(refdata_fis);
			List<Refdata.ITEM> pharma_list = refdataPharma.getItem();

			String smno8;
			for (Refdata.ITEM pharma : pharma_list) {
				String ean_code = pharma.getGtin();
				String pharma_code = pharma.getPhar();
				String owner = pharma.getAUTHHOLDERNAME();
				if (ean_code.length() == 13) {
					smno8 = ean_code.substring(4, 12);
					// Extract pharma corresponding to swissmedicno8 (source: swissmedic package file)
					ArrayList<String> pi_row = m_package_info.get(smno8);
					// Replace sequence_name
					if (pi_row != null) {
						// Präparatname + galenische Form
						if (CmlOptions.DB_LANGUAGE.equals("de"))
							pi_row.set(1, pharma.getNameDE());
						else if (CmlOptions.DB_LANGUAGE.equals("fr"))
							pi_row.set(1, pharma.getNameFR());
						else if (CmlOptions.DB_LANGUAGE.equals("it")) {
							// Take FR for Italian
							// https://github.com/zdavatz/aips2sqlite/issues/56
							pi_row.set(1, pharma.getNameFR());
						}
						// If med is in refdata file, then it is "in Handel!!" ;)
						pi_row.set(10, "");	// By default this is set to a.H. or p.C.
						// 22.03.2014: EAN-13 barcodes - replace with refdata if package exists
						pi_row.set(14, ean_code);
						// Pharma code
						pi_row.set(15, pharma_code);
						// Owner
						pi_row.set(17, owner);
					}
					else {
						if (CmlOptions.SHOW_ERRORS) {
							if (pharma.getATYPE().equals("PHARMA"))
								System.err.println(">> Does not exist in BAG xls: " + smno8  + " (" + pharma.getNameDE() + ")");
						}
					}
				} else if (ean_code.length() < 13) {
					if (CmlOptions.SHOW_ERRORS)
						System.err.println(">> EAN code too short: " + ean_code + ": " + pharma.getNameDE());
				} else if (ean_code.length() > 13) {
					if (CmlOptions.SHOW_ERRORS)
						System.err.println(">> EAN code too long: " + ean_code + ": " + pharma.getNameDE());
				}
			}

			stopTime = System.currentTimeMillis();
			if (CmlOptions.SHOW_LOGS)
				System.out.println(pharma_list.size() + " medis in " + (stopTime - startTime) / 1000.0f + " sec");

			// Load BAG xml file
			File bag_xml_file = new File(Constants.FILE_PREPARATIONS_XML);
			FileInputStream fis_bag = new FileInputStream(bag_xml_file);

			startTime = System.currentTimeMillis();
			if (CmlOptions.SHOW_LOGS)
				System.out.print("- Processing preparations xml... ");

			context = JAXBContext.newInstance(Preparations.class);
			um = context.createUnmarshaller();
			Preparations prepInfos = (Preparations) um.unmarshal(fis_bag);
			List<Preparations.Preparation> prep_list = prepInfos.getPreparations();

			int num_preparations = 0;
			for (Preparations.Preparation prep : prep_list) {
				String swissmedicno5_str = prep.getSwissmedicNo5();
				if (swissmedicno5_str != null) {
					String orggencode_str = ""; // "O", "G" or empty -> ""
					String flagSB20_str = ""; // "Y" -> 20% or "N" -> 10%
					if (prep.getOrgGenCode() != null)
						orggencode_str = prep.getOrgGenCode();
					if (prep.getFlagSB20() != null) {
						flagSB20_str = prep.getFlagSB20();
						if (flagSB20_str.equals("Y")) {
							if (CmlOptions.DB_LANGUAGE.equals("de"))
								flagSB20_str = "SB 20%";
							else if (CmlOptions.DB_LANGUAGE.equals("fr"))
								flagSB20_str = "QP 20%";
							else if (CmlOptions.DB_LANGUAGE.equals("it"))
								flagSB20_str = "QP 20%";
						} else if (flagSB20_str.equals("N")) {
							if (CmlOptions.DB_LANGUAGE.equals("de"))
								flagSB20_str = "SB 10%";
							else if (CmlOptions.DB_LANGUAGE.equals("fr"))
								flagSB20_str = "QP 10%";
							else if (CmlOptions.DB_LANGUAGE.equals("it"))
								flagSB20_str = "QP 20%";
						} else
							flagSB20_str = "";
					}
					m_add_info_map.put(swissmedicno5_str, orggencode_str + ";" + flagSB20_str);
				}

				List<Preparation.Packs> packs_list = prep.getPacks();
				for (Preparation.Packs packs : packs_list) {
					// Extract codes for therapeutic index / classification
					String bag_application = "";
					String therapeutic_code = "";
					List<Preparations.Preparation.ItCodes> itcode_list = prep.getItCodes();
					for (Preparations.Preparation.ItCodes itc : itcode_list) {
						List<Preparations.Preparation.ItCodes.ItCode> code_list = itc.getItCode();
						int index = 0;
						for (Preparations.Preparation.ItCodes.ItCode code : code_list) {
							if (index == 0) {
								if (CmlOptions.DB_LANGUAGE.equals("de"))
									therapeutic_code = code.getDescriptionDe();
								else if (CmlOptions.DB_LANGUAGE.equals("fr"))
									therapeutic_code = code.getDescriptionFr();
								else if (CmlOptions.DB_LANGUAGE.equals("it"))
									therapeutic_code = code.getDescriptionIt();
							} else {
								if (CmlOptions.DB_LANGUAGE.equals("de"))
									bag_application = code.getDescriptionDe();
								else if (CmlOptions.DB_LANGUAGE.equals("fr"))
									bag_application = code.getDescriptionFr();
								else if (CmlOptions.DB_LANGUAGE.equals("it"))
									bag_application = code.getDescriptionIt();
							}
							index++;
						}
					}
					// Generate new package info
					List<Preparation.Packs.Pack> pack_list = packs.getPack();
					for (Preparation.Packs.Pack pack : pack_list) {
						// Get SwissmedicNo8 and used it as a key to extract all the relevant package info
						String swissMedicNo8 = pack.getSwissmedicNo8();
						ArrayList<String> pi_row = null;
						if (swissMedicNo8 != null)
							pi_row = m_package_info.get(swissMedicNo8);
						// Preparation also in BAG xml file (we have a price)
						if (pi_row != null) {
							// Update Swissmedic catory if necessary ("N->A", Y->"A+")
							if (pack.getFlagNarcosis().equals("Y"))
								pi_row.set(5, pi_row.get(5) + "+");
							// Extract point limitations
							List<Preparations.Preparation.Packs.Pack.PointLimitations> point_limits = pack.getPointLimitations();
							for (Preparations.Preparation.Packs.Pack.PointLimitations limits : point_limits) {
								List<Preparations.Preparation.Packs.Pack.PointLimitations.PointLimitation> plimits_list = limits.getPointLimitation();
								if (plimits_list.size() > 0)
									if (plimits_list.get(0) != null)
										pi_row.set(12, ", LIM" + plimits_list.get(0).getPoints() + "");
							}
							// Extract exfactory and public prices
							List<Preparations.Preparation.Packs.Pack.Prices> price_list = pack.getPrices();
							for (Preparations.Preparation.Packs.Pack.Prices price : price_list) {
								List<Preparations.Preparation.Packs.Pack.Prices.PublicPrice> public_price = price
										.getPublicPrice();
								List<Preparations.Preparation.Packs.Pack.Prices.ExFactoryPrice> exfactory_price = price
										.getExFactoryPrice();
								if (exfactory_price.size() > 0) {
									try {
										float f = Float.valueOf(exfactory_price.get(0).getPrice());
										String ep = String.format("%.2f", f);
										pi_row.set(8, "CHF " + ep);
									} catch (NumberFormatException e) {
										if (CmlOptions.SHOW_ERRORS)
											System.err.println("Number format exception (exfactory price): " + swissMedicNo8
													+ " (" + public_price.size() + ")");
									}
								}
								if (public_price.size() > 0) {
									try {
										float f = Float.valueOf(public_price.get(0).getPrice());
										String pp = String.format("%.2f", f);
										pi_row.set(7, "CHF " + pp);
										if (CmlOptions.DB_LANGUAGE.equals("de"))
											pi_row.set(11, ", SL");
										else if (CmlOptions.DB_LANGUAGE.equals("fr"))
											pi_row.set(11, ", LS");
										else if (CmlOptions.DB_LANGUAGE.equals("it"))
											pi_row.set(11, ", LS");
									} catch (NullPointerException e) {
										if (CmlOptions.SHOW_ERRORS)
											System.err.println("Null pointer exception (public price): " + swissMedicNo8
													+ " (" + public_price.size() + ")");
									} catch (NumberFormatException e) {
										if (CmlOptions.SHOW_ERRORS)
											System.err.println("Number format exception (public price): " + swissMedicNo8
													+ " (" + public_price.size() + ")");
									}
								}
								// Add application area and therapeutic code
								if (!bag_application.isEmpty())
									pi_row.set(6, pi_row.get(6)	+ bag_application + " (BAG)");
								pi_row.set(9, therapeutic_code);
							}
						}
					}
				}
				num_preparations++;
			}

			stopTime = System.currentTimeMillis();
			if (CmlOptions.SHOW_LOGS)
				System.out.println(num_preparations + " preparations in " + (stopTime - startTime) / 1000.0f + " sec");

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JAXBException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Extracts Swiss DRG info from Swiss DRG Excel file
	 */
	private void extractSwissDRGInfo() {
		try {
			long startTime = System.currentTimeMillis();
			if (CmlOptions.SHOW_LOGS)
				System.out.print("- Processing Swiss DRG xlsx... ");
			// Load Swiss DRG file
			FileInputStream swiss_drg_file = null;
			if (CmlOptions.DB_LANGUAGE.equals("de"))
				swiss_drg_file = new FileInputStream(Constants.FILE_SWISS_DRG_DE_XLSX);
			else if (CmlOptions.DB_LANGUAGE.equals("fr"))
				swiss_drg_file = new FileInputStream(Constants.FILE_SWISS_DRG_FR_XLSX);
			else if (CmlOptions.DB_LANGUAGE.equals("it"))
				swiss_drg_file = new FileInputStream(Constants.FILE_SWISS_DRG_IT_XLSX);
			else
				swiss_drg_file = new FileInputStream(Constants.FILE_SWISS_DRG_DE_XLSX);

			// Get workbook instance for XLSX file (XSSF = Horrible SpreadSheet Format)
			XSSFWorkbook swiss_drg_workbook = new XSSFWorkbook(swiss_drg_file);

			// Get "Anlage 2 und Anlage 3"
			String zusatz_entgelt = "";
			String atc_code = "";
			String dosage_class = "";
			String price = "";

			// TODO: Add code for Anlage 3 (a==5)
			for (int a=4; a<=4; a++) {
				int num_rows = 0;
				String current_footnote = "";

				XSSFSheet swiss_drg_sheet = swiss_drg_workbook.getSheetAt(a);

				// Iterate through all rows of first sheet
				Iterator<Row> rowIterator = swiss_drg_sheet.iterator();

				while (rowIterator.hasNext()) {
					if (num_rows>7) {
						Row row = rowIterator.next();
						if (row.getCell(0)!=null)	// Zusatzentgelt
							zusatz_entgelt = ExcelOps.getCellValue(row.getCell(0));
						if (row.getCell(2)!= null)	// ATC Code
							atc_code = ExcelOps.getCellValue(row.getCell(2)).replaceAll("[^A-Za-z0-9.]", "");
						if (row.getCell(3)!=null)	// Dosage class
							dosage_class = ExcelOps.getCellValue(row.getCell(3));
						if (row.getCell(4)!=null) 	// Price
							price = ExcelOps.getCellValue(row.getCell(4));

						if (!zusatz_entgelt.isEmpty() && !dosage_class.isEmpty() && !price.isEmpty() &&
								!atc_code.contains(".") && !dosage_class.equals("BLANK") && !price.equals("BLANK")) {
							String swiss_drg_str = "";
							if (a==4) {
								if (CmlOptions.DB_LANGUAGE.equals("de"))
									swiss_drg_str = zusatz_entgelt + ", Dosierung " + dosage_class + ", CHF " + price;
								else if (CmlOptions.DB_LANGUAGE.equals("fr"))
									swiss_drg_str = zusatz_entgelt + ", dosage " + dosage_class + ", CHF " + price;
								else if (CmlOptions.DB_LANGUAGE.equals("it"))
									swiss_drg_str = zusatz_entgelt + ", dosaggio " + dosage_class + ", CHF " + price;
							}
							else if (a==5)
								swiss_drg_str = zusatz_entgelt + ", " + price;

							// Get list of dosages for a particular atc code
							ArrayList<String> dosages = m_swiss_drg_info.get(atc_code);
							// If there is no list, create a new one
							if (dosages==null)
								dosages = new ArrayList<String>();
							dosages.add(swiss_drg_str);
							// Update global swiss drg list
							m_swiss_drg_info.put(atc_code, dosages);
							// Update footnote map
							m_swiss_drg_footnote.put(atc_code, current_footnote);
						} else if (!zusatz_entgelt.isEmpty() && dosage_class.equals("BLANK") && price.equals("BLANK")) {
							if (zusatz_entgelt.contains(" ")) {
								String[] sub_script = zusatz_entgelt.split(" ");
								if (sub_script.length>1 && sub_script[0].contains("ZE")) {
									// Update atc code to footnote map
									current_footnote = sub_script[1];
								}
							}
						}
					}
					num_rows++;
				}
			}

			long stopTime = System.currentTimeMillis();
			if (CmlOptions.SHOW_LOGS) {
				System.out.println("processed all Swiss DRG packages in " + (stopTime - startTime) / 1000.0f + " sec");
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Extract EPha SwissmedicNo5 to ATC map
	 */
	private void extractSwissmedicNo5ToAtcMap() {
		try {
			long startTime = System.currentTimeMillis();

			if (CmlOptions.SHOW_LOGS)
				System.out.print("- Processing EPha product json file... ");
			// Load EPha product json file
			if (CmlOptions.DB_LANGUAGE.equals("de")) {
				ObjectMapper mapper = new ObjectMapper(); // can reuse, share globally
				TypeReference<HashMap<String,Object>> typeRef = new TypeReference<HashMap<String,Object>>() {};
				Map<String,Object> ephaProductData = mapper.readValue(new File(Constants.FILE_EPHA_PRODUCTS_DE_JSON), typeRef);
				@SuppressWarnings("unchecked")
				ArrayList<HashMap<String,String>> medList = (ArrayList<HashMap<String,String>>)ephaProductData.get("documents");
				for (HashMap<String,String> med : medList) {
					String s[] = med.get("zulassung").split(" ");
					for (String smno5 : s)
						m_smn5_atc_map.put(smno5, med.get("atc"));
				}
			} else if (CmlOptions.DB_LANGUAGE.equals("fr")) {
				ObjectMapper mapper = new ObjectMapper(); // can reuse, share globally
				TypeReference<HashMap<String,Object>> typeRef = new TypeReference<HashMap<String,Object>>() {};
				Map<String,Object> ephaProductData = mapper.readValue(new File(Constants.FILE_EPHA_PRODUCTS_FR_JSON), typeRef);
				@SuppressWarnings("unchecked")
				ArrayList<HashMap<String,String>> medList = (ArrayList<HashMap<String,String>>)ephaProductData.get("documents");
				for (HashMap<String,String> med : medList) {
					String s[] = med.get("zulassung").split(" ");
					for (String smno5 : s)
						m_smn5_atc_map.put(smno5, med.get("atc"));
				}
			} else if (CmlOptions.DB_LANGUAGE.equals("it")) {
				ObjectMapper mapper = new ObjectMapper(); // can reuse, share globally
				TypeReference<HashMap<String,Object>> typeRef = new TypeReference<HashMap<String,Object>>() {};
				Map<String,Object> ephaProductData = mapper.readValue(new File(Constants.FILE_EPHA_PRODUCTS_IT_JSON), typeRef);
				@SuppressWarnings("unchecked")
				ArrayList<HashMap<String,String>> medList = (ArrayList<HashMap<String,String>>)ephaProductData.get("documents");
				for (HashMap<String,String> med : medList) {
					String s[] = med.get("zulassung").split(" ");
					for (String smno5 : s)
						m_smn5_atc_map.put(smno5, med.get("atc"));
				}
			}

			long stopTime = System.currentTimeMillis();
			if (CmlOptions.SHOW_LOGS) {
				System.out.println("processed EPha product json file in " + (stopTime - startTime) / 1000.0f + " sec");
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Mapping between swissmedicno5 registration numbers and names
	 */
	private void extractReg5noNamesMapping() {
		m_smn5_to_list_of_names = new HashMap<String, ArrayList<String>>();

		for (MedicalInformations.MedicalInformation m : m_med_list) {
			// --> Read from aips.xml file
			if (m.getLang().equals(CmlOptions.DB_LANGUAGE) && m.getType().equals("fi")) {
				String title_aips = m.getTitle();
				String regnr_str = m.getAuthNrs();

				ArrayList<String> list_of_names = new ArrayList<String>();
				if (m_smn5_to_list_of_names.containsKey(regnr_str)) {
					list_of_names = m_smn5_to_list_of_names.get(regnr_str);
				}
				list_of_names.add(title_aips);
				m_smn5_to_list_of_names.put(regnr_str, list_of_names);
			}
		}
	}


	/**
	 * Main data processing happens here...
	 */
	public void process() {

		// Get stop words first
		getStopWords();

		// Extract EPha SwissmedicNo5 to ATC map
		extractSwissmedicNo5ToAtcMap();

		// Extract package information (this is the heavy-duty bit)
		extractPackageInfo();

		// Extract Swiss DRG information
		extractSwissDRGInfo();

		// Extract between swissmedicno5 and names
		extractReg5noNamesMapping();

		try {
			// Load CSS file: used only for self-contained xml files
			String amiko_style_v1_str = FileOps.readCSSfromFile(Constants.FILE_STYLE_CSS_BASE + "v1.css");

			// Create error report file
			ParseReport parse_errors = null;
			if (CmlOptions.GENERATE_REPORTS==true) {
				parse_errors = new ParseReport(Constants.FILE_PARSE_REPORT, CmlOptions.DB_LANGUAGE, "html");
				if (CmlOptions.DB_LANGUAGE.equals("de"))
					parse_errors.addHtmlHeader("Schweizer Arzneimittel-Kompendium", Constants.FI_DB_VERSION);
				else if (CmlOptions.DB_LANGUAGE.equals("fr"))
					parse_errors.addHtmlHeader("Compendium des Médicaments Suisse", Constants.FI_DB_VERSION);
				else if (CmlOptions.DB_LANGUAGE.equals("it"))
					parse_errors.addHtmlHeader("Compendio svizzero dei farmaci", Constants.FI_DB_VERSION);
			}

			// Create indications report file
			BufferedWriter bw_indications = null;
			Map<String, String> tm_indications = new TreeMap<String, String>();
			if (CmlOptions.INDICATIONS_REPORT==true) {
				ParseReport indications_report = new ParseReport(Constants.FILE_INDICATIONS_REPORT, CmlOptions.DB_LANGUAGE, "txt");
				bw_indications = indications_report.getBWriter();
			}

			/*
			 * Add pseudo Fachinfos to SQLite database
			 */
			int tot_pseudo_counter = 0;
			if (CmlOptions.ADD_PSEUDO_FI==true) {
				PseudoExpertInfo pseudo_fi = new PseudoExpertInfo(m_sql_db, CmlOptions.DB_LANGUAGE, m_map_products);
				// Process
				tot_pseudo_counter = pseudo_fi.process();
				System.out.println("");
			}

			/*
			 * Add real Fachinfos to SQLite database
			 */
			// Initialize counters for different languages
			int med_counter = 0;
			int tot_med_counter = 0;
			int missing_regnr_str = 0;
			int missing_pack_info = 0;
			int missing_atc_code = 0;
			int errors = 0;
			String fi_complete_xml = "";
			MessageDigest complete_xml_hash_code_digest = null;
			try {
				complete_xml_hash_code_digest = MessageDigest.getInstance("SHA-256");
			} catch(NoSuchAlgorithmException e) {
				e.printStackTrace();
			}

			// First pass is always with DB_LANGUAGE set to German! (most complete information)
			// The file dumped in ./reports is fed to AllDown.java to generate a multilingual ATC code / ATC class file, e.g. German - French
			Set<String> atccode_set = new TreeSet<String>();

			// Treemap for owner error report (sorted by key)
			TreeMap<String, ArrayList<String>> tm_owner_error = new TreeMap<String, ArrayList<String>>();

			HtmlUtils html_utils = null;

			System.out.println("Processing real Fachinfos...");

			// --> Read FACHINFOS! <--
			for (MedicalInformations.MedicalInformation m : m_med_list)
				if (m.getLang().equals(CmlOptions.DB_LANGUAGE) && m.getType().equals("fi")
						/* && m.getAuthHolder().toLowerCase().contains("ibsa") */) {
					// Database contains less than 5000 medis - this is a safe upperbound!
					if (tot_med_counter < 5000) {
						// Trim titles of leading and trailing spaces
						m.setTitle(m.getTitle().trim());
						// Extract section titles and section ids
						MedicalInformations.MedicalInformation.Sections med_sections = m.getSections();
						List<MedicalInformations.MedicalInformation.Sections.Section> med_section_list = med_sections.getSection();
						String ids_str = "";
						String titles_str = "";
						for (MedicalInformations.MedicalInformation.Sections.Section s : med_section_list) {
							ids_str += (s.getId() + ",");
							titles_str += (s.getTitle() + ";");
						}

						Document doc = Jsoup.parse(m.getContent());
						doc.outputSettings().escapeMode(EscapeMode.xhtml);

						html_utils = new HtmlUtils(m.getContent());
						html_utils.setLanguage(CmlOptions.DB_LANGUAGE);
						html_utils.clean();

						// Extract registration number (swissmedic no5)
						String regnr_str = "";
						if (CmlOptions.DB_LANGUAGE.equals("de"))
							regnr_str = html_utils.extractRegNrDE(m.getTitle());
						else if (CmlOptions.DB_LANGUAGE.equals("fr"))
							regnr_str = html_utils.extractRegNrFR(m.getTitle());
						else if (CmlOptions.DB_LANGUAGE.equals("it")) {
							regnr_str = html_utils.extractRegNrIt(m.getTitle());
						}

						// Pattern matcher for regnr command line option, (?s) searches across multiple lines
						Pattern regnr_pattern = Pattern.compile("(?s).*\\b" + CmlOptions.OPT_MED_REGNR);

						if (m.getTitle().toLowerCase().startsWith(CmlOptions.OPT_MED_TITLE.toLowerCase())
								&& regnr_pattern.matcher(regnr_str).find()
								&& m.getAuthHolder().toLowerCase().startsWith(CmlOptions.OPT_MED_OWNER.toLowerCase())) {

							System.out.println(tot_med_counter + " - " + m.getTitle() + ": " + regnr_str);

							if (regnr_str.isEmpty()) {
								errors++;
								if (CmlOptions.GENERATE_REPORTS == true) {
									parse_errors.append("<p style=\"color:#ff0099\">ERROR " + errors + ": reg. nr. could not be parsed in AIPS.xml (swissmedic) - " + m.getTitle() + " (" + regnr_str + ")</p>");
									// Add to owner errors
									ArrayList<String> error = tm_owner_error.get(m.getAuthHolder());
									if (error == null)
										error = new ArrayList<>();
									error.add(m.getTitle() + ";regnr");
									tm_owner_error.put(m.getAuthHolder(), error);
								}
								missing_regnr_str++;
								regnr_str = "";
							}

							// Associate ATC classes and subclasses (atc_map)
							String atc_class_str = "";
							String atc_description_str = "";
							// This bit is necessary because the ATC Code in the AIPS DB is broken sometimes
							String atc_code_str = "";

							boolean atc_error_found = false;

							// Use EPha ATC Codes, AIPS is fallback solution
							String authNrs = m.getAuthNrs();
							if (authNrs != null) {
								// Deal with multi-swissmedic no5 case
								String regnrs[] = authNrs.split(",");
								// Use set to avoid duplicate ATC codes
								Set<String> regnrs_set = new LinkedHashSet<>();
								// Loop through EPha ATC codes
								for (String r : regnrs) {
									regnrs_set.add(m_smn5_atc_map.get(r.trim()));
								}
								// Iterate through set and format nicely
								for (String r : regnrs_set) {
									if (atc_code_str == null || atc_code_str.isEmpty())
										atc_code_str = r;
									else
										atc_code_str += "," + r;
								}
							} else
								atc_error_found = true;

							// Notify any other problem with the EPha ATC codes
							if (atc_code_str == null || atc_code_str.isEmpty())
								atc_error_found = true;

							// Fallback solution
							if (atc_error_found == true) {
								if (m.getAtcCode() != null && !m.getAtcCode().equals("n.a.") && m.getAtcCode().length() > 1) {
									atc_code_str = m.getAtcCode();
									atc_code_str = atc_code_str.replaceAll("&ndash;", "(");
									atc_code_str = atc_code_str.replaceAll("Code", "").replaceAll("ATC", "")
											.replaceAll("&nbsp", "").replaceAll("\\(.*", "").replaceAll("/", ",")
											.replaceAll("[^A-Za-z0-9äöü,]", "");
									if (atc_code_str.charAt(1) == 'O') {
										// E.g. Ascosal Brausetabletten
										atc_code_str = atc_code_str.substring(0, 1) + '0' + atc_code_str.substring(2);
									}
									if (atc_code_str.length() > 7) {
										if (atc_code_str.charAt(7) != ',' || atc_code_str.length() != 15)
											atc_code_str = atc_code_str.substring(0, 7);
									}
								} else {
									// Work backwards using m_atc_map and m.getSubstances()
									String substances = m.getSubstances();
									if (substances != null) {
										if (m_atc_map.containsValue(substances)) {
											for (Map.Entry<String, String> entry : m_atc_map.entrySet()) {
												if (entry.getValue().equals(substances)) {
													atc_code_str = entry.getKey();
												}
											}
										}
									}
								}
								atc_error_found = false;
							}

							// Now let's clean the m.getSubstances()
							String substances = m.getSubstances();
							if ((substances == null || substances.length() < 3) && atc_code_str != null) {
								substances = m_atc_map.get(atc_code_str);
							}

							// Set clean substances
							m.setSubstances(substances);
							// Set clean ATC Code
							m.setAtcCode(atc_code_str);

							// System.out.println("ATC -> " + atc_code_str + ": " + substances);

							if (atc_code_str != null) {
								// \\s -> whitespace character, short for [ \t\n\x0b\r\f]
								// atc_code_str = atc_code_str.replaceAll("\\s","");
								// Take "leave" of the tree (most precise classification)
								String a = m_atc_map.get(atc_code_str);
								if (a != null) {
									atc_description_str = a;
									atccode_set.add(atc_code_str + ": " + a);
								} else {
									// Case: ATC1,ATC2
									if (atc_code_str.length() == 15) {
										String[] codes = atc_code_str.split(",");
										if (codes.length > 1) {
											String a1 = m_atc_map.get(codes[0]);
											if (a1 == null) {
												atc_error_found = true;
												a1 = "k.A.";
											}
											String a2 = m_atc_map.get(codes[1]);
											if (a2 == null) {
												atc_error_found = true;
												a2 = "k.A.";
											}
											atc_description_str = a1 + "," + a2;
										}
									} else if (m.getSubstances() != null) {
										// Fallback in case nothing else works
										atc_description_str = m.getSubstances();
										// Work backwards using m_atc_map and m.getSubstances(), change ATC code
										if (atc_description_str != null) {
											if (m_atc_map.containsValue(atc_description_str)) {
												for (Map.Entry<String, String> entry : m_atc_map.entrySet()) {
													if (entry.getValue().equals(atc_description_str)) {
														m.setAtcCode(entry.getKey());
													}
												}
											}
										}
									} else {
										atc_error_found = true;
										if (CmlOptions.DB_LANGUAGE.equals("de"))
											atc_description_str = "k.A.";
										else if (CmlOptions.DB_LANGUAGE.equals("fr"))
											atc_description_str = "n.s.";
										else if (CmlOptions.DB_LANGUAGE.equals("it"))
											atc_description_str = "n.d.";
									}
								}

								// Read out only two levels (L1, L3, L4, L5)
								for (int i = 1; i < 6; i++) {
									if (i != 2) {
										String atc_key = "";
										if (i <= atc_code_str.length())
											atc_key = atc_code_str.substring(0, i);
										char sep = (i >= 4) ? '#' : ';';    // #-separator between L4 and L5
										if (atc_key != null) {
											String c = m_atc_map.get(atc_key);
											if (c != null) {
												atccode_set.add(atc_key + ": " + c);
												atc_class_str += (c + sep);
											} else {
												atc_class_str += sep;
											}
										} else {
											atc_class_str += sep;
										}
									}
								}

								// System.out.println("atc class = " + atc_class_str);

								// If DRG medication, add to atc_description_str
								ArrayList<String> drg = m_swiss_drg_info.get(atc_code_str);
								if (drg != null) {
									atc_description_str += (";DRG");
								}
							}

							if (atc_error_found) {
								errors++;
								if (CmlOptions.GENERATE_REPORTS) {
									parse_errors.append("<p style=\"color:#0000bb\">ERROR " + errors + ": Broken or missing ATC-Code-Tag in AIPS.xml (Swissmedic) or ATC index (Wido) - " + m.getTitle() + " (" + regnr_str + ")</p>");
									// Add to owner errors
									ArrayList<String> error = tm_owner_error.get(m.getAuthHolder());
									if (error == null)
										error = new ArrayList<>();
									error.add(m.getTitle() + ";atccode");
									tm_owner_error.put(m.getAuthHolder(), error);
								}
								System.err.println(">> ERROR: " + tot_med_counter + " - no ATC-Code found in the XML-Tag \"atcCode\" - (" + regnr_str + ") " + m.getTitle());
								missing_atc_code++;
							}

							// Additional info stored in add_info_map
							String add_info_str = ";";
							List<String> rnr_list = Arrays.asList(regnr_str.split("\\s*, \\s*"));
							if (rnr_list.size() > 0)
								add_info_str = m_add_info_map.get(rnr_list.get(0));

							// Sanitize html
							String html_sanitized = "";
							// First check for bad boys (version=1! but actually version>1!)
							if (!m.getVersion().equals("1") || m.getContent().substring(0, 20).contains("xml")) {
								for (int i = 1; i < 22; ++i) {
									html_sanitized += html_utils.sanitizeSection(i, m.getTitle(), m.getAuthHolder(), CmlOptions.DB_LANGUAGE);
								}
								html_sanitized = "<div id=\"monographie\">" + html_sanitized + "</div>";
							} else {
								html_sanitized = m.getContent();
							}

							// Add author number
							html_sanitized = html_sanitized.replaceAll("<div id=\"monographie\">", "<div id=\"monographie\" name=\"" + m.getAuthNrs() + "\">");

							// Add Footer, timestamp in RFC822 format
							DateFormat dateFormat = new SimpleDateFormat("EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss' 'Z", Locale.getDefault());
							Date date = new Date();
							String footer_str = "<p class=\"footer\">Auto-generated by <a href=\"https://github.com/zdavatz/aips2sqlite\">aips2sqlite</a> on "
									+ dateFormat.format(date) + "</p>";

							// html_sanitized += footer_str;
							html_sanitized = html_sanitized.replaceAll("</div>$", footer_str + "</div>");

							// Extract section indications
							String section_indications = "";
							if (CmlOptions.DB_LANGUAGE.equals("de")) {
								String sstr1 = "Indikationen/Anwendungsmöglichkeiten";
								String sstr2 = "Dosierung/Anwendung";
								if (html_sanitized.contains(sstr1) && html_sanitized.contains(sstr2)) {
									int idx1 = html_sanitized.indexOf(sstr1) + sstr1.length();
									int idx2 = html_sanitized.substring(idx1, html_sanitized.length()).indexOf(sstr2);
									try {
										section_indications = html_sanitized.substring(idx1, idx1 + idx2);
									} catch (StringIndexOutOfBoundsException e) {
										e.printStackTrace();
									}
								}
							} else if (CmlOptions.DB_LANGUAGE.equals("fr")) {
								String sstr1 = "Indications/Possibilités demploi";
								String sstr2 = "Posologie/Mode demploi";

								html_sanitized = html_sanitized.replaceAll("Indications/Possibilités d&apos;emploi", sstr1);
								html_sanitized = html_sanitized.replaceAll("Posologie/Mode d&apos;emploi", sstr2);
								html_sanitized = html_sanitized.replaceAll("Indications/possibilités demploi", sstr1);
								html_sanitized = html_sanitized.replaceAll("Posologie/mode demploi", sstr2);

								if (html_sanitized.contains(sstr1) && html_sanitized.contains(sstr2)) {
									int idx1 = html_sanitized.indexOf(sstr1) + sstr1.length();
									int idx2 = html_sanitized.substring(idx1, html_sanitized.length()).indexOf(sstr2);
									try {
										if (idx1 >= 0 && idx2 >= 0 && idx1 < (idx1 + idx2))
											section_indications = html_sanitized.substring(idx1, idx1 + idx2);
									} catch (StringIndexOutOfBoundsException e) {
										e.printStackTrace();
									}
								}
							} else if (CmlOptions.DB_LANGUAGE.equals("it")) {
								String sstr1 = "Indicazioni/Possibilità d'impiego";
								String sstr2 = "Posologia/Impiego";

								html_sanitized = html_sanitized.replaceAll("Indicazioni/Possibilit&agrave; d'impiego", sstr1);

								if (html_sanitized.contains(sstr1) && html_sanitized.contains(sstr2)) {
									int idx1 = html_sanitized.indexOf(sstr1) + sstr1.length();
									int idx2 = html_sanitized.substring(idx1, html_sanitized.length()).indexOf(sstr2);
									try {
										section_indications = html_sanitized.substring(idx1, idx1 + idx2);
									} catch (StringIndexOutOfBoundsException e) {
										e.printStackTrace();
									}
								}
							}

							// Remove all p's, div's, span's and sup's
							section_indications = section_indications.replaceAll("\\<p.*?\\>", "").replaceAll("</p>", "");
							section_indications = section_indications.replaceAll("\\<div.*?\\>", "").replaceAll("</div>", "");
							section_indications = section_indications.replaceAll("\\<span.*?\\>", "").replaceAll("</span>", "");
							section_indications = section_indications.replaceAll("\\<sup.*?\\>", "").replaceAll("</sup>", "");

							// System.out.println(section_indications);

							if (CmlOptions.DB_LANGUAGE.equals("fr")) {
								// Remove apostrophes
								section_indications = section_indications.replaceAll("l&apos;", "").replaceAll("d&apos;", "");
								section_indications = section_indications.replaceAll("l", "").replaceAll("d", "");
							}
							// Remove all URLs
							section_indications = section_indications.replaceAll("\\b(http|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]", "");
							// Remove list of type a) b) c) ... 1) 2) ...
							section_indications = section_indications.replaceAll("^\\w\\)", "");
							// Remove numbers, commas, semicolons, parentheses, etc.
							section_indications = section_indications.replaceAll("[^A-Za-z\\xC0-\\xFF- ]", "");
							// Generate long list of keywords
							LinkedList<String> wordsAsList = new LinkedList<>(
									Arrays.asList(section_indications.split("\\s+")));
							// Remove stop words
							Iterator<String> wordIterator = wordsAsList.iterator();
							while (wordIterator.hasNext()) {
								// Note: This assumes there are no null entries in the list and all stopwords are stored in lower case
								String word = wordIterator.next().trim().toLowerCase();
								if (word.length() < 3 || m.getTitle().toLowerCase().contains(word) || m_stop_words_hash.contains(word))
									wordIterator.remove();
							}
							section_indications = "";
							for (String w : wordsAsList) {
								// Remove any leading dash or hyphen
								if (w.startsWith("-"))
									w = w.substring(1);
								section_indications += (w + ";");
								if (CmlOptions.INDICATIONS_REPORT == true) {
									// Add to map (key->value), word = key, value = how many times used
									// Is word w already stored in treemap?
									String t_str = tm_indications.get(w);
									if (t_str == null) {
										t_str = m.getTitle();
										tm_indications.put(w, t_str);
									} else {
										t_str += (", " + m.getTitle());
										tm_indications.put(w, t_str);
									}
								}
							}

							/**
							 * Update section "Packungen", generate packungen string for shopping cart, and extract therapeutisches index
							 */
							List<String> mTyIndex_list = new ArrayList<>();
							m_list_of_packages.clear();
							m_list_of_eancodes.clear();
							String mContent_str = updateSectionPackungen(m.getTitle(), m.authHolder, m.getAtcCode(), m_package_info, regnr_str, html_sanitized, mTyIndex_list);

							m.setContent(mContent_str);

							// Check if mPackSection_str is empty AND command line option PLAIN is not active
							if (CmlOptions.PLAIN == false && m_pack_info_str.isEmpty()) {
								errors++;
								if (CmlOptions.GENERATE_REPORTS) {
									parse_errors.append("<p style=\"color:#bb0000\">ERROR " + errors + ": SwissmedicNo5 not found in Packungen.xls (Swissmedic) - " + m.getTitle() + " (" + regnr_str + ")</p>");
									// Add to owner errors
									ArrayList<String> error = tm_owner_error.get(m.getAuthHolder());
									if (error == null)
										error = new ArrayList<>();
									error.add(m.getTitle() + ";swissmedic5");
									tm_owner_error.put(m.getAuthHolder(), error);
								}
								System.err.println(">> ERROR: " + tot_med_counter + " - SwissmedicNo5 not found in Swissmedic Packungen.xls - (" + regnr_str + ") " + m.getTitle());
								missing_pack_info++;
							}

							// Fix problem with wrong div class in original Swissmedic file
							if (CmlOptions.DB_LANGUAGE.equals("de")) {
								m.setStyle(m.getStyle().replaceAll("untertitel", "untertitle"));
								m.setStyle(m.getStyle().replaceAll("untertitel1", "untertitle1"));
							}

							// Correct formatting error introduced by Swissmedic
							m.setAuthHolder(m.getAuthHolder().replaceAll("&#038;", "&"));

							// Check if substances str has a '$a' and change it to '&alpha'
							if (m.getSubstances() != null)
								m.setSubstances(m.getSubstances().replaceAll("\\$a", "&alpha;"));

							if (CmlOptions.XML_FILE == true) {
								if (!regnr_str.isEmpty()) {

									// Generate and add hash code
									String html_str_no_timestamp = mContent_str.replaceAll("<p class=\"footer\">.*?</p>", "");
									String hash_code = html_utils.calcHashCode(html_str_no_timestamp);

									// Add header to html file
									mContent_str = mContent_str.replaceAll("<head>", "<head>" +
											"<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" name=\"fi_" + hash_code + "\"/>" +
											"<style>" + amiko_style_v1_str + "</style>");

									// Note: the following line is not necessary!
									// m.setContent(mContent_str);

									// Add header to xml file
									String xml_str = html_utils.convertHtmlToXml("fi", m.getTitle(), mContent_str, regnr_str);
									fi_complete_xml += (xml_str + "\n");

									BufferedWriter writer = null;
									// Write to html and xml files to disk
									String name = m.getTitle();
									// Replace all "Sonderzeichen"
									name = name.replaceAll("é","e");
									name = name.replaceAll("à","a");
									name = name.replaceAll("è","e");
									name = name.replaceAll("ê","e");
									name = name.replaceAll("É","E");
									name = name.replaceAll("î","i");
									name = name.replaceAll("ç","c");
									name = name.replaceAll("ä","a");
									name = name.replaceAll("ö","o");
									name = name.replaceAll("Ä","A");
									name = name.replaceAll("ü","u");
									name = name.replaceAll("[^a-zA-Z0-9]+", "_");
									if (CmlOptions.DB_LANGUAGE.equals("de")) {
										FileOps.writeToFile(mContent_str, Constants.FI_FILE_XML_BASE + "fi_de_html/", name + "_fi_de.html");
										writer = FileOps.writerToFile(Constants.FI_FILE_XML_BASE + "fi_de_xml/", name + "_fi_de.xml");
									} else if (CmlOptions.DB_LANGUAGE.equals("fr")) {
										FileOps.writeToFile(mContent_str, Constants.FI_FILE_XML_BASE + "fi_fr_html/", name + "_fi_fr.html");
										writer = FileOps.writerToFile(Constants.FI_FILE_XML_BASE + "fi_fr_xml/", name + "_fi_fr.xml");
									} else if (CmlOptions.DB_LANGUAGE.equals("it")) {
										FileOps.writeToFile(mContent_str, Constants.FI_FILE_XML_BASE + "fi_fr_html/", name + "_fi_it.html");
										writer = FileOps.writerToFile(Constants.FI_FILE_XML_BASE + "fi_it_xml/", name + "_fi_it.xml");
									}
									String xml_for_hash_code = "";
									if (writer != null) {
										xml_for_hash_code = html_utils.addHeaderToXml("singlefi", xml_str, writer);
										writer.close();
									}
									complete_xml_hash_code_digest.update(xml_for_hash_code.getBytes("UTF-8"));
								}
							}

							int customer_id = 0;
							// Is the customer paying? If yes add customer id
							// str1.toLowerCase().contains(str2.toLowerCase())
							if (m.getAuthHolder().toLowerCase().contains("desitin"))
								customer_id = 1;
							/*
							/ HERE GO THE OTHER PAYING CUSTOMERS (increment customer_id respectively)
							*/

							// Extract (O)riginal / (G)enerika info
							String orggen_str = "";
							if (add_info_str != null) {
								List<String> ai_list = Arrays.asList(add_info_str.split("\\s*;\\s*"));
								if (ai_list != null) {
									if (!ai_list.get(0).isEmpty())
										orggen_str = ai_list.get(0);
								}
							}

							// @maxl: 25.04.2015 -> set orggen_str to nil (we are using add_info_str for group names now...)
							orggen_str = "";

							/*
							 * Add medis, titles and ids to database
							 */
							String packages_str = "";
							for (String s : m_list_of_packages)
								packages_str += s;
							String eancodes_str = "";
							for (String e : m_list_of_eancodes)
								eancodes_str += (e + ", ");
							if (!eancodes_str.isEmpty() && eancodes_str.length() > 2)
								eancodes_str = eancodes_str.substring(0, eancodes_str.length() - 2);

							m_sql_db.addExpertDB(m, packages_str, regnr_str, ids_str, titles_str, atc_description_str, atc_class_str, m_pack_info_str,
									orggen_str, customer_id, mTyIndex_list, section_indications);
							m_sql_db.addProductDB(m, packages_str, eancodes_str, m_pack_info_str);

							med_counter++;
						}
					}
					tot_med_counter++;
				}
			System.out.println();
			System.out.println("--------------------------------------------");
			System.out.println("Total number of real Fachinfos: " + m_med_list.size());
			System.out.println("Number of FI with package information: " + tot_med_counter);
			System.out.println("Number of FI in generated database: " + med_counter);
			System.out.println("Number of errors in db: " + errors);
			System.out.println("Number of missing reg. nr. (min): " + missing_regnr_str);
			System.out.println("Number of missing pack info: " + missing_pack_info);
			System.out.println("Number of missing atc codes: " + missing_atc_code);
			System.out.println("--------------------------------------------");
			System.out.println("Total number of pseudo Fachinfos: " + tot_pseudo_counter);
			System.out.println("--------------------------------------------");

			if (CmlOptions.XML_FILE==true) {
				BufferedWriter writer = null;
				// Write kompendium xml file to disk
				if (CmlOptions.DB_LANGUAGE.equals("de")) {
					writer = FileOps.writerToFile(Constants.FI_FILE_XML_BASE, "fi_de.xml");
					if (CmlOptions.ZIP_BIG_FILES)
						FileOps.zipToFile(Constants.FI_FILE_XML_BASE, "fi_de.xml");
				}
				else if (CmlOptions.DB_LANGUAGE.equals("fr")) {
					writer = FileOps.writerToFile(Constants.FI_FILE_XML_BASE, "fi_fr.xml");
					if (CmlOptions.ZIP_BIG_FILES)
						FileOps.zipToFile(Constants.FI_FILE_XML_BASE, "fi_fr.xml");
				} else if (CmlOptions.DB_LANGUAGE.equals("it")) {
					FileOps.writeToFile(fi_complete_xml, Constants.FI_FILE_XML_BASE, "fi_it.xml");
					if (CmlOptions.ZIP_BIG_FILES)
						FileOps.zipToFile(Constants.FI_FILE_XML_BASE, "fi_it.xml");
				}

				if (writer != null) {
					byte[] digest = complete_xml_hash_code_digest.digest();
					BigInteger bigInt = new BigInteger(1, digest);
					String hash_code = bigInt.toString(16);
					html_utils.addHeaderToXml("kompendium", fi_complete_xml, writer, hash_code);
					writer.close();
				}

				// Copy stylesheet file to ./fis/ folders
				try {
					File src = new File(Constants.FILE_STYLE_CSS_BASE + "v1.css");
					File dst_de = new File(Constants.FI_FILE_XML_BASE + "fi_de_html/");
					File dst_fr = new File(Constants.FI_FILE_XML_BASE + "fi_fr_html/");
					File dst_it = new File(Constants.FI_FILE_XML_BASE + "fi_it_html/");
					if (src.exists() ) {
						if (dst_de.exists())
							FileUtils.copyFileToDirectory(src, dst_de);
						if (dst_fr.exists())
							FileUtils.copyFileToDirectory(src, dst_fr);
						if (dst_it.exists())
							FileUtils.copyFileToDirectory(src, dst_it);
					}
				} catch(IOException e) {
					// TODO: Unhandled!
				}
			}

			if (CmlOptions.GENERATE_REPORTS==true) {
				parse_errors.append("<br/>");
				parse_errors.append("<p>Number of medications with package information: " + tot_med_counter + "</p>");
				parse_errors.append("<p>Number of medications in generated database: " + med_counter + "</p>");
				parse_errors.append("<p>Number of errors in database: " + errors + "</p>");
				parse_errors.append("<p>Number of missing registration number: " + missing_regnr_str + "</p>");
				parse_errors.append("<p>Number of missing package info: " + missing_pack_info + "</p>");
				parse_errors.append("<p>Number of missing atc codes: " + missing_atc_code + "</p>");
				parse_errors.append("<br/>");
				// Write and close report file
				parse_errors.writeHtmlToFile();
				parse_errors.getBWriter().close();

				// Write owner error report to file
				ParseReport owner_errors = new ParseReport(Constants.FILE_OWNER_REPORT, CmlOptions.DB_LANGUAGE, "html");
				String report_style_str = FileOps.readCSSfromFile(Constants.FILE_REPORT_CSS_BASE + ".css");
				owner_errors.addStyleSheet(report_style_str);
				if (CmlOptions.DB_LANGUAGE.equals("de"))
					owner_errors.addHtmlHeader("Schweizer Arzneimittel-Kompendium", Constants.FI_DB_VERSION);
				else if (CmlOptions.DB_LANGUAGE.equals("fr"))
					owner_errors.addHtmlHeader("Compendium des Médicaments Suisse", Constants.FI_DB_VERSION);
				else if (CmlOptions.DB_LANGUAGE.equals("it")) {
					owner_errors.addHtmlHeader("Compendio svizzero dei farmaci", Constants.FI_DB_VERSION);
				}
				owner_errors.append(owner_errors.treemapToHtmlTable(tm_owner_error));
				owner_errors.writeHtmlToFile();
				owner_errors.getBWriter().close();
				// Dump to console...
				/*
				for (Map.Entry<String, ArrayList<String>> entry : tm_owner_error.entrySet()) {
					String author = entry.getKey();
					ArrayList<String> list = entry.getValue();
					for (String error : list)
						System.out.println(author + " -> " + error);
				}
				*/
			}

			if (CmlOptions.INDICATIONS_REPORT==true) {
				// Dump everything to file
				bw_indications.write("Total number of words: " + tm_indications.size() + "\n\n");
				for (Map.Entry<String, String> entry : tm_indications.entrySet()) {
					String key = entry.getKey();
					String value = entry.getValue();
					bw_indications.write(key + " [" + value + "]\n");
				}
				bw_indications.close();
			}

			if (CmlOptions.DB_LANGUAGE.equals("de")) {
				// Dump set to file, currently we do this only for German
				File atccodes_file = new File("./output/atc_codes_used_set.txt");
				if (!atccodes_file.exists()) {
					atccodes_file.getParentFile().mkdirs();
					atccodes_file.createNewFile();
				}
				FileWriter fwriter = new FileWriter(atccodes_file.getAbsoluteFile());
				BufferedWriter bwriter = new BufferedWriter(fwriter);

				Iterator<String> set_iterator = atccode_set.iterator();
				while (set_iterator.hasNext()) {
					bwriter.write(set_iterator.next() + "\n");
				}
				bwriter.close();
			}

			System.out.println("");

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String getSwissmedicNo8(String swissmedicNo5, int n) {
		String key = "";
		if (n<10)
			key = swissmedicNo5 + String.valueOf(n).format("00%d", n);
		else if (n<100)
			key = swissmedicNo5 + String.valueOf(n).format("0%d", n);
		else
			key = swissmedicNo5 + String.valueOf(n).format("%d", n);
		return key;
	}

	private String updateSectionPackungen(String title, String author, String atc_code, Map<String, ArrayList<String>> pack_info,
			String regnr_str, String content_str, List<String> tIndex_list) {
		Document doc = Jsoup.parse(content_str, "UTF-16");
		// package info string for original
		List<String> pinfo_originals_str = new ArrayList<>();
		// package info string for generika
		List<String> pinfo_generics_str = new ArrayList<>();
		// package info string for the rest
		List<String> pinfo_str = new ArrayList<>();
		// String containg all barcodes
		List<String> barcode_list = new ArrayList<>();

		int index = 0;

		TokenizerFactory tokenizerFactory = IndoEuropeanTokenizerFactory.INSTANCE;
		JaccardDistance m_jaccard = new JaccardDistance(tokenizerFactory);

		// Extract swissmedicno5 registration numbers
		List<String> swissmedicno5_list = Arrays.asList(regnr_str.split("\\s*,\\s*"));
		for (String smno5 : swissmedicno5_list) {
			// Extract original / generika info + Selbstbehalt info from
			// "add_info_map"
			String orggen_str = "";		// O=Original, G=Generika
			String flagsb_str = "";		// SB=Selbstbehalt
			String addinfo_str = m_add_info_map.get(smno5);
			if (addinfo_str != null) {
				List<String> ai_list = Arrays.asList(addinfo_str.split("\\s*;\\s*"));
				if (ai_list != null) {
					if (!ai_list.get(0).isEmpty())
						orggen_str = ", " + ai_list.get(0);		// O + G
					if (!ai_list.get(1).isEmpty())
						flagsb_str = ", " + ai_list.get(1);		// SB
				}
			}
			// Now generate many swissmedicno8 = swissmedicno5 + ***, check if they're keys and retrieve package info
			String swissmedicno8_key = "";
			String title_aips = title.replaceAll("®|", "");	// Minimally clean up title found in aips.xml

			for (int n=0; n<1000; ++n) {
				swissmedicno8_key = getSwissmedicNo8(smno5, n);
				// Check if swissmedicno8_key is a key of the map
				if (pack_info.containsKey(swissmedicno8_key)) {
					ArrayList<String> pi_row = m_package_info.get(swissmedicno8_key);
					if (pi_row != null) {
						boolean doit = true;

						// Minimal clean up of the name
						String title_refdata = pi_row.get(1).replaceAll(" %", "%").replaceAll("\\s+", " ");
						String title_swissmedic = pi_row.get(16).replaceAll(" %", "%").replaceAll("\\s+", " ");
						String eancode = pi_row.get(14);

						title_aips = title_aips.toLowerCase();
						title_swissmedic = title_swissmedic.toLowerCase();
						title_refdata = title_refdata.toLowerCase();

						// String owner = pi_row.get(17);
						// For the articles list in m_smn5_to_list_of_names calculate proximity between aips and refdata info
						if (m_smn5_to_list_of_names.containsKey(regnr_str)) {
							ArrayList<String> list_of_names = m_smn5_to_list_of_names.get(regnr_str);
							// Only consider cases in which there are multiple swissmedicno5
							if (list_of_names.size()>1) {
								// If eancode has been analyzed already, discard...
								double proximity = 0.0;
								if (title_swissmedic.equals(title_aips))
									proximity = 1.0;
								else if (title_refdata.contains(title_aips))
									proximity = 1.0;
								else
									proximity = m_jaccard.proximity(title_aips, title_swissmedic);

								if (proximity<0.5)
									doit = false;
								else if (m_map_eancode_to_owner.containsKey(eancode)) {
									if (m_map_eancode_to_owner.get(eancode)==author)
										doit = false;
								} else if (!title_aips.contains("paediatric") && (title_swissmedic.contains("paediatric") || title_refdata.contains("paediatric"))) {
									doit = false;
								} else if (!title_aips.contains("infant") && (title_swissmedic.contains("infant") || title_refdata.contains("infant"))) {
									doit = false;
								} else
									m_map_eancode_to_owner.put(eancode, author);
								System.out.println(title_aips + " / " + title_swissmedic + " / " + title_refdata + " / " + author + " -> " + proximity);
							}
						}

						if (doit)
						{
							// This string is used for "shopping carts" and contatins:
							// Präparatname | Package size | Package unit | Public price
							// | Exfactory price | Spezialitätenliste, Swissmedic Kategorie, Limitations
							// | EAN code | Pharma code
							String barcode_html = "";
							String efp = pi_row.get(8);	// exfactory price
							String pup = pi_row.get(7);	// public price
							String fep = "";
							String fap = "";
							String vat = "";
							int visible = 0xff;		// by default visible to all!
							int has_free_samples = 0x00;	// by default no free samples
							// Exctract fep and fap pricing information
							// FAP = Fabrikabgabepreis = EFP?
							// FEP = Fachhandelseinkaufspreis
							// EFP = FAP < FEP < PUP
							if (m_map_products!=null && eancode!=null && m_map_products.containsKey(eancode)) {
								Product product = m_map_products.get(eancode);
								// Correct these prices, if necessary... the m_map_products info comes from the owner directly!
								// @maxl: Added on 30.08.2015
								if (product.efp>0.0f)
									efp = String.format("CHF %.2f", product.efp);
								if (product.pp>0.0f)
									pup = String.format("CHF %.2f", product.pp);
								if (product.fap>0.0f)
									fap = String.format("CHF %.2f", product.fap);
								if (product.fep>0.0f)
									fep = String.format("CHF %.2f", product.fep);
								if (product.vat>0.0f)
									vat = String.format("%.2f", product.vat);
								visible = product.visible;
								has_free_samples = product.free_sample;
								/*
								System.out.println("--------------------------");
								System.out.println("SM5 = " + swissmedicno8_key);
								System.out.println("EFP = " + efp);
								System.out.println("PUP = " + pup);
								System.out.println("FAP = " + fap);
								System.out.println("FEP = " + fep);
								System.out.println("--------------------------");
								*/
							}

							// Some articles are listed in swissmedic_packages file but are not in the refdata file
							if (pi_row.get(10).equals("a.H.")) {
								pi_row.set(10, "ev.nn.i.H.");
							}
							if (pi_row.get(10).equals("p.c.")) {
								pi_row.set(10, "ev.ep.e.c.");
							}

							// Add only if medication is "in Handel" -> check pi_row.get(10)
							if (pi_row.get(10).isEmpty() || pi_row.get(10).equals("ev.nn.i.H.") || pi_row.get(10).equals("ev.ep.e.c.")) {
								// --> Extract EAN-13 or EAN-12 and generate barcodes
								try {
									if (!eancode.isEmpty()) {
										if (eancode.length()==12) {
											int cs = Utilities.getChecksum(eancode);
											eancode += cs;
										}
										BarCode bc = new BarCode();
										String barcodeImg64 = bc.encode(eancode);
										barcode_html = "<p class=\"barcode\">" + barcodeImg64 + "</p>";
										barcode_list.add(barcode_html);
									}
								} catch(IOException e) {
									e.printStackTrace();
								}
								m_list_of_packages.add(title_refdata + "|" + pi_row.get(3) + "|" + pi_row.get(4) + "|"
										+ efp + "|" + pup + "|" + fap + "|" + fep + "|" + vat + "|"
										+ pi_row.get(5) + ", " + pi_row.get(11) + ", " + pi_row.get(12) + "|"
										+ eancode + "|" + pi_row.get(15) + "|" + visible + "|" + has_free_samples + "\n");
								m_list_of_eancodes.add(eancode);
							}

							// Capitalize first word only
							String medtitle = Utilities.capitalizeFully(title_refdata, 1);
							// Remove [QAP?] -> not an easy one!
							medtitle = medtitle.replaceAll("\\[(.*?)\\?\\] ", "");
							// --> Add "ausser Handel" information
							String withdrawn_str = "";
							if (pi_row.get(10).length()>0)
								withdrawn_str = ", " + pi_row.get(10);
							// --> Add ex factory and public price information
							String price_efp = !efp.isEmpty() ? "EFP" + efp.replace("CHF", "") : "";
							String price_fap = !fap.isEmpty() ? "EFP" + fap.replace("CHF", "") : "";
							// String price_fep = !fep.isEmpty() ? "FEP" + fep.replace("CHF", "") : "";
							String price_pp = !pup.isEmpty() ? "PP" + pup.replace("CHF", "") : "";

							String price_info = "";
							if (price_efp.length()>0)
								price_info += ", " + price_efp;
							else if (price_fap.length()>0)
								price_info += ", " + price_fap;
							if (price_pp.length()>0)
								price_info += ", " + price_pp;

							if (price_info.length()>0) {
								// The rest of the package information
								String append_str = withdrawn_str + " [" + pi_row.get(5)
										+ pi_row.get(11) + pi_row.get(12)
										+ flagsb_str + orggen_str + "]";
								// @maxl 15.01.2016: For articles in SL add also pricing information
								if (pi_row.get(11).contains("SL") || pi_row.get(11).contains("LS")) {
									append_str = price_info + append_str;
								}
								// Generate package info string
								if (orggen_str.equals(", O"))
									pinfo_originals_str.add("<p class=\"spacing1\">" + medtitle + append_str + "</p>" + barcode_html);
								else if (orggen_str.equals(", G"))
									pinfo_generics_str.add("<p class=\"spacing1\">" + medtitle + append_str + "</p>" + barcode_html);
								else
									pinfo_str.add("<p class=\"spacing1\">" + medtitle + append_str + "</p>" + barcode_html);
							} else {
								//
								// @maxl (10.01.2014): Price for swissmedicNo8 pack is not listed in bag_preparations.xml!!
								//
								pinfo_str.add("<p class=\"spacing1\">" + medtitle + withdrawn_str + " [" + pi_row.get(5) + "]</p>" + barcode_html);
							}

							// --> Add "tindex_str" and "application_str" (see
							// SqlDatabase.java)
							if (index == 0) {
								tIndex_list.add(pi_row.get(9)); // therapeutic index
								tIndex_list.add(pi_row.get(6)); // application area
								index++;
							}
						}
					}
				}
			}
		}
		// Re-order the string alphabetically
		if (!m_list_of_packages.isEmpty()) {
			Collections.sort(m_list_of_packages, new AlphanumComp());
		}
		if (!pinfo_originals_str.isEmpty()) {
			Collections.sort(pinfo_originals_str, new AlphanumComp());
		}
		if (!pinfo_generics_str.isEmpty()) {
			Collections.sort(pinfo_generics_str, new AlphanumComp());
		}
		if (!pinfo_str.isEmpty()) {
			Collections.sort(pinfo_str, new AlphanumComp());
		}
		// Concatenate lists...
		pinfo_originals_str.addAll(pinfo_generics_str);
		pinfo_originals_str.addAll(pinfo_str);
		// Put everything in pinfo_str
		pinfo_str = pinfo_originals_str;

		// In case nothing was found
		if (index == 0) {
			tIndex_list.add("");
			tIndex_list.add("");
		}

		/*
		* Replace package information
		*/
		if (CmlOptions.PLAIN==false) {
			// Replace original package information with pinfo_str
			String p_str = "";
			for (String p : pinfo_str) {
				p_str += p;
			}

			// Generate a html-deprived string file
			m_pack_info_str = p_str.replaceAll("<p class=\"spacing1\">[<](/)?img[^>]*[>]</p>", "");
			m_pack_info_str = m_pack_info_str.replaceAll("<p class=\"barcode\">[<](/)?img[^>]*[>]</p>", "");
			m_pack_info_str = m_pack_info_str.replaceAll("\\<p.*?\\>", "");
			m_pack_info_str = m_pack_info_str.replaceAll("<\\/p\\>", "\n");

			// Remove last \n
			if (m_pack_info_str.length() > 0)
				m_pack_info_str = m_pack_info_str.substring(0, m_pack_info_str.length() - 1);

			doc.outputSettings().escapeMode(EscapeMode.xhtml);
			Element div7800 = doc.select("[id=Section7800]").first();

			// Initialize section titles
			String packages_title = "Packungen";
			String swiss_drg_title = "Swiss DRG";
			if (CmlOptions.DB_LANGUAGE.equals("fr")) {
				packages_title = "Présentation";
				swiss_drg_title = "Swiss DRG";
			}

			// Generate html for chapter "Packagungen" and subchapter "Swiss DRGs"
			// ** Chapter "Packungen"
			String section_html = "<div class=\"absTitle\">" + packages_title + "</div>" + p_str;
			// ** Subchapter "Swiss DRGs"
			// Loop through list of dosages for a particular atc code and format appropriately
			if (atc_code!=null) {
				// Update DRG footnote super scripts
				String footnotes = "1";
				String fn = m_swiss_drg_footnote.get(atc_code);
				if (fn!=null)
					footnotes += (", " + fn);
				// Generate Swiss DRG string
				String drg_str = "";
				ArrayList<String> dosages = m_swiss_drg_info.get(atc_code);
				// For most atc codes, there are NO special DRG sanctioned dosages...
				if (dosages!=null) {
					System.out.println(title + " (DRG)");
					for (String drg : dosages)
						drg_str += "<p class=\"spacing1\">" + drg + "</p>";
					if (!drg_str.isEmpty()) {
							section_html += ("<p class=\"paragraph\"></p><div class=\"absTitle\">" + swiss_drg_title
									+ "<sup>" + footnotes + "</sup></div>" + drg_str);
					}

					section_html += "<p class=\"noSpacing\"></p>";
					if (CmlOptions.DB_LANGUAGE.equals("de")) {
						section_html += "<p class=\"spacing1\"><sup>1</sup> Alle Spitäler müssen im Rahmen der jährlichen Datenerhebung (Detaillieferung) die SwissDRG AG zwingend über die Höhe der in Rechnung gestellten Zusatzentgelte informieren.</p>";
						section_html += "<p class=\"spacing1\"><sup>2</sup> Eine zusätzliche Abrechnung ist im Zusammenhang mit einer Fallpauschale der Basis-DRGs L60 oder L71 nicht möglich.</p>";
						section_html += "<p class=\"spacing1\"><sup>3</sup> Eine Abrechnung des Zusatzentgeltes ist nur über die in der Anlage zum Fallpauschalenkatalog aufgeführten Dosisklassen möglich.</p>";
						section_html += "<p class=\"spacing1\"><sup>4</sup> Dieses Zusatzentgelt ist nur abrechenbar für Patienten mit einem Alter < 15 Jahre.</p>";
						section_html += "<p class=\"spacing1\"><sup>5</sup> Dieses Zusatzentgelt darf nicht zusätzlich zur DRG A91Z abgerechnet werden, da in dieser DRG Apheresen die Hauptleistung darstellen. " +
								"Die Verfahrenskosten der  Apheresen sind in dieser DRG bereits vollumfänglich enthalten.</p>";
					} else if (CmlOptions.DB_LANGUAGE.equals("fr")) {
						section_html += "<p class=\"spacing1\"><sup>1</sup> Tous les hôpitaux doivent impérativement informer SwissDRG SA lors du relevé (relevé détaillé) sur le montant des rémunérations supplémentaires facturées.</p>";
						section_html += "<p class=\"spacing1\"><sup>2</sup> Une facturation supplémentaire aux forfaits par cas des DRG de base L60 ou L71 nest pas possible.</p>";
						section_html += "<p class=\"spacing1\"><sup>3</sup> Une facturation des rémunération supplémentaires n'est possible que pour les classes de dosage définies dans cette annexe.</p>";
						section_html += "<p class=\"spacing1\"><sup>4</sup> Cette rémunération supplémentaire n'est facturable que pour les patients âgés de moins de 15 ans.</p>";
						section_html += "<p class=\"spacing1\"><sup>5</sup> Cette rémunération supplémentaire ne peut pas être facturée en plus du DRG A91Z, la prestation principale de ce DRG étant l'aphérèse. " +
								"Les coûts du traitement par aphérèse sont déjà intégralement compris dans le DRG.</p>";
					} else if (CmlOptions.DB_LANGUAGE.equals("it")) {
						section_html += "<p class=\"spacing1\"><sup>1</sup>Tutti gli ospedali devono comunicare a SwissDRG SA l'importo dei costi aggiuntivi fatturati nell'ambito della raccolta annuale dei dati (consegna dettagliata).</p>";
						section_html += "<p class=\"spacing1\"><sup>1</sup>La fatturazione supplementare non è possibile in relazione al forfait per caso dei DRG di base L60 o L71.</p>";
						section_html += "<p class=\"spacing1\"><sup>1</sup>La fatturazione del costo aggiuntivo è possibile solo attraverso le classi di dose elencate nell'allegato al catalogo dei costi forfettari per caso.</p>";
						section_html += "<p class=\"spacing1\"><sup>1</sup>Questo costo aggiuntivo può essere fatturato solo per i pazienti di età inferiore ai 15 anni.</p>";
						section_html += "<p class=\"spacing1\"><sup>1</sup>Questo costo aggiuntivo non può essere fatturato in aggiunta al DRG A91Z, poiché l'aferesi è il servizio principale di questo DRG. " +
							"I costi procedurali dell'aferesi sono già interamente inclusi in questo DRG.</p>";
					}
				}
			}

			if (div7800 != null) {
				div7800.html(section_html);
			} else {
				Element div18 = doc.select("[id=section18]").first();
				if (div18 != null) {
					div18.html(section_html);
				} else {
					if (CmlOptions.SHOW_ERRORS)
						System.err.println(">> ERROR: elem is null, sections 18/7800 does not exist: " + title);
				}
			}
		}

		return doc.html();
	}
}
