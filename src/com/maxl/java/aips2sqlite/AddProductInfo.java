package com.maxl.java.aips2sqlite;

import java.util.List;
import java.util.Map;

public class AddProductInfo {

	// Main sqlite database
	SqlDatabase m_sql_db;
	// Map of products
	private Map<String, Product> m_map_products = null;	
	
	public AddProductInfo(SqlDatabase sql_db, Map<String, Product> map_products) {
		m_sql_db = sql_db;
		m_map_products = map_products;
	}
	
	public void process() {
		
		System.out.println("Processing all non-swissmedic xml products ...");	
		
		// List all products in 'db', compare with products in 'map_products', flag packages found
		List<String> list_of_packages = m_sql_db.listProducts();
		for (String pack : list_of_packages) {
			String packs[] = pack.split("\n");
			for (int i=0; i<packs.length; ++i) {
				if (!packs[i].isEmpty()) {
					String p[] = packs[i].split("\\|");
					if (p.length==12) {
						String title = p[0].trim();
						if (title.toLowerCase().startsWith(CmlOptions.OPT_MED_TITLE.toLowerCase())) {
							String eancode = p[9].trim();	
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
		// 		
		for (Map.Entry<String, Product> entry : m_map_products.entrySet()) {
			Product p = entry.getValue();
			if (p.title.toLowerCase().startsWith(CmlOptions.OPT_MED_TITLE.toLowerCase())) {
				if (p.processed==false) {
					System.out.println("not found: " + p.title + " -> " + p.eancode);
					if (p.fep>0.0f || p.fap>0.0f) {
						String u = p.units[0];
						if (CmlOptions.DB_LANGUAGE.equals("fd"))
							u = p.units[1];
						if (u.isEmpty())
							u = "k.A.";
						String size = p.size;
						if (size.isEmpty())
							size = "k.A.";
						String swissmedic_cat = p.swissmedic_cat;
						if (swissmedic_cat.isEmpty())
							swissmedic_cat = "k.A.";
						String title = p.title.trim();
						String fap = String.format("CHF %.2f", p.fap);	// Fabrikabgabepreis
						String fep = String.format("CHF %.2f", p.fep);	// Fachhandelseinkaufspreis
						String pack_info_str = title + ", " + u + " " + size + ", " + fap + " [" + swissmedic_cat + "]";
						String packages_str = title + "|" + p.size + "|" + u + "|" 
								+ p.efp + "|" + p.pp + "|" + fap + "|" + fep + "|" + String.format("%.2f", p.vat) + "|"
								+ p.swissmedic_cat + ",,|" + p.eancode + "|" + p.pharmacode + "|" + p.visible + "\n";
						m_sql_db.addExpertDB(title, p.author, p.eancode, 2, pack_info_str, packages_str);
					}
				}
			}
		}
	}
}
