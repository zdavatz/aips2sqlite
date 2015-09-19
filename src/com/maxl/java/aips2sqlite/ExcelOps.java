package com.maxl.java.aips2sqlite;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelOps {
	
	static public String getCellValue(Cell part) {
		if (part!=null) {
			switch (part.getCellType()) {
				case Cell.CELL_TYPE_BOOLEAN: return part.getBooleanCellValue() + "";
		        case Cell.CELL_TYPE_NUMERIC: return String.format("%.2f", part.getNumericCellValue());
		        case Cell.CELL_TYPE_STRING:	return part.getStringCellValue() + "";
		        case Cell.CELL_TYPE_BLANK: return "BLANK";
		        case Cell.CELL_TYPE_ERROR: return "ERROR";
		        case Cell.CELL_TYPE_FORMULA: return "FORMEL";
		    }
		}
		return "";
	}

	
	static public XSSFSheet getSheetsFromFile(String filename, int n) {
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
