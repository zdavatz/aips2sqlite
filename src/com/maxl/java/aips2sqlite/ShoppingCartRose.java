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

import com.maxl.java.shared.NotaPosition;
import com.maxl.java.shared.User;

public class ShoppingCartRose {

	public ShoppingCartRose() {
	}

	public void encryptFiles() {
		encryptSalesFiguresToFile(Constants.DIR_ZURROSE + "Abverkaufszahlen.csv", Constants.DIR_OUTPUT + "rose_sales_fig.ser");
		encryptCustomerMapToFile(Constants.DIR_ZURROSE + "Kunden_alle.csv", Constants.DIR_OUTPUT + "rose_conditions.ser", Constants.DIR_OUTPUT + "rose_ids.ser");
		encryptAutoGenerikaToFile(Constants.DIR_ZURROSE + "Autogenerika.csv", Constants.DIR_OUTPUT + "rose_autogenerika.ser");
		encryptDirectSubstToFile(Constants.DIR_ZURROSE + Constants.CSV_FILE_DIRECT_SUBST_ZR, Constants.DIR_OUTPUT + "rose_direct_subst.ser");
		encryptNotaToFile(Constants.DIR_ZURROSE + Constants.CSV_FILE_NOTA_ZR, Constants.DIR_OUTPUT + "rose_nota.ser");
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

	public void encryptCustomerMapToFile(String in_csv_file, String out_ser_file_1, String out_ser_file_2) {
		HashMap<String, User> user_map = new HashMap<>();
		HashMap<String, String> roseid_to_gln_map = new HashMap<>();

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
			// Parsing Kunden_alle.csv
			while ((line = br.readLine()) != null) {
				String token[] = line.split(";", -1);
				if (counter>0 && token.length>29) {
					User user = new User();
					user.gln_code = token[3];
					user.name1 = token[14];
					user.street = token[17];
					user.zip = token[19];
					user.city = token[20];
					user.email = token[21];
					user.top_customer = token[29].toLowerCase().trim().equals("true");

                    String special_rebate = token[28].replaceAll("[^\\d.]", "");
					if (!special_rebate.isEmpty())
						user.special_rebate = Float.valueOf(special_rebate);
                    String revenue = token[30].replaceAll("[^\\d.]", "");
					if (!revenue.isEmpty())
                        user.revenue = Float.valueOf(revenue);

					LinkedHashMap<String, Float> rebate_map = new LinkedHashMap<>();
					LinkedHashMap<String, Float> expenses_map = new LinkedHashMap<>();
					LinkedHashMap<String, Float> dlk_map = new LinkedHashMap<>();

					for (int i=0; i<Utilities.doctorPreferences.size(); ++i) {
						String pharma_company = (new ArrayList<>(Utilities.doctorPreferences.keySet())).get(i);
						// @cybermax 07.01.2017 -> Actavis is OUT!!
						if (!pharma_company.equals("actavis")) {
							// indices 4-5
							String rebate = token[4 + i].replaceAll("[^\\d.]", "");
							if (!rebate.isEmpty())
								rebate_map.put(pharma_company, Float.valueOf(rebate));
							else
								rebate_map.put(pharma_company, 0.0f);
							// indices 6-7
							String expenses = token[8 + i].replaceAll("[^\\d.]", "");
							if (!expenses.isEmpty()) {
								expenses_map.put(pharma_company, Float.valueOf(expenses));
							}
							else
								expenses_map.put(pharma_company, 0.0f);
							// indices 20-21
							String dlk_costs = token[24 + i].replaceAll("[^\\d.]", "");
							if (!dlk_costs.isEmpty())
								dlk_map.put(pharma_company, Float.valueOf(dlk_costs));
							else
								dlk_map.put(pharma_company, 0.0f);
						}
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
						for (Map.Entry<String, Float> e : user.dlk_map.entrySet()) {
							String name = e.getKey();
							if (expenses_map.containsKey(name)) {
								if (expenses_map.get(name)<user.dlk_map.get(name))
									expenses_map.put(name, user.dlk_map.get(name));
							}
						}
					}

					// Sort rebate map according to largest rebate (descending order)
					List<Entry<String, Float>> list_of_entries_1 = new ArrayList<>(rebate_map.entrySet());
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
					List<Entry<String, Float>> list_of_entries_2 = new ArrayList<>(expenses_map.entrySet());
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
					user.dlk_map = dlk_map;

					user_map.put(user.gln_code, user);

					// Rose id
					String rose_id = "";
					if (token[0]!=null && !token[0].isEmpty())
						rose_id = token[0].replaceAll("[^\\d]", "");
					roseid_to_gln_map.put(rose_id, user.gln_code);
				}
				counter++;
			}
			br.close();
		} catch (Exception e) {
			System.err.println(">> Error in processing file " + in_csv_file);
			e.printStackTrace();
		}

		// Serialize into a byte array output stream, then encrypt
		if (user_map.size()>0) {
			encryptObjectToFile(user_map, out_ser_file_1);
		} else {
			System.out.println("!! (1) Error occurred when generating " + out_ser_file_1);
			System.exit(1);
		}
		// Serialize second file
		if (roseid_to_gln_map.size()>0) {
			encryptObjectToFile(roseid_to_gln_map, out_ser_file_2);
		} else {
			System.out.println("!! (2) Error occurred when generating " + out_ser_file_2);
			System.exit(1);
		}
	}

	public void encryptSalesFiguresToFile(String in_csv_file, String out_ser_file) {
		HashMap<String, Float> sales_figures_map = new HashMap<>();

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
                        if (!token[2].isEmpty())
						    sales_figures = Float.valueOf(token[2]);
					}
					sales_figures_map.put(pharma_code, sales_figures);
				}
				counter++;
			}
			br.close();
		} catch (Exception e) {
			System.err.println(">> Error in processing file " + in_csv_file);
			e.printStackTrace();
		}

		// Serialize into a byte array output stream, then encrypt
		if (sales_figures_map.size()>0) {
			encryptObjectToFile(sales_figures_map, out_ser_file);
		} else {
			System.out.println("!! Error occurred when generating " + out_ser_file);
			System.exit(1);
		}
	}

	public void encryptAutoGenerikaToFile(String in_csv_file, String out_ser_file) {
		ArrayList<String> auto_generika_list = new ArrayList<>();

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
					if (ean_code != null)
						auto_generika_list.add(ean_code);
				}
				counter++;
			}
			br.close();
		} catch (Exception e) {
			System.err.println(">> Error in reading csv file " + in_csv_file);
			e.printStackTrace();
		}

		// Serialize into a byte array output stream, then encrypt
		if (auto_generika_list.size()>0) {
			encryptObjectToFile(auto_generika_list, out_ser_file);
		} else {
			System.out.println("!! Error occurred when generating " + out_ser_file);
			System.exit(1);
		}
	}

	public void encryptDirectSubstToFile(String in_csv_file, String out_ser_file) {
		HashMap<String, String> direct_subst_map = new HashMap<>();

		try {
			File file = new File(in_csv_file);
			if (!file.exists()) {
				System.out.println(in_csv_file + " does not exist! Returning...");
				return;
			}
			FileInputStream fis = new FileInputStream(in_csv_file);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, "utf-8"));
			String line;
			int num_rows = 0;
			while ((line = br.readLine())!=null) {
				if (num_rows>0 && line.contains(";")) {
					String token[] = line.split(";", -1);
					if (token.length==2)
						direct_subst_map.put(token[0], token[1]);
				}
				num_rows++;
			}
			br.close();
		} catch (Exception e) {
			System.err.println(">> Error in reading csv file " + in_csv_file);
			e.printStackTrace();
		}

		// Serialize into a byte array output stream, then encrypt
		if (direct_subst_map.size()>0) {
			encryptObjectToFile(direct_subst_map, out_ser_file);
		} else {
			System.out.println("!! Error occurred when generating " + out_ser_file);
			System.exit(1);
		}
	}

	public void encryptNotaToFile(String in_csv_file, String out_ser_file) {
		HashMap<String, List<NotaPosition>> nota_map = new HashMap<>();

		try {
			File file = new File(in_csv_file);
			if (!file.exists()) {
				System.out.println(in_csv_file + " does not exist! Returning...");
				return;
			}
			FileInputStream fis = new FileInputStream(in_csv_file);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, "utf-8"));
			String line;
			int num_rows = 0;
			while ((line = br.readLine())!=null) {
				if (line.contains(";")) {
					String token[] = line.split(";", -1);
                    if (token.length>5) {
						List<NotaPosition> list_of_nota_pos = new ArrayList<>();
						for (int i=1; i<=token.length-5; i+=5) {
							if (!token[i].isEmpty()) {
								NotaPosition nota_pos = new NotaPosition();
								//
								nota_pos.pharma_code = token[i];
								nota_pos.quantity = Integer.parseInt(token[i + 1]);
								nota_pos.status = token[i + 2];
								nota_pos.delivery_date = token[i + 3];
								nota_pos.last_order_date = token[i + 4];
								// Add to list
								list_of_nota_pos.add(nota_pos);
							}
						}
                        // Removes special characters -- assuming that token[0] is composed of numbers only
					    token[0] = token[0].replaceAll("[^0-9]", "");
						nota_map.put(token[0], list_of_nota_pos);
					} else if (token.length>4) {
						// Ensure backwards compatibility
                    	List<NotaPosition> list_of_nota_pos = new ArrayList<>();
						for (int i=1; i<=token.length-4; i+=4) {
							if (!token[i].isEmpty()) {
								NotaPosition nota_pos = new NotaPosition();
								//
								nota_pos.pharma_code = token[i];
								nota_pos.quantity = Integer.parseInt(token[i + 1]);
								nota_pos.status = token[i + 2];
								nota_pos.delivery_date = token[i + 3];
								// Add to list
								list_of_nota_pos.add(nota_pos);
							}
						}
						// Removes special characters -- assuming that token[0] is composed of numbers only
						token[0] = token[0].replaceAll("[^0-9]", "");
						nota_map.put(token[0], list_of_nota_pos);
					}
				}
				num_rows++;
			}
			br.close();
		} catch (Exception e) {
			System.err.println(">> Error in reading csv file " + in_csv_file);
			e.printStackTrace();
		}

        /*
		nota_map.forEach((k,v)-> {
            if (k.equals("100046")) {
                System.out.println(k);
                for (NotaPosition notaPosition : v) {
                    System.out.println(" -- " + notaPosition.pharma_code);
                }
            }
        });
        */
		// Serialize into a byte array output stream, then encrypt
		if (nota_map.size()>0) {
			encryptObjectToFile(nota_map, out_ser_file);
		} else {
			System.out.println("!! Error occurred when generating " + out_ser_file);
			System.exit(1);
		}
	}
}
