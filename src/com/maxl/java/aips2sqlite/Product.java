package com.maxl.java.aips2sqlite;

public class Product {
	boolean processed = false;
	public String title = "";
	public String author = "";
	public String eancode = "";
	public String pharmacode = "";
	public String size = "";
	public String units[] = {"", ""};	// 0: DE, 1: FR
	public String swissmedic_cat = "";
	public float efp = 0.0f;
	public float pp = 0.0f;
	public float fep = 0.0f;
	public float fap = 0.0f;
	public float vat = 8.0f;	// [%]
	public int visible = 0xff;	
}
