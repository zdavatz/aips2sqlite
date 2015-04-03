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
import java.io.StringReader;
import java.io.StringWriter;
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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxl.java.aips2sqlite.Preparations.Preparation;

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
	
	// Map of products
	private Map<String, Product> m_map_products = null;	

	// Package section string
	private static String m_pack_info_str = "";
	
	// Stop word hashset
	HashSet<String> m_stop_words_hash = null;

	/*
	 * Constructors
	 */
	public RealExpertInfo(SqlDatabase sql_db, List<MedicalInformations.MedicalInformation> med_list, 
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
				if (num_rows > 4) {
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

					// 0: Zulassungsnummer, 1: Sequenz, 2: Sequenzname, 3: Zulassunginhaberin, 4: T-Nummer, 5: ATC-Code, 6: Heilmittelcode
					// 7: Erstzulassung Präparat, 8: Zulassungsdatum Sequenz, 9: Gültigkeitsdatum, 10: Verpackung, 11: Packungsgrösse
					// 12: Einheit, 13: Abgabekategorie, 14: Wirkstoff, 15: Zusammensetzung, 16: Anwendungsgebiet Präparat, 17: Anwendungsgebiet Sequenz
					
					// @cybermax: 15.10.2013 - work around for Excel cells of type "Special" (cell0 and cell10)
					if (row.getCell(0) != null)
						swissmedic_no5 = String.format("%05d", (int)(row.getCell(0).getNumericCellValue()));	// Swissmedic registration number (5 digits)
					if (row.getCell(2) != null)
						sequence_name = ExcelOps.getCellValue(row.getCell(2)); 	// Sequence name
					if (row.getCell(6) != null)
						heilmittel_code = ExcelOps.getCellValue(row.getCell(6));	// Heilmittelcode					
					if (row.getCell(11) != null)						
						package_size = ExcelOps.getCellValue(row.getCell(11));	// Packungsgrösse
					if (row.getCell(12) != null)
						package_unit = ExcelOps.getCellValue(row.getCell(12));	// Einheit
					if (row.getCell(13) != null)
						swissmedic_cat = ExcelOps.getCellValue(row.getCell(13));	// Abgabekategorie	
					if (row.getCell(16) != null)
						application_area = ExcelOps.getCellValue(row.getCell(16));	// Anwendungsgebiet				
					if (row.getCell(10) != null) {							
						package_id = String.format("%03d", (int)(row.getCell(10).getNumericCellValue()));		// Verpackungs ID
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
						pack.add(withdrawn_str); 	// 10
						pack.add(speciality_str); 	// 11
						pack.add(plimitation_str); 	// 12
						pack.add(add_info_str); 	// 13
						// 22.03.2014: EAN-13 barcodes - initialization - check digit is missing!
						ean_code_str = "7680" + swissmedic_no8;
						pack.add(ean_code_str); 	// 14
						pack.add(pharma_code_str);	// 15
						
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
			} else if (CmlOptions.DB_LANGUAGE.equals("fr")) {
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
				System.out.println((m_atc_map.size() + 1) + " classes in "
						+ (stopTime - startTime) / 1000.0f + " sec");
			
			// Load Refdata xml file
			File refdata_xml_file = null;
			if (CmlOptions.DB_LANGUAGE.equals("de"))
				refdata_xml_file = new File(Constants.FILE_REFDATA_PHARMA_DE_XML);
			else if (CmlOptions.DB_LANGUAGE.equals("fr"))
				refdata_xml_file = new File(Constants.FILE_REFDATA_PHARMA_FR_XML);
			else {
				System.err.println("ERROR: DB_LANGUAGE undefined");
				System.exit(1);
			}
			FileInputStream refdata_fis = new FileInputStream(refdata_xml_file);

			startTime = System.currentTimeMillis();
			if (CmlOptions.SHOW_LOGS)
				System.out.println("- Unmarshalling Refdata Pharma " + CmlOptions.DB_LANGUAGE + "... ");

			JAXBContext context = JAXBContext.newInstance(Pharma.class);
			Unmarshaller um = context.createUnmarshaller();
			Pharma refdataPharma = (Pharma) um.unmarshal(refdata_fis);
			List<Pharma.ITEM> pharma_list = refdataPharma.getItem();

			String smno8;
			for (Pharma.ITEM pharma : pharma_list) {
				String ean_code = pharma.getGtin();
				String pharma_code = pharma.getPhar();
				if (ean_code.length() == 13) {
					smno8 = ean_code.substring(4, 12);									
					// Extract pharma corresponding to swissmedicno8 (source: swissmedic package file)
					ArrayList<String> pi_row = m_package_info.get(smno8);					
					// Replace sequence_name
					if (pi_row != null) {
						// Präparatname + galenische Form
						if (pharma.getAddscr().length() > 0)
							pi_row.set(1, pharma.getDscr() + ", " + pharma.getAddscr());
						else
							pi_row.set(1, pharma.getDscr());
						// If med is in refdata file, then it is "in Handel!!" ;)
						pi_row.set(10, "");
						if (pharma.getStatus().equals("I")) {
							if (CmlOptions.DB_LANGUAGE.equals("de"))
								pi_row.set(10, "a.H.");
							else if (CmlOptions.DB_LANGUAGE.equals("fr"))
								pi_row.set(10, "p.c.");
						}
						// 22.03.2014: EAN-13 barcodes - replace with refdata if package exists
						pi_row.set(14, ean_code);
						// Pharma code
						pi_row.set(15, pharma_code);
					} 
					else {
						if (CmlOptions.SHOW_ERRORS) {
							System.err.println(">> Does not exist in BAG xls: " + smno8 
										+ " (" + pharma.getDscr() + ", " + pharma.getAddscr() + ")");
						}
					}
				} else if (ean_code.length() < 13) {
					if (CmlOptions.SHOW_ERRORS)
						System.err.println(">> EAN code too short: " + ean_code + ": " + pharma.getDscr());
				} else if (ean_code.length() > 13) {
					if (CmlOptions.SHOW_ERRORS)
						System.err.println(">> EAN code too long: " + ean_code + ": " + pharma.getDscr());
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
				System.out.println("- Processing preparations xml... ");

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
						} else if (flagSB20_str.equals("N")) {
							if (CmlOptions.DB_LANGUAGE.equals("de"))
								flagSB20_str = "SB 10%";
							else if (CmlOptions.DB_LANGUAGE.equals("fr"))
								flagSB20_str = "QP 10%";
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
							} else {
								if (CmlOptions.DB_LANGUAGE.equals("de"))
									bag_application = code.getDescriptionDe();
								else if (CmlOptions.DB_LANGUAGE.equals("fr"))
									bag_application = code.getDescriptionFr();
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

			// Loop through all SwissmedicNo8 numbers
			/*
			for (Map.Entry<String, ArrayList<String>> entry : package_info.entrySet()) {
				String swissmedicno8 = entry.getKey();
				ArrayList<String> pi_row = entry.getValue();
			}
			*/

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
				System.out.println("processed all Swiss DRG packages in "
						+ (stopTime - startTime) / 1000.0f + " sec");
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
			}
				
			long stopTime = System.currentTimeMillis();
			if (CmlOptions.SHOW_LOGS) {
				System.out.println("processed EPha product json file in "
						+ (stopTime - startTime) / 1000.0f + " sec");
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}			
	}
	
	public void process() {
		
		// Get stop words first
		getStopWords();
		
		// Extract EPha SwissmedicNo5 to ATC map
		extractSwissmedicNo5ToAtcMap();
		
		// Extract package information (this is the heavy-duty bit)
		extractPackageInfo();
			
		// Extract Swiss DRG information
		extractSwissDRGInfo();
		
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
					
			// First pass is always with DB_LANGUAGE set to German! (most complete information)
			// The file dumped in ./reports is fed to AllDown.java to generate a multilingual ATC code / ATC class file, e.g. German - French
			Set<String> atccode_set = new TreeSet<String>();
			
			// Treemap for owner error report (sorted by key)
			TreeMap<String, ArrayList<String>> tm_owner_error = new TreeMap<String, ArrayList<String>>();
			
			HtmlUtils html_utils = null;
			
			System.out.println("Processing real Fachinfos...");	
			
			for (MedicalInformations.MedicalInformation m : m_med_list) {
				// --> Read FACHINFOS! <--				
				if (m.getLang().equals(CmlOptions.DB_LANGUAGE) && m.getType().equals("fi")) {
					// Database contains less than 5000 medis - this is a safe upperbound!
					if (tot_med_counter<5000) {						
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
						
						// Pattern matcher for regnr command line option, (?s) searches across multiple lines
						Pattern regnr_pattern = Pattern.compile("(?s).*\\b" + CmlOptions.OPT_MED_REGNR);						

						if (m.getTitle().toLowerCase().startsWith(CmlOptions.OPT_MED_TITLE.toLowerCase())  
								&& regnr_pattern.matcher(regnr_str).find() 
								&& m.getAuthHolder().toLowerCase().startsWith(CmlOptions.OPT_MED_OWNER.toLowerCase())) {	
							
							System.out.println(tot_med_counter + " - " + m.getTitle() + ": " + regnr_str);							
						
							if (regnr_str.isEmpty()) {							
								errors++;
								if (CmlOptions.GENERATE_REPORTS==true) {
									parse_errors.append("<p style=\"color:#ff0099\">ERROR " + errors + ": reg. nr. could not be parsed in AIPS.xml (swissmedic) - " + m.getTitle() + " (" + regnr_str + ")</p>");
									// Add to owner errors
									ArrayList<String> error = tm_owner_error.get(m.getAuthHolder());
									if (error==null)
										error = new ArrayList<String>();
									error.add(m.getTitle()+";regnr");
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
							if (authNrs!=null) {
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
									if (atc_code_str==null || atc_code_str.isEmpty())
										atc_code_str = r;
									else
										atc_code_str += "," + r;
								}
							} else
								atc_error_found = true;

							// Notify any other problem with the EPha ATC codes
							if (atc_code_str==null || atc_code_str.isEmpty())
								atc_error_found = true;
							
							// Fallback solution 
							if (atc_error_found==true) {
								if (m.getAtcCode()!=null && !m.getAtcCode().equals("n.a.") && m.getAtcCode().length()>1) {
									atc_code_str = m.getAtcCode();
									atc_code_str = atc_code_str.replaceAll("&ndash;", "(");
									atc_code_str = atc_code_str.replaceAll("Code", "").replaceAll("ATC", "")
										.replaceAll("&nbsp","").replaceAll("\\(.*","").replaceAll("/", ",")
										.replaceAll("[^A-Za-z0-9äöü,]", "");
									if (atc_code_str.charAt(1)=='O') {
										// E.g. Ascosal Brausetabletten
										atc_code_str = atc_code_str.substring(0,1) + '0' + atc_code_str.substring(2);
									}
									if (atc_code_str.length()>7) {
										if (atc_code_str.charAt(7)!=',' || atc_code_str.length()!=15)
											atc_code_str = atc_code_str.substring(0,7);
									}
								} else {
									// Work backwards using m_atc_map and m.getSubstances()
									String substances = m.getSubstances();
									if (substances!=null) {
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
							if ((substances==null || substances.length()<3) && atc_code_str!=null) {
								substances = m_atc_map.get(atc_code_str);
							}
							
							// Set clean substances
							m.setSubstances(substances);
							// Set clean ATC Code
							m.setAtcCode(atc_code_str);

							// System.out.println("ATC -> " + atc_code_str + ": " + substances);
							
							if (atc_code_str!=null) {
								// \\s -> whitespace character, short for [ \t\n\x0b\r\f]
								// atc_code_str = atc_code_str.replaceAll("\\s","");
								// Take "leave" of the tree (most precise classification)
								String a = m_atc_map.get(atc_code_str);
								if (a!=null) {
									atc_description_str = a;
									atccode_set.add(atc_code_str + ": " + a);
								} else {
									// Case: ATC1,ATC2
									if (atc_code_str.length()==15) {
										String[] codes = atc_code_str.split(",");
										if (codes.length>1) {
											String a1 = m_atc_map.get(codes[0]);
											if (a1==null) {
												atc_error_found = true;
												a1 = "k.A.";
											}
											String a2 = m_atc_map.get(codes[1]);
											if (a2==null) {
												atc_error_found = true;
												a2 = "k.A.";
											}
											atc_description_str = a1 + "," + a2;
										}											
									} else if (m.getSubstances()!=null) {
										// Fallback in case nothing else works
										atc_description_str = m.getSubstances();
										// Work backwards using m_atc_map and m.getSubstances(), change ATC code
										if (atc_description_str!=null) {
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
									}
								}
								
								// Read out only two levels (L1, L3, L4, L5)
								for (int i=1; i<6; i++) {
									if (i!=2) {
										String atc_key = "";
										if (i<=atc_code_str.length())
											atc_key = atc_code_str.substring(0, i);
										char sep = (i>=4) ? '#' : ';';	// #-separator between L4 and L5										
										if (atc_key!=null) {
											String c = m_atc_map.get(atc_key);
											if (c!=null) {
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
								if (drg!=null) {
									atc_description_str += (";DRG");
								}
							} 						
														
							if (atc_error_found) {
								errors++;
								if (CmlOptions.GENERATE_REPORTS) {
									parse_errors.append("<p style=\"color:#0000bb\">ERROR " + errors + ": Broken or missing ATC-Code-Tag in AIPS.xml (Swissmedic) or ATC index (Wido) - " + m.getTitle() + " (" + regnr_str + ")</p>");
									// Add to owner errors
									ArrayList<String> error = tm_owner_error.get(m.getAuthHolder());
									if (error==null)
										error = new ArrayList<String>();
									error.add(m.getTitle()+";atccode");
									tm_owner_error.put(m.getAuthHolder(), error);									
								}
								System.err.println(">> ERROR: " + tot_med_counter + " - no ATC-Code found in the XML-Tag \"atcCode\" - (" + regnr_str + ") " + m.getTitle());
								missing_atc_code++;							
							}

							// Additional info stored in add_info_map
							String add_info_str = ";";
							List<String> rnr_list = Arrays.asList(regnr_str.split("\\s*, \\s*"));
							if (rnr_list.size()>0)
								add_info_str = m_add_info_map.get(rnr_list.get(0));					
							
							// Sanitize html
							String html_sanitized = "";								
							// First check for bad boys (version=1! but actually version>1!)
							if (!m.getVersion().equals("1") || m.getContent().substring(0, 20).contains("xml")) {
								for (int i=1; i<22; ++i) {
									html_sanitized += html_utils.sanitizeSection(i, m.getTitle(), m.getAuthHolder(), CmlOptions.DB_LANGUAGE);
								}
								html_sanitized = "<div id=\"monographie\">" + html_sanitized + "</div>" ;
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
 							html_sanitized = html_sanitized.replaceAll("</div>$", footer_str+"</div>");
 							
							// Extract section indications
							String section_indications = "";
							if (CmlOptions.DB_LANGUAGE.equals("de")) {
								String sstr1 = "Indikationen/Anwendungsmöglichkeiten";
								String sstr2 = "Dosierung/Anwendung";
								if (html_sanitized.contains(sstr1) && html_sanitized.contains(sstr2)) {
									int idx1 = html_sanitized.indexOf(sstr1) + sstr1.length();
									int idx2 = html_sanitized.substring(idx1, html_sanitized.length()).indexOf(sstr2);
									try {
										section_indications = html_sanitized.substring(idx1, idx1+idx2);
									} catch(StringIndexOutOfBoundsException e) {
										e.printStackTrace();
									}
								}						
							} else if (CmlOptions.DB_LANGUAGE.equals("fr")) {
								String sstr1 = "Indications/Possibilités d’emploi";
								String sstr2 = "Posologie/Mode d’emploi";
								
								html_sanitized = html_sanitized.replaceAll("Indications/Possibilités d&apos;emploi", sstr1);
								html_sanitized = html_sanitized.replaceAll("Posologie/Mode d&apos;emploi", sstr2);
								html_sanitized = html_sanitized.replaceAll("Indications/possibilités d’emploi", sstr1);
								html_sanitized = html_sanitized.replaceAll("Posologie/mode d’emploi", sstr2);
														
								if (html_sanitized.contains(sstr1) && html_sanitized.contains(sstr2)) {
									int idx1 = html_sanitized.indexOf(sstr1) + sstr1.length();
									int idx2 = html_sanitized.substring(idx1, html_sanitized.length()).indexOf(sstr2);
									try {
										section_indications = html_sanitized.substring(idx1, idx1+idx2);
									} catch(StringIndexOutOfBoundsException e) {
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
								section_indications = section_indications.replaceAll("l’", "").replaceAll("d’", "");
							}
							// Remove all URLs
							section_indications = section_indications.replaceAll("\\b(http|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]", "");
							// Remove list of type a) b) c) ... 1) 2) ...
							section_indications = section_indications.replaceAll("^\\w\\)", "");
							// Remove numbers, commas, semicolons, parentheses, etc.								
							section_indications = section_indications.replaceAll("[^A-Za-z\\xC0-\\xFF- ]", "");
							// Generate long list of keywords
							LinkedList<String> wordsAsList = new LinkedList<String>(
									Arrays.asList(section_indications.split("\\s+")));
							// Remove stop words
							Iterator<String> wordIterator = wordsAsList.iterator();
							while (wordIterator.hasNext()) {
								// Note: This assumes there are no null entries in the list and all stopwords are stored in lower case
								String word = wordIterator.next().trim().toLowerCase();
								if (word.length()<3 || m.getTitle().toLowerCase().contains(word) || m_stop_words_hash.contains(word))
									wordIterator.remove();
							}
							section_indications = "";
							for (String w: wordsAsList) {
								// Remove any leading dash or hyphen
								if (w.startsWith("-"))
									w = w.substring(1);	
								section_indications += (w+";");
								if (CmlOptions.INDICATIONS_REPORT==true) {
									// Add to map (key->value), word = key, value = how many times used
									// Is word w already stored in treemap?
									String t_str = tm_indications.get(w);
									if (t_str==null) {
										t_str = m.getTitle();
										tm_indications.put(w, t_str);										
									} else {
										t_str += (", " + m.getTitle());
										tm_indications.put(w, t_str);
									}					
								}								
							}
							
							/*
							 * Update section "Packungen", generate packungen string for shopping cart, and extract therapeutisches index
							 */
							List<String> mTyIndex_list = new ArrayList<String>();
							m_list_of_packages.clear();
							m_list_of_eancodes.clear();
							String mContent_str = updateSectionPackungen(m.getTitle(), m.getAtcCode(), m_package_info, regnr_str, html_sanitized, mTyIndex_list);
								
							m.setContent(mContent_str);
								
							// Check if mPackSection_str is empty AND command line option PLAIN is not active
							if (CmlOptions.PLAIN==false && m_pack_info_str.isEmpty()) {	
								errors++;
								if (CmlOptions.GENERATE_REPORTS) {
									parse_errors.append("<p style=\"color:#bb0000\">ERROR " + errors + ": SwissmedicNo5 not found in Packungen.xls (Swissmedic) - " + m.getTitle() + " (" + regnr_str + ")</p>");
									// Add to owner errors
									ArrayList<String> error = tm_owner_error.get(m.getAuthHolder());
									if (error==null)
										error = new ArrayList<String>();
									error.add(m.getTitle()+";swissmedic5");
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
							m.setAuthHolder(m.getAuthHolder().replaceAll("&#038;","&"));
							
							// Check if substances str has a '$a' and change it to '&alpha'
							if( m.getSubstances()!=null )
								m.setSubstances( m.getSubstances().replaceAll("\\$a","&alpha;") );							
							
							if (CmlOptions.XML_FILE==true) {
								if (!regnr_str.isEmpty()) {
									// Add header to html file
									mContent_str = mContent_str.replaceAll("<head>", "<head>" + 
											"<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />" +
											"<style>" + amiko_style_v1_str + "</style>");												
									// Note: the following line is not necessary!
									// m.setContent(mContent_str);
																		
									// Add header to xml file
									String xml_str = html_utils.convertHtmlToXml("fi", m.getTitle(), mContent_str, regnr_str);									
									xml_str = html_utils.addHeaderToXml("singlefi", xml_str);
									fi_complete_xml += (xml_str + "\n");
									
									// Write to html and xml files to disk
									String name = m.getTitle();
									// Replace all "Sonderzeichen"
									name = name.replaceAll("[^a-zA-Z0-9]+", "_");									
									if (CmlOptions.DB_LANGUAGE.equals("de")) {
										FileOps.writeToFile(mContent_str, Constants.FI_FILE_XML_BASE + "fi_de_html/", name + "_fi_de.html");
										FileOps.writeToFile(xml_str, Constants.FI_FILE_XML_BASE + "fi_de_xml/", name + "_fi_de.xml");
									} else if (CmlOptions.DB_LANGUAGE.equals("fr")) {
										FileOps.writeToFile(mContent_str, Constants.FI_FILE_XML_BASE + "fi_fr_html/", name + "_fi_fr.html");										
										FileOps.writeToFile(xml_str, Constants.FI_FILE_XML_BASE + "fi_fr_xml/", name + "_fi_fr.xml");
									}
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
							if (add_info_str!=null) {
								List<String> ai_list = Arrays.asList(add_info_str.split("\\s*;\\s*"));
								if (ai_list!=null) {
									if (!ai_list.get(0).isEmpty())
										orggen_str = ai_list.get(0);				
								}
							}
							
							/*
							 * Add medis, titles and ids to database
							 */
							String packages_str = "";
							for (String s : m_list_of_packages)
								packages_str += s;
							String eancodes_str = "";
							for (String e : m_list_of_eancodes)
								eancodes_str += (e + ", ");
							if (!eancodes_str.isEmpty() && eancodes_str.length()>2)
								eancodes_str = eancodes_str.substring(0, eancodes_str.length()-2);
							
							m_sql_db.addExpertDB(m, packages_str, regnr_str, ids_str, titles_str, atc_description_str, atc_class_str, m_pack_info_str, 
									orggen_str, customer_id, mTyIndex_list, section_indications);
							m_sql_db.addProductDB(m, packages_str, eancodes_str, m_pack_info_str);							
							
							med_counter++;
						}
					}
					tot_med_counter++;				
				}
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
				fi_complete_xml = html_utils.addHeaderToXml("kompendium", fi_complete_xml);
				// Write kompendium xml file to disk
				if (CmlOptions.DB_LANGUAGE.equals("de")) {
					FileOps.writeToFile(fi_complete_xml, Constants.FI_FILE_XML_BASE, "fi_de.xml");
					if (CmlOptions.ZIP_BIG_FILES)
						FileOps.zipToFile(Constants.FI_FILE_XML_BASE, "fi_de.xml");
				}
				else if (CmlOptions.DB_LANGUAGE.equals("fr")) {
					FileOps.writeToFile(fi_complete_xml, Constants.FI_FILE_XML_BASE, "fi_fr.xml");
					if (CmlOptions.ZIP_BIG_FILES)
						FileOps.zipToFile(Constants.FI_FILE_XML_BASE, "fi_fr.xml");				
				}
				// Copy stylesheet file to ./fis/ folders
				try {
					File src = new File(Constants.FILE_STYLE_CSS_BASE + "v1.css");
					File dst_de = new File(Constants.FI_FILE_XML_BASE + "fi_de_html/");
					File dst_fr = new File(Constants.FI_FILE_XML_BASE + "fi_fr_html/");			
					if (src.exists() ) {
						if (dst_de.exists())
							FileUtils.copyFileToDirectory(src, dst_de);
						if (dst_fr.exists())
							FileUtils.copyFileToDirectory(src, dst_fr);
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
	
	private String updateSectionPackungen(String title, String atc_code, Map<String, ArrayList<String>> pack_info, 
			String regnr_str, String content_str, List<String> tIndex_list) {
		Document doc = Jsoup.parse(content_str, "UTF-16");
		// package info string for original
		List<String> pinfo_originals_str = new ArrayList<String>();
		// package info string for generika
		List<String> pinfo_generics_str = new ArrayList<String>();
		// package info string for the rest
		List<String> pinfo_str = new ArrayList<String>();
		// String containg all barcodes
		List<String> barcode_list = new ArrayList<String>();
		
		int index = 0;

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
			for (int n=0; n<1000; ++n) {
				swissmedicno8_key = getSwissmedicNo8(smno5, n);
				// Check if swissmedicno8_key is a key of the map
				if (pack_info.containsKey(swissmedicno8_key)) {
					ArrayList<String> pi_row = m_package_info.get(swissmedicno8_key);
					if (pi_row != null) {							
						// This string is used for "shopping carts" and contatins:
						// Präparatname | Package size | Package unit | Public price
						// | Exfactory price | Spezialitätenliste, Swissmedic Kategorie, Limitations
						// | EAN code | Pharma code
						String barcode_html = "";		
						String pup = pi_row.get(7);	// public price
						String efp = pi_row.get(8);	// exfactory price		
						String fep = "";						
						String fap = "";
						String vat = "";
						String eancode = pi_row.get(14);
						int visible = 0xff;
						// Exctract fep and fap pricing information
						// FAP = Fabrikabgabepreis = EFP?
						// FEP = Fachhandelseinkaufspreis
						// EFP = FAP < FEP < PUP
						if (m_map_products!=null && eancode!=null && m_map_products.containsKey(eancode)) {
							Product product = m_map_products.get(eancode);
							if (product.fap>0.0f)
								fap = String.format("CHF %.2f", product.fap);							
							if (product.fep>0.0f)
								fep = String.format("CHF %.2f", product.fep);
							if (product.vat>0.0f)
								vat = String.format("%.2f", product.vat);
							visible = product.visible;
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
									BarCode bc = new BarCode();																	
									if (eancode.length()==12) {
										int cs = bc.getChecksum(eancode);
										eancode += cs;
									}
									String barcodeImg64 = bc.encode(eancode);
									barcode_html = "<p class=\"barcode\">" + barcodeImg64 + "</p>";
									barcode_list.add(barcode_html);
								}
							} catch(IOException e) {
								e.printStackTrace();
							}							
							m_list_of_packages.add(pi_row.get(1) + "|" + pi_row.get(3) + "|" + pi_row.get(4) + "|" 
									+ efp + "|" + pup + "|" + fap + "|" + fep + "|" + vat + "|"
									+ pi_row.get(5) + ", " + pi_row.get(11) + ", " + pi_row.get(12) + "|"
									+ eancode + "|" + pi_row.get(15) + "|" + visible + "\n");
							m_list_of_eancodes.add(eancode);
						}
						
						// Remove double spaces in title and capitalize
						String medtitle = capitalizeFully(pi_row.get(1).replaceAll("\\s+", " "), 1);
						// Remove [QAP?] -> not an easy one!
						medtitle = medtitle.replaceAll("\\[(.*?)\\?\\] ", "");						
						// --> Add "ausser Handel" information
						String withdrawn_str = "";
						if (pi_row.get(10).length()>0)
							withdrawn_str = ", " + pi_row.get(10);
						// --> Add ex factory price information
						String price = !efp.isEmpty() ? efp : fep;				
						if (price.length()>0) {
							// The rest of the package information
							String append_str = ", " + price 
									+ withdrawn_str + " [" + pi_row.get(5) 
									+ pi_row.get(11) + pi_row.get(12) 
									+ flagsb_str + orggen_str + "]";
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
							pinfo_str.add("<p class=\"spacing1\">"
										+ medtitle + withdrawn_str + " [" + pi_row.get(5) + "]</p>" + barcode_html);
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
						section_html += "<p class=\"spacing1\"><sup>2</sup> Une facturation supplémentaire aux forfaits par cas des DRG de base L60 ou L71 n’est pas possible.</p>";
						section_html += "<p class=\"spacing1\"><sup>3</sup> Une facturation des rémunération supplémentaires n'est possible que pour les classes de dosage définies dans cette annexe.</p>";
						section_html += "<p class=\"spacing1\"><sup>4</sup> Cette rémunération supplémentaire n'est facturable que pour les patients âgés de moins de 15 ans.</p>";
						section_html += "<p class=\"spacing1\"><sup>5</sup> Cette rémunération supplémentaire ne peut pas être facturée en plus du DRG A91Z, la prestation principale de ce DRG étant l'aphérèse. " +
								"Les coûts du traitement par aphérèse sont déjà intégralement compris dans le DRG.</p>";
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
	
	private String capitalizeFully(String s, int N) {
		// Split string
		String[] tokens = s.split("\\s");
		// Capitalize only first word!
		tokens[0] = tokens[0].toUpperCase();
		// Reassemble string
		String full_s = "";
		if (tokens.length > 1) {
			for (int i = 0; i < tokens.length - 1; i++) {
				full_s += (tokens[i] + " ");
			}
			full_s += tokens[tokens.length - 1];
		} else {
			full_s = tokens[0];
		}
		return full_s;
	}
	
	private String calcEAN13Checksum(String ean12_str) {
		// Sum of all uneven digits
		int unevenSum = 0;
		for (int i=0; i<ean12_str.length(); i+=2) {
			unevenSum += Character.getNumericValue(ean12_str.charAt(i));
		}		
		// Sum of all even digits
		int evenSum = 0;
		for (int i=1; i<ean12_str.length(); i+=2) {
			evenSum += Character.getNumericValue(ean12_str.charAt(i));
		}
		// Checksum = 90 - total sum
		String checkSum = String.valueOf(90 - (3*evenSum+unevenSum));
		
		return checkSum;
	}	
	
	private String prettyFormat(String input) {
		try {
			Source xmlInput = new StreamSource(new StringReader(input));
			StringWriter stringWriter = new StringWriter();
			StreamResult xmlOutput = new StreamResult(stringWriter);
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			transformer.transform(xmlInput, xmlOutput);
			return xmlOutput.getWriter().toString();
		} catch (Exception e) {
			throw new RuntimeException(e); // simple exception handling, please review it
		}
	}
}
