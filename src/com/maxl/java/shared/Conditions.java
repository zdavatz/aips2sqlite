package com.maxl.java.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class Conditions implements java.io.Serializable {

	public String ean_code;
	public String name;
	float fep_chf;
	float gross_chf;
	TreeMap<Integer, Float> doctor;		// maps units to discount (%)
	TreeMap<Integer, Float> farmacy;	// maps units to discount (%)
	TreeMap<Integer, Float> promotion;	// maps units to discount (%)
	List<String> doctor_assort;			// maps list of assortable meds
	List<String> farmacy_assort;		// maps list of assortable meds
	List<String> promotion_assort;		// maps list of assortable meds
	List<Integer> promotion_months;

	public Conditions(String ean_code, String name, float fep_chf, float gross_chf) {
		this.ean_code = ean_code;
		this.name = name;
		this.fep_chf = fep_chf;
		this.gross_chf = gross_chf;
		doctor = new TreeMap<Integer, Float>();
		farmacy = new TreeMap<Integer, Float>();
		promotion = new TreeMap<Integer, Float>();
		doctor_assort = new ArrayList<String>();
		farmacy_assort = new ArrayList<String>();
		promotion_assort = new ArrayList<String>();
		promotion_months = new ArrayList<Integer>();
	}

	public void addDiscountDoc(int units, float discount) {
		doctor.put(units, discount);
	}

	public void addDiscountFarma(int units, float discount) {
		farmacy.put(units, discount);
	}

	public void addDiscountPromo(int units, float discount) {
		promotion.put(units, discount);
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
}
