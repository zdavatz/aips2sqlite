package com.maxl.java.aips2sqlite;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.Assert;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Construct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;

import com.aliasi.spell.JaccardDistance;
import com.aliasi.spell.JaroWinklerDistance;
import com.aliasi.tokenizer.IndoEuropeanTokenizerFactory;
import com.aliasi.tokenizer.TokenizerFactory;
import com.maxl.java.shared.User;

public class TakedaParse {
	
	Map<String, User> m_takeda_sap_user_map = null;
	List<RefdataItem> m_refdata_list = null;
	Map<String, User> m_refdata_gln_user_map = null;
	Map<String, User> m_medreg_gln_user_map = null;
	Map<String, User> m_yaml_user_map = null;
	Map<String, String> m_gln_sap_map = null;
	
	public TakedaParse() {
		
	}

	public void process() {
		try {
			parseTakedaFile();			
			parseRefDataFile();
			parseMedregFiles();
			parseYweseeYamlFile();
		} catch(XMLStreamException | IOException e) {
			e.printStackTrace();
		}
		generateGlnSapMap();
		exportCsvFile();
		// TODO: replace with JUnit test framework
		// test();		
		// testJaccard();
	}
	
	@Test
	public void test() {
		if (m_gln_sap_map!=null) {	
			Assert.assertEquals("231045", m_gln_sap_map.get("7601001324725"));
			Assert.assertEquals("291167", m_gln_sap_map.get("7601001398078"));
			Assert.assertEquals("262000", m_gln_sap_map.get("7601001380479"));
			Assert.assertEquals("243876", m_gln_sap_map.get("7601001370296"));
			Assert.assertEquals("234910", m_gln_sap_map.get("7601002080446"));
			Assert.assertEquals("233874", m_gln_sap_map.get("7601001375857"));
		}
	}
	
	private void testJaccard() {
        TokenizerFactory tokenizerFactory = IndoEuropeanTokenizerFactory.INSTANCE; //CharacterTokenizerFactory.INSTANCE;
        JaccardDistance jaccard = new JaccardDistance(tokenizerFactory);
        JaroWinklerDistance jaro_winkler = JaroWinklerDistance.JARO_WINKLER_DISTANCE;
        System.out.println(jaccard.proximity("Sidler S", "Trachsel Rudolf"));
        System.out.println(jaccard.proximity("R Champion", "Champion Rolf"));
        System.out.println(jaccard.proximity("B Arnet", "Arnet Bernhard"));
        System.out.println(jaccard.proximity("Ch. & U. Köppel", "Christian Köppel"));
        System.out.println(jaro_winkler.proximity("Nüesch Hans-Jakob", "HJ Nüesch"));
	}
	
	private String replaceUmlauteAndChars(String str) {
		str = str.replaceAll("ph", "f");
		str = str.replaceAll("è", "e").replaceAll("é", "e").replaceAll("à", "a");
		return str.replaceAll("ä", "ae").replace("ö", "oe").replaceAll("ü", "ue");
	}
	
	private class RetPair {
		String first;
		String second;		
		
		RetPair(String first, String second) {
			this.first = first;
			this.second = second;
		}
	}
	
	private RetPair retrieveGlnCodeNat(User user) {
		int index = -1;
		double prox = 0.0;

		JaroWinklerDistance jaro_winkler = JaroWinklerDistance.JARO_WINKLER_DISTANCE;
        TokenizerFactory tokenizerFactory = IndoEuropeanTokenizerFactory.INSTANCE;
        JaccardDistance jaccard = new JaccardDistance(tokenizerFactory);

        /*
         * Phase 1: Parse
         */
		// Clean name1 from Takeda SAP file
		String name1 = user.name1;
		if (name1!=null) {
			name1 = name1.toLowerCase().replaceAll("dr\\.|med\\.|méd\\.|[\\s.\\.]med\\s|dres\\.|docteur|docteuer|pd\\s|professeur|prof\\.|frau|herr\\s|fmh|\\.|&", "").trim();
			name1 = name1.replaceAll("\\s+|-", " ").trim();
			name1 = replaceUmlauteAndChars(name1);
		}
		// Clean name2 from Takeda SAP file
		String name2 = user.name2;
		if (name2!=null) {
			name2 = name2.toLowerCase().replaceAll("dr\\.|med\\.|méd\\.|[\\s\\.]med\\s|dres\\.|docteur|docteuer|pd\\s|professeur|prof\\.|frau|herr\\s|fmh|\\.|&", "").trim();
			name2 = name2.replaceAll("\\s+|-", " ").trim();
			name2 = replaceUmlauteAndChars(name2);
		}
		// Clean street from Takeda SAP file
		String street = user.street;
		if (street!=null) {
			street = replaceUmlauteAndChars(street.toLowerCase().trim());
		}
		List<String> list_of_names = Arrays.asList(name1, name2);
		String zip = user.zip;
        
		/*
		 * Phase 2: Match
		 */
        int i=0;
        for (RefdataItem ref : m_refdata_list) {
			String status = ref.status;
			if (status.equals("A") || status.equals("I")) {
				String p_type = ref.p_type;
				if (p_type.equals("NAT")) {
					for (String name : list_of_names) {
						if (!name.isEmpty()) {
							// Tokenize string
							String last_name = replaceUmlauteAndChars(ref.descr1.toLowerCase().replaceAll("\\.", "").replaceAll("-", " ")).trim();
							String first_name = replaceUmlauteAndChars(ref.descr2.toLowerCase().replaceAll("\\.", "").replaceAll("-", " ")).trim();
							String full_name = first_name + " " + last_name;					
							// Simple check first
							String name_tokens[] = name.split(" ");
							int match = 0;
							for (String n : name_tokens) {
								String first1 = first_name.split(" ")[0];
								if (n.equals(first_name) || n.equals(last_name) || n.equals(first1))
									match++;
								if (match>1) {
									// System.out.println("REF*-> " + name + " / " + full_name);
									prox = 1.0;	// Make sure that the next step is ignored.
									index = i;
									break;
								}
							}
							// Calculate edit distances
							double jc_prox = jaccard.proximity(full_name, name);
							double jw_prox = jaro_winkler.proximity(full_name, name);
							// Analyze only closer matches...
							if (jc_prox>=0.5 || (jc_prox>0.1 && jw_prox>0.33)) {	
								if (ref.zip!=null && ref.zip.equals(zip))
									jc_prox += 0.5;	
								String[] tokens = name.split(" ");
								if (!first_name.isEmpty() && tokens.length>1) {
									if (tokens[0].length()==1 && tokens[0].charAt(0)==first_name.charAt(0))
										jc_prox += 0.5;
								}
								if (jc_prox > prox) {
									// System.out.println("REF -> " + name + " / " + full_name);
									prox = jc_prox;
									index = i;
								}
							}
						}
					}
				}
			}
			i++;
		}	        
        
		// Loop through med reg file
		String gln = "";
		if (prox<1.0) {
			prox = 0.0;
			for (Map.Entry<String, User> entry : m_medreg_gln_user_map.entrySet()) {
				User u = entry.getValue();
				if (u.is_human) {
					for (String name : list_of_names) {
						String last_name = u.last_name.toLowerCase();
						String first_name = u.first_name.toLowerCase();
						String full_name = last_name + " " + first_name;
						// String medreg_street = replaceUmlaute(u.street.toLowerCase());
						double jw_prox = jaccard.proximity(replaceUmlauteAndChars(full_name), replaceUmlauteAndChars(name));
						if (jw_prox>=0.33) {
							if (u.zip!=null && u.zip.equals(zip))
								jw_prox += 0.5;
							if (jw_prox > prox) {
								// System.out.println("MED -> " + name + " / " + full_name);
								prox = jw_prox;
								gln = entry.getKey();	
							}
						}
					}
				}
			}
		}
		
		if (prox<2.0) {
			if (gln.isEmpty()) {
				if (index>=0) {
					RefdataItem ref = m_refdata_list.get(index);
					if (ref!=null)
						gln = ref.gln_code;
				}
			}
		}
		
		// Phase 3: Filter
		RetPair ret = new RetPair(gln, "NO");
		if (!gln.isEmpty()) {
			if (m_refdata_gln_user_map.containsKey(gln)) {
				// Filter last name
				{
					String last_name = m_refdata_gln_user_map.get(gln).last_name;
					last_name = last_name.toLowerCase().replaceAll("\\.", "").replaceAll("-", " ");
					last_name = last_name.split(" ")[0];
					last_name = replaceUmlauteAndChars(last_name);
					ret.second = "FP";
					if (!name1.isEmpty() && name1.contains(last_name))
						ret.second = "..";
					else if (!name2.isEmpty() && name2.contains(last_name))
						ret.second = "..";
					else if (!name1.isEmpty() && name1.startsWith(last_name))
						ret.second = "??";
					else if (!name2.isEmpty() && name2.startsWith(last_name))
						ret.second = "??";
				}
				// Filter first name
				{
					if (ret.second.equals("..")) {
						String first_name = m_refdata_gln_user_map.get(gln).first_name;
						first_name = first_name.toLowerCase().replaceAll("\\.", "").replaceAll("-", " ");
						first_name = first_name.split(" ")[0];
						first_name = replaceUmlauteAndChars(first_name);
						ret.second = "??";	// A wrong first name is less important...
						if (!name1.isEmpty() && name1.contains(first_name))
							ret.second = "..";
						else if (!name2.isEmpty() && name2.contains(first_name))
							ret.second = "..";
						else if (!name1.isEmpty() && name1.charAt(0)==first_name.charAt(0))
							ret.second = "??";
						else if (!name2.isEmpty() && name2.charAt(0)==first_name.charAt(0))
							ret.second = "??";
					}
				}
				if (ret.second.equals("..")) {
					// Double-check zip data
					String user_zip = m_refdata_gln_user_map.get(gln).zip;
					if (user_zip!=null && !user_zip.isEmpty()) {					
						if (!user_zip.equals(zip))
							ret.second = "??";
					}
				}
			}
		}
		
		return ret;
	}
	
	private RetPair retrieveGlnCodeJur(User user) {
		int index = -1;
		double prox = 0.0;

		JaroWinklerDistance jaro_winkler = JaroWinklerDistance.JARO_WINKLER_DISTANCE;
        TokenizerFactory tokenizerFactory = IndoEuropeanTokenizerFactory.INSTANCE;
        JaccardDistance jaccard = new JaccardDistance(tokenizerFactory);

        /*
         * Phase 1: Parse
         */
		// Clean name1 from Takeda SAP file
		String sap_name1 = user.name1.toLowerCase().replaceAll("-", " ");
		String sap_name2 = user.name2.toLowerCase().replaceAll("-", " ");
		String sap_zip = user.zip;
		String sap_street = user.street.toLowerCase();
		
		boolean perfect_match = false;
		
		int i=0;		
		for (RefdataItem ref : m_refdata_list) {
			String status = ref.status;
			if (status.equals("A") || status.equals("I")) {
				String p_type = ref.p_type;				
				if (p_type.equals("JUR")) {
					String ref_name1 = ref.descr1.toLowerCase().replaceAll("-", " ");
					String ref_name2 = ref.descr2.toLowerCase().replaceAll("-", " ");
					String ref_zip = ref.zip;
					String ref_street = ref.street;
					if (ref_street!=null)
						ref_street = ref_street.toLowerCase() + " " + ref.number;
					boolean possible_match = false;
					if (ref_name1.contains(sap_name1)) {
						// Simple check first
						possible_match = true;
					} else {
						// Less simple check... edit distances
						double jc_prox = jaccard.proximity(sap_name1, ref_name1);
						double jw_prox = jaro_winkler.proximity(sap_name1, ref_name1);
						if (jc_prox>=0.5 || (jc_prox>0.1 && jw_prox>0.33)) {	
							possible_match = true;
						}
					}
					if (possible_match) {
						if (ref_zip!=null && ref_zip.equals(sap_zip)) {
							if (ref_street!=null) {
								if (ref_street.equals(sap_street)) {
									// Perfect match... we're happy!
									prox = 1.0;	// Make sure that we can continue
									index = i;
									break;
								} else {
									// The street match is not perfect...
									sap_street = replaceUmlauteAndChars(sap_street);
									ref_street = replaceUmlauteAndChars(ref_street);
									sap_street = sap_street.replaceAll("-|\\.", "");
									ref_street = ref_street.replaceAll("-|\\.", "");
									double jc_prox = jaccard.proximity(sap_street, ref_street);
									double jw_prox = jaro_winkler.proximity(sap_name1, ref_name1);
									if (jc_prox>=0.5 || (jc_prox>0.1 && jw_prox>0.5)) {
										// System.out.println("REF -> (" + jc_prox + "," + jw_prox + ") " + sap_street + " / " + ref_street);
										prox = jc_prox;
										index = i;
										perfect_match = true;
									}
								}
							} 
						} 
					}
				}
			}
			i++;
		}
		
		// Phase 2: Match	
		String gln = "";
		if (prox<2.0) {
			if (gln.isEmpty()) {
				if (index>=0) {
					RefdataItem ref = m_refdata_list.get(index);
					if (ref!=null)
						gln = ref.gln_code;
				}
			}
		}
		// Phase 3: Filter
		RetPair ret = new RetPair(gln, "NO");	
		if (!gln.isEmpty()) {
			if (perfect_match)
				ret.second = "..";
			else
				ret.second = "??";
		}
		
		return ret;
	}
	
	private void generateGlnSapMap() {
		m_gln_sap_map = new TreeMap<String, String>();
		//		
		int num_entries = 1;
		// Loop trough Takeda's SAP to customer map
		for (Map.Entry<String, User> entry : m_takeda_sap_user_map.entrySet()) {
			String sap_id = entry.getKey();
			User sap_entry = entry.getValue();
			// First process all "NAT" (natürliche Personen)
			if (/*sap_id.startsWith("2742")*/ true) {
				if (sap_entry.is_human) {
					// Next instruction is the heavy-weight!
					RetPair ret_pair = retrieveGlnCodeNat(sap_entry);
					// Extract gln code
					String gln_code = ret_pair.first;
					if (gln_code!=null && !gln_code.isEmpty()) {
						String last_name = m_refdata_gln_user_map.get(gln_code).last_name;
						String first_name = m_refdata_gln_user_map.get(gln_code).first_name;
						System.out.println(String.format("%4d", num_entries) + " H | " + sap_id + " -> " + ret_pair.second + " | "
								+ gln_code + " | " 
								+ sap_entry.name1 + " + " + sap_entry.name2 + " | "
								+ first_name + " " + last_name);
					} else {
						System.out.println(String.format("%4d", num_entries) + " H | " + sap_id + " -> " + ret_pair.second + " | " 
								+ sap_entry.name1 + " + " + sap_entry.name2);
					}
					num_entries++;
					m_gln_sap_map.put(gln_code, sap_id);
				} else {
					// Next instruction is the heavy-weight!
					RetPair ret_pair = retrieveGlnCodeJur(sap_entry);
					// Extract gln code
					String gln_code = ret_pair.first;
					if (gln_code!=null && !gln_code.isEmpty()) {
						String name1 = m_refdata_gln_user_map.get(gln_code).name1;
						// String name2 = m_refdata_gln_user_map.get(gln_code).name2;
						String city = m_refdata_gln_user_map.get(gln_code).city;
						System.out.println(String.format("%4d", num_entries) + " C | " + sap_id + " -> " + ret_pair.second + " | "
								+ gln_code + " | " 
								+ sap_entry.name1 + " (" + sap_entry.city + ") | "
								+ name1 + " (" + city + ")");
					} else {
						System.out.println(String.format("%4d", num_entries) + " C | " + sap_id + " -> " + ret_pair.second + " | " 
								+ sap_entry.name1 + " (" + sap_entry.city + ")");
					}
					num_entries++;
					m_gln_sap_map.put(gln_code, sap_id);
				}
			}
		}
		System.out.println("Number of processed entries: " + (num_entries-1));
	}
	
	void exportCsvFile() {
		String csv_file = "";
		for (Map.Entry<String, String> entry : m_gln_sap_map.entrySet()) {
			String gln_code = entry.getKey();
			String sap_id = entry.getValue();
			String last_name = "";
			String first_name = "";
			String sd = "";
			String bm = "";
			String specialities = "";
			String status = "";
			// Medreg file
			if (m_medreg_gln_user_map.containsKey(gln_code)) {
				last_name = m_medreg_gln_user_map.get(gln_code).last_name;
				first_name = m_medreg_gln_user_map.get(gln_code).first_name;
				sd = m_medreg_gln_user_map.get(gln_code).selbst_disp ? "ja" : "nein";
				bm = m_medreg_gln_user_map.get(gln_code).bet_mittel ? "ja" : "nein";
			}
			// Yaml
			if (m_yaml_user_map.containsKey(gln_code)) {
				specialities = m_yaml_user_map.get(gln_code).specialities;
			}
			// Refdata
			if (m_refdata_gln_user_map.containsKey(gln_code)) {
				status = m_refdata_gln_user_map.get(gln_code).status.equals("A") ? "ja" : "nein";
			}
			csv_file += sap_id + ";" 
					+ gln_code + ";" 
					+ last_name + " " + first_name + ";"
					+ sd + ";" 
					+ bm + ";" 
					+ specialities + ";" 
					+ status 
					+ System.getProperty("line.separator"); 
		}		
		FileOps.writeToFile(csv_file, Constants.DIR_OUTPUT, "takeda_clean.csv");
	}
	
	private void parseTakedaFile() {
		m_takeda_sap_user_map = new TreeMap<String, User>();
		
		System.out.print("Processing Takeda SAP file... ");
		XSSFSheet takeda_sap_excel = ExcelOps.getSheetsFromFile(Constants.DIR_TAKEDA + Constants.FILE_SAP_TAKEDA, 0);		
		Iterator<Row> rowIterator = takeda_sap_excel.iterator();
		int num_rows = 0;
		while (rowIterator.hasNext()) {
			Row row = rowIterator.next();
			if (num_rows>0) {
				User cust = new User();				
				
				if (row.getCell(1)!=null)
					cust.sap_id = row.getCell(1).getStringCellValue();
				if (row.getCell(3)!=null)
					cust.name1 = row.getCell(3).getStringCellValue();
				if (row.getCell(4)!=null)
					cust.name2 = row.getCell(4).getStringCellValue();
				if (row.getCell(5)!=null)
					cust.city = row.getCell(5).getStringCellValue();
				if (row.getCell(6)!=null)
					cust.zip = row.getCell(6).getStringCellValue();
				if (row.getCell(8)!=null)
					cust.is_human = row.getCell(8).getStringCellValue().equals("CB");
				if (row.getCell(9)!=null)
					cust.street = row.getCell(9).getStringCellValue();
				if (row.getCell(10)!=null)
					cust.country = row.getCell(10).getStringCellValue();
					
				if (cust.sap_id!=null && !cust.sap_id.isEmpty()) {
					m_takeda_sap_user_map.put(cust.sap_id, cust);
				}
			}
			num_rows++;
			System.out.print("\rProcessing Takeda SAP file... " + num_rows);
		}
		System.out.println("");
	}
	
	private void parseRefDataFile() throws XMLStreamException, FileNotFoundException {
		RefdataItem refdata_item = null;
		m_refdata_gln_user_map = new HashMap<String, User>();
		String tag_content = null;
		
		System.out.print("Processing Refdata Partner file... ");
		XMLInputFactory factory = XMLInputFactory.newInstance();
		InputStream in = new FileInputStream(Constants.FILE_REFDATA_PARTNER_XML);
		XMLStreamReader reader = factory.createXMLStreamReader(in);
		int num_rows = 0;
		
		// Keep moving the cursor forward
		while (reader.hasNext()) {
			int event = reader.next();
			// Check if the element that the cursor is currently pointing to is a start element
			switch(event) {
			case XMLStreamConstants.START_DOCUMENT:
				m_refdata_list = new ArrayList<>();
				break;
			case XMLStreamConstants.START_ELEMENT:
				switch(reader.getLocalName().toLowerCase()) {
				case "partner":
					m_refdata_list = new ArrayList<>();
					break;
				case "item":
					refdata_item = new RefdataItem();
					break;
				case "role":
					break;
				}
				break;
			case XMLStreamConstants.CHARACTERS:
				tag_content = reader.getText().trim();
				break;
			case XMLStreamConstants.END_ELEMENT:
				switch (reader.getLocalName().toLowerCase()) {
				case "item":
					// Used for parsing and matching
					m_refdata_list.add(refdata_item);
					// Used for status info updates
					User user = new User();					
					user.status = refdata_item.status;
					user.is_human = refdata_item.p_type.equals("NAT") ? true : false;
					if (user.is_human) {
						user.last_name = refdata_item.descr1;
						user.first_name = refdata_item.descr2;
					} else {
						user.name1 = refdata_item.descr1;
						user.name2 = refdata_item.descr2;
						user.street = refdata_item.street;
						user.number = refdata_item.number;
						user.city = refdata_item.city;
					}
					m_refdata_gln_user_map.put(refdata_item.gln_code, user);
					num_rows++;
					System.out.print("\rProcessing Refdata Partner file... " + num_rows);
					break;					
				case "ptype":
					refdata_item.p_type = tag_content;
					break;					
				case "gln":
					refdata_item.gln_code = tag_content;
					break;					
				case "status":
					refdata_item.status = tag_content;
					break;
				case "lang":
					refdata_item.language = tag_content;
					break;
				case "descr1":
					refdata_item.descr1 = tag_content;
					break;
				case "descr2":
					refdata_item.descr2 = tag_content;
					break;
				case "type":
					refdata_item.type = tag_content;
					break;
				case "street":
					refdata_item.street = tag_content;
					break;
				case "strno":
					refdata_item.number = tag_content;
					break;
				case "zip":
					refdata_item.zip = tag_content;
					break;
				case "city":
					refdata_item.city = tag_content;
					break;
				case "ctn":
					refdata_item.canton = tag_content;
					break;
				}
				break;
			}
		}
		System.out.println("");
	}
	
	private void parseMedregFiles() {
		m_medreg_gln_user_map = new TreeMap<String, User>();
		
		System.out.print("Processing Medreg people file... ");
		XSSFSheet gln_codes_people_sheet = ExcelOps.getSheetsFromFile(Constants.FILE_GLN_CODES_PEOPLE, 0);	
		// Process MEDREG people's file - these are PUBLIC data
		Iterator<Row> rowIterator = gln_codes_people_sheet.iterator();
		int num_rows = 0;		
		while (rowIterator.hasNext()) {
			Row row = rowIterator.next();
			if (num_rows>0) {
				User cust = new User();
						
				if (row.getCell(0)!=null) 
					cust.gln_code = row.getCell(0).getStringCellValue();
				if (row.getCell(1)!=null)
					cust.last_name = row.getCell(1).getStringCellValue();
				if (row.getCell(2)!=null) 
					cust.first_name = row.getCell(2).getStringCellValue();
				if (row.getCell(3)!=null)
					cust.zip = row.getCell(3).getStringCellValue();
				if (row.getCell(4)!=null) 
					cust.city = row.getCell(4).getStringCellValue();
				if (row.getCell(7)!=null) 
					cust.category = row.getCell(7).getStringCellValue();
				if (row.getCell(8)!=null)
					cust.selbst_disp = row.getCell(8).getStringCellValue().equals("Ja");
				if (row.getCell(9)!=null) 
					cust.bet_mittel = row.getCell(9).getStringCellValue().equals("Ja");
				cust.is_human = true;
				
				if (cust.gln_code!=null && !cust.gln_code.isEmpty())
					m_medreg_gln_user_map.put(cust.gln_code, cust);
			}
			num_rows++;
			System.out.print("\rProcessing Medreg people file... " + num_rows);
		}
		System.out.println("");
		
		System.out.print("Processing Medreg companies file... ");
		XSSFSheet gln_codes_companies_sheet = ExcelOps.getSheetsFromFile(Constants.FILE_GLN_CODES_COMPANIES, 0);		
		// Process MEDREG companies' file - these are PUBLIC data
		rowIterator = gln_codes_companies_sheet.iterator();
		num_rows = 0;
		while (rowIterator.hasNext()) {
			Row row = rowIterator.next();
			if (num_rows>0) {
				User cust = new User();
						
				if (row.getCell(0)!=null) 
					cust.gln_code = row.getCell(0).getStringCellValue();
				if (row.getCell(1)!=null)
					cust.name1 = row.getCell(1).getStringCellValue();
				if (row.getCell(2)!=null) 
					cust.name2 = row.getCell(2).getStringCellValue();
				if (row.getCell(4)!=null)
					cust.number = row.getCell(4).getStringCellValue().trim();
				if (row.getCell(3)!=null)
					cust.street = row.getCell(3).getStringCellValue().trim();
				if (row.getCell(5)!=null)
					cust.zip = row.getCell(5).getStringCellValue();
				if (row.getCell(6)!=null) 
					cust.city = row.getCell(6).getStringCellValue();				
				if (row.getCell(9)!=null) 
					cust.category = row.getCell(9).getStringCellValue();
				cust.is_human = false;
				
				if (cust.gln_code!=null && !cust.gln_code.isEmpty())
					m_medreg_gln_user_map.put(cust.gln_code, cust);
			}
			num_rows++;
			System.out.print("\rProcessing Medreg companies file... " + num_rows);
		}
		System.out.println("");
	}
	
	private static String removeLastChar(String str) {
        return str.substring(0,str.length()-1);
    }
	
	@SuppressWarnings("unchecked")
	private void parseYweseeYamlFile() throws FileNotFoundException, IOException {
		m_yaml_user_map = new TreeMap<String, User>();
		
		System.out.print("Processing Ywesee YAML file... ");
		InputStream input = new FileInputStream(new File(Constants.DIR_YWESEE + Constants.FILE_YAML_YWESEE));
		Yaml yaml = new Yaml(new MyConstructor());
		Map<String, Object> gln_map = (Map<String, Object>) yaml.load(input);
		input.close();
		//
		int num_rows = 0;
		for (Map.Entry<String, Object> entry : gln_map.entrySet()) {
			Map<String, ?> object_map = (Map<String, ?>) entry.getValue();
			User cust = new User();

			cust.gln_code = (String) object_map.get(":ean13");
			cust.last_name = (String) object_map.get(":name");
			cust.first_name = (String) object_map.get(":firstname");
			cust.bet_mittel = (Boolean) object_map.get(":may_dispense_narcotics");
			cust.selbst_disp = (Boolean) object_map.get(":may_sell_drugs");
			List<String> spec_list = (List<String>) object_map.get(":specialities");
			List<String> capa_list = (List<String>) object_map.get(":capabilities");
			for (String spec : spec_list)
				cust.specialities += spec + ", ";
			for (String capa : capa_list)
				cust.capabilities += capa + ", ";
			if (cust.specialities.length()>1)
				cust.specialities = removeLastChar(cust.specialities);
			if (cust.capabilities.length()>1)
				cust.capabilities = removeLastChar(cust.capabilities);
			
			if (cust.gln_code!=null && !cust.gln_code.isEmpty())
				m_yaml_user_map.put(cust.gln_code, cust);
			num_rows++;
			System.out.print("\rProcessing Ywesee YAML file... " + num_rows);
		}
		System.out.println("");
	}
	
	private class MyConstructor extends Constructor {
        private Construct original;

        public MyConstructor() {
            original = this.yamlConstructors.get(null);
            this.yamlConstructors.put(null, new IgnoringConstruct());
        }

        private class IgnoringConstruct extends AbstractConstruct {
            public Object construct(Node node) {
                if (node.getTag().startsWith("!KnownTag")) {
                    return original.construct(node);
                } else {
                    switch (node.getNodeId()) {
                    case scalar:
                        return yamlConstructors.get(Tag.STR).construct(node);
                    case sequence:
                        return yamlConstructors.get(Tag.SEQ).construct(node);
                    case mapping:
                        return yamlConstructors.get(Tag.MAP).construct(node);
                    default:
                        throw new YAMLException("Unexpected node");
                    }
                }
            }
        }
    }
	
	private class RefdataItem {
		private String p_type;
		private String gln_code;
		private String status;
		private String language;
		private String descr1;
		private String descr2;
		private String type;
		private String street;
		private String number;
		private String zip;
		private String city;
		private String canton;
		private String country;
		
		void setPType(String p_type) {
			this.p_type = p_type;
		}
		
		String getPType() {
			return p_type;
		}
		
		void setGlnCode(String gln_code) {
			this.gln_code = gln_code;
		}
		
		String getGlnCode() {
			return gln_code;			
		}
		
		void setStates(String status) {
			this.status = status;
		}
		
		String getStatus() {
			return status;
		}
		
		void setLanguage(String language) {
			this.language = language;
		}
		
		String getLanguage() {
			return language;
		}
		
		void setDescr1(String descr1) {
			this.descr1 = descr1;
		}
		
		String getDescr1() {
			return descr1;
		}
		
		void setDescr2(String descr2) {
			this.descr2 = descr2;
		}
		
		String getDescr2() {
			return descr2;
		}

		void setType(String type) {
			this.type = type;
		}
		
		String getType() {
			return type;
		}
		
		void setStreet(String street) {
			this.street = street;
		}
		
		String getStreet() {
			return street;
		}
		
		void setNumber(String number) {
			this.number = number;
		}
		
		String getNumber() {
			return number;
		}
		
		void setZip(String zip) {
			this.zip = zip;
		}
		
		String getZip() {
			return zip;
		}
		
		void setCity(String city) {
			this.city = city;
		}
		
		String getCity() {
			return city;
		}
		
		void setCanton(String canton) {
			this.canton = canton;
		}
		
		String getCanton() {
			return canton;
		}
		
		void setCounty(String country) {
			this.country = country;
		}
		
		String getCountry() {
			return country;
		}
		
		@Override
		public String toString() {
			return gln_code + " -> " + descr1 + " " + descr2 + " (" + p_type + ", " + street + " " + number + ")"; 
		}
	}
}
