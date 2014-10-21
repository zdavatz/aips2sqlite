package com.maxl.java.aips2sqlite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

public class ShoppingCart implements java.io.Serializable {

	public ShoppingCart() {
		
	}
	
	private class Conditions implements java.io.Serializable {
		
		String ean_code;
		String name;
		float fep_chf;
		float gross_chf;
		TreeMap<Integer, Float> doctor;		// maps units to discount (%)
		TreeMap<Integer, Float> farmacy;	// maps units to discount (%)
		TreeMap<Integer, Float> promotion;	// maps units to discount (%)
		List<String> doctor_assort;			// maps list of assortable meds
		List<String> farmacy_assort;		// maps list of assortable meds
		List<String> promotion_assort;		// maps list of assortable meds
		List<Integer> promotion_months;
		
		public Conditions(String ean_code, String name, float fep_chf, float gross_chf) {
			this.ean_code = ean_code;
			this.name = name;
			this.fep_chf = fep_chf;
			this.gross_chf = gross_chf;
			doctor = new TreeMap<Integer, Float>();
			farmacy = new TreeMap<Integer, Float>();
			promotion = new TreeMap<Integer, Float>();
			doctor_assort = new ArrayList<String>();
			farmacy_assort = new ArrayList<String>();
			promotion_assort = new ArrayList<String>();
			promotion_months = new ArrayList<Integer>();
		}
		
		public void addDiscountDoc(int units, float discount) {
			doctor.put(units, discount);
		}
		
		public void addDiscountFarma(int units, float discount) {
			farmacy.put(units, discount);
		}
		
		public void addDiscountPromo(int units, float discount) {
			promotion.put(units, discount);
		}
		
		public void setAssortDoc(List<String> assort) {
			doctor_assort = assort;
		}
		
		public void setAssortFarma(List<String> assort) {
			farmacy_assort = assort;
		}
		
		public void setAssortPromo(List<String> assort) {
			promotion_assort = assort;
		}
		
		public void addPromoMonth(int month) {
			promotion_months.add(month);
		}
	}		
	
	public void listFiles(String path) {
		File folder = new File(path);
		File[] list_of_files = folder.listFiles(); 
		 
		for (int i=0; i<list_of_files.length; i++) {
			if (list_of_files[i].isFile()) {
				String file = list_of_files[i].getName();
				if (file.endsWith(".csv") || file.endsWith(".xls")) {
					System.out.println(file);
		        }
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public void readXls(String dir) {
		// First check if path exists
		File f = new File(dir);
		if (!f.exists() || !f.isDirectory()) {
			System.out.println("Directory " + dir + " does not exist!");
			return;
		}
		try {
			// Load ibsa xls file			
			FileInputStream ibsa_file = new FileInputStream(dir+"ibsa_conditions_xls.xls");
			// Get workbook instance for XLS file (HSSF = Horrible SpreadSheet Format)
			HSSFWorkbook ibsa_workbook = new HSSFWorkbook(ibsa_file);
			// Get second sheet from workbook
			HSSFSheet ibsa_sheet = ibsa_workbook.getSheetAt(1);
			// Iterate through all rows
			Iterator<Row> rowIterator = ibsa_sheet.iterator();
			// List of conditions for meds
			List<Conditions> list_conditions = new ArrayList<Conditions>();
			
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
							System.out.println(eancode + " -> " + name + " / " + Float.toString(fep) + " / " + Float.toString(gross) + " / ");
							// Rebates -> comma-separated list
							extractDiscounts(cond, getCellValue(row.getCell(7)));	// A-Praxis
							extractDiscounts(cond, getCellValue(row.getCell(8)));	// B-Praxis
							extractDiscounts(cond, getCellValue(row.getCell(10)));	// A-Apotheke
							extractDiscounts(cond, getCellValue(row.getCell(11)));	// B-Apotheke
							extractDiscounts(cond, getCellValue(row.getCell(13)));	// A-Promo-cycle
							extractDiscounts(cond, getCellValue(row.getCell(14)));	// B-Promo-cycle
							// Assortiebarkeit
							extractEans(cond, getCellValue(row.getCell(9)));
							extractEans(cond, getCellValue(row.getCell(12)));
							extractEans(cond, getCellValue(row.getCell(15)));							
							// Add to list of conditions
							list_conditions.add(cond);		
						}
					}
				}
				num_rows++;
			}
			// First serialize into a byte array output stream, then encrypt
			Crypto crypto = new Crypto();
			byte[] encrypted_msg = null;
			if (list_conditions.size()>0) {
				byte[] serializedBytes = serialize(list_conditions);
				if (serializedBytes!=null) {
					encrypted_msg = crypto.encrypt(serializedBytes);
					// System.out.println(Arrays.toString(encrypted_msg));
				}
			}
			// Write to file
			writeToFile(dir+"ibsa_conditions_msg.msg", encrypted_msg);
			
			// Read from file
			encrypted_msg = readFromFile(dir+"ibsa_conditions_msg.msg");

			// Test: first decrypt, then deserialize
			if (encrypted_msg!=null) {
				byte[] plain_msg = crypto.decrypt(encrypted_msg);
				List<Conditions> lc = new ArrayList<Conditions>();
				lc = (List<Conditions>)deserialize(plain_msg);
				for (int i=0; i<lc.size(); ++i) {
					System.out.println(lc.get(i).ean_code + " -> " + lc.get(i).name);
				}
			}
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
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

	private void extractDiscounts(Conditions c, String discount_str) {
		if (!discount_str.isEmpty()) {
			// Promotion-cycles
			Pattern pattern1 = Pattern.compile("(\\d{2})-(\\d{2})", Pattern.DOTALL);
			Matcher match1 = pattern1.matcher(discount_str);
			while (match1.find()) {
				int month1 = Integer.parseInt(match1.group(1));
				int month2 = Integer.parseInt(match1.group(2));
				System.out.println(month1 + "->" + month2);
				c.addPromoMonth(month1);
				c.addPromoMonth(month2);
			}				
			
			String[] rebates = discount_str.split("\\s*,\\s*");
			// Discounts
			Pattern pattern2 = Pattern.compile("\\((.*?)\\)", Pattern.DOTALL);
			for (int i=0; i<rebates.length; ++i) {
		
				Matcher match2 = pattern2.matcher(rebates[i]);
				while (match2.find()) {
					String discount = match2.group(1).replaceAll("%", "");
					String units = rebates[i].replaceAll("\\(.*\\)","");
					System.out.print(" " + units + "[" + discount + "%] ");
				}
			}
			System.out.println();
		}
	}
	
	private void extractEans(Conditions c, String eans_str) {
		if (!eans_str.isEmpty()) {
			String[] eans = eans_str.split("\\s*,\\s*");
			List<String> items = Arrays.asList(eans);
			System.out.println("Assortierbar mit " + items.size());
			c.setAssortPromo(items);
		}
	}
}
