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
import java.io.IOException;
import java.io.InputStream;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.bcel.classfile.Constant;
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
	private HashMap<String, String> m_flags_map = null;
	private HashMap<String, String> m_bag_exfacto_price_map = null;
	private HashMap<String, String> m_bag_public_price_map = null;
	private XSSFSheet m_dispo_articles_sheet = null;

	private Set<String> unit_set = new HashSet<String>();

	private String AllDBRows = "title, size, galen, unit, eancode, pharmacode, atc, theracode, stock, price, availability, supplier, likes, replaceean, " +
			"replacepharma, offmarket, flags, npl, publicprice, exfprice, dlkflag, title_FR";

	public DispoParse() {
		// Do nothing...
	}

	public void process(String type) {
		if (type.equals("fulldb") || type.equals("atcdb")) {
			// Initialize the database
			if (type.equals("fulldb"))
				initSqliteDB("rose_db_new_full.db");
			else if (type.equals("atcdb"))
				initSqliteDB("rose_db_new_atc_only.db");
			else
				return;
			// Process atc map
			getAtcMap();
			// Get SL map
			getSLMap();
			// Enhance SL map with information on"Abgabekategorie"
			enhanceFlags();
			// Process likes
			processLikes();
			// Process CSV file and generate Sqlite DB
			generateFullSQLiteDB(type);
		} else if (type.equals("quick")) {
			// Generate gln to stock map (csv file)
			generatePharmaToStockCsv();
		}
	}

	private void initSqliteDB(String db_name) {
		try {
			// Initializes org.sqlite.JDBC driver
			Class.forName("org.sqlite.JDBC");

			// Touch db file if it does not exist
			String db_url = System.getProperty("user.dir") + "/output/" + db_name;
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
				"stock INTEGER, price TEXT, availability TEXT, supplier TEXT, likes INTEGER, " +
				"replaceean TEXT, replacepharma TEXT, offmarket TEXT, " +
				"flags TEXT, npl TEXT, publicprice TEXT, exfprice TEXT, dlkflag TEXT, title_FR TEXT);";
	}

	private void createArticleDB()  {
		try {
			// Create SQLite database
			stat.executeUpdate("DROP TABLE IF EXISTS rosedb;");
			stat.executeUpdate("CREATE TABLE rosedb " + mainTable());
			// Insert statement
			m_prep_rosedb = conn.prepareStatement("INSERT INTO rosedb VALUES (null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
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
			m_prep_rosedb.setString(14, article.replace_ean_code);
			m_prep_rosedb.setString(15, article.replace_pharma_code);
			m_prep_rosedb.setBoolean(16, article.off_the_market);
			m_prep_rosedb.setString(17, article.flags);
			m_prep_rosedb.setBoolean(18, article.npl_article);
			m_prep_rosedb.setString(19, article.public_price);
			m_prep_rosedb.setString(20, article.exfactory_price);
			m_prep_rosedb.setBoolean(21, article.dlk_flag);
			m_prep_rosedb.setString(22, article.pack_title_FR);
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

	private void processLikes() {
		// Read like_db (if it exists)
		File file = new File(Constants.DIR_ZURROSE + "/" + Constants.CSV_LIKE_DB_ZR);
		if (!file.exists())
			m_ean_likes = new HashMap<>();
		else
			m_ean_likes = readLikeMap(file.getAbsolutePath());
		// List csv files in DIR_ZURROSE
		File[] list_of_files = new File(Constants.DIR_ZURROSE).listFiles();
		String like_db_str = "";
		// Read all files and extract id, timestamp and eans
		try {
			if (list_of_files!=null) {
				for (File f : list_of_files) {
					String file_name = f.getName();
					if (file_name.endsWith("csv") && file_name.startsWith("rose")) {
						// Get timestamp and id
						String[] tokens = file_name.substring(0, file_name.indexOf(".")).split("_");
						String user_id = "00000";
						String time_stamp = "";
						if (tokens.length == 2)
							time_stamp = tokens[1];
						else if (tokens.length == 3) {
							time_stamp = tokens[1];
							user_id = tokens[2];
						}
						FileInputStream fis = new FileInputStream(f);
						BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
						String eancode;
						while ((eancode = br.readLine()) != null) {
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
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		// - Save like_db
		FileOps.writeToFile(like_db_str, Constants.DIR_ZURROSE, Constants.CSV_LIKE_DB_ZR);
	}

	private void generateFullSQLiteDB(String db_type) {
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
		 *  13: EAN
		 *  14: Ersatzarztartikelnummer
		 *  15: ausser Handel (a.H.)
		 *  16: NPL-Artikel
		 *  17: Publikumspreis
		 *  18: Disponieren
		 *  19: Gruppe für sonstige Zuschläge -> DLK-flag
		 *  20: Artikelbezeichnung FR
		 */
		// Start timer
		long startTime = System.currentTimeMillis();

		int num_rows = 0;
		try {
			File file = new File(Constants.DIR_ZURROSE + "/" + Constants.CSV_FILE_FULL_DISPO_ZR);
			if (!file.exists())
				return;
			FileInputStream fis = new FileInputStream(file.getAbsoluteFile());
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, "Cp1252"));
			String line;
			List<Article> list_of_articles = new ArrayList<>();
			while ((line = br.readLine())!=null && num_rows<200000) {
				String token[] = line.split(";", -1);
				String parsed_size = "";
				String parsed_unit = "";
				if (num_rows>0 && token.length>19) {
					Article article = new Article();
					// Pharmacode
					if (token[0]!=null) {
						if (token[0].matches("[0-9]+"))
							article.pharma_code = token[0];
						else
							continue;
					}
					// Artikelname
					if (token[1]!=null)
						article.pack_title = token[1];
					// Strichcode
					if (token[2]!=null) {
						article.ean_code = token[2];
						if (article.ean_code.length()==13) {
							String ean = article.ean_code;
							article.regnr = ean.substring(4, 9);
							// Flags
							if (m_flags_map.containsKey(article.regnr)) {
								String flags = m_flags_map.get(article.regnr);
								String flag[] = flags.split(";", -1);
								// 0: O,G | 1: SL, SB 10%/20% | 2:A, B, C, ...
								if (flag.length==4) {
									String swissmedic_cat = flag[2];
									if (flag[1].contains("Y"))
										article.flags = swissmedic_cat + ", SL, SB 20%";
									else if (flag[1].contains("N"))
										article.flags = swissmedic_cat + ", SL, SB 10%";

									String org_gen_code = flag[0];
									if (!org_gen_code.isEmpty())
										article.flags += ", " + org_gen_code;
								}
							}
							if (m_ean_likes!=null && m_ean_likes.containsKey(ean))
								article.likes = m_ean_likes.get(ean);	// LIKES!!!
							else
								article.likes = 0;
						}
					}
					// Ausstand bis
					if (token[3]!=null)
						article.availability = token[3];
					// Packungsihnalt
					if (token[5]!=null) {    // SIZE = Packungsgrösse or Packungsinhalt
						article.pack_size = (int) (Float.parseFloat(token[5]));
						if (article.pack_size==0) {
							article.pack_size = parseSizeFromTitle(token[1]);
						}
					}
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
						if (article.pack_unit.isEmpty() || article.pack_unit.equals("0"))
							article.pack_unit = parsed_unit = parseUnitFromTitle(token[1]);
					}
					// Rose Basispreis
					if (token[12]!=null) {
						if (token[12].matches("^[0-9]+(\\.[0-9]{1,2})?$"))
							article.rose_base_price = String.format("%.2f", Float.parseFloat(token[12]));
					}
					// Ersatzartikel EAN
					if (token[13]!=null) {
						if (token[13].length()==13)
							article.replace_ean_code = token[13];
					}
					// Ersatzartikel Pharma
					if (token[14]!=null)
						article.replace_pharma_code = token[14];
					// Ausser Handel
					if (token[15]!=null)
						article.off_the_market = token[15].toLowerCase().equals("ja");
					// NPL-Artikel
					if (token[16]!=null)
						article.npl_article = token[16].toLowerCase().equals("ja");
                    /*
					if (token[17]!=null) {
						if (token[17].matches("^[0-9]+(\\.[0-9]{1,2})?$"))
							article.public_price = String.format("%.2f", Float.parseFloat(token[17]));
					}
					*/
					// Public and exfactory prices from BAG file
					if (!article.ean_code.isEmpty()) {
						String ean = article.ean_code;
						article.public_price = m_bag_public_price_map.containsKey(ean) ? m_bag_public_price_map.get(ean) : "";
						article.exfactory_price = m_bag_exfacto_price_map.containsKey(ean) ? m_bag_exfacto_price_map.get(ean) : "";
					}
					// Extract DLK-flag
					if (token[19]!=null)
						article.dlk_flag = token[19].toLowerCase().contains("100%");    // -> true
					// Extract Artikelbezeichnung FR
					if (token[20]!=null)
						article.pack_title_FR = token[20];
					else
						article.pack_title_FR = "";

					if (num_rows % 100 == 0) {
						System.out.println(num_rows + " [" + db_type + "] "
								+ " -> " + article.ean_code
								+ " / " + article.pack_title + " / " + article.pack_title_FR
								+ " / size = [" + article.pack_size + ", " + parsed_size + "]"
								+ " / unit = [" + article.pack_unit + ", " + parsed_unit + "]"
								+ " / pp = " + article.public_price
								+ " / efp = " + article.exfactory_price
								+ " / flags = " + article.flags
								+ " / dlk = " + article.dlk_flag);
					}

					list_of_articles.add(article);

					if (db_type.equals("fulldb")) {
						addArticleDB(article);
					} else if (db_type.equals("atcdb")) {
						// Add only products which have an ATC code
						if (!article.atc_code.isEmpty() && !article.atc_code.equals("k.A."))
							addArticleDB(article);
					}
				}
				num_rows++;
			}
			complete();
			long stopTime = System.currentTimeMillis();
			System.out.println("\nProcessing " + list_of_articles.size() + " articles in " + (stopTime-startTime)/1000.0f + " sec\n");

			br.close();

			int i = 0;
			for (String u : unit_set) {
				i++;
				System.out.println(i + " -> " + u);
			}

		} catch (Exception e) {
			System.err.println(">> Error in processCsv on row " + num_rows);
		}
	}

	private void generatePharmaToStockCsv() {
		// Read stock information from zurrose (short) file and voigt file
		// Format: gln -> { stock_rose, stock_voigt }
		TreeMap<String, int[]> map_pharma_to_stock = new TreeMap<>();

		try {
			File file = new File(Constants.DIR_ZURROSE + "/" + Constants.CSV_FILE_DISPO_ZR);
			if (!file.exists())
				return;
			FileInputStream fis = new FileInputStream(file.getAbsoluteFile());
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, "Cp1252"));
			String line;
			int num_rows = 0;
			while ((line = br.readLine())!=null) {
				String token[] = line.split(";", -1);
				if (num_rows>0 && token.length>8) {
					/*
						token[0] -> Pharmacode
						token[2] -> GTIN/Strichcode
						token[8] -> Lagerbestand
                    */
					if (token[0] != null) {
						if (token[0].length() == 7) {
							String pharma = token[0];
							// Rose stock
							int rose_stock = 0;
							if (token[8] != null && !token[8].isEmpty())
								rose_stock = (int) (Float.parseFloat(token[8]));
							int[] s = new int[2];
							s[0] = rose_stock;
							s[1] = 0;
							map_pharma_to_stock.put(pharma, s);
						}
					}
				}
				num_rows++;
			}
		} catch (Exception e) {
			System.err.println(">> Error in generateGlnToStickCsv -> " + Constants.CSV_FILE_DISPO_ZR);
		}

		try {
			File file = new File(Constants.DIR_ZURROSE + "/" + Constants.CSV_FILE_VOIGT_ZR);
			if (!file.exists())
				return;
			FileInputStream fis = new FileInputStream(file.getAbsoluteFile());
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, "utf-8"));
			String line;
			int num_rows = 0;
			while ((line = br.readLine())!=null) {
				String token[] = line.split(";", -1);
				if (num_rows>0 && token.length>1) {
					/*
					Altes VOIGT File:
						token[0] -> Material
						token[1] -> Artikeltext
						token[2] -> Lagerbestand
						token[3] -> Basismengeeinheit
						token[4] -> GTIN
						token[5] -> Pharmacode

					Neues VOIGT File:
						token[0] -> Pharmacode
						token[1] -> Lagerbestand
					*/

					if (token[0] != null) {
						if (token[0].length() == 7) {
							String pharma = token[0];

							// Extract voigt stock
							int voigt_stock = 0;
							if (token[1] != null && !token[1].isEmpty())
								voigt_stock = (int) (Float.parseFloat(token[1]));
							int[] s = new int[2];
							if (map_pharma_to_stock.containsKey(pharma))
								s = map_pharma_to_stock.get(pharma);  // Get saved stock
							else
								s[0] = 0;   // No zur Rose stock
							s[1] = voigt_stock;
							map_pharma_to_stock.put(pharma, s);
						}
					}
				}
				num_rows++;
			}
		} catch (Exception e) {
			System.err.println(">> Error in generateGlnToStickCsv -> " + Constants.CSV_FILE_VOIGT_ZR);
		}

		String stock_str = "";
		for (Map.Entry<String, int[]> entry : map_pharma_to_stock.entrySet()) {
			int[] s = entry.getValue();
			stock_str += entry.getKey() + ";" + s[0] + ";" + s[1] + "\n";
		}
		String out_dir = /*System.getProperty("user.dir") +*/ Constants.DIR_OUTPUT;
		FileOps.writeToFile(stock_str, out_dir, Constants.CSV_STOCK_INFO_ZR);
	}

	private void unitParse(String unit) {
		String[] token = unit.split(" ");
		if (token.length>1) {
			unit_set.add(token[1].trim());
		}
	}

	/**
	 * Extracts dosage/unit/prescription strength from package title
	 * @param pack_title
	 * @return extracted dosage
	 */
	private String parseUnitFromTitle(String pack_title) {
		String dosage = "";
		Pattern p = Pattern.compile("(\\d+)(\\.\\d+)?\\s*(ml|mg|g)");
		Matcher m = p.matcher(pack_title);
		if (m.find()) {
			dosage = m.group(1);
			String q = m.group(2);
			if (q!=null && !q.isEmpty()) {
				dosage += q;
			}
			dosage += (" " + m.group(3));
		}
		return dosage;
	}

	/**
	 * Extracts package size from title
	 *
	 */
	private int parseSizeFromTitle(String pack_title) {
		String size = "";
		Pattern p = Pattern.compile("(\\d+)\\s*(Stk)");
		Matcher m = p.matcher(pack_title);
		if (m.find()) {
			size = m.group(1);
		}
		if (!size.isEmpty())
			return Integer.valueOf(size);
		else
			return 0;
	}

	private void getSLMap() {
		m_flags_map = new HashMap<>();
		m_bag_exfacto_price_map = new HashMap<>();
		m_bag_public_price_map = new HashMap<>();

		String tag_content = "";

		XMLInputFactory xml_factory = XMLInputFactory.newInstance();
		// Next instruction allows to read "escape characters", e.g. &amp;
		xml_factory.setProperty("javax.xml.stream.isCoalescing", true);  // Decodes entities into one string

		try {
			InputStream in = new FileInputStream(Constants.FILE_PREPARATIONS_XML);
			XMLStreamReader reader = xml_factory.createXMLStreamReader(in, "UTF-8");

			String swissmedicno5 = "";
			String flagsb20 = "";
			String gtin = "";
			String state = "";

			int num_rows = 0;
			// Keep moving the cursor forward
			while (reader.hasNext()) {
				int event = reader.next();
				// Check if the element that the cursor is currently pointing to is a start element
				switch (event) {
					case XMLStreamConstants.START_DOCUMENT:
						break;
					case XMLStreamConstants.START_ELEMENT:
						switch (reader.getLocalName().toLowerCase()) {
							case "preparations":
								break;
							case "exfactoryprice":
								state = "exfactory";
								break;
							case "publicprice":
								state = "public";
								break;
						}
						break;
					case XMLStreamConstants.CHARACTERS:
						tag_content = reader.getText().trim();
						break;
					case XMLStreamConstants.END_ELEMENT:
						switch (reader.getLocalName().toLowerCase()) {
							case "preparation":
								m_flags_map.put(swissmedicno5, flagsb20);    // These meds are all in the SL-list
								num_rows++;
								System.out.print("\rProcessing BAG preparations file... " + num_rows);
								break;
							case "swissmedicno5":
								swissmedicno5 = tag_content;
								break;
							case "orggencode":
								flagsb20 = tag_content;
								break;
							case "flagsb20":
								flagsb20 += ";" + tag_content;
								break;
							case "gtin":
								gtin = tag_content;
								break;
							case "price":
								if (state.equals("exfactory")) {
									String exf_price = tag_content;
									if (gtin!=null && gtin.length() == 13) {
										m_bag_exfacto_price_map.put(gtin, exf_price);
									}
								} else if (state.equals("public")) {
									String pub_price = tag_content;
									if (gtin!=null && gtin.length() == 13) {
										m_bag_public_price_map.put(gtin, pub_price);
									}
								}
								break;
						}
						break;
				}
			}
			// Test
			/*
			System.out.println("");
			for (Map.Entry<String, String> s : m_flags_map.entrySet()) {
				if (s.getKey().equals("55378"))
					System.out.println(s.getKey() + " -> " + s.getValue());
			}
			*/
		} catch(XMLStreamException | FileNotFoundException e) {
			e.printStackTrace();
		}
		System.out.println("");
	}

	private void enhanceFlags() {
		// m_flags_map.put(swissmedicno5, flagsb20);
		try {
			// Load Swissmedic xls file
			FileInputStream packages_file = new FileInputStream(Constants.FILE_PACKAGES_XLSX);
			// Get workbook instance for XLSX file (XSSF = Horrible SpreadSheet Format)
			XSSFWorkbook packages_workbook = new XSSFWorkbook(packages_file);
			// Get first sheet from workbook
			XSSFSheet packages_sheet = packages_workbook.getSheetAt(0);
			// Iterate through all rows of first sheet
			Iterator<Row> rowIterator = packages_sheet.iterator();
			//
			int num_rows = 0;
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				if (num_rows > 5) {
					String swissmedic_no5 = ""; 	// SwissmedicNo5 registration number (5 digits)
					String swissmedic_cat = "";    	// Swissmedic category
					// 0: Zulassungsnummer, 13: Abgabekategorie Packung, 22: Betäubungsmittelhaltigen Präparaten
					if (row.getCell(0) != null)
						swissmedic_no5 = String.format("%05d", (int) (row.getCell(0).getNumericCellValue()));    // Swissmedic registration number (5 digits)
					if (row.getCell(13) != null)
						swissmedic_cat = ExcelOps.getCellValue(row.getCell(13));    // Abgabekategorie Packung
					if (row.getCell(22) != null) {
						String narcotic = ExcelOps.getCellValue(row.getCell(22));
						if (narcotic.equals("a") && swissmedic_cat.equals("A")) {
							swissmedic_cat = "A+";
						}
					}
					if (m_flags_map.containsKey(swissmedic_no5)) {
						String flags = m_flags_map.get(swissmedic_no5);
						if (!flags.endsWith(";"))
							flags += ";" + swissmedic_cat + ";";
						m_flags_map.put(swissmedic_no5, flags);
					}
				}
				num_rows++;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void getAtcMap() {
		m_atc_map = new TreeMap<>();

		try {
			CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(Constants.FILE_EPHA_ATC_CODES_CSV), "UTF-8"));
			List<String[]> myEntries = reader.readAll();
			for (String[] s : myEntries) {
				if (s.length>2) {
					String atc_code = s[0];
					String atc_class = s[1];
					m_atc_map.put(atc_code, atc_class);
				}
			}
			reader.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private void processXlsx() {
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
