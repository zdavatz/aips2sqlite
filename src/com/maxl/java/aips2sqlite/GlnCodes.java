package com.maxl.java.aips2sqlite;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class GlnCodes {
	
	XSSFSheet m_gln_codes_people_sheet = null;
	XSSFSheet m_gln_codes_companies_sheet = null;
	
	
	public GlnCodes() {
		// Load files
		m_gln_codes_people_sheet = getSheetsFromFile(Constants.FILE_GLN_CODES_PEOPLE, 0);
		m_gln_codes_companies_sheet = getSheetsFromFile(Constants.FILE_GLN_CODES_COMPANIES, 0);
	}
	
	public void generateCsvFile() {						
		String csv_file = "";

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
				
				csv_file += gln_person + "|" + family_name + "|" + first_name + "|" + plz 
						+ "|" + location + "||" + type + "|" + selbst_disp + "|" + bet_mittel + "\n"; 
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
				
				csv_file += gln_company + "|" + name_1 + "|" + name_2 + "|" + plz 
						+ "|" + location + "|" + street + " " + number + "|" + type + "||\n"; 
				System.out.print("\r- Generating GLN codes csv file... " + num_rows + "   ");
			}
			num_rows++;
		}
		
		// Write to file
		FileOps.writeToFile(csv_file, Constants.DIR_OUTPUT, "gln_codes_csv.csv");
		// ... and zip it
		FileOps.zipToFile(Constants.DIR_OUTPUT, "gln_codes_csv.csv");
		
		long stopTime = System.currentTimeMillis();
		if (CmlOptions.SHOW_LOGS) {
			System.out.println("- Processed " + (num_rows+num_rows_person_file) + " rows in "
					+ (stopTime - startTime) / 1000.0f + " sec");
		}
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
