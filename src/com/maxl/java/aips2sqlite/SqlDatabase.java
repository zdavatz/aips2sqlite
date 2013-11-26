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
import java.util.List;

public class SqlDatabase {

	private File m_db_file;
	private Connection conn;
	private Statement stat;
	private PreparedStatement prep;
	
	public String getDBFile() {
		return m_db_file.getAbsolutePath();
	}
	
	public void createDB(String db_lang) throws ClassNotFoundException, SQLException {		
		// Initializes org.sqlite.JDBC driver
		Class.forName("org.sqlite.JDBC");

		try {
			// Touch db file if it does not exist
			String db_url = System.getProperty("user.dir") + "/output/amiko_db_full_idx_" + db_lang + ".db";			
			File db_file = new File(db_url);
			if (!db_file.exists()) {
				db_file.getParentFile().mkdirs();
				db_file.createNewFile();
			}
			m_db_file = db_file;
			// Creates connection
			conn = DriverManager.getConnection("jdbc:sqlite:" + db_url);		
			stat = conn.createStatement();
			
	        // Create android metadata table
			stat.executeUpdate("DROP TABLE IF EXISTS android_metadata;");
	        stat.executeUpdate("CREATE TABLE android_metadata (locale TEXT default 'en_US');"); 	
	        stat.executeUpdate("INSERT INTO android_metadata VALUES ('en_US');");	        

			// Create SQLite database
	        stat.executeUpdate("DROP TABLE IF EXISTS amikodb;");
	        stat.executeUpdate("CREATE TABLE amikodb (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
	        		"title TEXT, auth TEXT, atc TEXT, substances TEXT, regnrs TEXT, atc_class TEXT, " +
	        		"tindex_str TEXT, application_str TEXT, indications_str TEXT, " +
	        		"customer_id INTEGER, pack_info_str TEXT, " + 
	        		"add_info_str TEXT, ids_str TEXT, titles_str TEXT, content TEXT, style_str TEXT);");
	        // Create indices
	        stat.executeUpdate("CREATE INDEX idx_title ON amikodb(title);");
	        stat.executeUpdate("CREATE INDEX idx_auth ON amikodb(auth);");
	        stat.executeUpdate("CREATE INDEX idx_atc ON amikodb(atc);");
	        stat.executeUpdate("CREATE INDEX idx_substances ON amikodb(substances);");
	        stat.executeUpdate("CREATE INDEX idx_regnrs ON amikodb(regnrs);");
	        stat.executeUpdate("CREATE INDEX idx_atc_class ON amikodb(atc_class);");
	        	        
			/*
	        // Create virtual table	   
	        stat.executeUpdate("DROP TABLE IF EXISTS amikodb_fts");	        
	        stat.executeUpdate("CREATE VIRTUAL TABLE amikodb_fts USING FTS3(_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
	        		"title TEXT, auth TEXT, atc TEXT, substances TEXT, regnrs TEXT, " +
	        		"ids_str TEXT, titles_str TEXT, style TEXT, content TEXT);");
	        */		        
	        prep = conn.prepareStatement("INSERT INTO amikodb VALUES (null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");	       			           
		} catch (IOException e) {
			System.err.println(">> SqlDatabase: DB file does not exist!");
		} catch (SQLException e ) {
			System.err.println(">> SqlDatabase: SQLException!");
		} 
	}
	
	public void addDB(MedicalInformations.MedicalInformation m) throws SQLException {
		prep.setString(1, m.getTitle());
        prep.setString(2, m.getAuthHolder());
		prep.setString(3, m.getAtcCode());
        prep.setString(4, m.getSubstances());  
        prep.addBatch();        
		conn.setAutoCommit(false);
        prep.executeBatch();
        conn.setAutoCommit(true);         
	}

	public void addDB(MedicalInformations.MedicalInformation m, String style_str, String regnr_str, String ids_str, 
			String titles_str, String atc_description_str, String atc_class_str, String pack_info_str, 
			String add_info_str, int customer_id, List<String> tIndex_list, String indications_str) throws SQLException {
		if (prep!=null) {
			prep.setString(1, m.getTitle());
	        prep.setString(2, m.getAuthHolder());
			prep.setString(3, m.getAtcCode() + ";" + atc_description_str);
	        prep.setString(4, m.getSubstances());
	        prep.setString(5, regnr_str);
	        prep.setString(6, atc_class_str);
	        prep.setString(7, tIndex_list.get(0));	// therapeutic index
	        prep.setString(8, tIndex_list.get(1));	// application area	 
	        prep.setString(9,  indications_str);	// indications section
	        prep.setInt(10, customer_id);	        
	        prep.setString(11,  pack_info_str);
	        prep.setString(12, add_info_str);
	        prep.setString(13, ids_str);
	        prep.setString(14, titles_str);
	        prep.setString(15, m.getContent()); 
	        prep.setString(16, style_str);
	        prep.addBatch();        
			conn.setAutoCommit(false);
	        prep.executeBatch();
	        conn.setAutoCommit(true);         
		} else {
			System.out.println("There is no database!");
			System.exit(0);
		}			
	}	
	
	public void readDB() throws SQLException { 		
		ResultSet rs = stat.executeQuery("select * from amikodb;");
        while (rs.next()) {
            System.out.println("title = " + rs.getString("title"));
            System.out.println("auth = " + rs.getString("auth"));
            System.out.println("atccode = " + rs.getString("atc"));
            System.out.println("substances = " + rs.getString("substances"));            
        }
        rs.close();
        conn.close();	
	}
}

