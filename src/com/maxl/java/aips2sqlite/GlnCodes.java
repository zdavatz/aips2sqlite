package com.maxl.java.aips2sqlite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class GlnCodes {
	
	XSSFSheet m_gln_codes_people_sheet = null;
	XSSFSheet m_gln_codes_companies_sheet = null;
	Map<String, String> m_gln_codes_moos = null;
	Map<String, String> m_gln_codes_targeting = null;
	Map<String, String> m_gln_codes_complete = null;
	
	public GlnCodes() {
		// Load medreg files
		m_gln_codes_people_sheet = getSheetsFromFile(Constants.FILE_GLN_CODES_PEOPLE, 0);
		m_gln_codes_companies_sheet = getSheetsFromFile(Constants.FILE_GLN_CODES_COMPANIES, 0);
		// Moosberger
		m_gln_codes_moos = readFromCsvToMap(Constants.DIR_SHOPPING + "moosberger_glns.csv");
		m_gln_codes_targeting = readFromCsvToMap(Constants.DIR_SHOPPING + "targeting_glns.csv");
		// Complete list of gln_codes
		m_gln_codes_complete = new TreeMap<String, String>();
	}
	
	private void processGlns(String gln, String cat) {
		String type = "";
		if (m_gln_codes_complete.containsKey(gln)) {
			String t[] = cat.split(";");
			if (t[1].matches(".*[Dd]rogerie.*")) {
				// Replace type string with "Drogerie"
				type = "Drogerie";
				System.out.println("-> Exists in med reg file: " + gln + " -> " + type);
			} else if (t[1].matches(".*[Gg]rossist.*")) {
				// Replace type string with "Grosist"
				type = "Grossist";
				System.out.println("-> Exists in med reg file: " + gln + " -> " + type);
			}
		} else {
			if (gln.length()==13) {
				String t[] = cat.split(";");				
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
				m_gln_codes_complete.put(gln, "|||||" + type + "||");
			} else {
				System.out.println("Found wrong GLN code: " + gln);
			}
		}
	}
	
	public void generateCsvFile() {						
		long startTime = System.currentTimeMillis();
		if (CmlOptions.SHOW_LOGS)
			System.out.print("- Generating GLN codes csv file... ");		
		
		// Process first people's file
		Iterator<Row> rowIterator = m_gln_codes_people_sheet.iterator();
		int num_rows = 0;		
		while (rowIterator.hasNext()) {
			Row row = rowIterator.next();
			if (num_rows>0) {
				String gln_person = "";		// 1
				String family_name = "";	// 2
				String first_name = "";		// 3				
				String plz = "";			// 4
				String location = "";		// 5
				String type = "";			// 7
				String selbst_disp = "";	// 8
				String bet_mittel = "";		// 9
				
				if (row.getCell(0)!=null) 
					gln_person = row.getCell(0).getStringCellValue();
				if (row.getCell(1)!=null)
					family_name = row.getCell(1).getStringCellValue();
				if (row.getCell(2)!=null) 
					first_name = row.getCell(2).getStringCellValue();
				if (row.getCell(3)!=null)
					plz = row.getCell(3).getStringCellValue();
				if (row.getCell(4)!=null) 
					location = row.getCell(4).getStringCellValue();
				if (row.getCell(7)!=null) 
					type = row.getCell(7).getStringCellValue();
				if (row.getCell(8)!=null)
					selbst_disp = row.getCell(8).getStringCellValue();
				if (row.getCell(9)!=null) 
					bet_mittel = row.getCell(9).getStringCellValue();
		
				if (type.matches(".*[AÄaä]rzt.*"))
					type = "Arzt";		
		
				if (!gln_person.isEmpty()) {
					m_gln_codes_complete.put(gln_person, family_name + "|" + first_name + "|" + plz 
							+ "|" + location + "||" + type + "|" + selbst_disp + "|" + bet_mittel);
				}
			}
			num_rows++;
		}
		
		// Process companies' file
		rowIterator = m_gln_codes_companies_sheet.iterator();
		num_rows = 0;
		while (rowIterator.hasNext()) {
			Row row = rowIterator.next();
			if (num_rows>0) {
				String gln_company = "";	// 1
				String name_1 = "";			// 2
				String name_2 = "";			// 3
				String plz = "";			// 4
				String location = "";		// 5
				String street = "";			// 6a
				String number = "";			// 6b				
				String type = "";			// 7
				
				if (row.getCell(0)!=null) 
					gln_company = row.getCell(0).getStringCellValue();
				if (row.getCell(1)!=null)
					name_1 = row.getCell(1).getStringCellValue();
				if (row.getCell(2)!=null) 
					name_2 = row.getCell(2).getStringCellValue();
				if (row.getCell(5)!=null)
					plz = row.getCell(5).getStringCellValue();
				if (row.getCell(6)!=null) 
					location = row.getCell(6).getStringCellValue();
				if (row.getCell(3)!=null)
					street = row.getCell(3).getStringCellValue().trim();
				if (row.getCell(4)!=null) 
					number = row.getCell(4).getStringCellValue().trim();
				if (row.getCell(9)!=null) 
					type = row.getCell(9).getStringCellValue();

				if (type.matches(".*Apotheke.*"))
					type = "Apotheke";		
				else if (type.matches(".*[Ss]pital.*"))
					type = "Spital";
				else if (type.matches(".*[Ww]issenschaft.*"))
					type = "Wissenschaft";
				else if (type.matches(".*[Bb]ehörde.*"))
					type = "Behörde";
				
				if (!gln_company.isEmpty()) {
					m_gln_codes_complete.put(gln_company, name_1 + "|" + name_2 + "|" + plz 
							+ "|" + location + "|" + street + " " + number + "|" + type + "||");
				}
			}
			num_rows++;
		}
		
		// Loop through the moosberger_glns file
		for (Map.Entry<String, String> entry : m_gln_codes_moos.entrySet()) {
			processGlns(entry.getKey(), entry.getValue());
		}
		// Loop through the targeting_glns file
		for (Map.Entry<String, String> entry : m_gln_codes_targeting.entrySet()) {
			processGlns(entry.getKey(), entry.getValue());
		}	
				
		// Write to file
		int rows = writeMapToCsv(m_gln_codes_complete, '|', "gln_codes_csv.csv", "");		
		// ... and zip it
		FileOps.zipToFile(Constants.DIR_OUTPUT, "gln_codes_csv.csv");	

		long stopTime = System.currentTimeMillis();
		if (CmlOptions.SHOW_LOGS) {
			System.out.println("- Processed " + rows + " rows in "
					+ (stopTime - startTime) / 1000.0f + " sec");
		}
	}
	
	private int writeMapToCsv(Map<String, String> map, char separator, String file_name, String add_str) {
		String csv_file = add_str;
		int num_rows = 0;
		for (Map.Entry<String, String> entry : map.entrySet()) {
			csv_file += entry.getKey() + separator + entry.getValue() + "\n";
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
	
	private Map<String,String> readFromCsvToMap(String filename) {
		Map<String, String> map = new TreeMap<String, String>();
		try {
			File file = new File(filename);
			if (!file.exists()) 
				return null;
			FileInputStream fis = new FileInputStream(filename);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
			String line;
			while ((line = br.readLine()) != null) {
				String token[] = line.split(";");
				if (token.length>2) {
					if (map.containsKey(token[0]))
						System.out.println("GLN code exists already!");
					// token[1]: class (A/B), token[2]: type
					map.put(token[0], token[1] + ";" + token[2]);
				}
			}
			br.close();
		} catch (Exception e) {
			System.err.println(">> Error in reading csv file");
		}
		
		return map;
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
