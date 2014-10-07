package com.maxl.java.aips2sqlite;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Iterator;

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

public class ShoppingCart {

	public ShoppingCart() {
		
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
	
	public void readXls(String path) {
		try {
			// Load ibsa xls file			
			FileInputStream ibsa_file = new FileInputStream(path+"ibsa_conditions_xls.xls");
			// Get workbook instance for XLS file (HSSF = Horrible SpreadSheet Format)
			HSSFWorkbook ibsa_workbook = new HSSFWorkbook(ibsa_file);
			// Get second sheet from workbook
			HSSFSheet ibsa_sheet = ibsa_workbook.getSheetAt(1);
			// Iterate through all rows
			Iterator<Row> rowIterator = ibsa_sheet.iterator();
			
			int num_rows = 0;
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				/*
				  1: Ean code
				  3: Präparatname
				  5: FEP
				  7: Arztpraxis A 
				  8: Arztpraxis B
				  9: assortierbar mit
				 10: Apotheke A
				 11: Apotheke B
				 12: assortierbar mit
				 13: Promotionszyklus A
				 14: Promotionszyklus B
				 15: assortierbar mit				 	
				*/
				if (num_rows>1) {
					if (row.getCell(0)!=null) {
						String eancode = getCellValue(row.getCell(1));
						if (eancode!=null && eancode.length()==16) {
							eancode = eancode.substring(0, eancode.length()-3);
							String name = getCellValue(row.getCell(3));
							String fep = getCellValue(row.getCell(5));
							System.out.println(eancode + " -> " + name + " / " + fep);
						}
					}
				}
				num_rows++;
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
		
	public void encrypt(String message) {
		try {
			// System.out.println("Plaintext: " + message + "\n");
			
			// Generate key
			KeyGenerator keygen = KeyGenerator.getInstance("AES");
			keygen.init(128);	// To use 256 bit keys, download "unlimited "unlimited strength" encryption policy files from Sun
			byte[] key = keygen.generateKey().getEncoded();			
			SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
			
			// Build the initialization vector (randomly)
			SecureRandom random = new SecureRandom();
			byte[] iv = new byte[16];
			random.nextBytes(iv);
			IvParameterSpec ivspec = new IvParameterSpec(iv);
						
	        System.out.println("Key -> " + new String(key, "utf-8"));
            System.out.println("Iv  -> " + new String(iv, "utf-8"));    			
			
			// Init cipher for encrypt mode
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivspec);
	        // Encrypt message
	        byte[] encrypted = cipher.doFinal(message.getBytes());
	        System.out.println("Ciphertext: " + encrypted + " / size = " + encrypted.length);

	        // Reinit cipher for decryption
	        cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivspec);
	        // Decrypt message
	        byte[] decrypted = cipher.doFinal(encrypted);
	        // System.out.println("Plaintext: " + new String(decrypted) + "\n");
			
		} catch (IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException 
				| InvalidKeyException | InvalidAlgorithmParameterException | NoSuchPaddingException 
				| NoSuchAlgorithmException ex) {
            ex.printStackTrace();
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
