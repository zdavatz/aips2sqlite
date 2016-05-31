package com.maxl.java.aips2sqlite;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.opencsv.CSVReader;

public class ShoppingCartDesitin {
	
	// GLN -> (EAN, Rebate)
	Map<String, TreeMap<String, Float>> m_map_conditions = null;
	Map<String, Product> m_map_products = null;
	
	public ShoppingCartDesitin(Map<String, Product> map_products) {
		m_map_products = map_products;
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
	
	public void processConditionFile(String in_dir) {
		// First check if path exists
		File f = new File(in_dir);
		if (!f.exists() || !f.isDirectory()) {
			System.out.println("Directory " + in_dir + " does not exist!");
			return;
		}	
		
		// Initialize conditions map
		m_map_conditions = new TreeMap<>();
		// Read from file
		String file_name = in_dir + "/" + Constants.FILE_ARTICLES_DESITIN;
		try {
			CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(file_name), "Cp1252"));
			List<String[]> my_entries = reader.readAll();
			int num_lines = 0;
			for (String[] s : my_entries) {
				if (s.length>8 && num_lines>0) {
					Product product = new Product();
					String gln = s[1];
					String ean = s[7];
					float rebate = Float.valueOf(s[5]);						
					float exfactory_price = Float.valueOf(s[8]);
					// Fill in product map
					product.eancode = ean;
					product.fap = product.efp = exfactory_price;
					product.vat = 2.5f;	// see ZD email 9/10/2015
					product.author = "Desitin Pharma GmbH";
					m_map_products.put(ean, product);				
					// Fill in rebate map
					TreeMap<String, Float> product_rebate = new TreeMap<String, Float>();
					if (m_map_conditions.containsKey(gln)) {
						product_rebate = m_map_conditions.get(gln);
					}
					product_rebate.put(ean, rebate);
					m_map_conditions.put(gln, product_rebate);
				}
				num_lines++;
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void encryptConditionsToDir(String out_dir, String file_name) {		
		// First serialize into a byte array output stream, then encrypt
		Crypto crypto = new Crypto();
		byte[] encrypted_msg = null;
		if (m_map_conditions!=null && m_map_conditions.size()>0) {
			byte[] serializedBytes = FileOps.serialize(m_map_conditions);
			if (serializedBytes!=null) {
				encrypted_msg = crypto.encrypt(serializedBytes);
			}
			// Write to file
			FileOps.writeToFile(out_dir + file_name + ".ser", encrypted_msg);
			System.out.println("Saved encrypted file " + file_name + ".ser");
		} else {
			System.out.println("!! Error occurred when generating " + file_name + ".ser");
			System.exit(1);
		}
	}
}
