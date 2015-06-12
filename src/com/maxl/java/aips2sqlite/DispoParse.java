/*
Copyright (c) 2015 Max Lungarella

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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.opencsv.CSVReader;

public class DispoParse {

	private File m_db_file;
	private Connection conn;
	private Statement stat;
	private PreparedStatement m_prep_rosedb;	
	private Map<String, String> m_atc_map = null;
	private Map<String, Integer> m_ean_likes = null;
	private XSSFSheet m_dispo_articles_sheet = null;
	
	private Set<String> unit_set = new HashSet<String>();
	
	private String AllDBRows = "title, size, galen, unit, eancode, pharmacode, atc, theracode, stock, price, availability, supplier, likes";
	
	public DispoParse() {
		// Initialize the database
		initSqliteDB();
	}
	
	public void process(String type) {		
		// Process atc map
		getAtcMap();

		if (type.equals("csv")) {
			// Process likes
			processLikes();
			// Load CSV file...		
			processCsv();
		}
	}
	
	private void initSqliteDB() {
		try {
			// Initializes org.sqlite.JDBC driver
			Class.forName("org.sqlite.JDBC");
			
			// Touch db file if it does not exist
			String db_url = System.getProperty("user.dir") + "/output/rose_db_full.db";
			m_db_file = FileOps.touchFile(db_url);				
			if (m_db_file==null)
				throw new IOException();
		
			// Creates connection
			conn = DriverManager.getConnection("jdbc:sqlite:" + db_url);		
			stat = conn.createStatement();
			
			// Add version number 
			stat.executeUpdate("PRAGMA user_version=" + Constants.FI_DB_VERSION.replaceAll("[^\\d]", "") + ";");
			
	        // Create android metadata table
			stat.executeUpdate("DROP TABLE IF EXISTS android_metadata;");
	        stat.executeUpdate("CREATE TABLE android_metadata (locale TEXT default 'en_US');"); 	
	        stat.executeUpdate("INSERT INTO android_metadata VALUES ('en_US');");	
	   			
	        // Create article db
	        createArticleDB();
	        
		} catch (IOException e) {
			System.err.println(">> DispoParse: DB file does not exist!");
			e.printStackTrace();
		} catch (ClassNotFoundException e ) {
			System.err.println(">> DispoParse: ClassNotFoundException!");
			e.printStackTrace();			
		} catch (SQLException e ) {
			System.err.println(">> DispoParse: SQLException!");
			e.printStackTrace();
		} 
	}
	
	private String mainTable() {
		return "(_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
	        		"title TEXT, size TEXT, galen TEXT, unit TEXT, " +
	        		"eancode TEXT, pharmacode TEXT, atc TEXT, theracode TEXT, " +
	        		"stock INTEGER, price TEXT, availability TEXT, supplier TEXT, likes INTEGER);";
	}
	
	private void createArticleDB()  {		       
		try {
			// Create SQLite database
	        stat.executeUpdate("DROP TABLE IF EXISTS rosedb;");
	        stat.executeUpdate("CREATE TABLE rosedb " + mainTable());
	        // Insert statement	
	        m_prep_rosedb = conn.prepareStatement("INSERT INTO rosedb VALUES (null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");	       			           
		} catch (SQLException e ) {
			System.err.println(">> DispoParse: SQLException!");
			e.printStackTrace();
		} 
	}
	
	private void addArticleDB(Article article) {
		try {
			m_prep_rosedb.setString(1, article.pack_title);
			m_prep_rosedb.setInt(2, article.pack_size);
			m_prep_rosedb.setString(3, article.galen_form);
			m_prep_rosedb.setString(4, article.pack_unit);
			m_prep_rosedb.setString(5, article.ean_code + ";" + article.regnr);
			m_prep_rosedb.setString(6, article.pharma_code);
			m_prep_rosedb.setString(7, article.atc_code + ";" + article.atc_class);
			m_prep_rosedb.setString(8, article.therapy_code);
			m_prep_rosedb.setInt(9, article.stock);
			m_prep_rosedb.setString(10, article.rose_base_price);
			m_prep_rosedb.setString(11, article.availability);
			m_prep_rosedb.setString(12, article.rose_supplier);		
			m_prep_rosedb.setInt(13, article.likes);
			m_prep_rosedb.addBatch();        
			conn.setAutoCommit(false);
			m_prep_rosedb.executeBatch();
	        conn.setAutoCommit(true); 
		} catch (SQLException e) {
			System.err.println(">> DispoParse: SQLException!");
			e.printStackTrace();
		}	
	}
		
	private void complete() {
		// Reorder tables alphabetically
		reorderAlphaDB();
		// Compress...
		vacuum();
	}
	
	private void reorderAlphaDB() {
		try {
	        stat.executeUpdate("DROP TABLE IF EXISTS rosedb_ordered;");
        	stat.executeUpdate("CREATE TABLE rosedb_ordered " + mainTable());     
	        stat.executeUpdate("INSERT INTO rosedb_ordered (" + AllDBRows + ") "
					+ "SELECT " + AllDBRows + " FROM rosedb ORDER BY " 
					+ "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE("
					+ "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE("
					+ "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE("
					+ "REPLACE(REPLACE(REPLACE(REPLACE("
					+ "REPLACE(REPLACE(REPLACE(REPLACE("
					+ "title,"
					+ "'é','e'),'à','a'),'è','e'),'ê','e'),'É','E'),"
					+ "'î','i'),'ç','c'),'ä','a'),'ö','o'),'Ä','A'),"
					+ "'ü','u'),'(','{('),'[','{['),'0','{0'),'1','{1'),'2','{2'),"
					+ "'3','{3'),'4','{4'),'5','{5'),'6','{6'),"
					+ "'7','{7'),'8','{8'),'9','{9')"
					+ " COLLATE NOCASE;");
	        stat.executeUpdate("DROP TABLE IF EXISTS rosedb;");
	        stat.executeUpdate("ALTER TABLE rosedb_ordered RENAME TO rosedb;");
		} catch (SQLException e) {
			System.err.println(">> DispoParse: SQLException!");
			e.printStackTrace();
		}
	}
	
	private void vacuum() {
		try {
			stat.executeUpdate("VACUUM;");
		} catch (SQLException e) {
			System.err.println(">> DispoParse: SQLException!");
			e.printStackTrace();
		}
	}
		
	public void processLikes() {
		// Read like_db (if it exists)
		File file = new File(Constants.DIR_ZURROSE + "/" + Constants.CSV_LIKE_DB_ZR);
		if (!file.exists())
			m_ean_likes = new HashMap<String, Integer>();
		else
			m_ean_likes = readLikeMap(file.getAbsolutePath());
		// List csv files in DIR_ZURROSE		
		File[] list_of_files = new File(Constants.DIR_ZURROSE).listFiles();
		String like_db_str = "";
		// Read all files and extract id, timestamp and eans
		try {
			for (File f : list_of_files) {
				String file_name = f.getName();
				if (file_name.endsWith("csv") && file_name.startsWith("rose")) {
					// Get timestamp and id
					String[] tokens = file_name.substring(0, file_name.indexOf(".")).split("_");
					String user_id = "00000";
					String time_stamp = "";
					if (tokens.length==2)
						time_stamp = tokens[1];
					else if (tokens.length==3) {
						time_stamp = tokens[1];
						user_id = tokens[2];
					}  						
					FileInputStream fis = new FileInputStream(f);
					BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
					String eancode;
					while ((eancode=br.readLine()) != null) {
						// Each line contains an eancode
						like_db_str += time_stamp + ";" + eancode + ";" + user_id + "\n";
						// Increment likes in like map
						if (m_ean_likes.containsKey(eancode)) {
							int num_likes = m_ean_likes.get(eancode);
							m_ean_likes.put(eancode, ++num_likes);
						}
					}										
					fis.close();		
					// Delete file
					f.delete();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		// - Save like_db
		FileOps.writeToFile(like_db_str, Constants.DIR_ZURROSE, Constants.CSV_LIKE_DB_ZR);
	}
	
	public void processCsv() {
		/*  File format
		 *   0: Pharmacode
		 *   1: Artikelname
		 *   2: Strichcode
		 *   3: Ausstand bis
		 *   4: Fehlt auf unbestimmte Zeit
		 *   5: Packungsinhalt
		 *   6: Therapeutischer Code
		 *   7: ATC-Key
		 *   8: Lagerbestand
		 *   9: Lieferant gem. Rose
		 *  10: Galen. Form
		 *  11: Dosierung
		 *  12: Rose Basispreis (rbp)
		 */
		try {
			File file = new File(Constants.DIR_ZURROSE + "/" + Constants.CSV_FILE_DISPO_ZR);
			if (!file.exists()) 
				return;
			FileInputStream fis = new FileInputStream(file.getAbsoluteFile());
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
			String line;
			int num_rows = 0;
			List<Article> list_of_articles = new ArrayList<Article>();
			while ((line = br.readLine())!=null && num_rows<9000) {
				String token[] = line.split(";");
				if (num_rows>0 && token.length>12) {
					Article article = new Article();					
					// Pharmacode
					if (token[0]!=null)	
						article.pharma_code = token[0];
					// Artikelname
					if (token[1]!=null)
						article.pack_title = token[1];
					// Strichcode
					if (token[2]!=null) {
						article.ean_code = token[2];
						if (article.ean_code.length()==13) {
							String ean = article.ean_code;
							article.regnr = ean.substring(4,9);
							if (m_ean_likes.containsKey(ean))
								article.likes = m_ean_likes.get(ean);	// LIKES!!!
							else 
								article.likes = 0;
						}
					}
					// Ausstand bis
					if (token[3]!=null)
						article.availability = token[3];
					// Packungsihnalt
					if (token[5]!=null)		// SIZE = Packungsgrösse or Packungsinhalt
						article.pack_size = (int)(Float.parseFloat(token[5]));				
					// Therapeutischer Code			
					if (token[6]!=null) {
						if (!token[6].isEmpty())
							article.therapy_code = token[6];
						else 
							article.therapy_code = "k.A.";
					}
					// ATC-Key
					if (token[7]!=null) {
						article.atc_code = token[7].toUpperCase();
						if (!article.atc_code.isEmpty())
							article.atc_class = m_atc_map.get(article.atc_code);
						else {
							article.atc_code = "k.A.";
							article.atc_class = "k.A.";
						}
					}
					// Lagerbestand
					if (token[8]!=null)
						article.stock = (int)(Float.parseFloat(token[8]));
					// Lieferant
					if (token[9]!=null)
						article.rose_supplier = token[9];
					// Galen. Form
					if (token[10]!=null)	// GALEN = Galenische Form					
						article.galen_form = token[10];
					// Dosierung
					if (token[11]!=null) {	// UNIT = Stärke or Dosierung
						unitParse(token[11]);
						article.pack_unit = token[11];				
					}
					// Rose Basispreis
					if (token[12]!=null) {
						if (token[12].matches("^[0-9]+(\\.[0-9]{1,2})?$"))
							article.rose_base_price = String.format("%.2f", Float.parseFloat(token[12]));
					}
					list_of_articles.add(article);
					addArticleDB(article);
					System.out.println(num_rows + " -> " + article.pack_title + " / likes = " + article.likes);
				}
				num_rows++;
			}
			complete();			
			System.out.println("Number of articles = " + list_of_articles.size());
			// 
			br.close();
			
			int i = 0;
			for (String u : unit_set) {
				i++;
				System.out.println(i + " -> " + u);
			}
			
		} catch (Exception e) {
			System.err.println(">> Error in processCsv");
		}	
	}
	
	public void processXlsx() {
		/*  Sheet format
		 *   0: Pharmacode
		 *   1: Artikelname
		 *   2: Strichcode
		 *   3: Ausstand bis
		 *   4: Fehlt auf unbestimmte Zeit
		 *   5: Packungsinhalt
		 *   6: Therapeutischer Code
		 *   7: ATC-Key
		 *   8: Lagerbestand
		 *   9: Lieferant gem. Rose
		 *  10: Galen. Form
		 *  11: Dosierung
		 *  12: Rose Basispreis (rbp)
		 */
		List<Article> list_of_articles = new ArrayList<Article>();
		Iterator<Row> rowIterator = m_dispo_articles_sheet.iterator();
		int num_rows = 0;
		while (rowIterator.hasNext() && num_rows<100) {
			Row row = rowIterator.next();
			if (num_rows>0) {
				Article article = new Article();
				if (row.getCell(0)!=null)
					article.pharma_code = intToString(ExcelOps.getCellValue(row.getCell(0)));
				if (row.getCell(1)!=null)
					article.pack_title = ExcelOps.getCellValue(row.getCell(1));
				if (row.getCell(2)!=null)
					article.ean_code = intToString(ExcelOps.getCellValue(row.getCell(2)));
				if (row.getCell(3)!=null)					
					article.availability = ExcelOps.getCellValue(row.getCell(3));
				if (row.getCell(5)!=null)
					article.pack_size = doubleToInt(ExcelOps.getCellValue(row.getCell(5)));				
				if (row.getCell(6)!=null)
					article.therapy_code = ExcelOps.getCellValue(row.getCell(6));
				if (row.getCell(7)!=null)
					article.atc_code = ExcelOps.getCellValue(row.getCell(7)).toUpperCase();
				if (row.getCell(8)!=null)
					article.stock = doubleToInt(ExcelOps.getCellValue(row.getCell(8)));
				if (row.getCell(9)!=null)
					article.rose_supplier = ExcelOps.getCellValue(row.getCell(9));
				if (row.getCell(10)!=null)
					article.galen_form = ExcelOps.getCellValue(row.getCell(10));
				if (row.getCell(11)!=null)
					article.pack_unit = ExcelOps.getCellValue(row.getCell(11));				
				if (row.getCell(12)!=null)
					article.rose_base_price = ExcelOps.getCellValue(row.getCell(12));
				list_of_articles.add(article);
				addArticleDB(article);
				System.out.println(article.rose_base_price);
			}
			num_rows++;
		}

		complete();
		
		System.out.println("Number of articles = " + list_of_articles.size());
	}

	private void unitParse(String unit) {
		String[] token = unit.split(" ");
		if (token.length>1) {
			unit_set.add(token[1].trim());
		}
	}
	
	private void getAtcMap() {
		m_atc_map = new TreeMap<String, String>();
		
		try {
			CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(Constants.FILE_EPHA_ATC_CODES_CSV), "UTF-8"));
			List<String[]> myEntries = reader.readAll();
			int num_rows = myEntries.size();
			for (String[] s : myEntries) {
				if (s.length>2) {
					String atc_code = s[0];
					String atc_class = s[1];
					m_atc_map.put(atc_code, atc_class);
				}
			}
			reader.close();

			/*
			// Load ATC classes xls file
			FileInputStream atc_classes_file = new FileInputStream(Constants.FILE_ATC_CLASSES_XLS);
			// Get workbook instance for XLS file (HSSF = Horrible SpreadSheet Format)
			HSSFWorkbook atc_classes_workbook = new HSSFWorkbook(atc_classes_file);
			// Get first sheet from workbook
			// HSSFSheet atc_classes_sheet = atc_classes_workbook.getSheetAt(1);	// --> 2013 file
			HSSFSheet atc_classes_sheet = atc_classes_workbook.getSheetAt(0);		// --> 2014 file			
			// Iterate through all rows of first sheet
			Iterator<Row> rowIterator = atc_classes_sheet.iterator();
		
			int num_rows = 0;
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				if (num_rows>2) {
					String atc_code = "";
					String atc_class = "";
					if (row.getCell(0)!=null) {
						atc_code = row.getCell(0).getStringCellValue().replaceAll("\\s", "");
					}
					if (row.getCell(2)!=null) {
						atc_class = row.getCell(2).getStringCellValue();
					}
					// Build a full map atc code to atc class
					if (atc_code.length()>0) {
						m_atc_map.put(atc_code, atc_class);
					}
				}
				num_rows++;
			}
			*/
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	private String intToString(String i) {
		if (i!=null) {
			int idx = i.indexOf(".");
			if (idx>0)
				return i.substring(0,idx);
			else
				return i;
		} else
			return null;
		
	}
	
	private int doubleToInt(String d) {
		if (d!=null) {
			int idx = d.indexOf(".");
			if (idx>0)
				return Integer.parseInt(d.substring(0,idx));
			else 
				return Integer.parseInt(d);
		} else
			return 0;
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
	
	private Map<String, Integer> readLikeMap(String filename) {
		Map<String, Integer> map = new HashMap<String, Integer>();
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
					String eancode = token[1];
					if (map.containsKey(eancode)) {
						int num_likes = map.get(eancode);
						map.put(eancode, ++num_likes);
					} else {
						map.put(eancode, 1);
					}
				}
			}
			br.close();
		} catch (Exception e) {
			System.err.println(">> Error in reading csv file");
		} 		
		return map;
	}
}
