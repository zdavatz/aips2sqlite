package com.maxl.java.aips2sqlite;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

	public ShoppingCart() {
		
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
	
	public void encryptConditionsToDisk(String dir, String filename) {
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
			
			int num_rows = 0;
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				/*
				  1: Präparatname
				  2: EAN code
				  5: FEP inkl. MWSt.
				  6: FAB exkl. MWSt.
				  7: Arztpraxis B
				  8: Artzpraxis A
				  9: assortierbar mit, comma-separated list
				 10: Apotheke B
				 11: Apotheke A
				 12: assortierbar mit, comma-separated list
				 13: Promotionszyklus B
				 14: Promotionszyklus A
				 15: assortierbar mit, comma-separated list
				 16: Spital C
				 17: Spital B
				 18: Spital A
				*/
				if (num_rows>1) {
					if (row.getCell(2)!=null) {
						String eancode = getCellValue(row.getCell(2));
						if (eancode!=null && eancode.length()==16) {
							// EAN code read in as "float"!
                            eancode = eancode.substring(0, eancode.length()-3);
							String name = getCellValue(row.getCell(1)).replaceAll("\\*", "").trim();
							float fep = Float.valueOf(getCellValue(row.getCell(5)));
							float fap = Float.valueOf(getCellValue(row.getCell(6)));	
							// Instantiate new med condition
							Conditions cond = new Conditions(eancode, name, fep, fap);								
							System.out.println(eancode + " -> " + name + " / " + Float.toString(fep) + " / " + Float.toString(fap) + " / ");							

							// Rebates
							try {
								extractDiscounts(cond, "B-doc", getCellValue(row.getCell(7)));	// B-Praxis
								extractDiscounts(cond, "A-doc", getCellValue(row.getCell(8)));	// A-Praxis		
								extractDiscounts(cond, "B-farma", getCellValue(row.getCell(10)));	// B-Apotheke
								extractDiscounts(cond, "A-farma", getCellValue(row.getCell(11)));	// A-Apotheke
								extractDiscounts(cond, "B-promo", getCellValue(row.getCell(13)));	// B-Promo-cycle
								extractDiscounts(cond, "A-promo", getCellValue(row.getCell(14)));	// A-Promo-cycle
								extractDiscounts(cond, "C-hospital", getCellValue(row.getCell(16)));	// C-hospital
								extractDiscounts(cond, "B-hospital", getCellValue(row.getCell(17)));	// B-hospital
								extractDiscounts(cond, "A-hospital", getCellValue(row.getCell(18)));	// A-hospital
								// Assortiebarkeit
								extractAssort(cond, "doc", getCellValue(row.getCell(9)));
								extractAssort(cond, "farma", getCellValue(row.getCell(12)));
								extractAssort(cond, "promo", getCellValue(row.getCell(15)));							
							} catch(Exception e) {
								System.out.println(">> Exception while processing Excel-File " + filename);
								System.out.println(">> Check " + eancode + " -> " +name);
								e.printStackTrace();
								System.exit(-1);
							}
							// Test
							TreeMap<Integer, Float> test = cond.getDiscountPromo('A');
							for (Map.Entry<Integer, Float> entry : test.entrySet()) {
								int unit = entry.getKey();
								float discount = entry.getValue();								
								// System.out.print(unit + " -> " + discount + "  ");
							}
							// System.out.println()
							
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
				byte[] serializedBytes = serialize(map_conditions);
				if (serializedBytes!=null) {
					encrypted_msg = crypto.encrypt(serializedBytes);
					// System.out.println(Arrays.toString(encrypted_msg));
				}
			}
			// Write to file
			writeToFile(Constants.DIR_OUTPUT + filename +".ser", encrypted_msg);
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
	
	private boolean extractDiscounts(Conditions c, String category, String discount_str) {
		boolean discounted = false;
		if (!discount_str.isEmpty()) {
			// Promotion-cycles
			if (category.equals("A-promo") || category.equals("B-promo")) {
				// Extract complex date
				Pattern date_pattern1 = Pattern.compile("\\b(\\d{2}).(\\d{2}).(\\d{4})-(\\d{2})\\b", Pattern.DOTALL);
				Matcher date_match1 = date_pattern1.matcher(discount_str);					
				while (date_match1.find()) {
					int day1 = Integer.parseInt(date_match1.group(1));
					int month1 = Integer.parseInt(date_match1.group(2));
					int year1 = Integer.parseInt(date_match1.group(3));
					int month2 = Integer.parseInt(date_match1.group(4));
					// System.out.println(day1 + "." + month1 + "." + year1 + "->" + month2);
					if (month1<month2) {
						int d1 = (new DateTime(year1, month1, day1, 0, 0, 0)).getDayOfYear();
						int d2 = 0;
						if (month2<12)
							d2 = (new DateTime(year1, month2+1, 1, 0, 0, 0)).getDayOfYear();						
						else // December 31st
							d2 = (new DateTime(year1, 12, 31, 0, 0, 0)).getDayOfYear();
						if (category.equals("A-promo")) {
							for (int m=month1; m<=month2; ++m)
								c.addPromoMonth(m, 'A');
							c.addPromoTime(d1, d2, 'A');
						}
						if (category.equals("B-promo")) {
							for (int m=month1; m<=month2; ++m)
								c.addPromoMonth(m, 'B');
							c.addPromoTime(d1, d2, 'B');
						}				
					}
					discounted = true;
				}
				// Extract simple date
				Pattern date_pattern2 = Pattern.compile("\\b(\\d{2})-(\\d{2})\\b", Pattern.DOTALL);
				Matcher date_match2 = date_pattern2.matcher(discount_str);
				while (date_match2.find()) {
					int month1 = Integer.parseInt(date_match2.group(1));
					int month2 = Integer.parseInt(date_match2.group(2));
					// System.out.println(month1 + "->" + month2);
					if (month1<month2) {
						DateTime curr_dt = new DateTime();
						int curr_year = curr_dt.getYear();
						int d1 = (new DateTime(curr_year, month1, 1, 0, 0, 0)).getDayOfYear();
						int d2 = 0;
						if (month2<12)
							d2 = (new DateTime(curr_year, month2+1, 1, 0, 0, 0)).getDayOfYear();
						else	// Januar 1st
							d2 = (new DateTime(curr_year, 12, 31, 0, 0, 0)).getDayOfYear();
						if (category.equals("A-promo")) {
							for (int m=month1; m<=month2; ++m)
								c.addPromoMonth(m, 'A');
							c.addPromoTime(d1, d2, 'A');
						}
						if (category.equals("B-promo")) {
							for (int m=month1; m<=month2; ++m)
								c.addPromoMonth(m, 'B');
							c.addPromoTime(d1, d2, 'B');
						}				
					}
					discounted = true;
				}
			} else {			
				// Split comma-separated list
				String[] rebates = discount_str.split("\\s*,\\s*");
				// Extract discounts: check for parentheses discounts
				Pattern pattern2 = Pattern.compile("([0-9/]+)\\((.*?)\\)", Pattern.DOTALL);
				Pattern pattern3 = Pattern.compile("\\((.*?)\\)", Pattern.DOTALL);
				// Loop through all elements of the list
				for (int i=0; i<rebates.length; ++i) {	
					Matcher match2 = pattern2.matcher(rebates[i]);
					if (match2.matches()) {
						// Get units by removing parentheses
						String units = rebates[i].replaceAll("\\(.*\\)","");
						// Get discount as content of the parentheses
						Matcher match3 = pattern3.matcher(rebates[i]);
						match3.find();
						String discount = match3.group(1).replaceAll("%", "");
						// Extract all units 
						// Note: discount can also be <0!
						if (units.contains("/")) {
							String single_unit[] = units.split("/");
							for (int j=0; j<single_unit.length; ++j) {
								discounted |= addDiscount(c, category, single_unit[j], discount);
							}
						} else {
							if (units!=null) {								
								int u = Integer.valueOf(units);	
								// Check if number of units is limited to <=100 and its a "loner"
								if (u<=100 && rebates.length==1) {
									// Increment units to 100 in steps of 10								
									for (int k=u; k<=100; k+=10) {
										String single_unit = String.format("%d", k);
										discounted |= addDiscount(c, category, single_unit, discount);
									}
								} else {
									// Do not increment...
									String single_unit = String.format("%d", u);
									discounted |= addDiscount(c, category, single_unit, discount);
								}	
							}
						}
					} else {
						// Found "Muster"! Barrabatt = -100%
						String units = rebates[i];
						// System.out.println("Muster -> " + units);
						discounted |= addDiscount(c, category, units, "-100");
					}
				}
			}
		}
		return discounted;
	}
	
	private boolean addDiscount(Conditions c, String category, String u, String d) {
		boolean discounted = true;
		
		int units = 0;
		float discount = 0.0f;
		if (u!=null)
			units = Integer.valueOf(u);					
		if (d!=null)
			discount = Float.valueOf(d);	
		
		if (category.equals("B-doc"))
			c.addDiscountDoc('B', units, discount);
		else if (category.equals("A-doc"))
			c.addDiscountDoc('A', units, discount);
		else if (category.equals("B-farma"))
			c.addDiscountFarma('B', units, discount);
		else if (category.equals("A-farma"))
			c.addDiscountFarma('A', units, discount);
		else if (category.equals("B-promo"))
			c.addDiscountPromo('B', units, discount);
		else if (category.equals("A-promo"))
			c.addDiscountPromo('A', units, discount);
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
	
	private void extractAssort(Conditions c, String category, String eans_str) {
		if (!eans_str.isEmpty()) {
			String[] eans = eans_str.split("\\s*,\\s*");
			List<String> items = Arrays.asList(eans);
			for (int i=0; i<items.size(); ++i) {
				if (items.get(i).contains("."))
					items.set(i, items.get(i).split("\\.")[0]);
			}
			if (category.equals("doc"))
				c.setAssortDoc(items);
			else if (category.equals("farma"))
				c.setAssortFarma(items);
			else if (category.equals("hospital"))
				c.setAssortPromo(items);
		}
	}
	
	public void encryptGlnsToDisk(String dir, String filename) {
		// First check if path exists
		File f = new File(dir);
		if (!f.exists() || !f.isDirectory()) {
			System.out.println("Directory " + dir + " does not exist!");
			return;
		}
		try {
			Map<String, String> gln_map = new TreeMap<String, String>();
			// Load csv file and dump to map
			FileInputStream glnCodesCsv = new FileInputStream(Constants.DIR_SHOPPING + filename + ".csv");
			BufferedReader br = new BufferedReader(new InputStreamReader(glnCodesCsv, "UTF-8"));
			String line;
			while ((line=br.readLine()) !=null ) {
				// Semicolon is used as a separator
				String[] gln = line.split(";");
				if (gln.length>1) {
					gln_map.put(gln[0], gln[1]);
				}
			}			
			// First serialize into a byte array output stream, then encrypt
			Crypto crypto = new Crypto();
			byte[] encrypted_msg = null;
			if (gln_map.size()>0) {
				byte[] serializedBytes = serialize(gln_map);
				if (serializedBytes!=null) {
					encrypted_msg = crypto.encrypt(serializedBytes);
				}
			}
			// Write to file
			writeToFile(Constants.DIR_OUTPUT + filename +".ser", encrypted_msg);
			System.out.println("Saved encrypted file " + filename +".ser");
			
			br.close();
		} catch(IOException e) {
			e.printStackTrace();			
		}
	}
	
	public void encryptJsonToDisk(String dir, String filename) {
		// First check if path exists
		File f = new File(dir);
		if (!f.exists() || !f.isDirectory()) {
			System.out.println("Directory " + dir + " does not exist!");
			return;
		}
		try {
			File inputFile = new File(Constants.DIR_SHOPPING + filename + ".json");
			FileInputStream inputStream = new FileInputStream(inputFile);
	        byte[] serializedBytes = new byte[(int) inputFile.length()];
	        inputStream.read(serializedBytes);
	        
			Crypto crypto = new Crypto();
			byte[] encrypted_msg = null;
			if (serializedBytes.length>0) {
				encrypted_msg = crypto.encrypt(serializedBytes);
			}
			// Write to file
			writeToFile(Constants.DIR_OUTPUT + filename +".ser", encrypted_msg);
			System.out.println("Saved encrypted file " + filename +".ser");

	        inputStream.close();
		} catch(IOException e) {
			e.printStackTrace();
		} 
	}
	
	
	private byte[] serialize(Object obj) {
		try {
			ByteArrayOutputStream bout = new ByteArrayOutputStream();	// new byte array
			ObjectOutputStream sout = new ObjectOutputStream(bout);		// serialization stream header
			sout.writeObject(obj);							// write object to serialied stream
			return (bout.toByteArray());
		} catch(IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private Object deserialize(byte[] byteArray) {
		try {
			ByteArrayInputStream bin = new ByteArrayInputStream(byteArray);
			ObjectInputStream sin = new ObjectInputStream(bin);
			return sin.readObject();
		} catch(IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private void writeToFile(String path, byte[] buf) {
		try {
			FileOutputStream fos = new FileOutputStream(path);
			fos.write(buf);
			fos.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private byte[] readFromFile(String path) {
		File file = new File(path);
		byte[] buf = new byte[(int)file.length()];
		try {
			DataInputStream dis = new DataInputStream(new FileInputStream(file));
			dis.readFully(buf);
			dis.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
		return buf;
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
