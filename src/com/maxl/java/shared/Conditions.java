package com.maxl.java.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class Conditions implements java.io.Serializable {

	public String ean_code;
	public String name;
	public float fep_chf;
	public float gross_chf;
	TreeMap<Integer, Float> doctor_A;		// maps units to discount (%)
	TreeMap<Integer, Float> farmacy_A;		// maps units to discount (%)
	TreeMap<Integer, Float> promotion_A;	// maps units to discount (%)
	TreeMap<Integer, Float> doctor_B;		// maps units to discount (%)
	TreeMap<Integer, Float> farmacy_B;		// maps units to discount (%)
	TreeMap<Integer, Float> promotion_B;	// maps units to discount (%)
	List<String> doctor_assort;				// maps list of assortable meds
	List<String> farmacy_assort;			// maps list of assortable meds
	List<String> promotion_assort;			// maps list of assortable meds
	List<Integer> promotion_months;

	public Conditions(String ean_code, String name, float fep_chf, float gross_chf) {
		this.ean_code = ean_code;
		this.name = name;
		this.fep_chf = fep_chf;
		this.gross_chf = gross_chf;		
		doctor_A = new TreeMap<Integer, Float>();
		farmacy_A = new TreeMap<Integer, Float>();
		promotion_A = new TreeMap<Integer, Float>();
		doctor_B = new TreeMap<Integer, Float>();
		farmacy_B = new TreeMap<Integer, Float>();
		promotion_B = new TreeMap<Integer, Float>();		
		doctor_assort = new ArrayList<String>();
		farmacy_assort = new ArrayList<String>();
		promotion_assort = new ArrayList<String>();
		promotion_months = new ArrayList<Integer>();
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

	public void setAssortDoc(List<String> assort) {
		doctor_assort = assort;
	}

	public void setAssortFarma(List<String> assort) {
		farmacy_assort = assort;
	}

	public void setAssortPromo(List<String> assort) {
		promotion_assort = assort;
	}

	public void addPromoMonth(int month) {
		promotion_months.add(month);
	}
	
	public boolean isPromoMonth(int month) {
		return promotion_months.contains(month);
	}
}
