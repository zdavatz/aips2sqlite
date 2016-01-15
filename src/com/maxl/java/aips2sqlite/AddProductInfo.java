package com.maxl.java.aips2sqlite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

public class AddProductInfo {

	// Main sqlite database
	SqlDatabase m_sql_db;
	// Map of products
	private Map<String, Product> m_map_products = null;	
	
	public AddProductInfo(SqlDatabase sql_db, Map<String, Product> map_products) {
		m_sql_db = sql_db;
		m_map_products = map_products;
	}
	
	private String na() {
		if (CmlOptions.DB_LANGUAGE.equals("de"))
			return "k.A.";
		else if (CmlOptions.DB_LANGUAGE.equals("fr"))
			return "p.c.";
		return "";
	}
	
	private int lang_id() {
		if (CmlOptions.DB_LANGUAGE.equals("de"))
			return 0;
		else if (CmlOptions.DB_LANGUAGE.equals("fr"))
			return 1;
		return 0;
	}

	private String[] update_packages_str(String packages_str, String ean)
	{
		// pack_info_str += name.toUpperCase() + ", " + u + ", " + size + ", " + fap + " [" + swissmedic_cat + "]\n";
		
		/**			
			important entries:
				0 - pack title*
				3 - ex factory price
				4 - public price
				5 - fap price*
				6 - fep price
				7 - vat
				8 - additional info (e.g. swissmedic_cat)*
				9 - ean code
		*/
		String new_packages_str = "";
		String new_pack_info_str = "";
		
		// Decompose packages_str
		String[] packages = packages_str.split("\n");
		if (packages!=null) {
			// Loop through all packages 
			for (String p : packages) {
				// Extract relevant info for this package
				if (!p.isEmpty()) {
					String[] entry = p.split("\\|");
					if (entry.length>10) {		
						String name = entry[0];
						String eancode = entry[9];
												
						// Check if ean code is in list sent by author
						if (eancode.equals(ean) && m_map_products.containsKey(eancode)) {
							Product product = m_map_products.get(eancode);
							float p_fap = product.fap;
							float p_efp = product.efp;
							float p_vat = product.vat;

							System.out.println("match with authors' data: " + ean + " | " + eancode + " -> " + name + " | efp = " + p_efp + " | fap = " + p_fap);
							
							// Do something only if there is no EFP and the product EFP is >0
							if (entry[3].isEmpty()) {
								if (p_efp>0.0f)
									entry[3] = String.format("CHF %.2f", p_efp);
								else if (p_fap>0.0f)
									entry[3] = String.format("CHF %.2f", p_fap);
							}

							// FAP
							if (entry[5].isEmpty() && p_fap>0.0f)
								entry[5] = String.format("CHF %.2f", p_fap);
							// VAT
							if (entry[7].isEmpty() && p_vat>0.0f)
								entry[7] = String.format("%.2f", p_vat);;
						}
						// Build new packages string with updated price info!	
						for (int i=0; i<=10; ++i) {
							new_packages_str += entry[i] + "|";
						}
						// Add "visibility" flag
						if (entry.length>11) {
							if (!entry[11].isEmpty()) {
								new_packages_str += entry[11] + "|";
							}
						}
						// Add "free samples"
						if (entry.length>12) {
							if (!entry[12].isEmpty())
								new_packages_str += entry[12];
						}
						new_packages_str += "\n";
					}
				}
			}
			
			// Loop through all packages and generate new pack info str
			packages = packages_str.split("\n");
			// Loop through all packages 
			for (String p : packages) {
				// Extract relevant info for this package
				if (!p.isEmpty()) {
					String[] entry = p.split("\\|");
					if (entry.length>10) {		
						String name = entry[0];
						String entry_8 = "";
						// Remove unnecessary commas in additional info string
						if (!entry[8].isEmpty()) {
							String[] add_info = entry[8].split(",", -1);
							for (String a : add_info) {
								a = a.replaceAll("\\s+", "");
								if (!a.isEmpty())
									entry_8 += (a + ", ");
							}
							// Remove last comma
							if (entry_8.length()>2)
								entry_8 = entry_8.substring(0, entry_8.length()-2);
						}
						if (entry_8.contains("SL")) {
							if (!entry[3].isEmpty())
								new_pack_info_str += name + ", " + entry[3].replace("CHF", "EFP") + " [" + entry_8 + "]\n";
							else if (!entry[5].isEmpty())
								new_pack_info_str += name + ", " + entry[5].replace("CHF", "EFP") + " [" + entry_8 + "]\n";
							else 
								new_pack_info_str += name + " [" + entry_8 + "]\n";
						}
						else
							new_pack_info_str += name + " [" + entry_8 + "]\n";
					} 
				}
			}
		}
		
		return new String[] {new_packages_str, new_pack_info_str};		
	}
			
	public void process() {		
		System.out.println("Post-processing sqlite db: cleaning and updating information...");	
		
		// List all products in 'db', compare with products in 'map_products', flag packages found
		// Note: each package has ONE ean code
		Map<Long, String> map_of_packages_in_sql_db = m_sql_db.mapProducts();
		// Loop through all packages
		for (Map.Entry<Long, String> pack_in_sql_db : map_of_packages_in_sql_db.entrySet()) {
			long id = pack_in_sql_db.getKey();
			String packs[] = pack_in_sql_db.getValue().split("\n");
			for (int i=0; i<packs.length; ++i) {
				if (!packs[i].isEmpty()) {
					String p[] = packs[i].split("\\|");					
					if (p.length==13) {
						String title = p[0].trim();						
						if (title.toLowerCase().startsWith(CmlOptions.OPT_MED_TITLE.toLowerCase())) {
							String eancode = p[9].trim();	
							// Check if eancode has been already processed. 
							// If yes, update group title and pricing information!
							// m_map_products contains the products listed in the authors' excel sheet or csv tables!
							if (m_map_products.containsKey(eancode)) {
								Product product = m_map_products.get(eancode);
								product.processed = true;							
								m_map_products.put(eancode, product);							
								// Update db with group name
								m_sql_db.updateAddInfo(id, product.group_title[lang_id()]);	
								// Update pricing info in "packages_str" (see sql_db)
								String packages_str = m_sql_db.getPackagesWithID(id);
								String pack_info_str = m_sql_db.getPackInfoWithID(id);
								// 
								String new_packs[] = update_packages_str(packages_str, eancode);
								packages_str = new_packs[0];
								pack_info_str = new_packs[1];								
								//
								if (!packages_str.isEmpty())
									m_sql_db.updatePackages(id, packages_str);
								if (!pack_info_str.isEmpty())
									m_sql_db.updatePackInfo(id, pack_info_str);
							}
						}
					}
				}
			}
		}
	}
		
	public void complete(List<String> list_of_authors) {	
		for (String author : list_of_authors) {
			// Generate map of products where the key is the "same" name
			Map<String, List<Product>> map_of_medis = new TreeMap<String, List<Product>>();
			for (Map.Entry<String, Product> entry : m_map_products.entrySet()) {
				Product p = entry.getValue();
				// Check if this author's info needs to be completed...
				// -> compare author and p.author
				if (p.author.toLowerCase().trim().contains(author)) {					
					if (p.title.toLowerCase().startsWith(CmlOptions.OPT_MED_TITLE.toLowerCase())) {
						// Find articles that are NOT in the aips xml
						// ... these articles must be grouped using the "same" name
						if (p.processed==false) {
							String title = p.title.trim();
							if (p.fep>0.0f || p.fap>0.0f) {
								System.out.println("not found in aips xml: " + title + " -> " + p.eancode);						
								List<Product> list_of_products = null;
								if (map_of_medis.containsKey(title))
									list_of_products = map_of_medis.get(title);
								else
									list_of_products = new ArrayList<Product>();
								list_of_products.add(p);
								map_of_medis.put(title, list_of_products);
							}
						}
					}
				}
			}
			
			// Add all the NOT FOUND products to the main sqlite db
			for (Map.Entry<String, List<Product>> entry : map_of_medis.entrySet()) {
				String name = entry.getKey().trim();
				List<Product> list_of_products = entry.getValue();
				String eancode_str = "";
				String pack_info_str = "";
				String add_info_str = "";
				String packages_str = "";
				String auth = "";
				for (Product p : list_of_products) {
					// Not necessary to check the price again -- it's done above
					String u = p.units[0];
					if (CmlOptions.DB_LANGUAGE.equals("fr"))
						u = p.units[1];
					if (u.isEmpty())
						u = na();
					String size = p.size;
					if (size.isEmpty())
						size = na();
					String swissmedic_cat = p.swissmedic_cat;
					if (swissmedic_cat.isEmpty())
						swissmedic_cat = na();
					String fap = String.format("CHF %.2f", p.fap);	// Fabrikabgabepreis = ex factory Preis
					String fep = String.format("CHF %.2f", p.fep);	// Fachhandelseinkaufspreis	
					// 
					auth = p.author;				
					pack_info_str += name.toUpperCase() + ", " + u + ", " + size + ", " + fap + " [" + swissmedic_cat + "]\n";
					add_info_str = p.group_title[lang_id()];
					packages_str += name.toUpperCase() + ", " + u + ", " + size + "|" + p.size + "|" + u + "|" 
							+ p.efp + "|" + p.pp + "|" + fap + "|" + fep + "|" + String.format("%.2f", p.vat) + "|"
							+ p.swissmedic_cat + ",,|" + p.eancode + "|" + p.pharmacode + "|" + p.visible + "|" + p.free_sample + "\n";
					eancode_str += p.eancode + ", ";
				}
				if (eancode_str.endsWith(", "))
					eancode_str = eancode_str.substring(0, eancode_str.length()-2);
				if (list_of_products.size()>0)
					m_sql_db.addExpertDB(name, auth, eancode_str, 2, pack_info_str, add_info_str, packages_str);			
			}
		}
	}
	
	/**
	 * Prune pricing information - these medis cannot be ordered
	 * @param list_of_authors
	 */
	void clean(List<String> list_of_authors) {
		for (String author : list_of_authors) {
			List<Long> list_of_delete = new ArrayList<Long>();
			// List all medis of author in sqlite db (excluing all pseudo FIs)
			Map<Long, String> map_of_medis = m_sql_db.mapMedisExcludingPseudo(author);
			// For each medi, check if ean code is in map_products
			for (Map.Entry<Long, String> medi : map_of_medis.entrySet()) {
				Long index = medi.getKey();
				String packages = medi.getValue();
				list_of_delete.add(index);
				for (Map.Entry<String, Product> product : m_map_products.entrySet()) {
					// As soon as one hit is found, break
					if (packages.contains(product.getKey())) {
						list_of_delete.remove(index);
						break;					
					}
				}
			}
			// Delete unmatched entries
			if (list_of_delete.size()>0) {
				System.out.println("Deleted the packages NOT in the list provided by " + author + "...");
				for (Long idx : list_of_delete) {
					System.out.println(map_of_medis.get(idx));
					m_sql_db.deleteEntry(idx);
				}
			}
		}
	}
}
