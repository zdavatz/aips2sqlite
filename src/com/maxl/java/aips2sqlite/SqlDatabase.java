/*
Copyright (c) 2013 Max Lungarella

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

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SqlDatabase {
	
	private String AllAmikoDBRows = "title, auth, atc, substances, regnrs, atc_class, " +
        		"tindex_str, application_str, indications_str, " +
        		"customer_id, pack_info_str, " +
        		"add_info_str, ids_str, titles_str, content, style_str, packages";
	private String AllProductDBRows = "title, author, eancodes, pack_info_str, packages";
	
	private File m_db_file;
	private Connection conn;
	private Statement stat;
	private PreparedStatement m_prep_amikodb, m_prep_productdb;
	
	public SqlDatabase(String db_lang) {
		try {
			// Initializes org.sqlite.JDBC driver
			Class.forName("org.sqlite.JDBC");

			// Touch db file if it does not exist
			String db_url = System.getProperty("user.dir") + "/output/amiko_db_full_idx_" + db_lang + ".db";
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
	        
	        // Create main expert table
	        createExpertDB();
	        // Create product table
	        createProductDB();
	        
		} catch (IOException e) {
			System.err.println(">> SqlDatabase: DB file does not exist!");
			e.printStackTrace();
		} catch (ClassNotFoundException e ) {
			System.err.println(">> SqlDatabase: ClassNotFoundException!");
			e.printStackTrace();			
		} catch (SQLException e ) {
			System.err.println(">> SqlDatabase: SQLException!");
			e.printStackTrace();
		} 
	}
	
	@Override
	public void finalize() {
		// Reorder tables alphabetically
		reorderAlphaDB("amikodb");
		reorderAlphaDB("productdb");
		// 
		vacuum();
	}
	
	public List<String> listProducts() {
		List<String> packages = new ArrayList<String>();		
		
		try {
			stat = conn.createStatement();
			String query = "SELECT packages FROM amikodb";
			ResultSet rs = stat.executeQuery(query);
			while (rs.next()) {
				packages.add(rs.getString(1));
			} 
		} catch (SQLException e) {
			System.err.println(">> SqlDatabase: SQLException in listProducts!");
		}
		
		return packages;
	}
	
	public List<String> listProductsExcludingPseudo() {
		List<String> packages = new ArrayList<String>();		
		
		try {
			stat = conn.createStatement();
			String query = "SELECT packages FROM amikodb WHERE tindex_str not like 'PSEUDO'";
			ResultSet rs = stat.executeQuery(query);
			while (rs.next()) {
				packages.add(rs.getString(1));
			} 
		} catch (SQLException e) {
			System.err.println(">> SqlDatabase: SQLException in listProducts!");
		}
		
		return packages;
	}
	
	public Map<Long, String> mapMedis(String author) {
		Map<Long, String> map_of_medis = new TreeMap<Long, String>();
		
		try {
			String auth = author.toLowerCase();
			stat = conn.createStatement();
			String query = "SELECT _id, packages FROM amikodb WHERE auth LIKE " + "'" + auth + "%'";	
			ResultSet rs = stat.executeQuery(query);
			while (rs.next()) {
				map_of_medis.put(rs.getLong(1), rs.getString(2));
			} 
		} catch (SQLException e) {
			System.err.println(">> SqlDatabase: SQLException in mapMedis!");
		}
		
		return map_of_medis;		
	}

	public Map<Long, String> mapMedisExcludingPseudo(String author) {
		Map<Long, String> map_of_medis = new TreeMap<Long, String>();
		
		try {
			String auth = author.toLowerCase();
			stat = conn.createStatement();
			String query = "SELECT _id, packages FROM amikodb WHERE auth LIKE " + "'" + auth + "%' and tindex_str not like 'PSEUDO'";	
			ResultSet rs = stat.executeQuery(query);
			while (rs.next()) {
				map_of_medis.put(rs.getLong(1), rs.getString(2));
			} 
		} catch (SQLException e) {
			System.err.println(">> SqlDatabase: SQLException in mapMedis!");
		}
		
		return map_of_medis;		
	}

	
	public void deleteEntry(Long index) {
		try {
			stat.executeUpdate("DELETE FROM amikodb WHERE _id=" + index);
		} catch (SQLException e) {
			System.err.println(">> SqlDatabase: SQLException in deleteEntry!");
		}
	}
	
	private String expertTable() {
		return "(_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
	        		"title TEXT, auth TEXT, atc TEXT, substances TEXT, regnrs TEXT, atc_class TEXT, " +
	        		"tindex_str TEXT, application_str TEXT, indications_str TEXT, " +
	        		"customer_id INTEGER, pack_info_str TEXT, " + 
	        		"add_info_str TEXT, ids_str TEXT, titles_str TEXT, content TEXT, style_str TEXT, packages TEXT);";
	}

	private String productTable() {
		return "(_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
	        		"title TEXT, author TEXT, eancodes TEXT, pack_info_str TEXT, packages TEXT);";
	}
	
	public String getDBFile() {
		return m_db_file.getAbsolutePath();
	}
	
	public void createExpertDB()  {		       
		try {
			// Create SQLite database
	        stat.executeUpdate("DROP TABLE IF EXISTS amikodb;");
	        stat.executeUpdate("CREATE TABLE amikodb " + expertTable());
	        // Create indexes
	        createTableIndexes("amikodb");
	        // Insert statement	
	        m_prep_amikodb = conn.prepareStatement("INSERT INTO amikodb VALUES (null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");	       			           
		} catch (SQLException e ) {
			System.err.println(">> SqlDatabase: SQLException!");
			e.printStackTrace();
		} 
	}
	
	public void createProductDB() {		
		try {			
			// Create product table
	        stat.executeUpdate("DROP TABLE IF EXISTS productdb;");
	        stat.executeUpdate("CREATE TABLE productdb " + productTable());
	        // Create indexes
	        createTableIndexes("productdb");
	        // Insert statement
	        m_prep_productdb = conn.prepareStatement("INSERT INTO productdb VALUES (null, ?, ?, ?, ?, ?);");	       			           
		} catch (SQLException e ) {
			System.err.println(">> SqlDatabase: SQLException!");
			e.printStackTrace();
		} 
	}
	
	public void createTableIndexes(String table_name) {
		try {
	        if (table_name.equals("amikodb")) {
		        stat.executeUpdate("CREATE INDEX idx_title ON amikodb(title);");
		        stat.executeUpdate("CREATE INDEX idx_auth ON amikodb(auth);");
		        stat.executeUpdate("CREATE INDEX idx_atc ON amikodb(atc);");
		        stat.executeUpdate("CREATE INDEX idx_substances ON amikodb(substances);");
		        stat.executeUpdate("CREATE INDEX idx_regnrs ON amikodb(regnrs);");
		        stat.executeUpdate("CREATE INDEX idx_atc_class ON amikodb(atc_class);");
	        } else if (table_name.equals("productdb")) {
		        stat.executeUpdate("CREATE INDEX idx_prod_title ON productdb(title);");
		        stat.executeUpdate("CREATE INDEX idx_prod_author ON productdb(author);");
		        stat.executeUpdate("CREATE INDEX idx_prod_eancodes ON productdb(eancodes);");
	        }
		} catch (SQLException e ) {
			System.err.println(">> SqlDatabase: SQLException!");
			e.printStackTrace();
		} 
	}
	
	public void addExpertDB(MedicalInformations.MedicalInformation m) {
		try {
			m_prep_amikodb.setString(1, m.getTitle());
			m_prep_amikodb.setString(2, m.getAuthHolder());
			m_prep_amikodb.setString(3, m.getAtcCode());
			m_prep_amikodb.setString(4, m.getSubstances());  
			m_prep_amikodb.addBatch();        
			conn.setAutoCommit(false);
			m_prep_amikodb.executeBatch();
	        conn.setAutoCommit(true);         
		} catch (SQLException e) {
			System.err.println(">> SqlDatabase: SQLException!");
			e.printStackTrace();
		}
	}

	public void addExpertDB(MedicalInformations.MedicalInformation m, String packages_str, String regnr_str, String ids_str, 
			String titles_str, String atc_description_str, String atc_class_str, String pack_info_str, 
			String add_info_str, int customer_id, List<String> tIndex_list, String indications_str) {
		try {
			if (m_prep_amikodb!=null) {
				m_prep_amikodb.setString(1, m.getTitle());
				m_prep_amikodb.setString(2, m.getAuthHolder());
				m_prep_amikodb.setString(3, m.getAtcCode() + ";" + atc_description_str);
				m_prep_amikodb.setString(4, m.getSubstances());
				m_prep_amikodb.setString(5, regnr_str);
				m_prep_amikodb.setString(6, atc_class_str);
				m_prep_amikodb.setString(7, tIndex_list.get(0));	// therapeutic index
				m_prep_amikodb.setString(8, tIndex_list.get(1));	// application area	 
				m_prep_amikodb.setString(9, indications_str);		// indications section
				m_prep_amikodb.setInt(10, customer_id);	        
				m_prep_amikodb.setString(11, pack_info_str);
				m_prep_amikodb.setString(12, add_info_str);
				m_prep_amikodb.setString(13, ids_str);
				m_prep_amikodb.setString(14, titles_str);
				m_prep_amikodb.setString(15, m.getContent()); 
				m_prep_amikodb.setString(17, packages_str);
				m_prep_amikodb.addBatch();        
				conn.setAutoCommit(false);
				m_prep_amikodb.executeBatch();
		        conn.setAutoCommit(true);         
			} else {
				System.out.println("There is no database!");
				System.exit(0);
			}			
		} catch (SQLException e) {
			System.err.println(">> SqlDatabase: SQLException!");
			e.printStackTrace();
		}
	}	
		
	public void addExpertDB(String title, String author, String eancode_str, int customer_id, 
			String pack_info_str, String packages_str) {
		try {
			if (m_prep_amikodb!=null) {
				m_prep_amikodb.setString(1, title);
				m_prep_amikodb.setString(2, author);
				m_prep_amikodb.setString(3, "NON-AIPS;" + eancode_str);
				m_prep_amikodb.setString(4, title);
				m_prep_amikodb.setString(5, eancode_str);
				m_prep_amikodb.setString(7, "NON-AIPS");
				m_prep_amikodb.setString(8, "NON-AIPS");	 
				m_prep_amikodb.setInt(10, customer_id);	        
				m_prep_amikodb.setString(11, pack_info_str);
				m_prep_amikodb.setString(17, packages_str);
				m_prep_amikodb.addBatch();        
				conn.setAutoCommit(false);
				m_prep_amikodb.executeBatch();
		        conn.setAutoCommit(true);         
			} else {
				System.out.println("There is no database!");
				System.exit(0);
			}			
		} catch (SQLException e) {
			System.err.println(">> SqlDatabase: SQLException!");
			e.printStackTrace();
		}
	}
	
	public void addProductDB(MedicalInformations.MedicalInformation m, String packages_str, String eancodes_str, 
			String pack_info_str) {
		try {
			if (m_prep_productdb!=null) {
				m_prep_productdb.setString(1, m.getTitle());
				m_prep_productdb.setString(2, m.getAuthHolder());
				m_prep_productdb.setString(3, eancodes_str);
				m_prep_productdb.setString(4, pack_info_str);
				m_prep_productdb.setString(5, packages_str);			
				m_prep_productdb.addBatch();        
				conn.setAutoCommit(false);
				m_prep_productdb.executeBatch();
		        conn.setAutoCommit(true);         
			} else {
				System.out.println("There is no database!");
				System.exit(0);
			}			
		} catch (SQLException e) {
			System.err.println(">> SqlDatabase: SQLException!");
			e.printStackTrace();
		}
	}	
	
	public void readDB() throws SQLException { 		
		ResultSet rs = stat.executeQuery("SELECT * FROM amikodb;");
	    while (rs.next()) {
	        System.out.println("title = " + rs.getString("title"));
	        System.out.println("auth = " + rs.getString("auth"));
	        System.out.println("atccode = " + rs.getString("atc"));
	        System.out.println("substances = " + rs.getString("substances"));            
	    }
	    rs.close();
	    conn.close();	
	}
	
	public void reorderAlphaDB(String table_name) {
		try {
	        stat.executeUpdate("DROP TABLE IF EXISTS " + table_name + "_ordered;");
	        String AllRows = "";
	        if (table_name.equals("amikodb")) {
	        	stat.executeUpdate("CREATE TABLE " + table_name + "_ordered " + expertTable());     
	        	AllRows = AllAmikoDBRows;
	        } else if (table_name.equals("productdb")) {
	        	stat.executeUpdate("CREATE TABLE " + table_name + "_ordered " + productTable());  
	        	AllRows = AllProductDBRows;
	        }
	        stat.executeUpdate("INSERT INTO " + table_name + "_ordered (" + AllRows + ") "
					+ "SELECT " + AllRows + " FROM " + table_name + " ORDER BY " 
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
	        stat.executeUpdate("DROP TABLE IF EXISTS " + table_name + ";");
	        stat.executeUpdate("ALTER TABLE " + table_name + "_ordered RENAME TO " + table_name + ";");
	        createTableIndexes(table_name);
		} catch (SQLException e) {
			System.err.println(">> SqlDatabase: SQLException!");
			e.printStackTrace();
		}
	}
	
	public void vacuum() {
		try {
			stat.executeUpdate("VACUUM;");
		} catch (SQLException e) {
			System.err.println(">> SqlDatabase: SQLException!");
			e.printStackTrace();
		}
	}
}

