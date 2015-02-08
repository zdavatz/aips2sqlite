package com.maxl.java.aips2sqlite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
	
	public void process() {
		
		System.out.println("Processing all non-swissmedic xml products ...");	
		
		// List all products in 'db', compare with products in 'map_products', flag packages found
		List<String> list_of_packages = m_sql_db.listProducts();
		// Loop through list of packages
		for (String pack : list_of_packages) {
			String packs[] = pack.split("\n");
			for (int i=0; i<packs.length; ++i) {
				if (!packs[i].isEmpty()) {
					String p[] = packs[i].split("\\|");
					if (p.length==12) {
						String title = p[0].trim();
						if (title.toLowerCase().startsWith(CmlOptions.OPT_MED_TITLE.toLowerCase())) {
							String eancode = p[9].trim();	
							// Check if eancode has been already processed
							if (m_map_products.containsKey(eancode)) {
								Product product = m_map_products.get(eancode);
								product.processed = true;
								m_map_products.put(eancode, product);
							}
						}
					}
				}
			}
		}
		
		// Generate map of products where the key is the "same" name
		Map<String, List<Product>> map_of_medis = new TreeMap<String, List<Product>>();
		for (Map.Entry<String, Product> entry : m_map_products.entrySet()) {
			Product p = entry.getValue();
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
		
		// Add all the non found products to the main sqlite db
		for (Map.Entry<String, List<Product>> entry : map_of_medis.entrySet()) {
			String name = entry.getKey().trim();
			List<Product> list_of_products = entry.getValue();
			String eancode_str = "";
			String pack_info_str = "";
			String packages_str = "";
			String author = "";
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
				String fap = String.format("CHF %.2f", p.fap);	// Fabrikabgabepreis
				String fep = String.format("CHF %.2f", p.fep);	// Fachhandelseinkaufspreis	
				// 
				author = p.author;
				pack_info_str += name.toUpperCase() + ", " + u + ", " + size + ", " + fap + " [" + swissmedic_cat + "]\n";
				packages_str += name.toUpperCase() + ", " + u + ", " + size + "|" + p.size + "|" + u + "|" 
						+ p.efp + "|" + p.pp + "|" + fap + "|" + fep + "|" + String.format("%.2f", p.vat) + "|"
						+ p.swissmedic_cat + ",,|" + p.eancode + "|" + p.pharmacode + "|" + p.visible + "\n";
				eancode_str += p.eancode + ", ";
			}
			if (eancode_str.endsWith(", "))
				eancode_str = eancode_str.substring(0, eancode_str.length()-2);
			m_sql_db.addExpertDB(name, author, eancode_str, 2, pack_info_str, packages_str);			
		}	
	}
	
	void clean(String author) {
		List<Long> list_of_delete = new ArrayList<Long>();
		// List all medis of author in sqlite db)
		Map<Long, String> map_of_medis = m_sql_db.mapMedis(author);
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
			System.out.println("Deleted the following packages...");
			for (Long idx : list_of_delete) {
				System.out.println(map_of_medis.get(idx));
				m_sql_db.deleteEntry(idx);
			}
		}
	}
}
