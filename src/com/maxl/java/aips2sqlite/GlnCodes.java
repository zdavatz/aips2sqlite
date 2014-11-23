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
	Map<String, String> m_gln_codes_studer = null;
	
	public GlnCodes() {
		// Load files
		m_gln_codes_people_sheet = getSheetsFromFile(Constants.FILE_GLN_CODES_PEOPLE, 0);
		m_gln_codes_companies_sheet = getSheetsFromFile(Constants.FILE_GLN_CODES_COMPANIES, 0);
		// Studer Marketing
		m_gln_codes_studer = readFromCsvToMap(Constants.DIR_SHOPPING + "ibsa_glns.csv");
	}
	
	public void generateCsvFile() {						
		String csv_file = "";

		// Counters
		int unique_gln_found = 0;
		int multiple_gln_found = 0;
		
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
				
				csv_file += gln_person + "|" + family_name + "|" + first_name + "|" + plz 
						+ "|" + location + "||" + type + "|" + selbst_disp + "|" + bet_mittel + "\n"; 
				
				if (m_gln_codes_studer.containsKey(gln_person)) {
					String cat = m_gln_codes_studer.get(gln_person);
					if (!cat.contains(";Arzt"))
						unique_gln_found++;
					else
						multiple_gln_found++;
					m_gln_codes_studer.put(gln_person, cat + ";Arzt");
				}
				
				System.out.print("\r- Generating GLN codes csv file... " + num_rows);
			}
			num_rows++;
		}
		int num_rows_person_file = num_rows;
		
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
				
				csv_file += gln_company + "|" + name_1 + "|" + name_2 + "|" + plz 
						+ "|" + location + "|" + street + " " + number + "|" + type + "||\n";
				
				if (m_gln_codes_studer.containsKey(gln_company)) {
					String cat = m_gln_codes_studer.get(gln_company);
					if (!cat.contains(";Betrieb"))
						unique_gln_found++;
					else
						multiple_gln_found++;
					m_gln_codes_studer.put(gln_company, cat + ";Betrieb");
				}
				
				System.out.print("\r- Generating GLN codes csv file... " + num_rows + "   ");
			}
			num_rows++;
		}
		
		// Write to file
		FileOps.writeToFile(csv_file, Constants.DIR_OUTPUT, "gln_codes_csv.csv");
		// ... and zip it
		FileOps.zipToFile(Constants.DIR_OUTPUT, "gln_codes_csv.csv");
		
		// Write map to file
		String add_str = "GLN codes found in Studer file: " + (m_gln_codes_studer.size()-1) 
				+ "\nGLN codes not found in Studer file: " + (m_gln_codes_studer.size()-1-unique_gln_found)				
				+ "\nUnique matched GLN codes found in Studer file: " + unique_gln_found 
				+ "\nMultiple matched GLN codes found in Studer file: " + multiple_gln_found
				+ "\n";
		writeMapToCsv(m_gln_codes_studer, "gln_codes_complete.csv", add_str);
		writeUnmatchedGLNcodesToFile(m_gln_codes_studer, "gln_codes_unmatched.csv");
		
		
		long stopTime = System.currentTimeMillis();
		if (CmlOptions.SHOW_LOGS) {
			System.out.println("- Processed " + (num_rows+num_rows_person_file) + " rows in "
					+ (stopTime - startTime) / 1000.0f + " sec");
		}
	}
	
	private void writeMapToCsv(Map<String, String> map, String file_name, String add_str) {
		String csv_file = add_str;
		for (Map.Entry<String, String> entry : map.entrySet()) {
			csv_file += entry.getKey() + ";" + entry.getValue() + "\n";
		}
		FileOps.writeToFile(csv_file, Constants.DIR_OUTPUT, file_name);
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
				if (token.length>1) {
					if (map.containsKey(token[0]))
						System.out.println("GLN code exists already!");
					map.put(token[0], token[1]);
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
