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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities.EscapeMode;
import org.jsoup.select.Elements;

import com.maxl.java.aips2sqlite.Preparations.Preparation;

public class RealPatientInfo {

	List<MedicalInformations.MedicalInformation> m_med_list = null;
	
	// Map to list with all the relevant information
	// HashMap is faster, but TreeMap is sort by the key :)
	private static Map<String, ArrayList<String>> m_package_info;

	// Map to String of atc classes, key is the ATC-code or any of its substrings
	private static Map<String, String> m_atc_map;

	// Map to String of additional info, key is the SwissmedicNo5
	private static Map<String, String> m_add_info_map;
	
	/*
	 * Constructors
	 */
	public RealPatientInfo(List<MedicalInformations.MedicalInformation> med_list) {
		m_med_list = med_list;
		
		// Initialize maps
		m_package_info = new TreeMap<String, ArrayList<String>>();
		m_atc_map = new TreeMap<String, String>();
		m_add_info_map = new TreeMap<String, String>();
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

					// 0: Zulassungsnummer, 1: Sequenz, 2: Sequenzname, 3: Zulassunginhaberin, 4: T-Nummer, 5: ATC-Code, 6: Heilmittelcode
					// 7: Erstzulassung Präparat, 8: Zulassungsdatum Sequenz, 9: Gültigkeitsdatum, 10: Verpackung, 11: Packungsgrösse
					// 12: Einheit, 13: Abgabekategorie, 14: Wirkstoff, 15: Zusammensetzung, 16: Anwendungsgebiet Präparat, 17: Anwendungsgebiet Sequenz
					
					// @cybermax: 15.10.2013 - work around for Excel cells of type "Special" (cell0 and cell10)
					if (row.getCell(0) != null)
						swissmedic_no5 = String.format("%05d", (int)(row.getCell(0).getNumericCellValue()));	// Swissmedic registration number (5 digits)
					if (row.getCell(2) != null)
						sequence_name = row.getCell(2).getStringCellValue(); 	// Sequence name
					if (row.getCell(6) != null)
						heilmittel_code = row.getCell(6).getStringCellValue();	// Heilmittelcode					
					if (row.getCell(11) != null)						
						package_size = row.getCell(11).getStringCellValue();	// Packungsgrösse
					if (row.getCell(12) != null)
						package_unit = row.getCell(12).getStringCellValue();	// Einheit
					if (row.getCell(13) != null)
						swissmedic_cat = row.getCell(13).getStringCellValue();	// Abgabekategorie	
					if (row.getCell(16) != null)
						application_area = row.getCell(16).getStringCellValue();	// Anwendungsgebiet				
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
						else if (CmlOptions.DB_LANGUAGE.equals("it"))
							withdrawn_str = "f.c.";	// fuori commercio
						pack.add(withdrawn_str); 	// 10
						pack.add(speciality_str); 	// 11
						pack.add(plimitation_str); 	// 12
						pack.add(add_info_str); 	// 13
						// 22.03.2014: EAN-13 barcodes - initialization
						ean_code_str = "7680" + swissmedic_no8;
						pack.add(ean_code_str); 	// 14
						
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
				HSSFSheet atc_classes_sheet = atc_classes_workbook.getSheetAt(1);
				// Iterate through all rows of first sheet
				rowIterator = atc_classes_sheet.iterator();

				num_rows = 0;
				while (rowIterator.hasNext()) {
					Row row = rowIterator.next();
					if (num_rows > 2) {
						String atc_code = "";
						String atc_class = "";
						if (row.getCell(0) != null) {
							atc_code = row.getCell(0).getStringCellValue().replaceAll("\\s", "");
						}
						if (row.getCell(2) != null) {
							atc_class = row.getCell(2).getStringCellValue();
						}
						// Build a full map atc code to atc class
						if (atc_code.length() > 0) {
							m_atc_map.put(atc_code, atc_class);
						}
					}
					num_rows++;
				}
			} else if (CmlOptions.DB_LANGUAGE.equals("fr")) {
				// Load multilinguagl ATC classes txt file
				String atc_classes_multi = FileOps.readFromFile(Constants.FILE_ATC_MULTI_LINGUAL_TXT);
				// Loop through all lines
				Scanner scanner = new Scanner(atc_classes_multi);
				while (scanner.hasNextLine()) {
					String line = scanner.nextLine();
					List<String> atc_class = Arrays.asList(line.split(": "));
					String atc_code = atc_class.get(0);
					String[] atc_classes_str = atc_class.get(1).split(";");
					String atc_class_french = atc_classes_str[1].trim();
					m_atc_map.put(atc_code, atc_class_french);
				}
				scanner.close();
			} else if (CmlOptions.DB_LANGUAGE.equals("it")) {
				// Load multilinguagl ATC classes txt file
				String atc_classes_multi = FileOps.readFromFile(Constants.FILE_ATC_MULTI_LINGUAL_TXT);
				// Loop through all lines
				Scanner scanner = new Scanner(atc_classes_multi);
				while (scanner.hasNextLine()) {
					String line = scanner.nextLine();
					List<String> atc_class = Arrays.asList(line.split(": "));
					String atc_code = atc_class.get(0);
					String[] atc_classes_str = atc_class.get(1).split(";");
					String atc_class_french = atc_classes_str[1].trim();
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
			else if (CmlOptions.DB_LANGUAGE.equals("it"))
				refdata_xml_file = new File(Constants.FILE_REFDATA_PHARMA_FR_XML);
			else {
				System.err.println("ERROR: DB_LANGUAGE undefined");
				System.exit(1);
			}
			FileInputStream refdata_fis = new FileInputStream(refdata_xml_file);

			startTime = System.currentTimeMillis();
			if (CmlOptions.SHOW_LOGS)
				System.out.print("- Unmarshalling Refdata Pharma " + CmlOptions.DB_LANGUAGE + "... ");

			JAXBContext context = JAXBContext.newInstance(Pharma.class);
			Unmarshaller um = context.createUnmarshaller();
			Pharma refdataPharma = (Pharma) um.unmarshal(refdata_fis);
			List<Pharma.ITEM> pharma_list = refdataPharma.getItem();

			String smno8;
			for (Pharma.ITEM pharma : pharma_list) {
				String ean_code = pharma.getGtin();
				if (ean_code.length() == 13) {
					smno8 = ean_code.substring(4, 12);
					// Extract pharma corresponding to swissmedicno8 (source: swissmedic package file)
					ArrayList<String> pi_row = m_package_info.get(smno8);
					// Replace sequence_name
					if (pi_row != null) {
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
							else if (CmlOptions.DB_LANGUAGE.equals("it"))
								pi_row.set(10, "f.c.");
						}
						// 22.03.2014: EAN-13 barcodes - replace with refdata if package exists
						pi_row.set(14, ean_code);
					} else {
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
	
	public void process() {
		// Extract package information (this is the heavy-duty bit)
		extractPackageInfo();
		
		// Load CSS file
		String amiko_style_v1_str = FileOps.readCSSfromFile(Constants.FILE_STYLE_CSS_BASE + "v1.css");
		
		// Initialize counters for different languages
		int med_counter = 0;
		int tot_med_counter = 0;
		String pi_complete_xml = "";
		
		HtmlUtils html_utils = null;
		
		System.out.println("Processing Patient Infos...");	
		
		for( MedicalInformations.MedicalInformation m : m_med_list ) {
			// --> Read PATIENTENINFOS! <--				
			if (m.getLang().equals(CmlOptions.DB_LANGUAGE) && m.getType().equals("pi")) {
				if (tot_med_counter<5000) {									
					if (m.getTitle().trim().toLowerCase().startsWith(CmlOptions.OPT_MED_TITLE.toLowerCase())  
							&& m.getAuthHolder().toLowerCase().startsWith(CmlOptions.OPT_MED_OWNER.toLowerCase())) {	
						
						// Extract section titles and section ids
						/*
						MedicalInformations.MedicalInformation.Sections med_sections = m.getSections();
						List<MedicalInformations.MedicalInformation.Sections.Section> med_section_list = med_sections.getSection();
						String ids_str = "";
						String titles_str = "";
						for( MedicalInformations.MedicalInformation.Sections.Section s : med_section_list ) {
							ids_str += (s.getId() + ",");
							titles_str += (s.getTitle() + ";");
						}	
						*/
						
						System.out.println(tot_med_counter + " - " + m.getTitle() + ": " + m.getAuthNrs());// + " ver -> "+ m.getVersion());						
											
						// Clean html
						html_utils = new HtmlUtils(m.getContent());
						html_utils.setLanguage(CmlOptions.DB_LANGUAGE);
						// Remove spans 
						html_utils.clean();	
																		
						// Sanitize html, the function returns nicely formatted html												
						String html_sanitized = html_utils.sanitizePatient(m.getTitle(), m.getAuthHolder(), CmlOptions.DB_LANGUAGE);
						String mContent_str = html_sanitized;
						
						/*
						 * Update "Packungen" section and extract therapeutisches index
						 */
						List<String> mTyIndex_list = new ArrayList<String>();						
						mContent_str = updateSectionPackungen(m.getTitle(), m.getAtcCode(), m_package_info, m.getAuthNrs(), html_sanitized, mTyIndex_list);
					
						if (CmlOptions.XML_FILE==true) {
							// Add header to html file							
							mContent_str = mContent_str.replaceAll("<head>", "<head>" + 
									"<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />" +
									"<style>" + amiko_style_v1_str + "</style>");	
							// --> Note: following line is not really necessary...
							// m.setContent(mContent_str);
									
							// Add header to xml file
							String xml_str = html_utils.convertHtmlToXml("pi", m.getTitle(), mContent_str, m.getAuthNrs());									
							xml_str = html_utils.addHeaderToXml("singlepi", xml_str);
							pi_complete_xml += (xml_str + "\n");
							
							// Write to html and xml files to disk
							String name = m.getTitle();
							// Replace all "Sonderzeichen"
							name = name.trim().replaceAll("[^a-zA-Z0-9]+", "_");									
							if (CmlOptions.DB_LANGUAGE.equals("de")) {
								FileOps.writeToFile(mContent_str, Constants.PI_FILE_XML_BASE + "pi_de_html/", name + "_pi_de.html");
								FileOps.writeToFile(xml_str, Constants.PI_FILE_XML_BASE + "pi_de_xml/", name + "_pi_de.xml");
							} else if (CmlOptions.DB_LANGUAGE.equals("fr")) {
								FileOps.writeToFile(mContent_str, Constants.PI_FILE_XML_BASE + "pi_fr_html/", name + "_pi_fr.html");
								FileOps.writeToFile(xml_str, Constants.PI_FILE_XML_BASE + "pi_fr_xml/", name + "_pi_fr.xml");
							} else if (CmlOptions.DB_LANGUAGE.equals("it")) {
								FileOps.writeToFile(mContent_str, Constants.PI_FILE_XML_BASE + "pi_it_html/", name + "_pi_it.html");
								FileOps.writeToFile(xml_str, Constants.PI_FILE_XML_BASE + "pi_it_xml/", name + "_pi_it.xml");
							}
						}
						
						med_counter++;
					}
				}
				tot_med_counter++;
			}
		}
		System.out.println();
		System.out.println("--------------------------------------------");
		System.out.println("Total number of med infos in database: " + m_med_list.size());
		System.out.println("Number of PI with package information: " + tot_med_counter);
		System.out.println("Number of PI which were processed: " + med_counter);
		System.out.println("--------------------------------------------");
	}
	
	private String updateSectionPackungen(String title, String atc_code, Map<String, ArrayList<String>> pack_info, 
			String regnr_str, String content_str, List<String> tIndex_list) {
		Document doc = Jsoup.parse(content_str, "UTF-16");
		doc.outputSettings().escapeMode(EscapeMode.xhtml);
		doc.outputSettings().prettyPrint(true);
		doc.outputSettings().indentAmount(4);
		
		// package info string for original
		List<String> pinfo_originals_str = new ArrayList<String>();
		// package info string for generika
		List<String> pinfo_generics_str = new ArrayList<String>();
		// package info string for the rest
		List<String> pinfo_str = new ArrayList<String>();
		
		int index = 0;

		// Extract swissmedicno5 registration numbers
		List<String> swissmedicno5_list = Arrays.asList(regnr_str.split("\\s*,\\s*"));
		for (String s : swissmedicno5_list) {
			// Extract original / generika info + Selbstbehalt info from
			// "add_info_map"
			String orggen_str = "";		// O=Original, G=Generika
			String flagsb_str = "";		// SB=Selbstbehalt 
			String addinfo_str = m_add_info_map.get(s);
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
				if (n<10)
					swissmedicno8_key = s + String.valueOf(n).format("00%d", n);
				else if (n<100)
					swissmedicno8_key = s + String.valueOf(n).format("0%d", n);
				else
					swissmedicno8_key = s + String.valueOf(n).format("%d", n);
				// Check if swissmedicno8_key is a key of the map
				if (pack_info.containsKey(swissmedicno8_key)) {
					ArrayList<String> pi_row = m_package_info.get(swissmedicno8_key);
					if (pi_row != null) {										
						
						// Remove double spaces in title and capitalize
						// String medtitle = capitalizeFully(pi_row.get(1).replaceAll("\\s+", " "), 1);
						String medtitle = capitalizeFully(pi_row.get(1).replaceAll("\\s+", " "));
						// Remove [QAP?] -> not an easy one!
						medtitle = medtitle.replaceAll("\\[(.*?)\\?\\] ", "");						
						// --> Add "ausser Handel" information
						String withdrawn_str = "";
						if (pi_row.get(10).length() > 0)
							withdrawn_str = ", " + pi_row.get(10);
						// --> Add public price information
						if (pi_row.get(7).length() > 0) {
							// The rest of the package information
							String append_str = ", " + pi_row.get(7) 
									+ withdrawn_str + " [" + pi_row.get(5) 
									+ pi_row.get(11) + pi_row.get(12) 
									+ flagsb_str + orggen_str + "]";
							// Generate package info string
							if (orggen_str.equals(", O"))
								pinfo_originals_str.add("<p class=\"spacing1\">" + medtitle + append_str + "</p>");
							else if (orggen_str.equals(", G"))
								pinfo_generics_str.add("<p class=\"spacing1\">" + medtitle + append_str + "</p>");
							else
								pinfo_str.add("<p class=\"spacing1\">" + medtitle + append_str + "</p>");								
						} else {
							//
							// @maxl (10.01.2014): Price for swissmedicNo8 pack is not listed in bag_preparations.xml!!
							//
							pinfo_str.add("<p class=\"spacing1\">"
										+ medtitle + withdrawn_str + " [" + pi_row.get(5) +"]</p>");
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
		if (CmlOptions.NO_PACK==false) {
			// Replace original package information with pinfo_str	
			String p_str = "<p class=\"spacing2\"> </p>";
			for (String p : pinfo_str) {
				p_str += p;
			}	
		
			doc.outputSettings().escapeMode(EscapeMode.xhtml);
			Elements elems = null;
			if (CmlOptions.DB_LANGUAGE.equals("de"))
				elems = doc.select("div[id^=section]").select("div:matchesOwn(Welche Packungen sind erhältlich?)");
			else if (CmlOptions.DB_LANGUAGE.equals("fr"))
				elems = doc.select("div[id^=section]").select("div:matchesOwn(Quels sont les emballages à disposition)");
			else if (CmlOptions.DB_LANGUAGE.equals("it"))
				elems = doc.select("div[id^=section]").select("div:matchesOwn(Quali confezioni sono disponibili?)");			
			if (elems!=null) {
				for (Element e : elems) {
					Elements siblings = e.siblingElements();
					if (siblings!=null) {
						// ** Chapter "Packungen"						
						// System.out.println(e.siblingElements().last().html());
						// Note: do not use "append"
						if (siblings.last()!=null)
							siblings.last().after(p_str);
					}
				}
			}			
		}
		
		return doc.html();
	}
	
	private String capitalizeFully(String s) {
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
}
