package com.maxl.java.shared;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import org.joda.time.DateTime;

public class Conditions implements java.io.Serializable {

	public String ean_code;
	public String name;
	public float fep_chf;
	public float fap_chf;
	HashMap<Character, TreeMap<Integer, Float>> doctor;			  // arzt
	HashMap<Character, TreeMap<Integer, Float>> pharmacy;		  // apotheke
	HashMap<Character, TreeMap<Integer, Float>> pharmacy_promo;	  // promotion apotheke
	HashMap<Character, TreeMap<Integer, Float>> drugstore;		  // drogerie
	HashMap<Character, TreeMap<Integer, Float>> drugstore_promo;  // promotion drogerie
	HashMap<Character, TreeMap<Integer, Float>> hospital;		  // spital
	HashMap<Character, List<Integer>> pharmacy_promo_months;
	HashMap<Character, List<Integer>> drugstore_promo_months;
	HashMap<Character, List<Integer>> pharmacy_promo_days;		
	HashMap<Character, List<Integer>> drugstore_promo_days;		
	TreeMap<String, List<String>> assort;
	
	public Conditions(String ean_code, String name, float fep_chf, float fap_chf) {
		this.ean_code = ean_code;
		this.name = name;
		this.fep_chf = fep_chf;
		this.fap_chf = fap_chf;		
		doctor = new HashMap<Character, TreeMap<Integer, Float>>();
		pharmacy = new HashMap<Character, TreeMap<Integer, Float>>();
		pharmacy_promo = new HashMap<Character, TreeMap<Integer, Float>>();
		drugstore = new HashMap<Character, TreeMap<Integer, Float>>();
		drugstore_promo = new HashMap<Character, TreeMap<Integer, Float>>();
		hospital = new HashMap<Character, TreeMap<Integer, Float>>();
		pharmacy_promo_months = new HashMap<Character, List<Integer>>();
		drugstore_promo_months = new HashMap<Character, List<Integer>>();
		pharmacy_promo_days = new HashMap<Character, List<Integer>>();	
		drugstore_promo_days = new HashMap<Character, List<Integer>>();	
		assort = new TreeMap<String, List<String>>();
	}

	public void addDiscountDoctor(char category, int units, float discount) {
		TreeMap<Integer, Float> reb = null;
		if (doctor.get(category)!=null)
			reb = doctor.get(category);
		else
			reb = new TreeMap<Integer, Float>();
		reb.put(units, discount);
		doctor.put(category, reb);
	}

	public TreeMap<Integer, Float> getDiscountDoctor(char category) {		
		return doctor.get(category);
	}

	public void addDiscountPharmacy(char category, int units, float discount, boolean promo) {
		TreeMap<Integer, Float> reb = null;
		if (promo==false) {
			if (pharmacy.get(category)!=null)
				reb = pharmacy.get(category);
			else
				reb = new TreeMap<Integer, Float>();
			reb.put(units, discount);
			pharmacy.put(category, reb);
		} else {
			if (pharmacy_promo.get(category)!=null)
				reb = pharmacy_promo.get(category);
			else
				reb = new TreeMap<Integer, Float>();
			reb.put(units, discount);
			pharmacy_promo.put(category, reb);
		}
	}

	public TreeMap<Integer, Float> getDiscountPharmacy(char category, boolean promo) {
		if (promo==false)
			return pharmacy.get(category);
		else
			return pharmacy_promo.get(category);
	}

	public void addDiscountDrugstore(char category, int units, float discount, boolean promo) {
		TreeMap<Integer, Float> reb = null;
		if (promo==false) {
			if (drugstore.get(category)!=null)
				reb = drugstore.get(category);
			else
				reb = new TreeMap<Integer, Float>();
			reb.put(units, discount);
			drugstore.put(category, reb);
		} else {
			if (drugstore_promo.get(category)!=null)
				reb = drugstore_promo.get(category);
			else
				reb = new TreeMap<Integer, Float>();
			reb.put(units, discount);
			drugstore_promo.put(category, reb);
		}
	}

	public TreeMap<Integer, Float> getDiscountDrugstore(char category, boolean promo) {
		if (promo==false)
			return drugstore.get(category);
		else
			return drugstore_promo.get(category);
	}
	
	public void addDiscountHospital(char category, int units, float discount) {
		TreeMap<Integer, Float> reb = null;
		if (hospital.get(category)!=null)
			reb = hospital.get(category);
		else
			reb = new TreeMap<Integer, Float>();
		reb.put(units, discount);
		hospital.put(category, reb);
	}

	public TreeMap<Integer, Float> getDiscountHospital(char category) {
		return hospital.get(category);
	}	
	
	public void setAssort(String customer_type, List<String> ass) {
		assort.put(customer_type, ass);
	}

	public List<String> getAssort(String customer_type) {
		return assort.get(customer_type);
	}
	
	public void addPromoMonth(String customer_type, char category, int month) {
		List<Integer> m = null;
		if (customer_type.equals("pharmacy")) {
			if (pharmacy_promo_months.get(category)!=null)
				m = pharmacy_promo_months.get(category);
			else
				m = new ArrayList<Integer>();
			m.add(month);
			pharmacy_promo_months.put(category, m);
		} else if (customer_type.equals("drugstore")) {
			if (drugstore_promo_months.get(category)!=null)
				m = drugstore_promo_months.get(category);
			else
				m = new ArrayList<Integer>();
			m.add(month);
			drugstore_promo_months.put(category, m);
		}
	}
		
	public boolean isPromoMonth(String customer_type, char category, int month) {
		if (customer_type.equals("pharmacy"))
			return pharmacy_promo_months.get(category).contains(month);
		else if (customer_type.equals("drugstore"))
			return drugstore_promo_months.get(category).contains(month);
		else
			return false;
	}
	
	public List<Integer> getPromoMonths(String customer_type, char category) {
		if (customer_type.equals("pharmacy"))
			return pharmacy_promo_months.get(category);
		else if (customer_type.equals("drugstore"))
			return drugstore_promo_months.get(category);
		else 
			return null;
	}
	
	public void printPromoMonths(String customer_type, char category) {
		if (customer_type.equals("pharmacy")) {
			for (int m : pharmacy_promo_months.get(category))
				System.out.println(category + "-pharmacy promotion month = " + m);		
		} else if (customer_type.equals("drugstore")) {
			for (int m : pharmacy_promo_months.get(category))
				System.out.println(category + "-drugstore promotion month = " + m);					
		}
	}
	
	public void addPromoTime(String customer_type, char category, int day1, int day2) {
		List<Integer> d = null;
		if (customer_type.equals("pharmacy")) {
			if (pharmacy_promo_days.get(category)!=null)
				d = pharmacy_promo_days.get(category);
			else
				d = new ArrayList<Integer>();
			d.add(day1);
			d.add(day2);
			pharmacy_promo_days.put(category, d);
		} else if (customer_type.equals("drugstore")) {
			if (drugstore_promo_days.get(category)!=null)
				d = drugstore_promo_days.get(category);
			else
				d = new ArrayList<Integer>();
			d.add(day1);
			d.add(day2);
			drugstore_promo_days.put(category, d);			
		}
	}

	public boolean isPromoTime(String customer_type, char category) {
		DateTime current_dt = new DateTime();
		int day_of_year = current_dt.getDayOfYear();
		if (customer_type.equals("pharmacy")) {
			for (int i=0; i<pharmacy_promo_days.get(category).size(); i+=2) {
				List<Integer> d = pharmacy_promo_days.get(category);
				if (day_of_year>=d.get(i) && day_of_year<d.get(i)) {
					return true;
				}
			}			
		} else if (customer_type.equals("drugstore")) {
			for (int i=0; i<drugstore_promo_days.get(category).size(); i+=2) {
				List<Integer> d = drugstore_promo_days.get(category);
				if (day_of_year>=d.get(i) && day_of_year<d.get(i)) {
					return true;
				}
			}						
		}
		return false;
	}
	
	public void printPromoTime(String customer_type, char category) {
		if (customer_type.equals("pharmacy")) {
			List<Integer> d = pharmacy_promo_days.get(category);			
			for (int i=0; i<d.size(); i+=2)
				System.out.println(category + "-pharmacy promo days = [" + d.get(i) + ", " + d.get(i+1) + "]");				
		} else if (customer_type.equals("drugstore")) {
			List<Integer> d = drugstore_promo_days.get(category);			
			for (int i=0; i<d.size(); i+=2)
				System.out.println(category + "-drugstore promo days = [" + d.get(i) + ", " + d.get(i+1) + "]");							
		}
	}
}
