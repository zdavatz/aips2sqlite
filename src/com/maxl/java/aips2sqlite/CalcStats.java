package com.maxl.java.aips2sqlite;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.stat.Frequency;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

public class CalcStats {

	Map<String, String> m_ean_customer_class_map = new HashMap<String, String>();
	Map<String, String> m_ean_to_name_map = new HashMap<String, String>();
	Map<String, Customer> map_of_doctors = new HashMap<String, Customer>();
	Map<String, Customer> map_of_pharmacies = new HashMap<String, Customer>();
	Map<String, Customer> map_of_rest_of_world = new HashMap<String, Customer>();
	Map<String, Customer> map_of_all_customers = new HashMap<String, Customer>();

	String m_user = "";

	Logger logger;
	
	private class Article {
		
		String ean_code;
		String name;
		int quantity;
		float price_CHF;		
	}
	
	private class Customer {

		String ean_code = "";
		String addr_code = "";
		String group = "";
		String name = "";
		List<Article> list_of_articles;
				
		float getTotalPaid() {
			float sum = 0.0f;
			for (Article a : list_of_articles) {
				sum += a.price_CHF;
			}
			return sum;
		}
		
		int numArticlesBought() {
			return list_of_articles.size();
		}
	}
	
	private class Logger {
	   
		PrintWriter out = null;
		
		Logger() {
			try {
				out = new PrintWriter(new FileWriter(Constants.DIR_OUTPUT + "/calc_stats_log.txt"/*, true*/) /*,true*/);
			} catch(IOException e) {
				e.printStackTrace();
			}				
		}
		
		void log(String message) { 
			System.out.println(message);
			out.write(message + "\r\n");
	    }
		
		void close() {
			out.close();
		}
	}
	
	public CalcStats(String user) {
		m_user = user;
		logger = new Logger();
	}

	public void loadGlnCodesCsv() {
		try {
			System.out.print("Loading gln_codes_csv... ");
			CSVReader reader = new CSVReader(new InputStreamReader(
					new FileInputStream(Constants.DIR_OUTPUT + "/gln_codes_csv.csv"), "UTF-8"), '|');
			List<String[]> myEntries = reader.readAll();
			for (String[] s : myEntries) {
				if (s.length>4) {
					String ean_code = s[0];
					String customer_class = s[4];
					m_ean_customer_class_map.put(ean_code, customer_class);
				}
			}
			reader.close();
			System.out.println("done.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void loadIbsaFiles() {
		try {
			String[] file_names = {"HYR1_2014.xlsx", "HYR2_2014.xlsx", "HYR1_2015.xlsx"};
			for (String file : file_names) {
				System.out.println("Processing file " + file + "...");
				// Load IBSA xlsx file			
				FileInputStream sales_file = new FileInputStream(Constants.DIR_IBSA + file);
				// Get workbook instance for XLSX file (XSSF = Horrible SpreadSheet Format)
				XSSFWorkbook sales_workbook = new XSSFWorkbook(sales_file);
				// Get first sheet from workbook
				XSSFSheet sales_sheet = sales_workbook.getSheetAt(0);			
				// Iterate through all rows of sheet
				Iterator<Row> row_iterator = sales_sheet.iterator();
				int num_rows = 0;			
				while (row_iterator.hasNext()) {
					Row row = row_iterator.next();
					if (num_rows>0) {
						Article article = new Article();
						String customer_ean = "";
						String customer_addr = "";
						String customer_group = "";
						if (row.getCell(4)!=null)
							customer_addr = ExcelOps.getCellValue(row.getCell(4));
						if (row.getCell(6)!=null)
							customer_ean = ExcelOps.getCellValue(row.getCell(6));
						//
						if (customer_addr!=null && !customer_addr.isEmpty()) {
							// Extract customer group (Kundengruppe)
							if (row.getCell(2)!=null)
								customer_group = ExcelOps.getCellValue(row.getCell(2));
							// Check if customer is already in list, else initialize a new one
							Customer customer = null;
							if (getCustomerGroup(customer_group).equals("arzt")) {
								if (map_of_doctors.containsKey(customer_addr))
									customer = map_of_doctors.get(customer_addr);
							} else if (getCustomerGroup(customer_group).equals("apotheke")) {
								if (map_of_pharmacies.containsKey(customer_addr))
									customer = map_of_pharmacies.get(customer_addr);
							} else {
								if (map_of_rest_of_world.containsKey(customer_addr))
									customer = map_of_rest_of_world.get(customer_addr);
							}
							if (customer==null) {
								customer = new Customer(); 	
								customer.ean_code = customer_ean;
								customer.addr_code = customer_addr;
								customer.list_of_articles = new ArrayList<Article>();
							}
							//
							if (row.getCell(12)!=null) {
								if (customer.name.isEmpty())
									customer.name = ExcelOps.getCellValue(row.getCell(12));
							}
							if (row.getCell(18)!=null) {
								article.ean_code = ExcelOps.getCellValue(row.getCell(18));
							}
							if (row.getCell(20)!=null) {
								article.name = ExcelOps.getCellValue(row.getCell(20));
								if (!article.ean_code.isEmpty())
									m_ean_to_name_map.put(article.ean_code, article.name);
							}
							if (row.getCell(23)!=null) {
								String quantity = ExcelOps.getCellValue(row.getCell(23));
								article.quantity = Float.valueOf(quantity).intValue();
							}
							if (row.getCell(25)!=null) {
								String price_CHF = ExcelOps.getCellValue(row.getCell(25));
								article.price_CHF = Float.valueOf(price_CHF);
							}					
							if (!article.ean_code.isEmpty()) {
								customer.list_of_articles.add(article);							
								if (getCustomerGroup(customer_group).equals("arzt")) {
									map_of_doctors.put(customer_addr, customer);
								} else if (getCustomerGroup(customer_group).equals("apotheke"))
									map_of_pharmacies.put(customer_addr, customer);
								else
									map_of_rest_of_world.put(customer_addr, customer);
								// This list contains ALL customers
								map_of_all_customers.put(customer_addr, customer);
							}
						} else {
							System.out.println("EAN code not found...");
						}
					}
					num_rows++;
				}
			}
			System.out.println("Loading revenue files done.");
			
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}
	
	public void processIbsaData() {
		loadGlnCodesCsv();
		loadIbsaFiles();
		
		System.out.println("Processing data...");
		
		// Generate lists (easier to work with)
		List<Customer> list_of_doctors = new ArrayList<Customer>(map_of_doctors.values());
		List<Customer> list_of_pharmacies = new ArrayList<Customer>(map_of_pharmacies.values());
		List<Customer> list_of_rest_of_world = new ArrayList<Customer>(map_of_rest_of_world.values());		
		List<Customer> list_of_all_customers = new ArrayList<Customer>(map_of_all_customers.values());				
		List<Customer> list_of_doctors_and_pharmacies = new ArrayList<Customer>();
		list_of_doctors_and_pharmacies.addAll(list_of_doctors);
		list_of_doctors_and_pharmacies.addAll(list_of_pharmacies);		

		// Generate 
		processIbsaList(list_of_doctors, "doctors", "ibsa_doc_stats.csv");		
		processIbsaList(list_of_pharmacies, "pharmacies", "ibsa_pharma_stats.csv");
		processIbsaList(list_of_doctors_and_pharmacies, "docs + pharmas", "ibsa_docs_and_pharma_stats.csv");
		processIbsaList(list_of_rest_of_world, "rest of world", "ibsa_rest_of_world_stats.csv");
		processIbsaList(list_of_all_customers, "all customers", "ibsa_all_customers_stats.csv");		
		
		logger.close();
	}
			
	private void processIbsaList(List<Customer> list_of_customers, String type, String file_name) {
		// Sort according to revenue generated by customer
		Collections.sort(list_of_customers, new Comparator<Customer>() {
			@Override
			public int compare(Customer c1, Customer c2) {
				return (int)(c2.getTotalPaid() - c1.getTotalPaid());
			}
		});	
				
		NumberFormat formatter = new DecimalFormat("#0.00");
				
		// Cumulated revenue starting from top-ranked (most valued customer)
		float tot_revenues_customers = totRevenuesForList(list_of_customers);		
		int tot_num_customers = list_of_customers.size();
		logger.log("Total num. " + type + " = " + tot_num_customers + " with total revenues of " + String.format("%,.2f", tot_revenues_customers));
		
		float tot = 0.0f;
		int tot_articles = 0;
		int doc_index = 1;
		for (Customer doc : list_of_customers) {
			tot += doc.getTotalPaid();
			tot_articles += doc.numArticlesBought();
			if (tot>0.8*tot_revenues_customers)
				break;				
			doc_index++;
		}
		logger.log(doc_index + " " + type + " (" + formatter.format((doc_index*100.0f)/tot_num_customers) + "%)" 
				+ " are responsible for 80% of the revenues, i.e., " + String.format("%,.2f", tot) + " for " + tot_articles + " articles");
		logger.log("");
		
		// Extract list of all articles that these most valuable customers bought
		List<Article> cum_list_of_articles = new ArrayList<Article>();
		for (int k=0; k<doc_index; ++k) {
			cum_list_of_articles.addAll(list_of_customers.get(k).list_of_articles);
		}
		// Test
		System.out.println();
		
		// 1. Global relative frequency histogram
		Frequency f = new Frequency();
		for (Article a : cum_list_of_articles) {
			f.addValue(a.quantity);
		}
		/* 
		// TEST
		Iterator<Map.Entry<Comparable<?>, Long>> iter = f.entrySetIterator();
		while (iter.hasNext()) {	
			Map.Entry<Comparable<?>, Long> entry = iter.next();
			System.out.println(entry.getKey() + " -> " + entry.getValue());
		}
		*/	
		// 2. Individual (drug-based) frequency histogram
		Map<String, Frequency> map_meds_and_quants = new HashMap<String, Frequency>();
		for (Article a : cum_list_of_articles) {
			String ean = a.ean_code;
			Frequency hist_of_quantities = new Frequency();
			if (map_meds_and_quants.containsKey(ean))
				hist_of_quantities = map_meds_and_quants.get(ean);
			hist_of_quantities.addValue(a.quantity);
			map_meds_and_quants.put(ean, hist_of_quantities);
		}
		try {
			CSVWriter writer = new CSVWriter(new FileWriter(Constants.DIR_OUTPUT + "/" + file_name), '|', CSVWriter.NO_QUOTE_CHARACTER);
			for (Map.Entry<String, Frequency> entry : map_meds_and_quants.entrySet()) {
				List<String> csv_line = new ArrayList<String>();
				String ean_code = entry.getKey();
				String name = m_ean_to_name_map.get(ean_code);
				csv_line.add(name);
				csv_line.add(ean_code);
				Iterator<Map.Entry<Comparable<?>, Long>> iter2 = entry.getValue().entrySetIterator();
				// Loop through frequency histogram
				while (iter2.hasNext()) {	
					Map.Entry<Comparable<?>, Long> entry2 = iter2.next();
					csv_line.add(entry2.getKey() + "(" + entry2.getValue() + ")");
				}
				// Change the list to an array
				String[] csv_line_arr = csv_line.toArray(new String[csv_line.size()]);
				writer.writeNext(csv_line_arr);
			}
			writer.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	private String getCustomerGroup(String group) {
		if (group.equals("A.Ärzte") || group.equals("A.Praxis") || group.equals("A.Prx+Sp") || group.equals("Zahn"))
			return "arzt";
		else if (group.equals("Apotheke") || group.equals("Psy/Apo"))
			return "apotheke";
		else
			return "row";
	}
	
	private float totPriceForArticleList(List<Article> list_of_articles) {
		float tot = 0.0f;
		for (Article a : list_of_articles)
			tot += a.price_CHF;
		return tot;
	}
	
	private float totRevenuesForList(List<Customer> list_of_customers) {
		float tot = 0.0f;
		for (Customer c : list_of_customers) 
			tot += c.getTotalPaid();
		return tot;
	}
	
	private Customer findCustomerInList(String ean, List<Customer> list_of_customers) {
		for (Customer c : list_of_customers) {
			if (c.ean_code.equals((ean)))
				return c;
		}
		return null;
	}

	private Customer findCustomerInSet(String ean, Set<Customer> set_of_customers) {
		for (Customer c : set_of_customers) {
			if (c.ean_code.equals((ean)))
				return c;
		}
		return null;
	}	
}

