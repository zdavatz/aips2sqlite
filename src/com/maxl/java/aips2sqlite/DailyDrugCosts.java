package com.maxl.java.aips2sqlite;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.maxl.java.aips2sqlite.refdata.Articles;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DailyDrugCosts {

	ArrayList<Preparation> m_list_of_preparations = new ArrayList<Preparation>();
	TreeMap<String, Substance> m_atc_map = null;
	TreeMap<String, Substance> m_atc_to_substance_map = null;
	TreeMap<String, RefdataInfo> m_gtin_to_refdata_map = null;
	TreeMap<String, Dosage> m_dosages_map = null;
	TreeMap<String, IQPrices> m_pharma_to_iqprices_map = null;

	private class DDD {
		public DDD(float quantity, String unit, String admroute) {
			this.quantity = quantity;
			this.unit = unit;
			this.admroute = admroute;
		}
		float quantity;
		String unit;
		String admroute;
		String notes;
	}

	private class Dosage {
		String name_short;
		String name_full;
		String route_adm_short;
		String route_adm_full;
		String units;
	}

	private class Substance {
		public String description_la;
		public List<DDD> list_of_ddds;
	}

	private class Preparation {
		public String name_de;
		public String description_de;
		public String swissmedic_no5;
		public String atc_code;
		public ArrayList<Pack> list_of_packs;
		public Substance substance;
	}

	private class Pack {
		public String name_de;
		public String description_de;
		public String gtin;
		public String swissmedic_no8;
		public String atc_code;
		public List<DDD> list_of_ddds;
		public float exfactory_price;
		public float public_price;
	}

	private class RefdataInfo {
		public String gtin;
		public String pharma_code;
		public String name_de;
		public String name_fr;
		public String atc_code;
		public String auth_holder;
	}

	private class IQPrices {
		public String pharma_code;
		public String atc_code;
		public String price_grosso;
		public String price_public;
		public String quantity;
	}

	DailyDrugCosts() {

	}

	private void adjustUnits(DDD ddd) {
		if (ddd.unit.equals("g")) {
			ddd.quantity *= 1000.0;
			ddd.unit = "mg";
		}
		if (ddd.unit.equals("mcg")) {
			ddd.quantity /= 1000.0;
			ddd.unit = "mg";
		}
		if (ddd.unit.equals("TU")) {
			ddd.quantity *= 1000.0;
			ddd.unit = "U";
		}
		if (ddd.unit.equals("Mio UI") || ddd.unit.equals("MU")) {
			ddd.quantity *= 1e6;
			ddd.unit = "U";
		}
		if (ddd.unit.equals("U.I. hCG") || ddd.unit.equals("UI")) {
			ddd.unit = "U";
		}
	}

	private DDD extractDddFromName(String name) {
		DDD ddd = new DDD(0.0f, "", "");
		Pattern regx = Pattern.compile("\\d+(\\.\\d+)?\\s\\w+\\/\\d+h");
		Matcher match = regx.matcher(name);
		if (match.find()) {
			String a[] = match.group(0).split(" ");
			ddd.quantity = Float.valueOf(a[0]);
			ddd.unit = a[1].split("/")[0];
		}
		regx = Pattern.compile("(\\d+)(\\.\\d+)?\\s*(\\w+)\\s*\\/Dosis");
		match = regx.matcher(name);
		if (match.find()) {
			String n = match.group(1);	// group(0) -> whole regular expression
			String m = match.group(2);
			if (n!=null && !n.isEmpty()) {
				if (m!=null && !m.isEmpty()) {
					ddd.quantity = Float.valueOf(n + m);
					ddd.unit = match.group(3);
				} else {
					ddd.quantity = Float.valueOf(n);
					ddd.unit = match.group(3);
				}
			}
		}
		// e.g. 1 mg/ml 20 ml or 100 mg/5ml 200ml or 100 mg/5ml 5 Amp 2ml
		regx = Pattern.compile("(\\d+)(\\.\\d+)?\\s*mg\\/(\\d+)?ml\\s*(\\d+)?\\s*\\D*\\s*(\\d+)\\s*ml");
		match = regx.matcher(name);
		if (match.find()) {
			String n1 = match.group(1);
			String n2 = match.group(2);
			String d = match.group(3);
			String l = match.group(4);
			String m = match.group(5);
			if (n2==null)
				n2 = "";
			float f = 1.0f;
			if (l!=null && !l.isEmpty())
				f = Float.valueOf(l);
			if (n1!=null && !n1.isEmpty()) {
				if (m!=null && !m.isEmpty()) {
					if (d!=null && !d.isEmpty()) {
						ddd.quantity = f*Integer.valueOf(m)/Integer.valueOf(d)*Float.valueOf(n1 + n2);
						ddd.unit = "mg";
					} else {
						ddd.quantity = f*Integer.valueOf(m)*Float.valueOf(n1 + n2);
						ddd.unit = "mg";
					}
				}
			}
		}
		// e.g. 200 mg/4ml 80 ml
		regx = Pattern.compile("(\\d+)(\\.\\d+)?\\s*mg\\/(\\d+)?ml\\s*(\\d+)\\s*ml");
		match = regx.matcher(name);
		if (match.find()) {
			String n1 = match.group(1);
			String n2 = match.group(2);
			String d = match.group(3);
			String m = match.group(4);
			if (n2==null)
				n2 = "";
			if (n1!=null && !n1.isEmpty()) {
				if (m!=null && !m.isEmpty()) {
					if (d!=null && !d.isEmpty()) {
						ddd.quantity = Integer.valueOf(m)/Integer.valueOf(d)*Float.valueOf(n1 + n2);
						ddd.unit = "mg";
					} else {
						ddd.quantity = Integer.valueOf(m)*Float.valueOf(n1 + n2);
						ddd.unit = "mg";
					}
				}
			}
		}

		return ddd;
	}

	private int extractQuantityFromDescription(String description) {
		// First identify more complex patterns, e.g. 6x 10 Stk or 3x 60 Dosen
		Pattern regx = Pattern.compile("(\\d+)\\s*x\\s*(\\d+)\\s*(Stk|Dosen)\\b");
		Matcher match = regx.matcher(description);
		if (match.find()) {
			String n = match.group(1);	// group(0) -> whole regular expression
			String m = match.group(2);
			if (n!=null && m!=null && !n.isEmpty() && !m.isEmpty())
				return Integer.valueOf(n) * Integer.valueOf(m);
		}
		// Identify less complex, but more common patterns
		regx = Pattern.compile("(\\d+)\\s*((S|s)tk|(D|d)os|(A|a)mp|x|Minibag|Durchstf|Fert(ig)*spr|Monodos|Fert(ig*)pen|Btl)\\b");
		match = regx.matcher(description);
		if (match.find()) {
			String n = match.group(1);	// group(0) -> whole regular expression
			if (n!=null && !n.isEmpty())
				return Integer.valueOf(n);
		}
		// Tubes and dispensers are special, e.g. 3 Disp 80 g
		regx = Pattern.compile("(\\d+)*\\s*(Disp|Tb)\\s*(\\d+)\\s*g\\b");
		match = regx.matcher(description);
		if (match.find()) {
			String n = match.group(1);	// group(0) -> whole regular expression
			String m = match.group(3);
			if (m!=null && !m.isEmpty()) {
				if (n!=null && !n.isEmpty())
					return Integer.valueOf(n) * Integer.valueOf(m);
				else
					return Integer.valueOf(m);
			}
		}
		return 1;
	}

	private String removeDDDFromName(String name) {
		name = name.toLowerCase();
		name = name.replaceAll("\\d+\\s\\w+\\/\\d+h", "");
		name = name.replaceAll("\\d+(\\.\\d+)?\\s\\w+\\/\\d*ml", "");
		name = name.replaceAll("\\d+\\s\\w+\\/(\\d+\\.\\d+)?ml", "");
		return name;
	}

	private String removeAllDigitsFromName(String name) {
		// name = name.replaceAll("(^| ).%?( |$)", "").trim();
		name = name.replaceAll("\\d+\\b", "").trim();
		return name;
	}

	private String removeStringFromName(String name, String str) {
		name = name.toLowerCase();
		str = str.toLowerCase();
		return name.replaceAll("\\b"+str+"\\b", "");
	}

	private String removeSingleChars(String name) {
		name = name.toLowerCase();
		return name.replaceAll("(^| ).( |$)", "");
	}

	public void process() {
		try {
			int missing_atc_codes = 0;
			String missing_atc_codes_str = "";
			int missing_articles = 0;
			String missing_articles_str = "";
			int unknown_galens = 0;
			String quantity_zero_str = "";

			// Read core info from files
			parseDosageFormsJson();
			parseBagPreparationsFile();		
			parseRefdataPharmaFile();
			parseWidoATCIndexFile();
			parse2015ATCwithDDDsFile();
			parseIQPharmaFile();

			// Enhance WIDO map
			for (Map.Entry<String, Substance> entry : m_atc_to_substance_map.entrySet()) {
				String atc = entry.getKey();
				if (!m_atc_map.containsKey(atc)) {
					m_atc_map.put(atc, entry.getValue());
					missing_articles++;
				}
			}
			System.out.println("ATC codes which are missing in the WIDO file -> " + missing_articles);

			// Loop through all preparations and packs
			String csv_str = "GTIN;Pharma;Name;Author;ATC;Galen;Amount;Stk;Dosage;Unit;DosageREF;UnitREF;EFP;PUP;WHODosage;WHOUnit;RoA;WHONotes;Mult;EFPDaily;PUPDaily;IG;iqPGR;iqPUP;iqAmount\n";
			for (Preparation p : m_list_of_preparations) {
				String name_de = p.name_de;
				String atc_code = p.atc_code;
				float quantity = 0;
				String unit = "";

				if (p.substance!=null) {
					// Only one DDD per preparation exists!
					DDD ddd = new DDD(p.substance.list_of_ddds.get(0).quantity, p.substance.list_of_ddds.get(0).unit, "");
					adjustUnits(ddd);
					quantity = ddd.quantity;
					unit = ddd.unit;
				}
				if (atc_code!=null && !atc_code.isEmpty()) {
					if (m_atc_map.containsKey(atc_code)) {
						for (Pack pack : p.list_of_packs) {
							pack.name_de = name_de;
							pack.atc_code = atc_code;
							pack.list_of_ddds = m_atc_map.get(atc_code).list_of_ddds;

							// Pre-parse all ddds listed in the WHO file - notes is relevant for the selection
							boolean skip_next_one = false;
							// Parse ddd_unit and adjust accordingly
							for (DDD ddd : pack.list_of_ddds) {
								adjustUnits(ddd);
								if (quantity > 0.0f && ddd.quantity > 0.0f && skip_next_one==false) {
									String refdata_name_de = "";
									String refdata_pharma_code = "";
									String refdata_author_holder = "";
									DDD ddd_refdata = new DDD(0.0f, "", "");
									if (m_gtin_to_refdata_map.containsKey(pack.gtin)) {
										refdata_name_de = m_gtin_to_refdata_map.get(pack.gtin).name_de;
										refdata_pharma_code = m_gtin_to_refdata_map.get(pack.gtin).pharma_code;
										refdata_author_holder = m_gtin_to_refdata_map.get(pack.gtin).auth_holder;
										ddd_refdata = extractDddFromName(refdata_name_de);
										adjustUnits(ddd_refdata);
									}

									float factor = 0.0f;
									if (ddd_refdata.quantity>0.0f) {
										if (ddd.unit.toLowerCase().equals(ddd_refdata.unit.toLowerCase())) {
											factor = ddd_refdata.quantity / ddd.quantity;
										}
									}
									if (factor<=0.0f) {
										if (unit.toLowerCase().equals(ddd.unit.toLowerCase())) {
											factor = quantity / ddd.quantity;
										} else if (ddd.unit.toLowerCase().equals("tablet")) {
											factor = 1;
										}
									}

									String clean_pack_name = removeAllDigitsFromName(pack.name_de);
									String galen_form = removeStringFromName(refdata_name_de, pack.name_de);
									galen_form = removeStringFromName(galen_form, clean_pack_name);
									galen_form = removeDDDFromName(galen_form);
									galen_form = removeStringFromName(galen_form, pack.description_de);
									galen_form = removeStringFromName(galen_form, String.valueOf(quantity));
									galen_form = removeStringFromName(galen_form, unit);
									galen_form = removeAllDigitsFromName(galen_form);
									galen_form = removeSingleChars(galen_form);
									galen_form = galen_form.trim();

									String route_adm = "";
									// Note: the dosage map has been sorted!
									boolean name_found = false;
									int curr_name_short_len = 0;
									for (Map.Entry<String, Dosage> entry : m_dosages_map.entrySet()) {
										String name_short = entry.getKey();
										Dosage dosage = entry.getValue();
										String name_full = dosage.name_full;
										if (galen_form.contains(name_short) && name_short.length()>curr_name_short_len) {
											galen_form = name_full;
											route_adm = dosage.route_adm_short;
											curr_name_short_len = name_short.length();
											name_found = true;
										}
										if (refdata_name_de.toLowerCase().contains(name_full)
												|| (refdata_name_de.toLowerCase().contains(name_short) && name_short.length()>curr_name_short_len)) {
											galen_form = name_full;
											route_adm = dosage.route_adm_short;
											curr_name_short_len = name_short.length();
											name_found = true;
										}
									}
									if (name_found==false) {
										// Check exceptional cases using the refdata name
										if (refdata_name_de.toLowerCase().contains("c solv")) {
											galen_form = "trockensubstanz mit l�sungsmittel";
											route_adm = "P";
											name_found = true;
										} else if (refdata_name_de.toLowerCase().contains("nadelschutz fertspr")) {
											galen_form = "fertigspritze mit nadelschutz";
											route_adm = "P";
											name_found = true;
										} else if (refdata_name_de.toLowerCase().contains("inj kit")) {
											galen_form = "injektionsl�sung";
											route_adm = "P";
											name_found = true;
										} else if (refdata_name_de.toLowerCase().contains("durchstf")) {
											galen_form = "durchstechflasche";
											route_adm = "P";
											name_found = true;
										} else if (refdata_name_de.toLowerCase().contains("amp")) {
											galen_form = "injektionsl�sung in ampulle";
											route_adm = "P";
											name_found = true;
										} else if (refdata_name_de.toLowerCase().contains("filmtab")) {
											galen_form = "filmtablette";
											route_adm = "O";
											name_found = true;
										} else {
											galen_form = "unbekannt";
											unknown_galens++;
										}
									}
									// Cross-check information extracted from refdata name with the one from WHO
									if (route_adm.equals(ddd.admroute)) {

										int N = extractQuantityFromDescription(pack.description_de);
										String f = "";
										String efp_daily = "";
										String pup_daily = "";
										if (factor > 0.0f) {
											if (N>0)
												factor *= N;
											f =  String.format("%.4f", factor);
											efp_daily = String.format("%.2f", pack.exfactory_price / factor);
											pup_daily = String.format("%.2f", pack.public_price / factor);
										}

										boolean mismatch = false;
										if (ddd.notes!=null && !ddd.notes.isEmpty()) {
											// Specify here all exceptions to the WHO rule!
											if (galen_form.equals("gel") && !ddd.notes.contains("gel"))
												mismatch = true;
											if (galen_form.contains("pflaster") && !ddd.notes.contains("patch"))
												mismatch = true;
											if (galen_form.contains("vagina") && !ddd.notes.contains("vaginal"))
												mismatch = true;
											if (galen_form.contains("injektion") && !ddd.notes.contains("depot inj"))
												mismatch = true;
											if (galen_form.contains("tablette") && ddd.notes.contains("ring"))
												mismatch = true;
										}

										if (!mismatch) {
											String iq_atc_code = "";
											String iq_price_grosso = "";
											String iq_price_public = "";
											String iq_quantity = "";
											if (m_pharma_to_iqprices_map.containsKey(refdata_pharma_code)) {
												iq_atc_code = m_pharma_to_iqprices_map.get(refdata_pharma_code).atc_code;
												iq_price_grosso = String.format("%,.2f", Float.valueOf(m_pharma_to_iqprices_map.get(refdata_pharma_code).price_grosso));
												iq_price_public = String.format("%,.2f", Float.valueOf(m_pharma_to_iqprices_map.get(refdata_pharma_code).price_public));
												iq_quantity = m_pharma_to_iqprices_map.get(refdata_pharma_code).quantity;
											}

											skip_next_one = true;
											csv_str += pack.gtin + ";"
													+ refdata_pharma_code + ";"
													+ refdata_name_de + ";"
													+ refdata_author_holder + ";"
													+ pack.name_de + ";"
													+ clean_pack_name + ";"
													+ pack.atc_code + ";"
													+ galen_form + ";"
													+ pack.description_de + ";"
													+ N + ";"
													+ quantity + ";"
													+ unit + ";"
													+ ddd_refdata.quantity + ";"
													+ ddd_refdata.unit + ";"
													+ pack.exfactory_price + ";"
													+ pack.public_price + ";"
													+ ddd.quantity + ";" + ddd.unit + ";" + ddd.admroute + ";" + ddd.notes + ";"
													+ f + ";"
													+ efp_daily + ";"
													+ pup_daily + ";"
													+ iq_atc_code + ";"
													+ iq_price_grosso + ";"
													+ iq_price_public + ";"
													+ iq_quantity
													+ "\n";
										}
									}
								} else {
									quantity_zero_str += p.name_de + " -> " + quantity + " / " + ddd.quantity + " / " + skip_next_one + "\n";
								}
							}
						}
					} else {
						missing_articles += p.list_of_packs.size();
						missing_articles_str += p.name_de + " (" + p.atc_code + ")\n";
						System.out.print("\rPacks (articles) that are not ATC matchable -> " + missing_articles);
					}
				} else {
					missing_atc_codes += p.list_of_packs.size();
					missing_atc_codes_str += p.name_de + " (" + p.atc_code + ")\n";
					System.out.print("\rPacks (articles) that have no ATC code -> " + missing_atc_codes);
				}
			}
			System.out.println("");
			System.out.println("Nicht erkannte Darreichungsformen = " + unknown_galens);
			if (!csv_str.isEmpty())
				FileOps.writeToFile(csv_str, Constants.DIR_OUTPUT, "daily_drug_dosages.csv");
			if (!missing_articles_str.isEmpty())
				FileOps.writeToFile(missing_articles_str, Constants.DIR_OUTPUT, "missing_atc_codes.csv");
			if (!missing_atc_codes_str.isEmpty())
				FileOps.writeToFile(missing_atc_codes_str, Constants.DIR_OUTPUT, "articles_with_no_atc_code.csv");
			if (!quantity_zero_str.isEmpty())
				FileOps.writeToFile(quantity_zero_str, Constants.DIR_OUTPUT, "articles_no_quantity.csv");
		} catch(XMLStreamException | IOException | JAXBException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private void parseDosageFormsJson() throws IOException {
		ObjectMapper mapper = new ObjectMapper(); // can reuse, share globally
		TypeReference<HashMap<String,Object>> typeRef = new TypeReference<HashMap<String,Object>>() {};

		File json_file = Paths.get(System.getProperty("user.dir"), Constants.DIR_INPUT, Constants.FILE_DOSAGE_FORMS_JSON).toFile();
		if (!json_file.exists())
			System.out.println("ERROR: Could not read file " + json_file);

		Map<String,Object> dosageFormsData = mapper.readValue(json_file, typeRef);
		ArrayList<HashMap<String, String>> dosageList = (ArrayList<HashMap<String, String>>)dosageFormsData.get("dosage_forms");

		m_dosages_map = new TreeMap<>();
		for (HashMap<String, String> dosage : dosageList) {
			Dosage dose = new Dosage();
			dose.name_full = dosage.get("galenic_full");
			dose.name_short = dosage.get("galenic_short");
			dose.route_adm_full = dosage.get("route_adm_full");
			dose.route_adm_short = dosage.get("route_adm_short");
			dose.units = dosage.get("units");
			String[] short_names = dose.name_short.split(",", -1);
			for (String sn : short_names) {
				sn = sn.trim();
				if (sn!=null && !sn.isEmpty())
					m_dosages_map.put(sn, dose);
			}
		}

		// Sort according to length of short name (larger comes first)
		// This will ensure that when the abbreviations are replaced we will get the correct one!
		/*
		Set<Entry<String, Dosage>> set = m_dosages_map.entrySet();
		List<Entry<String, Dosage>> list_of_entries = new ArrayList<Entry<String, Dosage>>(set);
		Collections.sort(list_of_entries, new Comparator<Entry<String, Dosage>>() {
			@Override
			public int compare(Entry<String, Dosage> d1, Entry<String, Dosage> d2) {
				int value1 = d1.getValue().name_short.length();
				int value2 = d2.getValue().name_short.length();
				return Integer.valueOf(value1)
						.compareTo(value2);
			}
		});
		m_dosages_map.clear();
		for (Entry<String, Dosage> e : list_of_entries)
			m_dosages_map.put(e.getKey(), e.getValue());
		*/

		System.out.println("Number of dosage forms in database: " + m_dosages_map.size());
	}

	private void parseBagPreparationsFile() throws XMLStreamException, FileNotFoundException {
		String tag_content = null;
		Preparation preparation = null;
		ArrayList<Pack> list_of_packs = null;
		ArrayList<Substance> list_of_substances = null;
		Pack pack = null;
		Substance substance = null;
		DDD ddd = null;

		System.out.print("Processing BAG preparations file...");

		XMLInputFactory xml_factory = XMLInputFactory.newInstance();
		// Next instruction allows to read "escape characters", e.g. &amp;
		xml_factory.setProperty("javax.xml.stream.isCoalescing", true);  // Decodes entities into one string
		InputStream in = new FileInputStream(Constants.FILE_PREPARATIONS_XML);
		XMLStreamReader reader = xml_factory.createXMLStreamReader(in, "UTF-8");

		String price = "";
		String description = "";
		boolean parsing_limitations = false;
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
				case "preparation":
					preparation = new Preparation();
					break;
				case "packs":
					list_of_packs = new ArrayList<Pack>();
					break;
				case "pack":
					pack = new Pack();
					break;
				case "substances":
					list_of_substances = new ArrayList<Substance>();
					break;
				case "substance":
					substance = new Substance();
					substance.list_of_ddds = new ArrayList<DDD>();
					ddd = new DDD(0.0f, "", "");
					break;
				case "prices":
					break;
				case "exfactoryprice":
					break;
				case "publicprice":
					break;
				case "limitations":
					parsing_limitations = true;
					break;
				}
				break;
			case XMLStreamConstants.CHARACTERS:
				tag_content = reader.getText().trim();
				break;
			case XMLStreamConstants.END_ELEMENT:
				switch (reader.getLocalName().toLowerCase()) {
				case "preparation":
					m_list_of_preparations.add(preparation);
					System.out.print("\rProcessing BAG preparations file... " + num_rows++);
					break;
				case "pack":
					pack.description_de = description;
					list_of_packs.add(pack);
					break;
				case "packs":
					preparation.list_of_packs = list_of_packs;
					break;
				case "namede":
					preparation.name_de = tag_content;
					break;
				case "atccode":
					preparation.atc_code = tag_content;
					break;
				case "swissmedicno5":
					preparation.swissmedic_no5 = tag_content;
					break;
				case "swissmedicno8":
					pack.swissmedic_no8 = tag_content;
					break;
				case "gtin":
					pack.gtin = tag_content;
					break;
				case "substance":
					substance.list_of_ddds.add(ddd);
					preparation.substance = substance;
					break;
				case "descriptionla":
					substance.description_la = tag_content;
					break;
				case "descriptionde":
					if (!parsing_limitations)
						description = tag_content;
					break;
				case "quantity":
					String q = tag_content;
					if (q!=null && !q.isEmpty()) {
						q = q.replaceAll("max.|min.|ca.|<|'|\\s+","");
						q = q.split("-")[0];
						ddd.quantity = Float.valueOf(q);
					}
					break;
				case "quantityunit":
					ddd.unit = tag_content;
					break;
				case "price":
					price = tag_content;
					break;
				case "exfactoryprice":
					String efp = price;
					if (!efp.isEmpty())
						pack.exfactory_price = Float.valueOf(efp);
					break;
				case "publicprice":
					String pup = price;
					if (!pup.isEmpty())
						pack.public_price = Float.valueOf(pup);
					break;
				case "limitations":
					parsing_limitations = false;
					break;
				}
				break;
			}
		}
		System.out.println("");
	}

	void parseRefdataPharmaFile() throws FileNotFoundException, JAXBException {
		m_gtin_to_refdata_map = new TreeMap<String, RefdataInfo>();

		System.out.print("Processing Refdata Pharma file...");

		// Load Refdata xml file
		File refdata_xml_file = new File(Constants.FILE_REFDATA_PHARMA_XML);
		FileInputStream refdata_fis = new FileInputStream(refdata_xml_file);

		JAXBContext context = JAXBContext.newInstance(Articles.class);
		Unmarshaller um = context.createUnmarshaller();
		Articles refdata_articles = (Articles) um.unmarshal(refdata_fis);
		List<Articles.Article> article_list = refdata_articles.getArticle();

		int num_rows = 0;
		for (Articles.Article article : article_list) {
			String product_class = article.getMedicinalProduct().getProductClassification().getProductClass();
			String ean_code;
			if (product_class.equals("PHARMA")) {
				ean_code = article.getPackagedProduct().getDataCarrierIdentifier();
			} else if (product_class.equals("NONPHARMA")) {
				ean_code = article.getMedicinalProduct().getIdentifier();
			} else {
				continue;
			}
			String nameDe = "";
			String nameFr = "";
			List<Articles.Article.PackagedProduct.Name> name_list = article.getPackagedProduct().getName();
			for (Articles.Article.PackagedProduct.Name name: name_list) {
				if (name.getLanguage().equals("DE")) {
					nameDe = name.getFullName();
				} else if (name.getLanguage().equals("FR")) {
					nameFr = name.getFullName();
				}
			}
			String atc = article.getMedicinalProduct().getProductClassification().getAtc();

			if (ean_code.length() == 13) {
				RefdataInfo refdata = new RefdataInfo();
				refdata.gtin = ean_code;
				// No pharma code in the new XML format
				// https://github.com/zdavatz/aips2sqlite/issues/70
				refdata.pharma_code = "";
				refdata.name_de = nameDe;
				refdata.name_fr = nameFr;
				refdata.atc_code = atc == null ? "" : atc;
				refdata.auth_holder = article.getPackagedProduct().getHolder().getName();
				m_gtin_to_refdata_map.put(ean_code, refdata);
				System.out.print("\rProcessing BAG preparations file... " + num_rows++);
			} else if (ean_code.length() < 13) {
				if (CmlOptions.SHOW_ERRORS)
					System.err.println(">> EAN code too short: " + ean_code + ": " + nameDe);
			} else if (ean_code.length() > 13) {
				if (CmlOptions.SHOW_ERRORS)
					System.err.println(">> EAN code too long: " + ean_code + ": " + nameFr);
			}
		}
		System.out.println("");
	}

	void parseWidoATCIndexFile() throws FileNotFoundException, IOException {
		// Code -> ATC class
		m_atc_map = new TreeMap<String, Substance>();
		// Read all codes in wido excel file
		FileInputStream atc_classes_file = new FileInputStream(Constants.FILE_WHO_ATC_CLASSES_XLS);
		// Get workbook instance for XLS file (HSSF = Horrible SpreadSheet Format)
		HSSFWorkbook atc_classes_workbook = new HSSFWorkbook(atc_classes_file);
		// Get first sheet from workbook
		// HSSFSheet atc_classes_sheet = atc_classes_workbook.getSheetAt(1); // --> 2013 file
		HSSFSheet atc_classes_sheet = atc_classes_workbook.getSheetAt(0); 	 // --> 2014 file
		// Iterate through all rows of first sheet
		Iterator<Row> rowIterator = atc_classes_sheet.iterator();
		//
		int num_rows = 0;
		while (rowIterator.hasNext()) {
			Row row = rowIterator.next();
			if (num_rows > 2) {
				String atc_code = "";
				float daily_dose = 0.0f;
				String unit = "";
				String admr = "";
				String notes = "";
				if (row.getCell(1)!=null)
					atc_code = ExcelOps.getCellValue(row.getCell(1)).replaceAll("\\s", "");
				if (row.getCell(9)!=null)
					notes = ExcelOps.getCellValue(row.getCell(9)).replaceAll("[\\t\\n\\r]"," ").trim().toLowerCase();
				if (row.getCell(6)!=null && row.getCell(7)!=null) {
					String dose = ExcelOps.getCellValue(row.getCell(6));
					if (dose!=null && !dose.isEmpty() && !dose.equals("BLANK"))
						daily_dose = Float.valueOf(dose);
					unit = ExcelOps.getCellValue(row.getCell(7));
					if (row.getCell(8)!=null) {
						admr = ExcelOps.getCellValue(row.getCell(8));
					}
				}
				// Build a full map atc code to atc class
				if (atc_code.length()==7 && !atc_code.equals("BLANK")) {
					List<DDD> list_of_ddds = new ArrayList<DDD>();
					if (m_atc_map.containsKey(atc_code)) {
						list_of_ddds = m_atc_map.get(atc_code).list_of_ddds;
					}
					DDD ddd = new DDD(daily_dose, unit, admr);
					if (ddd.quantity>0.0f && !ddd.unit.isEmpty()) {
						ddd.notes = notes;
						list_of_ddds.add(ddd);
						Substance substance = new Substance();
						substance.list_of_ddds = list_of_ddds;
						m_atc_map.put(atc_code, substance);
					}
				}
			}
			num_rows++;
		}
	}

	void parse2015ATCwithDDDsFile() throws FileNotFoundException, XMLStreamException {
		m_atc_to_substance_map = new TreeMap<String, Substance>();

		System.out.print("Processing 2015 ATC with DDDs file...");

		XMLInputFactory xml_factory = XMLInputFactory.newInstance();
		// Next instruction allows to read "escape characters", e.g. &amp;
		xml_factory.setProperty("javax.xml.stream.isCoalescing", true);  // Decodes entities into one string
		InputStream in = new FileInputStream(Constants.FILE_ATC_WITH_DDDS_XML);
		XMLStreamReader reader = xml_factory.createXMLStreamReader(in, "UTF-8");

		// Keep moving the cursor forward
		while (reader.hasNext()) {
			int event = reader.next();
			// Check if the element that the cursor is currently pointing to is a start element
			switch (event) {
			case XMLStreamConstants.START_DOCUMENT:
				break;
			case XMLStreamConstants.START_ELEMENT:
				switch (reader.getLocalName().toLowerCase()) {
				case "row":
					String atc = "";
					float daily_dose = 0.0f;
					String unit = "";
					String admr = "";
					for (int i=0; i<reader.getAttributeCount(); ++i) {
						String attribute = reader.getAttributeLocalName(i);
						switch (attribute) {
						case "ATCCode":
							atc = reader.getAttributeValue(i);
							break;
						case "DDD":
							String str = reader.getAttributeValue(i);
							if (str!=null && !str.isEmpty())
								daily_dose = Float.valueOf(str);
							break;
						case "UnitType":
							unit = reader.getAttributeValue(i);
							break;
						case "AdmCode":
							admr = reader.getAttributeValue(i);
							break;
						}
						if (atc!=null && !atc.isEmpty()) {
							List<DDD> list_of_ddds = new ArrayList<DDD>();
							if (m_atc_to_substance_map.containsKey(atc)) {
								list_of_ddds = m_atc_to_substance_map.get(atc).list_of_ddds;
							}
							DDD ddd = new DDD(daily_dose, unit, admr);
							if (ddd.quantity>0.0f && !ddd.unit.isEmpty()) {
								list_of_ddds.add(ddd);
								Substance substance = new Substance();
								substance.list_of_ddds = list_of_ddds;
								m_atc_to_substance_map.put(atc, substance);
							}
						}
					}
				}
				break;
			case XMLStreamConstants.CHARACTERS:
				break;
			case XMLStreamConstants.END_ELEMENT:
				break;
			}
		}
		System.out.println("");
	}

	void parseIQPharmaFile() throws FileNotFoundException, IOException {
		// Pharma code -> IQPrices class
		m_pharma_to_iqprices_map = new TreeMap<String, IQPrices>();

		System.out.println("Processing iQPharma file... ");
		XSSFSheet iqpharma_excel = ExcelOps.getSheetsFromFile(Constants.DIR_IBSA + "iq_pharma_data_dec_2015.xlsx", 3);
		Iterator<Row> rowIterator = iqpharma_excel.iterator();

		int num_rows = 0;
		while (rowIterator.hasNext()) {
			Row row = rowIterator.next();
			if (num_rows > 3) {
				if (row.getCell(1)!=null) {
					String pharma_code = ExcelOps.getCellValue(row.getCell(1));
					IQPrices iqp = new IQPrices();
					iqp.atc_code = "";
					iqp.price_grosso = "";
					iqp.price_public = "";
					iqp.quantity = "";
					if (row.getCell(2)!=null)
						iqp.atc_code = ExcelOps.getCellValue(row.getCell(2));
					if (row.getCell(3)!=null)
						iqp.price_grosso = ExcelOps.getCellValue(row.getCell(3));
					if (row.getCell(4)!=null)
						iqp.price_public = ExcelOps.getCellValue(row.getCell(4));
					if (row.getCell(5)!=null)
						iqp.quantity = ExcelOps.getCellValue(row.getCell(5));
					m_pharma_to_iqprices_map.put(pharma_code, iqp);
				}
			}
			num_rows++;
		}
	}
}
