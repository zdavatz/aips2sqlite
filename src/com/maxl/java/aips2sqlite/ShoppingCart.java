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
			
			int num_rows = 0;
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				/*
				  1: Präparatname
				  2: Units
				  3: Gal. form (DE)
				  4: Gal. form (FR)
				  5: EAN code
				  6: Pharma code
				  7: Reg.
				  8: FEP exkl. MWSt.
				  9: FAP exkl. MWSt.
				 10: MWSt.
				 11: visible Arzt, Apotheke
				 12: visible Drogerie
				 13: visible Spital
				 14: visible Grosshandel
				 15: B-Arztpraxis
				 16: A-Artzpraxis
				 17: assortierbar mit, comma-separated list
				 18: B-Apotheke
				 19: A-Apotheke
				 20: assortierbar mit, comma-separated list
				 21: Promotionszyklus B-Apotheke
				 22: Promotionszyklus A-Apotheke
				 23: assortierbar mit, comma-separated list
				 24: B-Drogerie
				 25: A-Drogerie
				 26: assortierbar mit, comma-separated list
				 27: Promotionszyklus, B-Drogerie
				 28: Promotionszyklus, A-Drogerie
				 29: assortierbar mit, comma-separated list				 
				 30: C-Spital
				 31: B-Spital
				 32: A-Spital
				 33: assortierbar mit, comma-separated list
				*/

				if (num_rows>1) {
					if (row.getCell(5)!=null) {
						String eancode = getCellValue(row.getCell(5));
						if (eancode!=null && eancode.length()==16) {
							// EAN code read in as "float"!
                            eancode = eancode.substring(0, eancode.length()-3);
							String name = getCellValue(row.getCell(1)).replaceAll("\\*", "").trim();
							float fep = 0.0f;
							if (!getCellValue(row.getCell(8)).isEmpty())
								fep = Float.valueOf(getCellValue(row.getCell(8)));
							float fap = 0.0f;
							if (!getCellValue(row.getCell(9)).isEmpty())							
								fap = Float.valueOf(getCellValue(row.getCell(9)));	
							float vat = 8.0f;	// [%]
							if (!getCellValue(row.getCell(10)).isEmpty())
								vat = Float.valueOf(getCellValue(row.getCell(10)));
							// 11: doctors, pharmacy; 12: drugstore; 13: hospital; 14: wholesaler
							int visibility = 0x00;	// article visible to no customer group
							if (!getCellValue(row.getCell(11)).toLowerCase().isEmpty())
								if (getCellValue(row.getCell(11)).equals("x"))
									visibility |= 0x08;
							if (!getCellValue(row.getCell(12)).toLowerCase().isEmpty())
								if (getCellValue(row.getCell(12)).equals("x"))
									visibility |= 0x04;
							if (!getCellValue(row.getCell(13)).toLowerCase().isEmpty())
								if (getCellValue(row.getCell(13)).equals("x"))
									visibility |= 0x02;
							if (!getCellValue(row.getCell(14)).isEmpty())
								if (getCellValue(row.getCell(14)).toLowerCase().equals("x"))
									visibility |= 0x01;							
							Product product = new Product();
							product.processed = false;
							product.title = name;
							product.author = "IBSA Institut Biochimique SA";	// Currently only one company
							product.size = getCellValue(row.getCell(2));		// Packungsgrösse
							if (product.size!=null && product.size.endsWith(".00"))
								product.size = product.size.substring(0, product.size.length()-3);
							product.units[0] = getCellValue(row.getCell(3));	// Galenische Form (DE)
							product.units[1] = getCellValue(row.getCell(4));	// Galenische Form (FR)
							if (product.units[0]!=null)
								product.units[0] = product.units[0].trim();
							if (product.units[1]!=null)
								product.units[1] = product.units[1].trim();
							product.eancode = eancode;
							String pharmacode = getCellValue(row.getCell(6));
							if (pharmacode!=null && pharmacode.length()>3)
								product.pharmacode = pharmacode.substring(0, pharmacode.length()-3);
							product.swissmedic_cat = getCellValue(row.getCell(7));
							product.fep = fep;
							product.fap = fap;
							product.vat = vat;
							product.visible = visibility;
							map_products.put(eancode, product);
							
							// Instantiate new med condition
							Conditions cond = new Conditions(eancode, name, fep, fap);	
							System.out.println(eancode + " -> " + name + " " + product.size + ", " + product.units[0]);							

							// Rebates
							try {
								extractDiscounts(cond, "B-doctor", getCellValue(row.getCell(15)));			// B-Arztpraxis
								extractDiscounts(cond, "A-doctor", getCellValue(row.getCell(16)));			// A-Arztpraxis		
								extractDiscounts(cond, "B-pharmacy", getCellValue(row.getCell(18)));		// B-Apotheke
								extractDiscounts(cond, "A-pharmacy", getCellValue(row.getCell(19)));		// A-Apotheke
								extractDiscounts(cond, "B-pharmacy-promo", getCellValue(row.getCell(21)));	// B-Apotheke promo-cycle
								extractDiscounts(cond, "A-pharmacy-promo", getCellValue(row.getCell(22)));	// A-Apotheke promo-cycle
								extractDiscounts(cond, "B-drugstore", getCellValue(row.getCell(24)));		// B-Drogerie
								extractDiscounts(cond, "A-drugstore", getCellValue(row.getCell(25)));		// A-Drogerie
								extractDiscounts(cond, "B-drugstore-promo", getCellValue(row.getCell(27)));	// B-Drogerie promo-cycle
								extractDiscounts(cond, "A-drugstore-promo", getCellValue(row.getCell(28)));	// A-Drogerie promo-cycle
								extractDiscounts(cond, "C-hospital", getCellValue(row.getCell(30)));		// C-Spital
								extractDiscounts(cond, "B-hospital", getCellValue(row.getCell(31)));		// B-Spital
								extractDiscounts(cond, "A-hospital", getCellValue(row.getCell(32)));		// A-Spital
								// Assortiebarkeit
								extractAssort(cond, "doctor", getCellValue(row.getCell(17)));
								extractAssort(cond, "pharmacy", getCellValue(row.getCell(20)));
								extractAssort(cond, "pharmacy-promo", getCellValue(row.getCell(23)));							
								extractAssort(cond, "drugstore", getCellValue(row.getCell(26)));
								extractAssort(cond, "drugstore-promo", getCellValue(row.getCell(29)));
								extractAssort(cond, "hospital", getCellValue(row.getCell(33)));								
							} catch(Exception e) {
								System.out.println(">> Exception while processing Excel-File " + filename);
								System.out.println(">> Check " + eancode + " -> " +name);
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
							if (u<=100 && rebates.length==1) {
								// Increment units to 100 in steps of 10								
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
						if (rebates.length==1) {
							if (Debug)
								System.out.println("# loner to 100 -> " + u);	
							for (int k=u; k<=100; k+=10) {
								String single_unit = String.format("%d", k);
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
	
	private void extractAssort(Conditions c, String category, String eans_str) {
		if (!eans_str.isEmpty()) {
			String[] eans = eans_str.split("\\s*,\\s*");
			List<String> items = Arrays.asList(eans);
			for (int i=0; i<items.size(); ++i) {
				if (items.get(i).contains("."))
					items.set(i, items.get(i).split("\\.")[0]);
			}
			c.setAssort(category, items);
		}
	}
	
	public void encryptCsvToDir(String in_filename_1, String in_filename_2, String in_dir, 
			String out_filename, String out_dir, int skip, int cols) {
		// First check if paths exist
		File f = new File(in_dir);
		if (!f.exists() || !f.isDirectory()) {
			System.out.println("Directory " + in_dir + " does not exist!");
			return;
		}
		// First check if path exists
		f = new File(out_dir);
		if (!f.exists() || !f.isDirectory()) {
			System.out.println("Directory " + out_dir + " does not exist!");
			return;
		}
		try {
			Map<String, String> gln_map = new TreeMap<String, String>();
			// Load csv file and dump to map			
			{
				FileInputStream glnCodesCsv = new FileInputStream(in_dir + "/" + in_filename_1 + ".csv");
				BufferedReader br = new BufferedReader(new InputStreamReader(glnCodesCsv, "UTF-8"));
				String line;
				while ((line=br.readLine()) !=null ) {
					// Semicolon is used as a separator
					String[] gln = line.split(";");
					if (gln.length>(cols-1)) {
						if (cols==2)
							gln_map.put(gln[0], gln[1]);
						else if (cols==3)
							gln_map.put(gln[0], gln[1]+";"+gln[2]);
					}
				}			
				glnCodesCsv.close();				
				br.close();
			}
			// Used when files are merged
			{
				if (!in_filename_2.isEmpty()) {
					FileInputStream glnCodesCsv = new FileInputStream(in_dir + "/" + in_filename_2 + ".csv");
					BufferedReader br = new BufferedReader(new InputStreamReader(glnCodesCsv, "UTF-8"));
					String line;
					while ((line=br.readLine()) !=null ) {
						// Semicolon is used as a separator
						String[] gln = line.split(";");
						if (!gln_map.containsKey(gln[0]) && gln.length>(cols-1)) {
							if (cols==2)
								gln_map.put(gln[0], gln[1]);
							else if (cols==3)
								gln_map.put(gln[0], gln[1]+";"+gln[2]);
						}
					}	
					glnCodesCsv.close();
					br.close();
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
			writeToFile(out_dir + out_filename +".ser", encrypted_msg);
			System.out.println("Saved encrypted file " + out_filename +".ser");
		} catch(IOException e) {
			e.printStackTrace();			
		}
	}
	
	public void encryptFileToDir(String filename, String dir) {
		// First check if path exists
		File f = new File(dir);
		if (!f.exists() || !f.isDirectory()) {
			System.out.println("Directory " + dir + " does not exist!");
			return;
		}
		try {
			File inputFile = new File(dir + "/" + filename);
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
