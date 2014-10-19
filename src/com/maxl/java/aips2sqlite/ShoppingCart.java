package com.maxl.java.aips2sqlite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

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
		
		public Conditions(String ean_code, String name, float fep_chf, float gross_chf) {
			this.ean_code = ean_code;
			this.name = name;
			this.fep_chf = fep_chf;
			this.gross_chf = gross_chf;
			doctor = new TreeMap<Integer, Float>();
			farmacy = new TreeMap<Integer, Float>();
			promotion = new TreeMap<Integer, Float>();
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
	public void readXls(String path) {
		// First check if path exists
		File f = new File(path);
		if (!f.exists() || !f.isDirectory()) {
			System.out.println("Directory " + path + " does not exist!");
			return;
		}
		try {
			// Load ibsa xls file			
			FileInputStream ibsa_file = new FileInputStream(path+"ibsa_conditions_xls.xls");
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
							// Add to list of conditions
							list_conditions.add(cond);
							System.out.println(eancode + " -> " + name + " / " + Float.toString(fep) + " / " + Float.toString(gross));				
						}
					}
				}
				num_rows++;
			}
			// First serialize into a byte array output stream, then encrypt
			Crypto crypto = new Crypto();
			byte[] encrypted_msg = null;
			if (list_conditions.size()>0) {
				ByteArrayOutputStream bout = new ByteArrayOutputStream();	// new byte array
				ObjectOutputStream sout = new ObjectOutputStream(bout);		// serialization stream header
				sout.writeObject(list_conditions);							// write object to serialied stream
				byte[] serializedBytes = bout.toByteArray();
				encrypted_msg = crypto.encrypt(serializedBytes);
				System.out.println(Arrays.toString(encrypted_msg));
			}
			// Test: first decrypt, then deserialize
			byte[] plain_msg = crypto.decrypt(encrypted_msg);
			ByteArrayInputStream bin = new ByteArrayInputStream(plain_msg);
			ObjectInputStream sin = new ObjectInputStream(bin);
			try {
				List<Conditions> lc = new ArrayList<Conditions>();
				lc = (List<Conditions>)sin.readObject();
				System.out.println(lc.get(0).ean_code);
			} catch(Exception e) {
				e.printStackTrace();
			}
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	private String getCellValue(Cell part) {
		if (part!=null) {
		    switch (part.getCellType()) {
		        case Cell.CELL_TYPE_BOOLEAN: return part.getBooleanCellValue() + "";
		        case Cell.CELL_TYPE_NUMERIC: return String.format("%.2f", part.getNumericCellValue());
		        case Cell.CELL_TYPE_STRING:	return part.getStringCellValue() + "";
		        case Cell.CELL_TYPE_BLANK: return "BLANK";
		        case Cell.CELL_TYPE_ERROR: return "ERROR";
		        case Cell.CELL_TYPE_FORMULA: return "FORMULA";
		    }
		}
		return "";
	}

}
