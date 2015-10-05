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
	Map<String, String> m_sap_gln_map = null;
	Map<String, RetPair> m_sap_flag_map = null;

	JaroWinklerDistance m_jaro_winkler = JaroWinklerDistance.JARO_WINKLER_DISTANCE;
    TokenizerFactory tokenizerFactory = IndoEuropeanTokenizerFactory.INSTANCE;
    JaccardDistance m_jaccard = new JaccardDistance(tokenizerFactory);

    boolean DEBUG = true;
	
	private class RetPair {
		String first;
		String second;		
		
		RetPair(String first, String second) {
			this.first = first;
			this.second = second;
		}
	}
    
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
		if (m_sap_gln_map!=null) {	
			Assert.assertEquals("7601001324725", m_sap_gln_map.get("231045"));
			Assert.assertEquals("7601001375857", m_sap_gln_map.get("233874"));
		}
	}
	
	private void testJaccard() {
        System.out.println(m_jaccard.proximity("Sidler S", "Trachsel Rudolf"));
        System.out.println(m_jaccard.proximity("R Champion", "Champion Rolf"));
        System.out.println(m_jaccard.proximity("B Arnet", "Arnet Bernhard"));
        System.out.println(m_jaccard.proximity("Ch. & U. Köppel", "Christian Köppel"));
        System.out.println(m_jaro_winkler.proximity("Nüesch Hans-Jakob", "HJ Nüesch"));
	}
	
	private String replaceUmlauteAndChars(String str) {
		str = str.replaceAll("ph", "f");
		str = str.replaceAll("sant'\\s*", "st ");
		str = str.replaceAll("è", "e").replaceAll("é", "e").replaceAll("à", "a").replaceAll("î", "i").replaceAll("ë", "e");
		return str.replaceAll("ä", "ae").replace("ö", "oe").replaceAll("ü", "ue");
	}
	
	private String minimalCleanString(String str) {
		str = str.toLowerCase();
		str = str.replaceAll("\\sag$|\\ssa$|\\sgmbh$", "");
		str = str.replaceAll("dr\\.|sc\\.|nat\\.|med\\.|méd\\.|[\\s.\\.]med\\s|gebr\\.|dres\\.|pharm\\.|docteur|docteuer|pd\\s|professeur|prof\\.|frau\\s|herr\\s|herrn\\s|madame\\s|monsieur\\s|fmh|\\.|&|&amp;|\\+\\s*co$|\\+", "").trim();		
		str = str.replaceAll("\\s+|-|/", " ").trim();
		str = str.replaceAll("\\sde\\s|\\sde\\s*la\\s", " ").trim();
		return replaceUmlauteAndChars(str);
	}
	
	private String cleanString(String str) {
		str = str.toLowerCase();
		// removed: apotheke|farmacia|pharmacie
		str = str.replaceAll("standort|filiale|\\sag$|\\ssa$|\\sgmbh$|rg\\.|/\\s*rechg|/\\s*apo", "").trim();
		str = str.replaceAll("regionalspital|kantonales\\s*spital", "spital").trim();
		str = str.replaceAll("dr\\.|sc\\.|nat\\.|med\\.|méd\\.|[\\s.\\.]med\\s|gebr\\.|dres\\.|pharm\\.|docteur|docteuer|pd\\s|professeur|prof\\.|frau\\s|herr\\s|herrn\\s|madame\\s|monsieur\\s|fmh|\\.|&|&amp;|\\+\\s*co$|\\+", "").trim();
		str = str.replaceAll("\\s+|-|/", " ").trim();
		str = str.replaceAll("\\sde\\s|\\sde\\s*la\\s", " ").trim();
		return replaceUmlauteAndChars(str);
	}
	
	private String cleanStreet(String str) {
		str = str.toLowerCase();
		str = str.replaceAll("av\\.", "avenue").replaceAll("ch\\.", "chemin").replaceAll("ch\\s", "chemin ");
		str = str.replaceAll("chemin\\s|route\\s", "rue ");
		str = str.replaceAll("-|\\.", "").trim();
		str = str.replaceAll("\\sde\\s*la\\s", " ").trim();
		return replaceUmlauteAndChars(str);
	}
	
	private boolean stringsMatch(String str1, String str2) {  
		double jc_prox = m_jaccard.proximity(str1, str2);
		if (jc_prox>0.98) {
			return true;	
		}
		return false;
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
			name1 = cleanString(name1);
		}
		// Clean name2 from Takeda SAP file
		String name2 = user.name2;
		if (name2!=null) {
			name2 = cleanString(name2);
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
		int fallback_index = -1;
		String match_type = "";
		String fallback_match_type = "";
		
		int match_level = -1;

		JaroWinklerDistance jaro_winkler = JaroWinklerDistance.JARO_WINKLER_DISTANCE;
        TokenizerFactory tokenizerFactory = IndoEuropeanTokenizerFactory.INSTANCE;
        JaccardDistance jaccard = new JaccardDistance(tokenizerFactory);

        /*
         * Phase 1: Parse
         */
		// Clean name1 from Takeda SAP file
		String sap_name1 = cleanString(user.name1.toLowerCase());
		String sap_name2 = minimalCleanString(user.name2.toLowerCase());
		sap_name1 = sap_name1.length()<3 ? "" : sap_name1;
		sap_name2 = sap_name2.length()<3 ? "" : sap_name2;

		String sap_zip = user.zip;
		String sap_street = user.street.toLowerCase();
			
		int i=0;		
		for (RefdataItem ref : m_refdata_list) {
			String status = ref.status;
			
			if (status.equals("A") || status.equals("I")) {
				String p_type = ref.p_type;				
				if (p_type.equals("JUR")) {				
					
					String ref_name1 = cleanString(ref.descr1);
					String ref_name2 = minimalCleanString(ref.descr2);
					ref_name1 = ref_name1.length()<3 ? "" : ref_name1;
					ref_name2 = ref_name2.length()<3 ? "" : ref_name2;
									
					String ref_zip = ref.zip;
					String ref_street = ref.street;
					//
					if (ref_street!=null)
						ref_street = (ref_street + " " + ref.number).toLowerCase().trim();
					else
						ref_street = "";
					if (ref_zip==null)
						ref_zip = "";
					
					// Clean stree names
					sap_street = cleanStreet(sap_street);
					ref_street = cleanStreet(ref_street);

					// Composite name
					String combo_sap_name = sap_name1;
					if (!sap_name2.isEmpty()) {
						combo_sap_name = (sap_name1 + " " + sap_name2.split(" ")[0]).trim();						
					}
					String combo_ref_name = ref_name1;
					if (!ref_name2.isEmpty()) {
						combo_ref_name = (ref_name1 + " " + ref_name2.split(" ")[0]).trim();	
					}
					
					// Init bools
					boolean possible_match = false;
					boolean name1_match = false;
					boolean street_special = false;
					
					// First check if ref_name contains "gruppierung"
					String[] groups = {"sun store", "galenicare", "farmacieplus"};
					for (String g : groups) {
						if (!ref_zip.isEmpty() && !ref_street.isEmpty()) {
							if (sap_name1.contains(g) || sap_name2.contains(g)) {
								if (ref_name1.contains(g) || ref_name2.contains(g)) {
									possible_match = name1_match = true;
									street_special = true;
								}
							} else if (ref_name1.contains(g) || ref_name2.contains(g)) {
								possible_match = name1_match = true;
								street_special = true;
							}
						}			
					}			
					
					if (sap_name1.contains("cura drogerie") && ref_name1.contains("cura drogerie"))
						System.out.println(sap_name1 + " / " + ref_name1);

					if (!name1_match && !ref_name1.isEmpty() && !combo_sap_name.isEmpty() && stringsMatch(ref_name1, combo_sap_name)) {
						possible_match = name1_match = true;												
					}
					if (!name1_match && !ref_name2.isEmpty() && !combo_sap_name.isEmpty() && stringsMatch(ref_name2, combo_sap_name)) {
						possible_match = name1_match = true;												
					}
					if (!name1_match && !sap_name1.isEmpty() && !combo_ref_name.isEmpty() && stringsMatch(sap_name1, combo_ref_name)) {
						possible_match = name1_match = true;												
					}
					if (!name1_match && !ref_name1.isEmpty() && !sap_name1.isEmpty() && stringsMatch(ref_name1, sap_name1)) {
						possible_match = name1_match = true;
					}
					if (!name1_match && !ref_name2.isEmpty() && !sap_name2.isEmpty() && stringsMatch(ref_name2, sap_name2)) {
						possible_match = true;		// Lowest prio
					}
					if (!name1_match && !ref_name2.isEmpty() && !sap_name1.isEmpty() && stringsMatch(ref_name2, sap_name1)) {
						possible_match = name1_match = true;
					}
					if (!name1_match && !ref_name1.isEmpty() && !sap_name2.isEmpty() && stringsMatch(ref_name1, sap_name2)) {
						possible_match = name1_match = true;
					}					
					if (!ref_name1.isEmpty() && !sap_name1.isEmpty() && (ref_name1.contains(sap_name1) || sap_name1.contains(ref_name1))) {
						possible_match = true;
						if (ref_name1.equals(sap_name1))
							name1_match = true;
					}
					if (!name1_match && !ref_name2.isEmpty() && !sap_name1.isEmpty() && ref_name2.contains(sap_name1)) {
						possible_match = true;
						if (ref_name2.equals(sap_name1))
							name1_match = true;
					}
					if (!name1_match && !ref_name1.isEmpty() && !sap_name2.isEmpty() && ref_name1.contains(sap_name2)) {
						possible_match = true;
						if (ref_name1.equals(sap_name2))
							name1_match = true;
					}					
					if (!name1_match) {
						// Less simple check... edit distances
						double jc_prox = jaccard.proximity(sap_name1, ref_name1);
						double jw_prox = jaro_winkler.proximity(sap_name1, ref_name1);
						// Not too strict!
						if (jc_prox>=0.66 || (jc_prox>0.2 && jw_prox>0.7)) {
							possible_match = true;
						}
					}
					// Exclude case where sap_name1 is "pharmacie" and sap_name2 is empty
					if (sap_name2.isEmpty() 
							&& (sap_name1.equals("farmacie") || sap_name1.equals("apotheke"))) {
						possible_match = false;
					}
					if (possible_match) {
						if (!street_special && !ref_name2.isEmpty() && !sap_name2.isEmpty()) {
							double jc_prox_name2 = jaccard.proximity(sap_name2, ref_name2);
							if (ref_name2.equals(sap_name2) || jc_prox_name2>0.9) {
								// Perfect match
								if (name1_match) {
									if (DEBUG)
										System.out.println(jc_prox_name2 + " -> " + sap_name2 + " / " + ref_name2);
									match_type = "..";
									index = i;
									break;
								}
							} else {
								// Less simple check... edit distances
								double jc_prox = jaccard.proximity(sap_name2, ref_name2);
								double jw_prox = jaro_winkler.proximity(sap_name2, ref_name2);								
								// Strict
								if (jc_prox>=0.75 || (jc_prox>0.33 && jw_prox>0.75)) {
									if (name1_match) {
										if (DEBUG)
											System.out.println(jc_prox + " / " + jw_prox + " -> " + sap_name2 + " / " + ref_name2);
										match_type = "..";
										index = i;
										break;
									}
								}
							}
						}
						if (sap_street.length()>2 && ref_street.length()>2) {						
							if (sap_street.replaceAll("\\s", "").equals(ref_street.replaceAll("\\s", ""))) {
								if (!ref_zip.isEmpty() && !sap_zip.isEmpty() && ref_zip.equals(sap_zip)) {
									// Case 1: Perfect match... we're happy!
									if (DEBUG)
										System.out.println("REF-PM-> " + sap_name1 + " (" + sap_street + ") / " + ref_name1 + " (" + ref_street + ")");
									match_type = ".."; // "perfect";
									index = i;
									break;
								} else {
									fallback_match_type = "MI"; // "perfect";
									fallback_index = i;
									match_level = 1;
								}
							} else {
								// Case 2: The street match is not perfect... how close are we?
								sap_street = sap_street.replaceAll(",", "");
								ref_street = ref_street.replaceAll(",", "");
								double jc_prox = jaccard.proximity(sap_street, ref_street);
								double jw_prox = jaro_winkler.proximity(sap_street, ref_street);

								if (jc_prox>=0.75 || (jc_prox>0.33 && jw_prox>0.75)) {		// originals 0.5/0.1/0.66 (two weak!)
								// Case 2a: Partial match of addresses and perfect ZIP match
									if (!ref_zip.isEmpty() && ref_zip.equals(sap_zip)) {																			
										// Check street numbers
										String sap_number = sap_street.replaceAll("[^0-9]", "").trim();
										String ref_number = ref_street.replaceAll("[^0-9]", "").trim();
										if (sap_number.equals(ref_number)) {
											if (DEBUG)
												System.out.println("REF2-  -> (" + jc_prox + "," + jw_prox + ") " + sap_street + " / " + ref_street);
											match_type = ".."; // "perfect, but address typo";
											index = i;
											match_level = 6;
											if (jc_prox>=0.75)
												break;
										} else {
											if (!street_special && match_level<5) {
												if (DEBUG)
													System.out.println("SO   -> (" + jc_prox + "," + jw_prox + ") " + sap_zip + " / " + ref_zip + " -> " + sap_number + " / " + ref_number);
												match_type = "SO";
												index = i;
												match_level = 5;
											}
										}
									}
								} else {
									if (!street_special) {
										// Case 2b: Address match pretty bad, but the ZIPs match?
										if (!ref_zip.isEmpty() && ref_zip.equals(sap_zip)) {
											if (name1_match) {
												if (DEBUG)
													System.out.println("ZO   -> (" + jc_prox + "," + jw_prox + ") " + sap_zip + " / " + ref_zip);
												match_type = "ZO"; // "perfect, but address typo";
												index = i;
												match_level = 4;
												// break;
											}
										}
									}
								}
							}
						} else {
							// Case 3:
							if (!sap_name2.isEmpty() && !ref_name2.isEmpty()) {
								if (ref_name2.contains(sap_name2)) {
									if (!ref_zip.isEmpty() && ref_zip.equals(sap_zip)) {
										// Case 4: First and second name match perfectly, we're lucky!
										if (DEBUG)
											System.out.println(sap_name2 + " / " + ref_name2);
										index = i;
										match_type = ".."; // "perfect match, but no zip!";
										break;
									}
								}
							}
							// Case 4: The match cannot be completed because street information is missing...
							if (sap_street.isEmpty() || ref_street.isEmpty()) {
								if (!ref_zip.isEmpty() && ref_zip.equals(sap_zip)) {
									if (name1_match) {
										fallback_match_type = "NS"; // "weird, no address in SAP/Refdata file!";
										fallback_index = i;
										match_level = 2;
									}
								} else {
									if (name1_match && !street_special) {
										fallback_match_type = "MI"; // "weird, no address in SAP/Refdata file!";
										fallback_index = i;
										match_level = 1;
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
		if (index<0) {
			index = fallback_index;
			match_type = fallback_match_type;
		}
		if (index>=0) {
			RefdataItem ref = m_refdata_list.get(index);
			if (ref!=null)
				gln = ref.gln_code;
		}

		// Phase 3: Filter
		RetPair ret = new RetPair(gln, "NO");	
		if (!gln.isEmpty()) {
			ret.second = match_type;
		}
		
		return ret;
	}
	
	private void generateGlnSapMap() {
		m_sap_gln_map = new TreeMap<String, String>();
		m_sap_flag_map = new TreeMap<String, RetPair>();
		//		
		int num_entries = 1;
		// Loop trough Takeda's SAP to customer map
		for (Map.Entry<String, User> entry : m_takeda_sap_user_map.entrySet()) {
			String sap_id = entry.getKey();
			User sap_entry = entry.getValue();
			// First process all "NAT" (natürliche Personen)
			if (sap_id.contains(CmlOptions.TAKEDA_RANGE) /* true */) {
				if (sap_entry.is_human) {
					// Next instruction is the heavy-weight!
					RetPair ret_pair = retrieveGlnCodeNat(sap_entry);
					m_sap_flag_map.put(sap_id, ret_pair);
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
					// System.out.println("");
					num_entries++;
					m_sap_gln_map.put(sap_id, gln_code);
				} else {
					// Next instruction is the heavy-weight!
					RetPair ret_pair = retrieveGlnCodeJur(sap_entry);
					m_sap_flag_map.put(sap_id, ret_pair);
					// Extract gln code
					String gln_code = ret_pair.first;
					if (gln_code!=null && !gln_code.isEmpty()) {
						String name1 = m_refdata_gln_user_map.get(gln_code).name1;
						if (!m_refdata_gln_user_map.get(gln_code).name2.isEmpty())
							name1 += " / " + m_refdata_gln_user_map.get(gln_code).name2;
						// String name2 = m_refdata_gln_user_map.get(gln_code).name2;
						String city = m_refdata_gln_user_map.get(gln_code).city;
						String street = m_refdata_gln_user_map.get(gln_code).street + " " + m_refdata_gln_user_map.get(gln_code).number;
						System.out.println(String.format("%4d", num_entries) + " C | " + sap_id + " -> " + ret_pair.second + " | "
								+ gln_code + " | " 
								+ sap_entry.name1 + " (" + sap_entry.city + ", " + sap_entry.street + ") | "
								+ name1 + " (" + city + ", " + street + ")");
					} else {
						System.out.println(String.format("%4d", num_entries) + " C | " + sap_id + " -> " + ret_pair.second + " | " 
								+ sap_entry.name1 + " (" + sap_entry.city + ")");
					}
					// System.out.println("");
					num_entries++;
					m_sap_gln_map.put(sap_id, gln_code);
				}
			}
		}
		System.out.println("Number of processed entries: " + (num_entries-1));
	}
	
	void exportCsvFile() {
		String csv_file = "sap_id;gln_code;name1;name2;sd;bm;specialities;status;flag" + "\n";
		for (Map.Entry<String, String> entry : m_sap_gln_map.entrySet()) {
			String sap_id = entry.getKey();
			String gln_code = entry.getValue();
			String name1 = "";
			String name2 = "";
			String sd = "";
			String bm = "";
			String bm_type = "";
			String specialities = "";
			String status = "";
			String matching_flag = "";

			// Medreg file
			if (m_medreg_gln_user_map.containsKey(gln_code)) {
				sd = m_medreg_gln_user_map.get(gln_code).selbst_disp ? "ja" : "nein";
				bm = m_medreg_gln_user_map.get(gln_code).bet_mittel ? "ja" : "nein";
				bm_type = m_medreg_gln_user_map.get(gln_code).bm_type;
			}
			// Yaml
			if (m_yaml_user_map.containsKey(gln_code)) {
				specialities = m_yaml_user_map.get(gln_code).specialities;
			}
			// Refdata
			if (m_refdata_gln_user_map.containsKey(gln_code)) {
				User user = m_refdata_gln_user_map.get(gln_code);
				if (user!=null) {
					if (user.is_human) {
						name1 = user.last_name;
						name2 = user.first_name;
					} else {
						name1 = user.name1;
						name2 = user.name2;
						sd = "";
						bm = bm_type;
					}
					status = user.status.equals("A") ? "ja" : "nein";
				}
			}
			// Get flag
			if (m_sap_flag_map.containsKey(sap_id))
				matching_flag = m_sap_flag_map.get(sap_id).second;
			
			csv_file += sap_id + ";" 
					+ gln_code + ";" 
					+ name1 + ";" 
					+ name2 + ";"
					+ sd + ";" 
					+ bm + ";" 
					+ specialities + ";" 
					+ status + ";"
					+ matching_flag
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
		// Next instruction allows to read "escape characters", e.g. &amp;
		factory.setProperty("javax.xml.stream.isCoalescing", true);  // Decodes entities into one string
		InputStream in = new FileInputStream(Constants.FILE_REFDATA_PARTNER_XML);
		XMLStreamReader reader = factory.createXMLStreamReader(in, "UTF-8");
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
					cust.bet_mittel = row.getCell(8).getStringCellValue().equals("Ja");
				if (row.getCell(9)!=null) 
					cust.selbst_disp = row.getCell(9).getStringCellValue().equals("Ja");
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
				if (row.getCell(7)!=null)
					cust.country = row.getCell(7).getStringCellValue();
				if (row.getCell(9)!=null) 
					cust.category = row.getCell(9).getStringCellValue();
				if (row.getCell(10)!=null)
					cust.bm_type = row.getCell(10).getStringCellValue();
				cust.is_human = false;
				
				if (cust.gln_code!=null && !cust.gln_code.isEmpty())
					m_medreg_gln_user_map.put(cust.gln_code, cust);
			}
			num_rows++;
			System.out.print("\rProcessing Medreg companies file... " + num_rows);
		}
		System.out.println("");
	}
	
	private static String removeLastChars(String str, int n) {
        return str.substring(0, str.length()-n);
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
			if (cust.specialities.length()>2)
				cust.specialities = removeLastChars(cust.specialities, 2);
			if (cust.capabilities.length()>2)
				cust.capabilities = removeLastChars(cust.capabilities, 2);
			
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
