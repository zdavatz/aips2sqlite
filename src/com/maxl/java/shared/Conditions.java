package com.maxl.java.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.joda.time.DateTime;

public class Conditions implements java.io.Serializable {

	public String ean_code;
	public String name;
	public float fep_chf;
	public float fap_chf;
	TreeMap<Integer, Float> doctor_A;		// maps units to discount (%)
	TreeMap<Integer, Float> farmacy_A;		// maps units to discount (%)
	TreeMap<Integer, Float> promotion_A;	// maps units to discount (%)
	TreeMap<Integer, Float> doctor_B;		// maps units to discount (%)
	TreeMap<Integer, Float> farmacy_B;		// maps units to discount (%)
	TreeMap<Integer, Float> promotion_B;	// maps units to discount (%)
	TreeMap<Integer, Float> hospital_A;
	TreeMap<Integer, Float> hospital_B;
	TreeMap<Integer, Float> hospital_C;
	List<Integer> promotion_months_A;
	List<Integer> promotion_months_B;	
	List<Integer> promotion_days_A;
	List<Integer> promotion_days_B;
	List<String> doctor_assort;				// maps list of assortable meds
	List<String> farmacy_assort;			// maps list of assortable meds
	List<String> promotion_assort;			// maps list of assortable meds

	public Conditions(String ean_code, String name, float fep_chf, float fap_chf) {
		this.ean_code = ean_code;
		this.name = name;
		this.fep_chf = fep_chf;
		this.fap_chf = fap_chf;		
		doctor_A = new TreeMap<Integer, Float>();
		farmacy_A = new TreeMap<Integer, Float>();
		promotion_A = new TreeMap<Integer, Float>();
		doctor_B = new TreeMap<Integer, Float>();
		farmacy_B = new TreeMap<Integer, Float>();
		promotion_B = new TreeMap<Integer, Float>();	
		hospital_A = new TreeMap<Integer, Float>();
		hospital_B = new TreeMap<Integer, Float>();
		hospital_C = new TreeMap<Integer, Float>();		
		promotion_months_A = new ArrayList<Integer>();
		promotion_months_B = new ArrayList<Integer>();
		promotion_days_A = new ArrayList<Integer>();
		promotion_days_B = new ArrayList<Integer>();
		doctor_assort = new ArrayList<String>();
		farmacy_assort = new ArrayList<String>();
		promotion_assort = new ArrayList<String>();
	}

	public void addDiscountDoc(char category, int units, float discount) {
		if (category=='A')
			doctor_A.put(units, discount);
		else if (category=='B')
			doctor_B.put(units, discount);
	}

	public TreeMap<Integer, Float> getDiscountDoc(char category) {
		if (category=='A')
			return doctor_A;
		else if (category=='B')
			return doctor_B;
		else
			return null;
	}
	
	public void addDiscountFarma(char category, int units, float discount) {
		if (category=='A')
			farmacy_A.put(units, discount);
		else if (category=='B')
			farmacy_B.put(units, discount);
	}

	public TreeMap<Integer, Float> getDiscountFarma(char category) {
		if (category=='A')
			return farmacy_A;
		else if (category=='B')
			return farmacy_B;
		else
			return null;
	}
	
	public void addDiscountPromo(char category, int units, float discount) {
		if (category=='A')
			promotion_A.put(units, discount);
		else if (category=='B')
			promotion_B.put(units, discount);
	}

	public TreeMap<Integer, Float> getDiscountPromo(char category) {
		if (category=='A')
			return promotion_A;
		else if (category=='B')
			return promotion_B;
		else
			return null;
	}

	public void addDiscountHospital(char category, int units, float discount) {
		if (category=='A')
			hospital_A.put(units, discount);
		else if (category=='B')
			hospital_B.put(units, discount);
		else if (category=='C')
			hospital_C.put(units, discount);
	}

	public TreeMap<Integer, Float> getDiscountHospital(char category) {
		if (category=='A')
			return hospital_A;
		else if (category=='B')
			return hospital_B;
		else if (category=='C')
			return hospital_C;
		else
			return null;
	}	
	
	public void setAssortDoc(List<String> assort) {
		doctor_assort = assort;
	}

	public List<String> getAssortDoc() {
		return doctor_assort;
	}
	
	public void setAssortFarma(List<String> assort) {
		farmacy_assort = assort;	
	}

	public List<String> getAssortFarma() {
		return farmacy_assort;
	}
	
	public void setAssortPromo(List<String> assort) {
		promotion_assort = assort;
	}

	public List<String> getAssortPromo() {
		return promotion_assort;
	}

	public void addPromoMonth(int month, char category) {
		if (category=='A')
			promotion_months_A.add(month);
		else if (category=='B')
			promotion_months_B.add(month);
	}
		
	public boolean isPromoMonth(int month, char category) {
		if (category=='A')
			return promotion_months_A.contains(month);
		else if (category=='B')
			return promotion_months_B.contains(month);
		else 
			return false;
	}
	
	public List<Integer> getPromoMonths(char category) {
		if (category=='A')
			return promotion_months_A;
		else if (category=='B')
			return promotion_months_B;
		return null;
	}
	
	public void printPromoMonths(char category) {
		if (category=='A') {
			for (int m : promotion_months_A)
				System.out.println("Promotion month A = " + m);
		} else if (category=='B') {
			for (int m : promotion_months_B)
				System.out.println("Promotion month B = " + m);
		}
	}
	
	public void addPromoTime(int day1, int day2, char category) {
		if (category=='A') {
			promotion_days_A.add(day1);
			promotion_days_A.add(day2);			
		} else if (category=='B') {
			promotion_days_B.add(day1);
			promotion_days_B.add(day2);			
		}
	}

	public boolean isPromoTime(char category) {
		DateTime current_dt = new DateTime();
		int day_of_year = current_dt.getDayOfYear();
		if (category=='A') {
			for (int i=0; i<promotion_days_A.size(); i+=2) {
				if (day_of_year>=promotion_days_A.get(i) && day_of_year<promotion_days_A.get(i))
					return true;
			}
		} else if (category=='B') {
			for (int i=0; i<promotion_days_B.size(); i+=2) {
				if (day_of_year>=promotion_days_B.get(i) && day_of_year<promotion_days_B.get(i))
					return true;
			}
	}
		return false;
	}
	
	public void printPromoTime(char category) {
		if (category=='A') {
			for (int i=0; i<promotion_days_A.size(); i+=2) {
				System.out.println("Promotion day A = [" + promotion_days_A.get(i) + ", " + promotion_days_A.get(i+1) + "]");
			}
		} else if (category=='B') {
			for (int i=0; i<promotion_days_B.size(); i+=2) {
				System.out.println("Promotion day B = [" + promotion_days_B.get(i) + ", " + promotion_days_B.get(i+1) + "]");
			}
		}
	}
}
