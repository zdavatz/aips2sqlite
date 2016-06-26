package com.maxl.java.aips2sqlite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.maxl.java.shared.User;

public class ShoppingCartRose {

	public ShoppingCartRose() {
		//
	}
		
	public void encryptFiles() {
		encryptSalesFiguresToFile(Constants.DIR_ZURROSE + "Abverkaufszahlen.csv", Constants.DIR_OUTPUT + "rose_sales_fig.ser");
		encryptCustomerMapToFile(Constants.DIR_ZURROSE + "Kunden_alle.csv", Constants.DIR_OUTPUT + "rose_conditions.ser");
		encryptAutoGenerikaToFile(Constants.DIR_ZURROSE + "Autogenerika.csv", Constants.DIR_OUTPUT + "rose_autogenerika.ser");
	}

	private void encryptObjectToFile(Object object, String file_name) {
		byte[] serializedBytes = FileOps.serialize(object);

		// Write simply serialized file
		if (serializedBytes!=null) {
			FileOps.writeToFile(file_name + ".clear", serializedBytes);
			System.out.println("Saved serialized file " + file_name);
		}

		Crypto crypto = new Crypto();
		byte[] encrypted_msg = null;
		if (serializedBytes!=null) {
			encrypted_msg = crypto.encrypt(serializedBytes);
		}
		// Write to encrypted file
		if (encrypted_msg!=null) {
			FileOps.writeToFile(file_name, encrypted_msg);
			System.out.println("Saved encrypted file " + file_name);
		}
	}
	
	public void encryptCustomerMapToFile(String in_csv_file, String out_ser_file) {
		HashMap<String, User> user_map = new HashMap<String, User>();
		try {
			File file = new File(in_csv_file);
			if (!file.exists()) {
				System.out.println(in_csv_file + " does not exist! Returning...");
				return;
			}
			FileInputStream fis = new FileInputStream(in_csv_file);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, "cp1252"));
			int counter = 0;
			String line;
			while ((line = br.readLine()) != null) {
				String token[] = line.split(";", -1);
				if (counter>0 && token.length>23) {
					User user = new User();
					user.gln_code = token[3];
					user.name1 = token[16];
					user.street = token[19];
					user.zip = token[21];
					user.city = token[22];
					user.email = token[23];
					LinkedHashMap<String, Float> rebate_map = new LinkedHashMap<String, Float>();
					LinkedHashMap<String, Float> expenses_map = new LinkedHashMap<String, Float>();
									
					for (int i=0; i<5; ++i) {
						String pharma_company = (new ArrayList<>(Utilities.doctorPreferences.keySet())).get(i);
						//
						String rebate = token[4+i].replaceAll("[^\\d.]", "");
						if (!rebate.isEmpty())
							rebate_map.put(pharma_company, Float.valueOf(rebate));
						else
							rebate_map.put(pharma_company, 0.0f);
						String expenses = token[9+i].replaceAll("[^\\d.]", "");
						//							
						if (!expenses.isEmpty())
							expenses_map.put(pharma_company, Float.valueOf(expenses));						
						else
							expenses_map.put(pharma_company, 0.0f);
					}					
							
					// Is the user already in the user_map?
					if (user_map.containsKey(user.gln_code)) {
						user = user_map.get(user.gln_code);
						// Compare maps and always get biggest numbers
						for (Map.Entry<String, Float> r : user.rebate_map.entrySet()) {
							String name = r.getKey();
							if (rebate_map.containsKey(name)) {
								if (rebate_map.get(name)<user.rebate_map.get(name))
									rebate_map.put(name, user.rebate_map.get(name));
							}
						}
						for (Map.Entry<String, Float> e : user.expenses_map.entrySet()) {
							String name = e.getKey();
							if (expenses_map.containsKey(name)) {
								if (expenses_map.get(name)<user.expenses_map.get(name))
									expenses_map.put(name, user.expenses_map.get(name));
							}							
						}
					} 

					// Sort rebate map according to largest rebate (descending order)
					List<Entry<String, Float>> list_of_entries_1 = new ArrayList<Entry<String, Float>>(rebate_map.entrySet());
					Collections.sort(list_of_entries_1, new Comparator<Entry<String, Float>>() {
						@Override
						public int compare(Entry<String, Float> e1, Entry<String, Float> e2) {
							return -Float.valueOf(e1.getValue()).compareTo(e2.getValue());
						}
					});
					rebate_map.clear();
					for (Entry<String, Float> e : list_of_entries_1) {
						rebate_map.put(e.getKey(), e.getValue());
					}
					// Sort expenses map according to largest expense (descending order)
					List<Entry<String, Float>> list_of_entries_2 = new ArrayList<Entry<String, Float>>(expenses_map.entrySet());
					Collections.sort(list_of_entries_2, new Comparator<Entry<String, Float>>() {
						@Override
						public int compare(Entry<String, Float> e1, Entry<String, Float> e2) {
							return -Float.valueOf(e1.getValue()).compareTo(e2.getValue());
						}
					});
					expenses_map.clear();
					for (Entry<String, Float> e : list_of_entries_2) {
						expenses_map.put(e.getKey(), e.getValue());
					}
					
					user.rebate_map = rebate_map;
					user.expenses_map = expenses_map;	
					
					user_map.put(user.gln_code, user);
				}
				counter++;
			}
			br.close();
		} catch (Exception e) {
			System.err.println(">> Error in reading csv file " + in_csv_file);
		}
		
		// Serialize into a byte array output stream, then encrypt
		if (user_map!=null && user_map.size()>0) {
			encryptObjectToFile(user_map, out_ser_file);
		} else {
			System.out.println("!! Error occurred when generating " + out_ser_file);
			System.exit(1);
		}
	}
	
	public void encryptSalesFiguresToFile(String in_csv_file, String out_ser_file) {
		HashMap<String, Float> sales_figures_map = new HashMap<String, Float>();

		try {
			File file = new File(in_csv_file);
			if (!file.exists()) {
				System.out.println(in_csv_file + " does not exist! Returning...");
				return;
			}
			FileInputStream fis = new FileInputStream(in_csv_file);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, "cp1252"));
			int counter = 0;
			String line;
			while ((line = br.readLine()) != null) {
				String token[] = line.split(";", -1);
				if (counter>0 && token.length>2) {
					String pharma_code = token[0];
					float sales_figures = 0;
					if (token[2]!=null) {
						token[2] = token[2].replaceAll("'", "");
						sales_figures = Float.valueOf(token[2]);
					}
					sales_figures_map.put(pharma_code, sales_figures);
				}
				counter++;
			}
			br.close();
		} catch (Exception e) {
			System.err.println(">> Error in reading csv file " + in_csv_file);
		}
		
		// Serialize into a byte array output stream, then encrypt
		if (sales_figures_map!=null && sales_figures_map.size()>0) {
			encryptObjectToFile(sales_figures_map, out_ser_file);
		} else {
			System.out.println("!! Error occurred when generating " + out_ser_file);
			System.exit(1);
		}
	}
	
	public void encryptAutoGenerikaToFile(String in_csv_file, String out_ser_file) {
		ArrayList<String> auto_generika_list = new ArrayList<String>();

		try {
			File file = new File(in_csv_file);
			if (!file.exists()) {
				System.out.println(in_csv_file + " does not exist! Returning...");
				return;
			}
			FileInputStream fis = new FileInputStream(in_csv_file);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, "cp1252"));
			int counter = 0;
			String line;
			while ((line = br.readLine()) != null) {
				String token[] = line.split(";", -1);
				if (counter>0 && token.length>10) {
					String ean_code = token[10];
					if (ean_code!=null)
					auto_generika_list.add(ean_code);
				}
				counter++;
			}
			br.close();
		} catch (Exception e) {
			System.err.println(">> Error in reading csv file " + in_csv_file);
		}
		
		// Serialize into a byte array output stream, then encrypt
		if (auto_generika_list!=null && auto_generika_list.size()>0) {
			encryptObjectToFile(auto_generika_list, out_ser_file);
		} else {
			System.out.println("!! Error occurred when generating " + out_ser_file);
			System.exit(1);
		}		
	}
}
