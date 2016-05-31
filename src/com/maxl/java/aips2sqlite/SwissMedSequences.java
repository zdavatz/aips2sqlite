package com.maxl.java.aips2sqlite;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SwissMedSequences {

	private TreeMap<String, ArrayList<Article>> m_smn5plus_to_article_map = null;
	private TreeMap<String, String> m_gtin_to_name_map = null;
	private HashMap<String, String> m_map_of_short_to_long_galens = null;
		
	private class Article {
		String name;
		String smn8;
		String quantity;
		String unit;
	}
	
	SwissMedSequences() {}

	private String[] strings_to_be_cleared = {"c solv", "mit solv", "supp", "o nadel", "rektal", "m nadel(n)", "nadel(n)", "(m|o) kons",
			"(m|o) zucker", "emuls(ion)?", "ecobag", "o aroma", "o konserv", "zuckerfrei", "sans sucre"};
	
	// Some additional galenic forms go in here...
	private String[] add_galen_forms = {"augensalbe", "nasensalbe", "salbe", "depottabl", "lacktabl", "tabl", "filmtabs", "depotdrag",
            "depot", "drag", "infusionslösung", "injektionslösung", "injektionspräparat", "lös", "injektionssuspension", "susp", "kaps",
			"blist", "ampulle", "balsam", "bals", "emuls", "vial", "creme", "crème", "amp", "tropfen", "paste", "stift", "glob",
            "liq", "tee", "supp", "pulver", "lot", "spray", "nebul", "tb", "gaze", "klist", "tinkt", "sirup", "blätter",
            "pastillen", "pastilles", "zäpfchen", "gel", "pfl", "fertigspr", "bonbons", "gran", "kaugummi", "trockensub"};
	
    private String cleanName(String name) {
    	name = name.toLowerCase();
    	// Replace stk, e.g. 6 Fertspr 0.5 ml
    	name = name.replace("2015/2016", "");
    	name = name.replaceAll("(\\d+)*\\s+((S|s)tk|(D|d)os|(A|a)mp|(M|m)inibag|(D|d)urchstf|(F|f)ertspr|(E|e)inwegspr|(M|m)onodos|(F|f)ert(ig)?pen|(B|b)tl|(S|s)pritzamp|(T|t)rinkamp|(F|f)l|(V|v)ial)(\\s+\\d+(\\.\\d+)?\\s+(ml|mg))*", "");
    	for (String s : strings_to_be_cleared)
    		name = name.replaceAll("\\b" + s + "\\b", "");
    	name = name.replaceAll("\\b(\\d+)*\\s+x\\b", "");
    	name = name.replaceAll("\\(.*?\\)", "");
    	name = name.replaceAll("i\\.m\\./i\\.v(\\.)?|i\\.v\\./i\\.m\\.|i\\.m\\.|i\\.v\\.|\\bus(\\.)?\\s*vet\\.|\\comp\\.?", "");
    	name = name.replaceAll("\\+\\s*\\+", "");
    	name = name.split(",")[0];
    	return name.trim();
    }
    
    private boolean isAnimalMed(String name) {
    	Pattern p = Pattern.compile("\\bus(\\.)?\\s*vet(\\.)?");
		Matcher m = p.matcher(name);    	
    	if (m.find())
    		return true;
    	return false;
    }
    
    private ArrayList<String> extractGalenFromName(String name) {
    	ArrayList<String> list_of_galens = new ArrayList<>();
    	int pos = 1000;		// Big number
    	for (Map.Entry<String, String> entry : m_map_of_short_to_long_galens.entrySet()) {
    		String galen = entry.getKey();
    		String[] g = galen.split(",", -1);
    		for (String s : g) {
    			s = s.trim();
    			Pattern p = Pattern.compile("\\b" + s.toLowerCase() + "\\b");
    			Matcher m = p.matcher(name);
	    		if (m.find()) {
	    			list_of_galens.add(s.toLowerCase());
	    			if (m.start()<pos) {
	    				pos = m.start();
	    				if (list_of_galens.size()>1)
	    					Collections.swap(list_of_galens, list_of_galens.size()-1, 0);
	    			}
	    		}
    		}
    	}
    	return list_of_galens;
    }

    private ArrayList<String> extractAddGalenFromName(String name) {
    	ArrayList<String> list_of_galens = new ArrayList<>();
    	for (String galen : add_galen_forms) {
    		Pattern p = Pattern.compile("\\b" + galen + "\\b");
    		Matcher m = p.matcher(name);
    		if (m.find())
    			list_of_galens.add(galen);
    		else if (name.toLowerCase().contains(galen.toLowerCase()))
       			list_of_galens.add(galen);  
    	}
    	return list_of_galens;
    }
    
    private String findGalenShort(String galen) {
    	for (Map.Entry<String, String> entry : m_map_of_short_to_long_galens.entrySet()) {
    		String galen_short = entry.getKey();
    		String galen_long = entry.getValue();
    		if (galen_long!=null) {
	    		if (galen_long.toLowerCase().startsWith(galen.toLowerCase()) 
	    				|| galen.toLowerCase().startsWith(galen_long.toLowerCase()))
	    			return galen_short;
    		}
    	}
    	return "";
    }
    
    private ArrayList<String> createDosageMatchers(String quantity, String unit) {
    	ArrayList<String> list_of_matchers = new ArrayList<>();
    	// Clean up
    	quantity = quantity.toLowerCase().replaceAll(",", ".");   
    	unit = unit.toLowerCase().replaceAll("\\(.*?\\)", "").trim();
    	
    	String q = quantity;
    	String u = unit;
    	    
    	if (q.matches("\\d+") && u.matches("(A|a)mp.*?")) {
        	// 0th matcher
        	list_of_matchers.add(q + " amp");
    		return list_of_matchers;
    	}
    	
    	// 0th matcher
    	list_of_matchers.add(quantity + " " + unit);
    	list_of_matchers.add(quantity + unit);

    	// 1st matcher for quantity, e.g. 1 x 50 or 1 x 50 ml
    	Pattern p = Pattern.compile("(\\d+)\\s*x\\s*(\\d+)\\s*(ml|mg|g)?");
    	Matcher m = p.matcher(q);    	
    	if (m.find()) {
    		quantity = m.group(1);    
    		if (m.group(3)!=null && !m.group(3).isEmpty()) {
    			list_of_matchers.add(quantity);
    		} else {
    			quantity = m.group(2);
    		}
    	}
       	// 3rd matcher: unit
       	p = Pattern.compile(".*?\\s*(\\d*)\\s*(ml)\\s*.*?");
       	m = p.matcher(u);
       	if (m.find()) {
       		String q2 = m.group(1);
       		unit = m.group(2);
       		if (q2!=null && !q2.isEmpty()) {
	       		list_of_matchers.add(q2 + " " + unit);
	       		list_of_matchers.add(q2 + unit);
       		}
       		list_of_matchers.add(q + " " + unit);
       		list_of_matchers.add(q + unit);
       		list_of_matchers.add(quantity + " " + unit);
       		list_of_matchers.add(quantity + unit);
       	}
       	// 4th matcher
       	if (q.matches("\\d+\\s*(ml|mg)"))
       		list_of_matchers.add(q);
    	return list_of_matchers;
    }

    private String removeAddDosageFromName(String name) {
        return name.replaceAll("(\\d+)(\\.\\d+)?\\s*(mg|g)","");
    }
    
    private String addGalenToName(String name, String galen) {
    	if (name.contains(galen))
    		name = name.replace(galen, "");
		name += " " + galen;
		return name;
    }
    
    private String removeSpaces(String name) {
    	// Replace multiple spaces with single space
    	return name.replaceAll("\\s\\s+", " ");    
    }

    /**
     * main function where data are processed
     */
    public void process() {
		try {
			// Read core info from files
			parseSwissmedicPackagesFile();
			parseRefdataPharmaFile();
			parseDosageFormsJson();
			
			String csv_str = "";
			for (Map.Entry<String, ArrayList<Article>> e : m_smn5plus_to_article_map.entrySet()) {
				String smn5plus = e.getKey();
				ArrayList<Article> list_of_articles = e.getValue();
				if (list_of_articles!=null) {
					String sub_csv_str = "";
					boolean animal_med = false;
					for (Article a : list_of_articles) {
						String gtin = "7680" + a.smn8;
						int cs = Utilities.getChecksum(gtin);
						gtin += cs;
                        // Check refdata gtin to name map
						if (m_gtin_to_name_map.containsKey(gtin)) {
							String galens = "";
							String name = m_gtin_to_name_map.get(gtin);
							String clean_name = name.toLowerCase();
							// Exclude medicaments for animals
							if (!isAnimalMed(clean_name)) {
								// Cleaning: 1st pass
								ArrayList<String> list_of_matchers = createDosageMatchers(a.quantity, a.unit);
								for (String m : list_of_matchers) {
									clean_name = clean_name.replaceAll("\\s+" + m + "\\b", "");
								}
								ArrayList<String> list_of_galens = extractGalenFromName(clean_name);
								for (String g : list_of_galens) {
									galens += g + ",";
					    			clean_name = clean_name.replaceAll("\\b" + g + "\\b", " ");
								}
								if (list_of_galens.isEmpty()) {
									list_of_galens = extractAddGalenFromName(clean_name);
									for (String g : list_of_galens) {
										galens += g + ",";
						    			clean_name = clean_name.replaceAll("\\b" + g + "\\b", " ");
									}
								}
								galens = galens.split(",")[0].trim();	// Take only first one
								// Cleaning: 2nd pass
								clean_name = cleanName(clean_name);
                                clean_name = removeAddDosageFromName(clean_name);
								clean_name = removeSpaces(clean_name);
								clean_name = Utilities.capitalizeFully(clean_name, 1);
                                // Add "galenische Form" to clean name
								clean_name = addGalenToName(clean_name, galens);
                                //
								sub_csv_str += name + ";" + clean_name + ";" + galens + ";" + gtin + ";" + a.quantity + ";" + a.unit + ";";
							} else {
								animal_med = true;
							}
						} else {
							String galens = "";
							String clean_name = a.name.toLowerCase();
							//
							if (!isAnimalMed(clean_name)) {
								// Cleaning: 2nd pass (compare with quantity and unit)
								ArrayList<String> list_of_matchers = createDosageMatchers(a.quantity, a.unit);
								for (String m : list_of_matchers)
									clean_name = clean_name.replaceAll("\\s+" + m + "\\b", "");

								ArrayList<String> list_of_galens = extractGalenFromName(clean_name);
								for (String g : list_of_galens) {
									galens += g + ",";
					    			clean_name = clean_name.replace(g, " ");
								}
								if (list_of_galens.isEmpty()) {
									list_of_galens = extractAddGalenFromName(clean_name);
									for (String g : list_of_galens) {
										galens += g + ",";
						    			clean_name = clean_name.replaceAll("\\b" + g + "\\b", " ");
									}						
									// Find short form if not empty
									if (!galens.isEmpty())
										galens = findGalenShort(galens.split(",")[0].trim());
								}
								
								if (galens.isEmpty()) {
									String g[] = a.name.split(",");								
									if (g.length>1) {
										int last_idx = g.length-1;		
										if (g[last_idx].matches("\\D+")) {
											list_of_galens = extractAddGalenFromName(g[last_idx].toLowerCase());
											for (String gg : list_of_galens) {
												String g_short = findGalenShort(gg);
												if (!g_short.isEmpty())
													galens += g_short + ",";
												else
													galens += gg + ",";
											}
											// Fallback in case no short form can be found
											if (galens.isEmpty()) {
												galens = g[last_idx].trim();
											}
										}
									}
								}
								
								galens = galens.split(",")[0].trim().toLowerCase();	// Take only first one
								// Cleaning: 1st pass
								clean_name = cleanName(clean_name);
								clean_name = removeSpaces(clean_name);							
								clean_name = Utilities.capitalizeFully(clean_name, 1);
								clean_name = addGalenToName(clean_name, galens);
								//
								sub_csv_str += a.name + ";" + clean_name + ";" + galens + ";" + gtin + ";" + a.quantity + ";" + a.unit + ";";
							} else {
								animal_med = true;
							}
						}
					}
					if (!animal_med)
						csv_str += smn5plus + ";" + sub_csv_str + "\n";
				}				
			}
			if (!csv_str.isEmpty())
				FileOps.writeToFile(csv_str, Constants.DIR_OUTPUT, "swiss_medic_sequences.csv");
		} catch(IOException | JAXBException e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unchecked")
	private void parseDosageFormsJson() throws IOException {
		ObjectMapper mapper = new ObjectMapper(); // can reuse, share globally				
		TypeReference<HashMap<String,Object>> typeRef = new TypeReference<HashMap<String,Object>>() {};				
		Map<String,Object> dosageFormsData = mapper.readValue(new File(Constants.FILE_DOSAGE_FORMS_JSON), typeRef);		
		ArrayList<HashMap<String, String>> dosageList = (ArrayList<HashMap<String, String>>)dosageFormsData.get("dosage_forms");

		m_map_of_short_to_long_galens = new HashMap<>();
		for (HashMap<String, String> dosage : dosageList) {
			String galen_short = dosage.get("galenic_short");
			String galen_long = dosage.get("galenic_full");
			if (!galen_short.isEmpty()) {
				m_map_of_short_to_long_galens.put(galen_short, galen_long);
			}
		}
				
		System.out.println("Number of dosage forms in database: " + m_map_of_short_to_long_galens.size());
	}
	
	private void parseSwissmedicPackagesFile() throws FileNotFoundException, IOException {
		m_smn5plus_to_article_map = new TreeMap<String, ArrayList<Article>>();
		//
		System.out.print("Processing packages xlsx... ");
		// Load Swissmedic xls file			
		FileInputStream packages_file = new FileInputStream(Constants.FILE_PACKAGES_XLSX);
		// Get workbook instance for XLSX file (XSSF = Horrible SpreadSheet Format)
		XSSFWorkbook packages_workbook = new XSSFWorkbook(packages_file);
		// Get first sheet from workbook
		XSSFSheet packages_sheet = packages_workbook.getSheetAt(0);
		// Iterate through all rows of first sheet
		Iterator<Row> rowIterator = packages_sheet.iterator();
		int num_rows = 0;
		while (rowIterator.hasNext()) {
			Row row = rowIterator.next();
			if (num_rows > 5) {
				String swissmedic_no5 = ""; // SwissmedicNo5 registration number (5 digits)
				String dosage_id = "";
				String package_id = "";
				String name = "";
				String swissmedic_no8 = ""; // SwissmedicNo8 = SwissmedicNo5 + Package id (8 digits)
				String quantity = "";
				String unit = "";
					
				// 0: Zulassungsnummer, 1: Dosisstärkenummer, 2: Präparatebezeichnung, 3: Zulassunginhaberin, 4: Heilmittelcode, 5: IT-Nummer, 6: ATC-Code
				// 7: Erstzulassung Präparat, 8: Zulassungsdatum Sequenz, 9: Gültigkeitsdatum, 10: Packungscode, 11: Packungsgrösse
				// 12: Einheit, 13: Abgabekategorie Packung, 14: Abgabekategorie Dosisstärke, 15: Abgabekategorie Präparat, 
				// 16: Wirkstoff, 17: Zusammensetzung, 18: Anwendungsgebiet Präparat, 19: Anwendungsgebiet Dosisstärke, 20: Gentechnisch hergestellte Wirkstoffe
				// 21: Kategorie bei Insulinen, 22: Betäubungsmittelhaltigen Präparaten
					
				if (row.getCell(0)!=null)
					swissmedic_no5 = String.format("%05d", (int)(row.getCell(0).getNumericCellValue()));// Swissmedic registration number (5 digits)
				if (row.getCell(1)!=null)
					dosage_id = String.format("%02d", (int)(row.getCell(1).getNumericCellValue())); 	// Sequence name
				if (row.getCell(2)!=null)
					name = row.getCell(2).getStringCellValue();
				if (row.getCell(10)!=null) {							
					package_id = String.format("%03d", (int)(row.getCell(10).getNumericCellValue()));	// Verpackungs ID
					swissmedic_no8 = swissmedic_no5 + package_id;					
				}
				if (row.getCell(11)!=null) {
					quantity = row.getCell(11).getStringCellValue();
				}
				if (row.getCell(12)!=null) {
					unit = row.getCell(12).getStringCellValue();
				}					
				
				String smn5_plus = swissmedic_no5 + dosage_id;
				
				if (!smn5_plus.isEmpty() && !swissmedic_no5.isEmpty()) {
					ArrayList<Article> list_of_articles = new ArrayList<>();
					if (m_smn5plus_to_article_map.containsKey(smn5_plus)) 
						list_of_articles = m_smn5plus_to_article_map.get(smn5_plus);
					Article a = new Article();
					a.name = name;
					a.smn8 = swissmedic_no8;
					a.quantity = quantity;
					a.unit = unit;
					list_of_articles.add(a);
					m_smn5plus_to_article_map.put(smn5_plus, list_of_articles);
				}
			}
			System.out.print("\rProcessing packages xlsx... " + num_rows++);
		}
		System.out.println("");
	}
	
	private void parseRefdataPharmaFile() throws FileNotFoundException, JAXBException {
		m_gtin_to_name_map = new TreeMap<>();

		System.out.print("Processing refdata pharma xml file...");
		
		// Load Refdata xml file
		File refdata_xml_file = new File(Constants.FILE_REFDATA_PHARMA_XML);
		FileInputStream refdata_fis = new FileInputStream(refdata_xml_file);

		JAXBContext context = JAXBContext.newInstance(Refdata.class);
		Unmarshaller um = context.createUnmarshaller();
		Refdata refdataPharma = (Refdata) um.unmarshal(refdata_fis);
		List<Refdata.ITEM> pharma_list = refdataPharma.getItem();

		int num_rows = 0;
		for (Refdata.ITEM pharma : pharma_list) {
			String ean_code = pharma.getGtin();
			if (ean_code.length() == 13) {
				String name = pharma.getNameDE();
				// Clean name, we need only dosage
				m_gtin_to_name_map.put(ean_code, name);
				System.out.print("\rProcessing refdata pharma xml file... " + num_rows++);
			} else if (ean_code.length() < 13) {
				if (CmlOptions.SHOW_ERRORS)
					System.err.println(">> EAN code too short: " + ean_code + ": " + pharma.getNameDE());
			} else if (ean_code.length() > 13) {
				if (CmlOptions.SHOW_ERRORS)
					System.err.println(">> EAN code too long: " + ean_code + ": " + pharma.getNameDE());
			}
		}
		System.out.println("");
	}
}
