package com.maxl.java.aips2sqlite;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.joda.time.DateTime;

import com.maxl.java.shared.Conditions;

public class ShoppingCart implements java.io.Serializable {
	
	boolean Debug = false;
	Map<String, Product> map_products = null;
	
	public ShoppingCart(Map<String, Product> map_products) {
		this.map_products = map_products;
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
	
	public void encryptConditionsToDir(String filename, String dir) {
		// First check if path exists
		File f = new File(dir);
		if (!f.exists() || !f.isDirectory()) {
			System.out.println("Directory " + dir + " does not exist!");
			return;
		}
		try {
			// Load ibsa xls file			
			FileInputStream ibsa_file = new FileInputStream(dir + filename + ".xls");
			// Get workbook instance for XLS file (HSSF = Horrible SpreadSheet Format)
			HSSFWorkbook ibsa_workbook = new HSSFWorkbook(ibsa_file);
			// Get first sheet from workbook
			HSSFSheet ibsa_sheet = ibsa_workbook.getSheetAt(0);
			// Iterate through all rows
			Iterator<Row> rowIterator = ibsa_sheet.iterator();
			// Map of ean code to rebate condition
			Map<String, Conditions> map_conditions = new TreeMap<String, Conditions>();
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
							String name = getCellValue(row.getCell(1)).replaceAll("\\*", "").trim();
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
							Product product = new Product();
							product.processed = false;
							product.title = name;
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
							map_products.put(eancode, product);
							
							// Instantiate new med condition
							Conditions cond = new Conditions(eancode, name.toUpperCase() + ", " + product.units[0] + ", " + product.size, fep, fap);	
							System.out.println(eancode + " -> " + name + " " + product.size + ", " + product.units[0]);							

							// Rebates
							try {
								extractDiscounts(cond, "B-doctor", getCellValue(row.getCell(17)));			// B-Arztpraxis
								extractDiscounts(cond, "A-doctor", getCellValue(row.getCell(18)));			// A-Arztpraxis		
								extractDiscounts(cond, "B-pharmacy", getCellValue(row.getCell(20)));		// B-Apotheke
								extractDiscounts(cond, "A-pharmacy", getCellValue(row.getCell(21)));		// A-Apotheke
								extractDiscounts(cond, "B-pharmacy-promo", getCellValue(row.getCell(23)));	// B-Apotheke promo-cycle
								extractDiscounts(cond, "A-pharmacy-promo", getCellValue(row.getCell(24)));	// A-Apotheke promo-cycle
								extractDiscounts(cond, "B-drugstore", getCellValue(row.getCell(25)));		// B-Drogerie
								extractDiscounts(cond, "A-drugstore", getCellValue(row.getCell(26)));		// A-Drogerie
								extractDiscounts(cond, "B-drugstore-promo", getCellValue(row.getCell(29)));	// B-Drogerie promo-cycle
								extractDiscounts(cond, "A-drugstore-promo", getCellValue(row.getCell(30)));	// A-Drogerie promo-cycle
								extractDiscounts(cond, "C-hospital", getCellValue(row.getCell(32)));		// C-Spital
								extractDiscounts(cond, "B-hospital", getCellValue(row.getCell(33)));		// B-Spital
								extractDiscounts(cond, "A-hospital", getCellValue(row.getCell(34)));		// A-Spital
								// Assortiebarkeit
								extractAssort(cond, "doctor", getCellValue(row.getCell(19)), map_visibility);
								extractAssort(cond, "pharmacy", getCellValue(row.getCell(22)), map_visibility);
								extractAssort(cond, "pharmacy-promo", getCellValue(row.getCell(25)), map_visibility);							
								extractAssort(cond, "drugstore", getCellValue(row.getCell(28)), map_visibility);
								extractAssort(cond, "drugstore-promo", getCellValue(row.getCell(31)), map_visibility);
								extractAssort(cond, "hospital", getCellValue(row.getCell(35)), map_visibility);								
							} catch(Exception e) {
								System.out.println(">> Exception while processing Excel-File " + filename);
								System.out.println(">> Check " + eancode + " -> " + name);
								e.printStackTrace();
								System.exit(-1);
							}

							// Test
							/*
							TreeMap<Integer, Float> test = cond.getDiscountPharmacy('A', true);
							for (Map.Entry<Integer, Float> entry : test.entrySet()) {
								int unit = entry.getKey();
								float discount = entry.getValue();								
								System.out.print(unit + " -> " + discount + "  ");
							}
							System.out.println();
							*/
							// Add to list of conditions
							map_conditions.put(eancode, cond);		
						}
					}
				}
				num_rows++;
			}
			// First serialize into a byte array output stream, then encrypt
			Crypto crypto = new Crypto();
			byte[] encrypted_msg = null;
			if (map_conditions.size()>0) {
				byte[] serializedBytes = FileOps.serialize(map_conditions);
				if (serializedBytes!=null) {
					encrypted_msg = crypto.encrypt(serializedBytes);
					// System.out.println(Arrays.toString(encrypted_msg));
				}
			}
			// Write to file
			FileOps.writeToFile(Constants.DIR_OUTPUT + filename +".ser", encrypted_msg);
			System.out.println("Saved encrypted file " + filename +".ser");

			// TEST: Read from file
			/*
			encrypted_msg = readFromFile(Constants.DIR_OUTPUT + filename + ".ser");
			// Test: first decrypt, then deserialize
			if (encrypted_msg!=null) {
				byte[] plain_msg = crypto.decrypt(encrypted_msg);
				Map<String, Conditions> mc = new TreeMap<String, Conditions>();
				mc = (TreeMap<String, Conditions>)deserialize(plain_msg);
				for (Map.Entry<String, Conditions> entry : mc.entrySet()) {
					System.out.println(entry.getKey() + " -> " + entry.getValue().name);
				}
			}
			*/
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	private void extractDiscounts(Conditions c, String category, String discount_str) {
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
					if (category.equals("A-pharmacy-promo") || category.equals("B-pharmacy-promo")) {
						for (int m=month1; m<=month2; ++m)
							c.addPromoMonth("pharmacy", category.charAt(0), m);
						c.addPromoTime("pharmacy", category.charAt(0), d1, d2);
					}
					if (category.equals("A-drugstore-promo") || category.equals("B-drugstore-promo")) {
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
						System.out.println("# simple date -> from " + d1 + " to " + d2);		
					if (category.equals("A-pharmacy-promo") || category.equals("B-pharmacy-promo")) {
						for (int m=month1; m<=month2; ++m)
							c.addPromoMonth("pharmacy", category.charAt(0), m);
						c.addPromoTime("pharmacy", category.charAt(0), d1, d2);
					}
					if (category.equals("A-drugstore-promo") || category.equals("B-drugstore-promo")) {
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
					String discount = rebate_match2.group(1).replaceAll("%", "");
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
							if (u<=100 && i==(rebates.length-1)) {
								// Increment units to 100 in steps of 10
								if (Debug)
									System.out.println("# last loner " + u + " to 100");
								for (int k=u; k<=100; k+=10) {
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
							if (Debug)
								System.out.println("# loner -> " + u + " to 100");	
							for (int k=u; k<=100; k+=10) {
								String single_unit = String.format("%d", k);
								// No discount
								addDiscount(c, category, single_unit, "0");
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
		
		if (category.equals("B-doctor"))
			c.addDiscountDoctor('B', units, discount);
		else if (category.equals("A-doctor"))
			c.addDiscountDoctor('A', units, discount);
		else if (category.equals("B-pharmacy"))
			c.addDiscountPharmacy('B', units, discount, false);
		else if (category.equals("A-pharmacy"))
			c.addDiscountPharmacy('A', units, discount, false);
		else if (category.equals("B-pharma-promo"))
			c.addDiscountPharmacy('B', units, discount, true);
		else if (category.equals("A-pharma-promo"))
			c.addDiscountPharmacy('A', units, discount, true);
		else if (category.equals("B-drugstore"))
			c.addDiscountDrugstore('B', units, discount, false);
		else if (category.equals("A-drugstore"))
			c.addDiscountDrugstore('A', units, discount, false);
		else if (category.equals("B-drugstore-promo"))
			c.addDiscountDrugstore('B', units, discount, true);
		else if (category.equals("A-drugstore-promo"))
			c.addDiscountDrugstore('A', units, discount, true);		
		else if (category.equals("C-hospital"))
			c.addDiscountHospital('C', units, discount);
		else if (category.equals("B-hospital"))
			c.addDiscountHospital('B', units, discount);
		else if (category.equals("A-hospital"))
			c.addDiscountHospital('A', units, discount);						
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
}
