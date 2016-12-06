/*
Copyright (c) 2016 Max Lungarella

This file is part of Aips2SQLite.

Aips2SQLite is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

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
