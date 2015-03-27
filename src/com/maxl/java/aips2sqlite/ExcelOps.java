package com.maxl.java.aips2sqlite;

import org.apache.poi.ss.usermodel.Cell;

public class ExcelOps {
	
	static public String getCellValue(Cell part) {
		if (part!=null) {
			switch (part.getCellType()) {
				case Cell.CELL_TYPE_BOOLEAN: 
					return part.getBooleanCellValue() + "";
		        case Cell.CELL_TYPE_NUMERIC: 
		        	return String.format("%.2f", part.getNumericCellValue());
		        case Cell.CELL_TYPE_STRING:	
		        	return part.getStringCellValue() + "";
		        case Cell.CELL_TYPE_BLANK: 
		        	return "BLANK";
		        case Cell.CELL_TYPE_ERROR: 
		        	return "ERROR";
		        case Cell.CELL_TYPE_FORMULA: 
		        	return "FORMEL";
		    }
		}
		return "";
	}
	
}
