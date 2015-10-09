package com.maxl.java.aips2sqlite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import com.maxl.java.shared.User;
import com.opencsv.CSVReader;

public class GlnCodes implements java.io.Serializable {
	
	XSSFSheet m_gln_codes_people_sheet = null;
	XSSFSheet m_gln_codes_companies_sheet = null;
	//
	Map<String, String> m_gln_codes_moos_cond = null;
	Map<String, String> m_gln_codes_moos_targ = null;
	Map<String, String> m_gln_codes_moos_full = null;
	Map<String, String> m_gln_codes_desitin_full = null;
	//
	Map<String, User> m_medreg_addresses = null;
	Map<String, User> m_ibsa_addresses = null;	// GLN+TYPE -> Information
	Map<String, User> m_desitin_addresses = null;
	//
	Map<String, String> m_debitor_to_gln = new HashMap<String, String>();
	
	public GlnCodes() {
		// Load medreg files
		m_gln_codes_people_sheet = ExcelOps.getSheetsFromFile(Constants.FILE_GLN_CODES_PEOPLE, 0);
		m_gln_codes_companies_sheet = ExcelOps.getSheetsFromFile(Constants.FILE_GLN_CODES_COMPANIES, 0);
		// Mosberger conditions
		m_gln_codes_moos_cond = readFromSimpleCsvToMap(Constants.DIR_IBSA + Constants.FILE_CUST_IBSA);
		m_gln_codes_moos_targ = readFromSimpleCsvToMap(Constants.DIR_IBSA + Constants.FILE_TARG_IBSA);
		// Mosberger full info without conditions
		m_gln_codes_moos_full = readIbsaAddressCsvToMap(Constants.DIR_IBSA + Constants.FILE_MOOS_ADDR, 16);
		// Desitin full info without conditions
		m_gln_codes_desitin_full = readDesitinAddressCsvToMap(Constants.DIR_DESITIN + Constants.FILE_CUST_DESITIN);
		// Complete list of gln_codes
		m_medreg_addresses = new HashMap<String, User>();
		m_ibsa_addresses = new HashMap<String, User>();
		m_desitin_addresses = new HashMap<String, User>();
	}
	
	public void generateCsvFile() {	
		// Process both medreg address files
		processMedRegFiles();

		{
			// Initialize desitin addresses with medreg addresses
			for (Map.Entry<String, User> entry : m_medreg_addresses.entrySet()) {
				m_desitin_addresses.put(entry.getKey(), entry.getValue());
			}
			// Loop through the desitin address file (augment medreg info)
			for (Map.Entry<String, String> entry : m_gln_codes_desitin_full.entrySet()) {
				processDesitinFull(entry.getValue());
			}		
			if (CmlOptions.SHOW_LOGS)
				System.out.println("- Processed desitin main address file... (" + m_desitin_addresses.size() + ")");			
		}
		// Write ibsa address file to file
		writeMapToCsv(m_desitin_addresses, '|', "desitin_gln_codes_csv.csv", "", 'd');					
		// Encrypt and serialize
		encryptMapToDir(m_desitin_addresses, "desitin_gln_codes.ser");
		// ... and zip it
		FileOps.zipToFile(Constants.DIR_OUTPUT, "desitin_gln_codes.ser");		
		
		{
			// Loop through the ibsa condition files (augment medreg info)
			for (Map.Entry<String, String> entry : m_gln_codes_moos_cond.entrySet()) {
				preProcessWithMedreg(entry.getKey(), entry.getValue(), "i");
			}
			if (CmlOptions.SHOW_LOGS)
				System.out.println("- Processed gln codes mosberger conditions file... (" + m_ibsa_addresses.size() + ")");		
	
			// Loop through the ibsa targeting file (augment medreg info)
			for (Map.Entry<String, String> entry : m_gln_codes_moos_targ.entrySet()) {
				preProcessWithMedreg(entry.getKey(), entry.getValue(), "i");
			}	
			if (CmlOptions.SHOW_LOGS)
				System.out.println("- Processed gln codes mosberger targetting file... (" + m_ibsa_addresses.size() + ")");			
					
			// Loop through the moosberger full info without conditions
			int no_gln_cnt = 0;
			for (Map.Entry<String, String> entry : m_gln_codes_moos_full.entrySet()) {
				if (processMoosFull(entry.getValue()))
					no_gln_cnt++;
				/*
				if (entry.getKey().startsWith("0125936"))
					System.out.println(entry.getValue());
				*/
				/*
				 * String gln = entry.getKey();
				 * processMoosFull(gln, entry.getValue());
				 */
			}	
			if (CmlOptions.SHOW_LOGS)
				System.out.println("- Processed gln codes mosberger address file... (" + m_ibsa_addresses.size() + ") - " + no_gln_cnt + " entries have no gln code.");		
		}
		// Write ibsa address file to file
		writeMapToCsv(m_ibsa_addresses, '|', "ibsa_gln_codes_csv.csv", "", 'i');					
		// Encrypt and serialize
		encryptMapToDir(m_ibsa_addresses, "gln_codes.ser");
		// ... and zip it
		FileOps.zipToFile(Constants.DIR_OUTPUT, "gln_codes.ser");
	}

	private void processMedRegFiles() {
		// Process MEDREG people's file - these are PUBLIC data
		Iterator<Row> rowIterator = m_gln_codes_people_sheet.iterator();
		int num_rows = 0;		
		while (rowIterator.hasNext()) {
			Row row = rowIterator.next();
			if (num_rows>0) {
				User cust = new User();
				
				if (row.getCell(0)!=null) 
					cust.gln_code = row.getCell(0).getStringCellValue();
				if (row.getCell(1)!=null)
					cust.last_name = row.getCell(1).getStringCellValue();
				if (row.getCell(2)!=null) 
					cust.first_name = row.getCell(2).getStringCellValue();
				if (row.getCell(3)!=null)
					cust.zip = row.getCell(3).getStringCellValue();
				if (row.getCell(4)!=null) 
					cust.city = row.getCell(4).getStringCellValue();
				if (row.getCell(7)!=null) 
					cust.category = row.getCell(7).getStringCellValue();
				if (row.getCell(8)!=null)
					cust.selbst_disp = row.getCell(8).getStringCellValue().equals("Ja");
				if (row.getCell(9)!=null) 
					cust.bet_mittel = row.getCell(9).getStringCellValue().equals("Ja");
		
				if (cust.category.matches(".*[AÄaä]rzt.*"))
					cust.category = "Arzt";		
		
				cust.addr_type = "S";	// S: shipping = default by medreg addresses
				cust.is_human = true;
				
				if (!cust.gln_code.isEmpty()) {
					String key = cust.gln_code + cust.addr_type;
					m_medreg_addresses.put(key, cust);
				}
			}
			num_rows++;
		}
		if (CmlOptions.SHOW_LOGS)
			System.out.println("- Processed gln codes people... (" + m_medreg_addresses.size() + ")");		
		
		// Process MEDREG companies' file - these are PUBLIC data
		rowIterator = m_gln_codes_companies_sheet.iterator();
		num_rows = 0;
		while (rowIterator.hasNext()) {
			Row row = rowIterator.next();
			if (num_rows>0) {
				User cust = new User();
				
				if (row.getCell(0)!=null) 
					cust.gln_code = row.getCell(0).getStringCellValue();
				if (row.getCell(1)!=null)
					cust.name1 = row.getCell(1).getStringCellValue();
				if (row.getCell(2)!=null) 
					cust.name2 = row.getCell(2).getStringCellValue();
				if (row.getCell(4)!=null)
					cust.number = row.getCell(4).getStringCellValue().trim();
				if (row.getCell(3)!=null)
					cust.street = row.getCell(3).getStringCellValue().trim() + " " + cust.number;	// = street + number
				if (row.getCell(5)!=null)
					cust.zip = row.getCell(5).getStringCellValue();
				if (row.getCell(6)!=null) 
					cust.city = row.getCell(6).getStringCellValue();
				
				if (row.getCell(9)!=null) 
					cust.category = row.getCell(9).getStringCellValue();
				if (cust.category.matches(".*Apotheke.*"))
					cust.category = "Apotheke";		
				else if (cust.category.matches(".*[Ss]pital.*"))
					cust.category = "Spital";
				else if (cust.category.matches(".*[Ww]issenschaft.*"))
					cust.category = "Wissenschaft";
				else if (cust.category.matches(".*[Bb]ehörde.*"))
					cust.category = "Behörde";
				
				cust.addr_type = "S";
				cust.is_human = false;
				
				if (!cust.gln_code.isEmpty()) {
					String key = cust.gln_code + cust.addr_type;
					m_medreg_addresses.put(key, cust);
				}
			}
			num_rows++;
		}
		if (CmlOptions.SHOW_LOGS)
			System.out.println("- Processed gln codes companies... (" + m_medreg_addresses.size() + ")");		
	}
	
	private void preProcessWithMedreg(String gln, String cat, String owner) {
		String key = gln + "S";

		if (m_medreg_addresses.containsKey(key)) {
			// This GLN exists already in the MEDREG database
			String t[] = cat.split(";");
			User cust = m_medreg_addresses.get(key);
			if (t[1].matches(".*[Dd]rogerie.*")) {
				// Replace type string with "Drogerie"
				cust.category = "Drogerie";
				System.out.println("-> Exists in med reg file: " + gln + " -> " + cust.category);				
			} else if (t[1].matches(".*[Gg]rossist.*")) {
				// Replace type string with "Grosist"
				cust.category = "Grossist";
				System.out.println("-> Exists in med reg file: " + gln + " -> " + cust.category);				
			}
			if (t.length>2)
				cust.email = t[2];
			cust.addr_type = "S";
			cust.owner = owner;
			//
			if (owner.equals("i"))
				m_ibsa_addresses.put(key, cust);
		} else {	
			// Create new entry by getting INFORMATION from address file (non-MEDREG)
			if (gln.length()==13) {
				String t[] = cat.split(";");			
				User cust = new User();
				String type = "";
				if (t.length>1) {
					if (t[1].matches(".*[AÄaä]rzt.*"))
						type = "Arzt";	
					else if (t[1].matches(".*[Pp]hysiotherap.*"))
						type = "Physio";
					else if (t[1].matches(".*[Aa]potheke.*"))
						type = "Apotheke";		
					else if (t[1].matches(".*[Ss]pital.*"))
						type = "Spital";
					else if (t[1].matches(".*[Dd]rogerie.*"))
						type = "Drogerie";
					else if (t[1].matches(".*[Gg]rossist.*"))
						type = "Grossist";
				}
				if (t.length>2) {
					cust.email = t[2];
				}
				cust.gln_code = gln;
				cust.addr_type = "S";				
				cust.owner = owner;				
				cust.category = type;
				//
				if (owner.equals("i"))
					m_ibsa_addresses.put(key, cust);
			} else {
				System.out.println("Found wrong GLN code: " + gln);
			}
		}
	}
	
	/**
	 * Completes address information according to mosberger's master file
	 * @param value
	 * @return
	 */
	private boolean processMoosFull(String value) {	
		boolean no_gln_flag = false;
		String[] token = value.split(";", -1);
		String extended_gln_code = token[1] + token[0];
		if (m_ibsa_addresses.containsKey(extended_gln_code)) {
			User cust = m_ibsa_addresses.get(extended_gln_code);
			// Check if this is an IBSA customer, if yes, complete information...						
			if (!cust.owner.isEmpty() && cust.owner.charAt(0)=='i') {
				// Address type and gln code "should" already be correct...
				if (cust.ideale_id.isEmpty())
					cust.ideale_id = token[2];
				if (cust.xpris_id.isEmpty())
					cust.xpris_id = token[3];
				if (cust.title.isEmpty())
					cust.title = token[4];
				if (cust.first_name.isEmpty())
					cust.first_name = token[5];
				if (cust.last_name.isEmpty())
					cust.last_name = token[6];
				if (cust.name1.isEmpty())
					cust.name1 = token[7];
				if (cust.name2.isEmpty())			
					cust.name2 = token[8];
				if (cust.name3.isEmpty())
					cust.name3 = token[9];
				// Street is special - because Mosberger and medreg do not conform
				if (cust.street.isEmpty())
					cust.street = token[10];	// = street + number
				if (cust.zip.isEmpty())
					cust.zip = token[11];
				if (cust.city.isEmpty())
					cust.city = token[12];
				if (cust.phone.isEmpty())
					cust.phone = token[13];
				if (cust.fax.isEmpty())
					cust.fax = token[14];
				if (cust.email.isEmpty())
					cust.email = token[15];
				cust.owner = "";				
				m_ibsa_addresses.put(extended_gln_code, cust);
			}
		} else {	// Create new entry		
			if (extended_gln_code.length()==14) {				
				User cust = new User();
				cust.addr_type = token[0];
				cust.gln_code = token[1];				
				cust.ideale_id = token[2];
				cust.xpris_id = token[3];
				cust.title = token[4];
				cust.first_name = token[5];
				cust.last_name = token[6];
				cust.name1 = token[7];
				cust.name2 = token[8];
				cust.name3 = token[9];
				cust.street = token[10];	// = street + number
				cust.zip = token[11];
				cust.city = token[12];
				cust.phone = token[13];
				cust.fax = token[14];
				cust.email = token[15];
				cust.owner = "i";
				m_ibsa_addresses.put(extended_gln_code, cust);
			} else {
				no_gln_flag = true;
				// System.out.println("No gln code: " + extended_gln_code + " -> " + token[3]);
			}
		}
		return no_gln_flag;
	}
	
	private void processDesitinFull(String value) {
		User cust = null;

		String[] token = value.split(";", -1);
		if (token[2].isEmpty())
			token[2] = m_debitor_to_gln.get(token[1]);
		if (token[2]!=null && token[2].length()==13) {
			String extended_gln_code = token[2] + token[0];	// Gln code + {S,B,O}					
			// 1. pass: use information from medreg
			if (m_medreg_addresses.containsKey(extended_gln_code)) {
				cust = m_medreg_addresses.get(extended_gln_code);
				cust.addr_type = token[0];
				cust.owner = "d";
				m_desitin_addresses.put(extended_gln_code, cust);
			}
			// 2. pass: use information from desitin file
			if (m_desitin_addresses.containsKey(extended_gln_code))
				cust = m_desitin_addresses.get(extended_gln_code);	
			else
				cust = new User();
			
			cust.addr_type = token[0];
			cust.gln_code = token[2];				
			cust.name1 = token[7];			
			cust.name3 = token[5];	
			cust.street = token[8];		// = street + number!		
			cust.country = token[10];
			cust.zip = token[11];
			cust.city = token[12];
			cust.phone = token[13];
			cust.email = token[14];
			String type = token[24];
			if (type.equals("10"))
				cust.category = "Spital";
			else if (type.equals("20"))
				cust.category = "Arzt";
			else if (type.equals("30"))
				cust.category = "Apotheke";
			else if (type.equals("40"))
				cust.category = "Grossist";
			cust.owner = "d";			// d = Desitin

			m_desitin_addresses.put(extended_gln_code, cust);
		}
	}
	
	private Map<String, String> readFromSimpleCsvToMap(String file_name) {
		Map<String, String> map = new HashMap<String, String>();
		try {
			File file = new File(file_name);
			if (!file.exists()) 
				return null;
			FileInputStream fis = new FileInputStream(file_name);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, "Cp1252"));
			String line;
			while ((line = br.readLine()) != null) {
				String token[] = line.split(";");
				if (token.length>2) {
					if (map.containsKey(token[0]))
						System.out.println("GLN code exists already!");
					// token[1]: class (A/B), token[2]: type, token[3]: email address
					if (token.length>3)
						map.put(token[0], token[1] + ";" + token[2] + ";" + token[3]);
					else if (token.length==3)
						map.put(token[0], token[1] + ";" + token[2]);
				}
			}
			br.close();
		} catch (Exception e) {
			System.err.println(">> Error in reading csv file");
		}
		
		return map;
	}	
	
	private Map<String, String> readIbsaAddressCsvToMap(String file_name, int num_entries) {
		Map<String, String> map = new HashMap<String, String>();
		try {
			File file = new File(file_name);
			if (!file.exists()) 
				return null;
			FileInputStream fis = new FileInputStream(file_name);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, "Cp1252"));
			String line;
			int num_rows = 0;
			while ((line = br.readLine()) != null) {
				if (num_rows>0) {
					String token[] = line.split(";",-1);
					if (token.length>=num_entries) {
						// token[0] -> {S,B,O}
						// token[1] -> GLN Code
						// token[2] -> Ideale ID
						// token[3] -> XPris ID
						if (!token[0].isEmpty() && !token[3].isEmpty()/*!token[1].isEmpty()*/) {
							String key = /*token[1]*/ token[3] + token[0];	// gln + addr_type (S,B,O)
							if (map.containsKey(key))
								System.out.println("GLN code exists already! This is unlikely...");
							// Fill in map
							// String value_str = token[0] + ";" + token[1] + ";";
							String value_str = "";
							for (int i=0; i<num_entries-1; ++i)
								value_str += token[i] + ";";
							value_str += token[num_entries-1];
							map.put(key, value_str);
						}
					}
				}
				num_rows++;
			}
			br.close();
		} catch (Exception e) {
			System.err.println(">> Error in reading csv file");
		}
		
		return map;
	}
	
	private Map<String, String> readDesitinAddressCsvToMap(String file_name) {
		Map<String, String> map = new HashMap<String, String>();
		try {
			File file = new File(file_name);
			if (!file.exists()) 
				return null;
			CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(file_name)));	// Encoding: "Cp1252" 
			List<String[]> my_entries = reader.readAll();
			int num_lines = 0;
			for (String[] token : my_entries) {
				if (num_lines>0) {
					// Create debitor ID to gln code map
					if (!token[2].isEmpty())
						m_debitor_to_gln.put(token[1], token[2]);
					// Use abbreviation
					if (token[0].contains("Ship"))
						token[0] = "S";
					else if (token[0].contains("Bill"))
						token[0] = "B";		// this category has no gln code!!
					// 
					String extended_key = token[1] + token[0];	// debitor + addr_type (S,B,O)
					if (map.containsKey(extended_key))
						System.out.println("GLN code exists already! This is unlikely...");
					// Fill in map
					String value_str = "";
					for (int i=0; i<token.length-1; ++i) {
						value_str += token[i] + ";";
					}
					value_str += token[token.length-1];
					map.put(extended_key, value_str);
				}
				num_lines++;
			}
			reader.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
		return map;
	}
	
	private void encryptMapToDir(Map<String, User> map, String file_name) {
		// First serialize into a byte array output stream, then encrypt
		Crypto crypto = new Crypto();
		byte[] encrypted_msg = null;
		if (map.size()>0) {
			byte[] serializedBytes = FileOps.serialize(map);
			if (serializedBytes!=null) {
				encrypted_msg = crypto.encrypt(serializedBytes);
			}
		}
		// Write to file
		FileOps.writeToFile(Constants.DIR_OUTPUT + file_name, encrypted_msg);
		System.out.println("Saved encrypted file " + file_name);
	}
	
	private int writeMapToCsv(Map<String, User> map, char separator, String file_name, String add_str, char owner) {
		String csv_file = add_str;
		int num_rows = 0;
		System.out.print("- Generating GLN codes csv file...");
		List<User> customer_list = new ArrayList<User>(map.values());
		if (owner=='i') {
			for (User c : customer_list) {					
				csv_file += c.gln_code + separator 
						+ c.ideale_id + separator
						+ c.xpris_id + separator
						+ c.addr_type + separator
						+ c.category + separator
						+ c.title + separator
						+ c.first_name + separator
						+ c.last_name + separator
						+ c.name1 + separator
						+ c.name2 + separator
						+ c.name3 + separator
						+ c.street + separator
						+ c.zip + separator
						+ c.city + separator
						+ c.phone + separator
						+ c.fax + separator
						+ c.email + separator
						+ c.selbst_disp + separator
						+ c.bet_mittel + "\n";
				num_rows++;
				System.out.print("\r- Generating ibsa address csv file... " + num_rows + "   ");
			}
		} else if (owner=='d') {
			for (User c : customer_list) {	
				csv_file += c.gln_code + separator 
						+ c.addr_type + separator
						+ c.category + separator
						+ c.title + separator
						+ c.first_name + separator
						+ c.last_name + separator
						+ c.name1 + separator
						+ c.name2 + separator
						+ c.name3 + separator
						+ c.street + separator
						+ c.zip + separator
						+ c.city + separator
						+ c.phone + separator
						+ c.fax + separator
						+ c.email + separator
						+ c.selbst_disp + separator
						+ c.bet_mittel + "\n";
				num_rows++;
				System.out.print("\r- Generating desitin address csv file... " + num_rows + "   ");
			}
		}
		System.out.println();
		FileOps.writeToFile(csv_file, Constants.DIR_OUTPUT, file_name);
		return num_rows;
	}
	
	private void writeUnmatchedGLNcodesToFile(Map<String, String> map, String file_name) {
		String csv_file = "";
		for (Map.Entry<String, String> entry : map.entrySet()) {
			String token[] = entry.getValue().split(";");
			if (token.length<2)
				csv_file += entry.getKey() + ";" + entry.getValue() + "\n";
		}
		FileOps.writeToFile(csv_file, Constants.DIR_OUTPUT, file_name);
	}		
}
