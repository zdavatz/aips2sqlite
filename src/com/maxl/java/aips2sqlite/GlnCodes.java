package com.maxl.java.aips2sqlite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.maxl.java.shared.User;

public class GlnCodes implements java.io.Serializable {
	
	XSSFSheet m_gln_codes_people_sheet = null;
	XSSFSheet m_gln_codes_companies_sheet = null;
	Map<String, String> m_gln_codes_moos_cond = null;
	Map<String, String> m_gln_codes_moos_targ = null;
	Map<String, String> m_gln_codes_moos_full = null;
	Map<String, User> m_gln_codes_complete = null;	// GLN+TYPE -> Information
	
	public GlnCodes() {
		// Load medreg files
		m_gln_codes_people_sheet = getSheetsFromFile(Constants.FILE_GLN_CODES_PEOPLE, 0);
		m_gln_codes_companies_sheet = getSheetsFromFile(Constants.FILE_GLN_CODES_COMPANIES, 0);
		// Mosberger conditions
		m_gln_codes_moos_cond = readFromSimpleCsvToMap(Constants.DIR_SHOPPING + Constants.FILE_MOOSBERGER);
		m_gln_codes_moos_targ = readFromSimpleCsvToMap(Constants.DIR_SHOPPING + Constants.FILE_TARGETING);
		// Mosberger full info without conditions
		m_gln_codes_moos_full = readFromComplexCsvToMap(Constants.DIR_SHOPPING + Constants.FILE_MOOS_ADDR, 16);
		// Complete list of gln_codes
		m_gln_codes_complete = new HashMap<String, User>();
	}
	
	public void generateCsvFile() {						
		long startTime = System.currentTimeMillis();	
		
		// Process first people's file
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
					m_gln_codes_complete.put(key, cust);
				}
			}
			num_rows++;
		}
		if (CmlOptions.SHOW_LOGS)
			System.out.println("- Processed gln codes people... (" + m_gln_codes_complete.size() + ")");		
		
		// Process companies' file
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
				if (row.getCell(3)!=null && row.getCell(4)!=null)
					cust.street = row.getCell(3).getStringCellValue().trim();
				if (row.getCell(4)!=null)
					cust.number = row.getCell(4).getStringCellValue().trim();
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
					m_gln_codes_complete.put(key, cust);
				}
			}
			num_rows++;
		}
		if (CmlOptions.SHOW_LOGS)
			System.out.println("- Processed gln codes companies... (" + m_gln_codes_complete.size() + ")");		
		
		// Loop through the moosberger_glns file
		for (Map.Entry<String, String> entry : m_gln_codes_moos_cond.entrySet()) {
			processGlns(entry.getKey(), entry.getValue());
		}
		if (CmlOptions.SHOW_LOGS)
			System.out.println("- Processed gln codes mosberger conditions file... (" + m_gln_codes_complete.size() + ")");		

		// Loop through the targeting_glns file
		for (Map.Entry<String, String> entry : m_gln_codes_moos_targ.entrySet()) {
			processGlns(entry.getKey(), entry.getValue());
		}	
		if (CmlOptions.SHOW_LOGS)
			System.out.println("- Processed gln codes mosberger targetting file... (" + m_gln_codes_complete.size() + ")");			
				
		// Loop through the moosberger full info without conditions
		for (Map.Entry<String, String> entry : m_gln_codes_moos_full.entrySet()) {
			processMoosFull(entry.getKey(), entry.getValue());
		}	
		if (CmlOptions.SHOW_LOGS)
			System.out.println("- Processed gln codes mosberger address file... (" + m_gln_codes_complete.size() + ")");		
		
		// Write to file
		int rows = writeMapToCsv(m_gln_codes_complete, '|', "gln_codes_csv.csv", "");		
		// ... and zip it
		// FileOps.zipToFile(Constants.DIR_OUTPUT, "gln_codes_csv.csv");	
		
		// Encrypt and serialize
		encryptMapToDir(m_gln_codes_complete, "gln_codes.ser");
		// ... and zip it
		FileOps.zipToFile(Constants.DIR_OUTPUT, "gln_codes.ser");
		
		long stopTime = System.currentTimeMillis();
		if (CmlOptions.SHOW_LOGS) {
			System.out.println("- Processed " + rows + " rows in "
					+ (stopTime - startTime) / 1000.0f + " sec");
		}
	}

	private void processGlns(String gln, String cat) {
		String key = gln + "S";
		if (m_gln_codes_complete.containsKey(key)) {
			String t[] = cat.split(";");
			User cust = m_gln_codes_complete.get(key);
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
			m_gln_codes_complete.put(key, cust);			
		} else {	// Create new entry
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
				cust.category = type;
				m_gln_codes_complete.put(key, cust);
			} else {
				System.out.println("Found wrong GLN code: " + gln);
			}
		}
	}
	
	private void processMoosFull(String key, String value) {
		if (m_gln_codes_complete.containsKey(key)) {
			String[] token = value.split(";", -1);
			User cust = m_gln_codes_complete.get(key);
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
			if (cust.street.isEmpty())
				cust.street = token[10];
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
			m_gln_codes_complete.put(key, cust);
			
		} else {	// Create new entry		
			if (key.length()==14) {				
				String[] token = value.split(";", -1);
				User cust = new User();
				cust.gln_code = key.substring(0, 13);
				cust.addr_type = key.substring(13);
				cust.title = token[4];
				cust.first_name = token[5];
				cust.last_name = token[6];
				cust.name1 = token[7];
				cust.name2 = token[8];
				cust.name3 = token[9];
				cust.street = token[10];
				cust.zip = token[11];
				cust.city = token[12];
				cust.phone = token[13];
				cust.fax = token[14];
				cust.email = token[15];
				cust.owner = "i";
				m_gln_codes_complete.put(key, cust);
			} else {
				System.out.println("Found wrong key code: " + key);
			}
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
	
	private Map<String, String> readFromComplexCsvToMap(String file_name, int num_entries) {
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
						if (!token[0].isEmpty() && !token[1].isEmpty()) {
							String key = token[1] + token[0];	// gln + addr_type
							if (map.containsKey(key))
								System.out.println("GLN code exists already! This is unlikely...");
							// Fill in map
							String value_str = token[0] + ";" + token[1] + ";";
							for (int i=2; i<num_entries-1; ++i)
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
	
	private int writeMapToCsv(Map<String, User> map, char separator, String file_name, String add_str) {
		String csv_file = add_str;
		int num_rows = 0;
		System.out.print("- Generating GLN codes csv file...");
		List<User> customer_list = new ArrayList<User>(map.values());
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
					+ c.street + " " + c.number + separator
					+ c.zip + separator
					+ c.city + separator
					+ c.phone + separator
					+ c.fax + separator
					+ c.email + separator
					+ c.selbst_disp + separator
					+ c.bet_mittel + "\n";
			num_rows++;
			System.out.print("\r- Generating GLN codes csv file... " + num_rows + "   ");
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
	
	private XSSFSheet getSheetsFromFile(String filename, int n) {
		XSSFSheet sheet = null;		
		try {
			FileInputStream file = new FileInputStream(filename);
			// Get workbook
			XSSFWorkbook workbook = new XSSFWorkbook(file);
			// Get sheet
			sheet = workbook.getSheetAt(n);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return sheet;
	}
}
