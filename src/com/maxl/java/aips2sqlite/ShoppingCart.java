package com.maxl.java.aips2sqlite;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
			// Get second sheet from workbook
			HSSFSheet ibsa_sheet = ibsa_workbook.getSheetAt(1);
			// Iterate through all rows
			Iterator<Row> rowIterator = ibsa_sheet.iterator();
			// Map of ean code to rebate condition
			Map<String, Conditions> map_conditions = new TreeMap<String, Conditions>();
			
			int num_rows = 0;
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				/*
				  1: Ean code
				  3: Präparatname
				  5: FEP
				  6: Gross
				  7: Arztpraxis A, units(%) 
				  8: Arztpraxis B, units(%)
				  9: assortierbar mit, comma-separated list
				 10: Apotheke A, units(%)
				 11: Apotheke B, units(%)
				 12: assortierbar mit, comma-separated list
				 13: Promotionszyklus A, units(%)
				 14: Promotionszyklus B, units(%)
				 15: assortierbar mit, comma-separated list	
				*/
				if (num_rows>1) {
					if (row.getCell(0)!=null) {
						String eancode = getCellValue(row.getCell(1));
						if (eancode!=null && eancode.length()==16) {
							eancode = eancode.substring(0, eancode.length()-3);
							String name = getCellValue(row.getCell(3)).replaceAll("\\*", "").trim();
							float fep = Float.valueOf(getCellValue(row.getCell(5)));
							float gross = Float.valueOf(getCellValue(row.getCell(6)));	
							// Instantiate new med condition
							Conditions cond = new Conditions(eancode, name, fep, gross);								
							// System.out.println(eancode + " -> " + name + " / " + Float.toString(fep) + " / " + Float.toString(gross) + " / ");
							// Rebates -> comma-separated list
							boolean disc = false;
							disc |= extractDiscounts(cond, "A-doc", getCellValue(row.getCell(7)));	// A-Praxis
							disc |= extractDiscounts(cond, "B-doc", getCellValue(row.getCell(8)));	// B-Praxis
							disc |= extractDiscounts(cond, "A-farma", getCellValue(row.getCell(10)));	// A-Apotheke
							disc |= extractDiscounts(cond, "B-farma", getCellValue(row.getCell(11)));	// B-Apotheke
							disc |= extractDiscounts(cond, "A-promo", getCellValue(row.getCell(13)));	// A-Promo-cycle
							disc |= extractDiscounts(cond, "B-promo", getCellValue(row.getCell(14)));	// B-Promo-cycle
							// Assortiebarkeit
							extractEans(cond, getCellValue(row.getCell(9)));
							extractEans(cond, getCellValue(row.getCell(12)));
							extractEans(cond, getCellValue(row.getCell(15)));							
							// Add to list of conditions
							// if (disc==true)
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

	private boolean extractDiscounts(Conditions c, String category, String discount_str) {
		boolean discounted = false;
		if (!discount_str.isEmpty()) {
			// Promotion-cycles
			Pattern pattern1 = Pattern.compile("(\\d{2})-(\\d{2})", Pattern.DOTALL);
			Matcher match1 = pattern1.matcher(discount_str);
			while (match1.find()) {
				int month1 = Integer.parseInt(match1.group(1));
				int month2 = Integer.parseInt(match1.group(2));
				// System.out.println(month1 + "->" + month2);
				c.addPromoMonth(month1);
				c.addPromoMonth(month2);
				discounted = true;
			}				
			
			String[] rebates = discount_str.split("\\s*,\\s*");
			// Discounts
			Pattern pattern2 = Pattern.compile("\\((.*?)\\)", Pattern.DOTALL);
			for (int i=0; i<rebates.length; ++i) {	
				Matcher match2 = pattern2.matcher(rebates[i]);
				while (match2.find()) {
					String u = rebates[i].replaceAll("\\(.*\\)","");
					String d = match2.group(1).replaceAll("%", "");
					if (!d.contains("-")) {		// makes sure it's not a promotion cycle!
						int units = 0;
						float discount = 0.0f;
						if (u!=null)
							units = Integer.valueOf(u);					
						if (d!=null)
							discount = Float.valueOf(d);						
						// System.out.println(units + " -> [" + discount + "]");						
						discounted = true;
						if (category.equals("A-doc"))
							c.addDiscountDoc('A', units, discount);
						else if (category.equals("B-doc"))
							c.addDiscountDoc('B', units, discount);
						else if (category.equals("A-farma"))
							c.addDiscountFarma('A', units, discount);
						else if (category.equals("B-farma"))
							c.addDiscountFarma('B', units, discount);
						else if (category.equals("B-promo"))
							c.addDiscountPromo('A', units, discount);
						else if (category.equals("B-promo"))
							c.addDiscountPromo('B', units, discount);
						else
							discounted = false;
					}
				}
			}
		}
		return discounted;
	}
	
	private void extractEans(Conditions c, String eans_str) {
		if (!eans_str.isEmpty()) {
			String[] eans = eans_str.split("\\s*,\\s*");
			List<String> items = Arrays.asList(eans);
			// System.out.println("Assortierbar mit " + items.size());
			c.setAssortPromo(items);
		}
	}
}
