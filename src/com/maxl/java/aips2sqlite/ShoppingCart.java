package com.maxl.java.aips2sqlite;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.joda.time.DateTime;

import com.maxl.java.shared.Conditions;

public class ShoppingCart implements java.io.Serializable {
	
	private static final long serialVersionUID = 1L;
	
	boolean Debug = false;
	Map<String, Conditions> m_map_conditions = null;
	Map<String, Product> m_map_products = null;
	Map<String, String> m_map_group_id = null;
	
	public ShoppingCart(Map<String, Product> map_products) {
		m_map_products = map_products;		
	}
	
	private String getCellValue(Cell part) {
		if (part!=null) {
		    switch (part.getCellType()) {
		        case Cell.CELL_TYPE_BOOLEAN: return part.getBooleanCellValue() + "";
		        case Cell.CELL_TYPE_NUMERIC: return String.format("%.2f", part.getNumericCellValue());
		        case Cell.CELL_TYPE_STRING:	return part.getStringCellValue() + "";
		        case Cell.CELL_TYPE_BLANK: return "";
		        case Cell.CELL_TYPE_ERROR: return "ERROR";
		        case Cell.CELL_TYPE_FORMULA: return "FORMULA";
		    }
		}
		return "";
	}
	
	public void listFiles(String path) {
		File folder = new File(path);
		File[] list_of_files = folder.listFiles(); 
		 
		for (int i=0; i<list_of_files.length; i++) {
			if (list_of_files[i].isFile()) {
				String file = list_of_files[i].getName();
				if (file.endsWith(".csv") || file.endsWith(".xls") || file.endsWith(".json")) {
					System.out.println("Found file: " + file);
		        }
			}
		}
	}
		
	public Map<String, String> readPharmacyGroups() {

		System.out.println("\nExtracting pharmacy groups...");
		
		try {
			// Load ibsa xls file			
			FileInputStream pharma_conditions = new FileInputStream(Constants.DIR_SHOPPING + "ibsa_pharma_conditions.xlsx");
			// Get workbook instance for XLS file (HSSF = Horrible SpreadSheet Format)
			XSSFWorkbook ibsa_workbook = new XSSFWorkbook(pharma_conditions);
			// Get first sheet from workbook
			XSSFSheet ibsa_sheet = ibsa_workbook.getSheetAt(0);
			// Iterate through all rows
			Iterator<Row> rowIterator = ibsa_sheet.iterator();
			//
			m_map_group_id = new TreeMap<String, String>();
			int num_rows = 0;
			while (rowIterator.hasNext() && num_rows<2) {
				Row row = rowIterator.next();
				if (num_rows==0) {
					int num_groups = row.getLastCellNum();
					char id = 'C';
					for (int col=3; col<num_groups; ++col) {
						// Extract group name
						String name_group = getCellValue(row.getCell(col));
						name_group = name_group.toLowerCase().trim();		
						// Check if it's a standard group or promotion
						if (name_group.contains("standard")) {
							// Add category to group name
							m_map_group_id.put(name_group, String.valueOf(id));
							System.out.println("Pharma-group #" + (id-'C'+1) + ": " + name_group + " (ID = " + id + ")");
							id++;
						} else if (name_group.contains("aktion")) {
							String standard_name_group = name_group.replaceAll("aktion", "standard").trim();
							if (m_map_group_id.containsKey(standard_name_group)) {
								String cat = m_map_group_id.get(standard_name_group);
								m_map_group_id.put(name_group, cat + "-promo");
							} else {
								m_map_group_id.put(name_group, String.valueOf(id) + "-promo");
								id++;
							}
							System.out.println("Pharma-group #" + (id-'C'+1) + ": " + name_group + " (ID = " + m_map_group_id.get(name_group) + ")");									
						}
					}
				}
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
		return m_map_group_id;
	}
	
	private Map<Integer, String> createCondMap() {
        Map<Integer, String> cond_map = new TreeMap<Integer, String>();
        cond_map.put(17, "B-doctor");			// B-Arztpraxis
        cond_map.put(18, "A-doctor");			// A-Arztpraxis	
        cond_map.put(20, "B-pharmacy");			// B-Apotheke
        cond_map.put(21, "A-pharmacy");			// A-Apotheke
        cond_map.put(23, "B-pharmacy-promo");	// B-Apotheke promo-cycle
        cond_map.put(24, "A-pharmacy-promo");	// A-Apotheke promo-cycle
        cond_map.put(25, "B-drugstore");		// B-Drogerie
        cond_map.put(26, "A-drugstore");		// A-Drogerie
        cond_map.put(29, "B-drugstore-promo");	// B-Drogerie promo-cycle
        cond_map.put(30, "A-drugstore-promo");	// A-Drogerie promo-cycle
        cond_map.put(32, "C-hospital");        	// C-Spital
        cond_map.put(33, "B-hospital");			// B-Spital
        cond_map.put(34, "A-hospital");			// A-Spital
        return Collections.unmodifiableMap(cond_map);
    }
	
	private Map<Integer, String> createAssortMap() {
        Map<Integer, String> assort_map = new TreeMap<Integer, String>();
        assort_map.put(19, "doctor");			
        assort_map.put(22, "pharmacy");			
        assort_map.put(25, "pharmacy-promo");		
        assort_map.put(28, "drugstore");		
        assort_map.put(31, "drugstore-promo");	
        assort_map.put(35, "hospital");	
        return Collections.unmodifiableMap(assort_map);
	}
	
	private String toExcelColName(int number) {
        StringBuilder sb = new StringBuilder();
        while (number-- > 0) {
            sb.append((char)('A' + (number % 26)));
            number /= 26;
        }
        return sb.reverse().toString();
    }
	
	private void processMainConditionsFile(String path) {

		System.out.println("\nProcessing main conditions file... ");
		
		try {
			// Load main ibsa conditiosn xls			
			FileInputStream ibsa_file = new FileInputStream(path);
			// Get workbook instance for XLS file (HSSF = Horrible SpreadSheet Format)
			HSSFWorkbook ibsa_workbook = new HSSFWorkbook(ibsa_file);
			// Get first sheet from workbook
			HSSFSheet ibsa_sheet = ibsa_workbook.getSheetAt(0);
			// Iterate through all rows
			Iterator<Row> rowIterator = ibsa_sheet.iterator();
			// Map of ean code to visibility flag
			Map<String, Integer> map_visibility = new TreeMap<String, Integer>();			
			
			// First round to extract list of products (eancodes) visible for particular customer group
			int num_rows = 0;
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				if (num_rows>1) {
					if (row.getCell(7)!=null) {
						String eancode = getCellValue(row.getCell(7));
						if (eancode!=null && eancode.length()==16) {
                            eancode = eancode.substring(0, eancode.length()-3);
							// 13: doctors, pharmacy; 14: drugstore; 15: hospital; 16: wholesaler
							int visibility = 0x00;	// article visible to no customer group
							if (!getCellValue(row.getCell(13)).toLowerCase().isEmpty())
								if (getCellValue(row.getCell(13)).equals("x"))
									visibility |= 0x08;
							if (!getCellValue(row.getCell(14)).toLowerCase().isEmpty())
								if (getCellValue(row.getCell(14)).equals("x"))
									visibility |= 0x04;
							if (!getCellValue(row.getCell(15)).toLowerCase().isEmpty())
								if (getCellValue(row.getCell(15)).equals("x"))
									visibility |= 0x02;
							if (!getCellValue(row.getCell(16)).isEmpty())
								if (getCellValue(row.getCell(16)).toLowerCase().equals("x"))
									visibility |= 0x01;								
							map_visibility.put(eancode, visibility);
						}
					}
				}
				num_rows++;
			}
			
			Map<Integer, String> cond_map = createCondMap();
			Map<Integer, String> assort_map = createAssortMap();
			
			num_rows = 0;
			rowIterator = ibsa_sheet.iterator();
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				/*
				  1: Präparatname
				  2: Gruppe (DE)
				  3: Gruppe (FR)
				  4: Units				  
				  5: Gal. form (DE)
				  6: Gal. form (FR)
				  7: EAN code
				  8: Pharma code
				  9: Reg.
				 10: FEP exkl. MWSt.
				 11: FAP exkl. MWSt.
				 12: MWSt.
				 13: visible Arzt, Apotheke
				 14: visible Drogerie
				 15: visible Spital
				 16: visible Grosshandel
				 17: B-Arztpraxis
				 18: A-Artzpraxis
				 19: assortierbar mit, comma-separated list
				 20: B-Apotheke
				 21: A-Apotheke
				 22: assortierbar mit, comma-separated list
				 23: Promotionszyklus B-Apotheke
				 24: Promotionszyklus A-Apotheke
				 25: assortierbar mit, comma-separated list
				 26: B-Drogerie
				 27: A-Drogerie
				 28: assortierbar mit, comma-separated list
				 29: Promotionszyklus, B-Drogerie
				 30: Promotionszyklus, A-Drogerie
				 31: assortierbar mit, comma-separated list				 
				 32: C-Spital
				 33: B-Spital
				 34: A-Spital
				 35: assortierbar mit, comma-separated list
				*/

				if (num_rows>1) {
					if (row.getCell(7)!=null) {
						String eancode = getCellValue(row.getCell(7));
						if (eancode!=null && eancode.length()==16) {
							// EAN code read in as "float"!
                            eancode = eancode.substring(0, eancode.length()-3);
							float fep = 0.0f;
							if (!getCellValue(row.getCell(10)).isEmpty())
								fep = Float.valueOf(getCellValue(row.getCell(10)));
							float fap = 0.0f;
							if (!getCellValue(row.getCell(11)).isEmpty())							
								fap = Float.valueOf(getCellValue(row.getCell(11)));	
							float vat = 8.0f;	// [%]
							if (!getCellValue(row.getCell(12)).isEmpty())
								vat = Float.valueOf(getCellValue(row.getCell(12)));
							// 13: doctors, pharmacy; 14: drugstore; 15: hospital; 16: wholesaler
							int visibility = 0x00;	// article visible to no customer group
							if (!getCellValue(row.getCell(13)).toLowerCase().isEmpty())
								if (getCellValue(row.getCell(13)).equals("x"))
									visibility |= 0x08;
							if (!getCellValue(row.getCell(14)).toLowerCase().isEmpty())
								if (getCellValue(row.getCell(14)).equals("x"))
									visibility |= 0x04;
							if (!getCellValue(row.getCell(15)).toLowerCase().isEmpty())
								if (getCellValue(row.getCell(15)).equals("x"))
									visibility |= 0x02;
							if (!getCellValue(row.getCell(16)).isEmpty())
								if (getCellValue(row.getCell(16)).toLowerCase().equals("x"))
									visibility |= 0x01;							
							String name = getCellValue(row.getCell(1)).replaceAll("\\*", "").trim();
							String group_name_de = getCellValue(row.getCell(2)).replaceAll("\\*", "").trim();
							String group_name_fr = getCellValue(row.getCell(3)).replaceAll("\\*", "").trim();
							
							//  
							Product product = new Product();
							product.processed = false;
							product.title = name;
							product.group_title[0] = group_name_de;
							product.group_title[1] = group_name_fr;
							product.author = "IBSA Institut Biochimique SA";	// Currently only one company
							product.size = getCellValue(row.getCell(4));		// Packungsgrösse
							if (product.size!=null && product.size.endsWith(".00"))
								product.size = product.size.substring(0, product.size.length()-3);
							product.units[0] = getCellValue(row.getCell(5));	// Galenische Form (DE)
							product.units[1] = getCellValue(row.getCell(6));	// Galenische Form (FR)
							if (product.units[0]!=null)
								product.units[0] = product.units[0].trim();
							if (product.units[1]!=null)
								product.units[1] = product.units[1].trim();
							product.eancode = eancode;
							String pharmacode = getCellValue(row.getCell(8));
							if (pharmacode!=null && pharmacode.length()>3)
								product.pharmacode = pharmacode.substring(0, pharmacode.length()-3);
							product.swissmedic_cat = getCellValue(row.getCell(9));
							product.fep = fep;
							product.fap = fap;
							product.vat = vat;
							product.visible = visibility;
							m_map_products.put(eancode, product);
							
							// Instantiate new med condition
							Conditions cond = new Conditions(eancode, name.toUpperCase() + ", " + product.units[0] + ", " + product.size, fep, fap);	
							System.out.println(eancode + " -> " + name);							

							// Rebates
							int col = 0;
							try {
								// Konditionen
								for (Map.Entry<Integer, String> entry : cond_map.entrySet()) {
									String value = entry.getValue();
									col = entry.getKey();
									extractDiscounts(cond, value, getCellValue(row.getCell(col)));
								}
								// Assortierbarkeiten
								for (Map.Entry<Integer, String> entry : assort_map.entrySet()) {
									String value = entry.getValue();
									col = entry.getKey();
									extractAssort(cond, value, getCellValue(row.getCell(col)), map_visibility);
								}						
							} catch(Exception e) {
								System.out.println(">> Exception while processing Excel-File " + path);
								int error_row = row.getRowNum()+1;
								int error_col = col+1;
								System.out.println(">> Check " + name + " (" + eancode + ") -> [r=" + error_row + " c=" + toExcelColName(error_col) + "]");
								e.printStackTrace();
								System.exit(-1);
							}
							// Add to list of conditions
							m_map_conditions.put(eancode, cond);		
						}
					}
				}
				num_rows++;
			}
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	private void processSecondaryConditionsFile(String path) {
		
		System.out.println("\nProcessing secondary conditions file...");
		
		if (m_map_conditions==null || m_map_group_id==null)
			return;
		
		try {
			// Load ibsa xls file			
			FileInputStream pharma_conditions = new FileInputStream(path);
			// Get workbook instance for XLS file (HSSF = Horrible SpreadSheet Format)
			XSSFWorkbook ibsa_workbook = new XSSFWorkbook(pharma_conditions);
			// Get first sheet from workbook
			XSSFSheet ibsa_sheet = ibsa_workbook.getSheetAt(0);
			// Iterate through all rows
			Iterator<Row> rowIterator = ibsa_sheet.iterator();
			int num_rows = 0;
			Row first_row = null;
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				int num_groups = row.getLastCellNum();
				if (num_rows==0) {	// Save first row
					first_row = row;
				} else if (num_rows>0) {
					if (row.getCell(2)!=null) {
						String eancode = getCellValue(row.getCell(2));
						if (eancode!=null && eancode.length()==16) {
                            eancode = eancode.substring(0, eancode.length()-3);
                            if (m_map_conditions.containsKey(eancode)) {
                            	Conditions cond = m_map_conditions.get(eancode);
                            	String name = cond.name;
    							System.out.println(eancode + " -> " + name);		
	                            // Loop through all columns
	                            for (int col=3; col<num_groups; ++col) {
	                            	// Check if group_name is in group map
	                            	String group = getCellValue(first_row.getCell(col)).toLowerCase().trim();
	                            	if (m_map_group_id.containsKey(group)) {
	                            		try {
	                            			// Add new category to conditions...
	                            			String group_id = m_map_group_id.get(group);
	                            			// Generate conditions-compatible group id
	                            			group_id = group_id.substring(0, 1) + "-pharmacy" + group_id.substring(1);
	                            			if (Debug)
	                            				System.out.println("  Processing pharma-group: '" + group_id + "'");	                            			
	        								extractDiscounts(cond, group_id, getCellValue(row.getCell(col)));						
	        							} catch(Exception e) {
	        								System.out.println(">> Exception while processing Excel-File " + path);
	        								int error_row = row.getRowNum()+1;
	        								int error_col = col+1;
	        								System.out.println(">> Check " + name + " (" + eancode + ") -> [r=" + error_row + " c=" + toExcelColName(error_col) + "]");
	        								e.printStackTrace();
	        								System.exit(-1);
	        							}
	        							// Add to list of conditions
	        							m_map_conditions.put(eancode, cond);
	                            	}
	                            }
                            }
						}
					}					
				}
				num_rows++;
			}
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	private void testConditionsMap() {
		System.out.println("Test conditions map...");
		for (Map.Entry<String, Conditions> entry : m_map_conditions.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue().name);
			Conditions cond = entry.getValue();
			Set<Character> pharma_category = cond.getCategoriesPharmacy(false);
			for (char c : pharma_category) {
				TreeMap<Integer, Float> discount_map = entry.getValue().getDiscountPharmacy(c, false);
				if (discount_map!=null)
					System.out.println("  " + c + " -> " + discount_map.size());
			}
		}
	}
	
	public void encryptConditionsToDir(String in_dir, String out_dir, String filename) {
		// First check if path exists
		File f = new File(in_dir);
		if (!f.exists() || !f.isDirectory()) {
			System.out.println("Directory " + in_dir + " does not exist!");
			return;
		}		

		// Initialize map conditions
		m_map_conditions = new TreeMap<String, Conditions>();

		// 1. Process main ibsa conditions file
		processMainConditionsFile(in_dir + "/" + "ibsa_conditions.xls");		
		// 2. Process secondary ibsa conditions file (pharmacies)
		processSecondaryConditionsFile(in_dir + "/" + "ibsa_pharma_conditions.xlsx");
		
		System.out.println("");

		// Test conditions map
		// testConditionsMap();
		
		// First serialize into a byte array output stream, then encrypt
		Crypto crypto = new Crypto();
		byte[] encrypted_msg = null;
		if (m_map_conditions!=null && m_map_conditions.size()>0) {
			byte[] serializedBytes = FileOps.serialize(m_map_conditions);
			if (serializedBytes!=null) {
				encrypted_msg = crypto.encrypt(serializedBytes);
			}
			// Write to file
			FileOps.writeToFile(out_dir + filename + ".ser", encrypted_msg);
			System.out.println("Saved encrypted file " + filename + ".ser");
		} else {
			System.out.println("!! Error occurred when generating " + filename + ".ser");
			System.exit(1);
		}
	}
	
	private void extractDiscounts(Conditions c, String category, String discount_str) 
		throws Exception {
		if (!discount_str.isEmpty()) {
			// All regex patterns
			Pattern date_pattern1 = Pattern.compile("\\b(\\d{2}).(\\d{2}).(\\d{4})-(\\d{2})\\b", Pattern.DOTALL);
			Pattern date_pattern2 = Pattern.compile("\\b(\\d{2})-(\\d{2})\\b", Pattern.DOTALL);
			Pattern rebate_pattern1 = Pattern.compile("([0-9/.:]+)\\((.*?)\\)", Pattern.DOTALL);
			Pattern rebate_pattern2 = Pattern.compile("\\((.*?)\\)", Pattern.DOTALL);	
			Pattern rebate_pattern3 = Pattern.compile("([0-9]+):([0-9]+)(:[0-9]+)?", Pattern.DOTALL);

			// *** Complex date regex ***
			Matcher date_match1 = date_pattern1.matcher(discount_str);	
			while (date_match1.find()) {
				int day1 = Integer.parseInt(date_match1.group(1));
				int month1 = Integer.parseInt(date_match1.group(2));
				int year1 = Integer.parseInt(date_match1.group(3));
				int month2 = Integer.parseInt(date_match1.group(4));
				if (month1<month2) {
					int d1 = (new DateTime(year1, month1, day1, 0, 0, 0)).getDayOfYear();
					int d2 = 0;
					if (month2<12)
						d2 = (new DateTime(year1, month2+1, 1, 0, 0, 0)).getDayOfYear();						
					else // December 31st
						d2 = (new DateTime(year1, 12, 31, 0, 0, 0)).getDayOfYear();
					if (Debug)
						System.out.println("# complex date -> from " + d1 + " to " + d2);						
					if (category.endsWith("-pharmacy-promo")) {
						for (int m=month1; m<=month2; ++m)
							c.addPromoMonth("pharmacy", category.charAt(0), m);
						c.addPromoTime("pharmacy", category.charAt(0), d1, d2);
					}
					if (category.endsWith("-drugstore-promo")) {
						for (int m=month1; m<=month2; ++m)
							c.addPromoMonth("drugstore", category.charAt(0), m);
						c.addPromoTime("drugstore", category.charAt(0), d1, d2);
					}				
				}
			}
			// *** Simple date regex ***
			Matcher date_match2 = date_pattern2.matcher(discount_str);
			while (date_match2.find()) {
				int month1 = Integer.parseInt(date_match2.group(1));
				int month2 = Integer.parseInt(date_match2.group(2));
				if (month1<month2) {
					DateTime curr_dt = new DateTime();
					int curr_year = curr_dt.getYear();
					int d1 = (new DateTime(curr_year, month1, 1, 0, 0, 0)).getDayOfYear();
					int d2 = 0;
					if (month2<12)
						d2 = (new DateTime(curr_year, month2+1, 1, 0, 0, 0)).getDayOfYear();
					else	// Januar 1st
						d2 = (new DateTime(curr_year, 12, 31, 0, 0, 0)).getDayOfYear();
					if (Debug)
						System.out.println("# simple date -> from " + d1 + " to " + d2 + " (" + month1 + " - " + month2 + ")");		
					if (category.endsWith("-pharmacy-promo")) {
						for (int m=month1; m<=month2; ++m)
							c.addPromoMonth("pharmacy", category.charAt(0), m);
						c.addPromoTime("pharmacy", category.charAt(0), d1, d2);
					}
					if (category.endsWith("-drugstore-promo")) {
						for (int m=month1; m<=month2; ++m)
							c.addPromoMonth("drugstore", category.charAt(0), m);
						c.addPromoTime("drugstore", category.charAt(0), d1, d2);
					}					
				}
			}
			
			// Split comma-separated list
			String[] rebates = discount_str.split("\\s*,\\s*");	
			// Loop through all elements of the list
			for (int i=0; i<rebates.length; ++i) {		
				// *** units(discount in %) pattern ***
				Matcher rebate_match1 = rebate_pattern1.matcher(rebates[i]);
				if (rebate_match1.matches()) {
					if (Debug)
						System.out.println("# rebate -> " + rebates[i]);	
					// Get units by removing parentheses
					String units = rebates[i].replaceAll("\\(.*\\)","");
					// Get discount as content of the parentheses
					Matcher rebate_match2 = rebate_pattern2.matcher(rebates[i]);
					rebate_match2.find();
					String parenthesis_str = rebate_match2.group(1);
					// Validation...
					if (!parenthesis_str.contains("%")) {
						throw new Exception("Missing '%' in expression: " + rebates[i]);
					}
					if (!parenthesis_str.matches("[-]?\\d+\\%")) {
						throw new Exception("Fix format: " + parenthesis_str);
					}
					String discount = parenthesis_str.replaceAll("%", "");
					// Extract all units 
					// Note: discount can also be <0!
					if (units!=null) {				
						Matcher rebate_match3 = rebate_pattern3.matcher(units);
						if (rebate_match3.matches()) {
							int step = 10;
							if (rebate_match3.groupCount()==3) {
								if (rebate_match3.group(3)!=null) {
									String s = rebate_match3.group(3);
									step = Integer.valueOf(s.replaceAll(":",""));
								}
							}
							int from = Integer.valueOf(rebate_match3.group(1));
							int to = Integer.valueOf(rebate_match3.group(2));
							// Increment units to max in steps of step (default=10)								
							for (int k=from; k<=to; k+=step) {
								String single_unit = String.format("%d", k);
								addDiscount(c, category, single_unit, discount);
							}
						} else {
							int u = Integer.valueOf(units);						
							// Check if number of units is limited to <=100 and its a "loner"
							if (u<100 && i==(rebates.length-1)) {
								if (Debug)
									System.out.println("# last loner " + u + " to 100 in steps of 10");
								for (int k=u; k<=100; k+=10) {
									String single_unit = String.format("%d", k);
									addDiscount(c, category, single_unit, discount);
								}
							} else if (u>=100 && u<500 && i==(rebates.length-1)) {
								if (Debug)
									System.out.println("# last loner " + u + " to 500 in steps of 10");
								for (int k=u; k<=500; k+=100) {
									String single_unit = String.format("%d", k);
									addDiscount(c, category, single_unit, discount);
								}								
							} else {
								String single_unit = String.format("%d", u);
								addDiscount(c, category, single_unit, discount);
							}
						}
						continue;
					}
				} 
				if (rebates[i].matches("([0-9.]+)")) {
					String units = rebates[i];
					int u = Float.valueOf(units).intValue();	
					if (u==1 || u==2) {
						// Found "Muster"! Barrabatt = -100%
						units = String.format("%d", u);
						if (Debug)
							System.out.println("# muster -> " + units);						
						addDiscount(c, category, units, "-100");
					} else {
						// Loners are filled up to 100 units with a 10er increment
						if (rebates.length==1 || i==(rebates.length-1)) {
							if (u<100) {
								if (Debug)
									System.out.println("# loner -> " + u + " to 100 in steps of 10");	
								for (int k=u; k<=100; k+=10) {
									String single_unit = String.format("%d", k);
									// No discount
									addDiscount(c, category, single_unit, "0");
								}
							} else if (u>=100 && u<500){
								if (Debug)
									System.out.println("# loner -> " + u + " to 500 in steps of 100");										
								for (int k=u; k<=500; k+=100) {
									String single_unit = String.format("%d", k);
									// No discount
									addDiscount(c, category, single_unit, "0");
								}								
							}
						} else {							
							if (Debug)
								System.out.println("# single loner -> " + u);	
							String single_unit = String.format("%d", u);
							addDiscount(c, category, single_unit, "0");
						}
					}
					continue;					
				}
			}
		}
	}
	
	private boolean addDiscount(Conditions c, String category, String u, String d) {
		boolean discounted = true;
		
		int units = 0;
		float discount = 0.0f;
		if (u!=null)
			units = (Float.valueOf(u)).intValue();					
		if (d!=null)
			discount = Float.valueOf(d);	
		
		if (category.endsWith("-doctor"))
			c.addDiscountDoctor(category.charAt(0), units, discount);
		else if (category.endsWith("-pharmacy"))
			c.addDiscountPharmacy(category.charAt(0), units, discount, false);
		else if (category.endsWith("-pharmacy-promo"))
			c.addDiscountPharmacy(category.charAt(0), units, discount, true);
		else if (category.endsWith("-drugstore"))
			c.addDiscountDrugstore(category.charAt(0), units, discount, false);
		else if (category.endsWith("-drugstore-promo"))
			c.addDiscountDrugstore(category.charAt(0), units, discount, true);
		else if (category.endsWith("-hospital"))
			c.addDiscountHospital(category.charAt(0), units, discount);
		else
			discounted = false;
		
		return discounted;
	}
	
	private void extractAssort(Conditions c, String category, String eans_str, Map<String, Integer> map_visibility) {
		if (!eans_str.isEmpty()) {
			// Get all encodes in the list
			String[] eans = eans_str.split("\\s*,\\s*");
			List<String> items = Arrays.asList(eans);
			List<String> cleaned_eans = new ArrayList<String>();
			// Loop through the list
			for (int i=0; i<items.size(); ++i) {
				String ean_code = items.get(i);				
				if (ean_code.contains("."))
					ean_code = ean_code.split("\\.")[0];
				if (map_visibility.containsKey(ean_code)) {
					int visible = map_visibility.get(ean_code);
					if ((visible & 0x08)>0 && (category.equals("doctor") || category.equals("pharmacy") || category.equals("pharmacy-promo"))
							|| ((visible & 0x04)>0 && (category.equals("drugstore")) || category.equals("drugstore-promo"))
							|| ((visible & 0x02)>0 && (category.equals("hospital")))
							|| ((visible & 0x01)>0 && (category.equals("wholesaler")))) {
						cleaned_eans.add(ean_code);
					}
				}
			}
			c.setAssort(category, cleaned_eans);
		}
	}
}
