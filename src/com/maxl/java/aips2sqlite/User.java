package com.maxl.java.aips2sqlite;

public class User implements java.io.Serializable {

	private static final long serialVersionUID = 1L;
	
	String gln_code = "";
	String addr_type = "";	// S: shipping, B: billing, O: Office	
	String category = "";	// arzt, spital, drogerie, ...
	String title = "";
	String first_name = "";	
	String last_name = "";	
	String name1 = "";		// company name 1
	String name2 = "";		// company name 2
	String name3 = "";		// company name 3
	String street = "";		// street / pobox
	String number = "";
	String zip = "";
	String city = "";
	String country = "";
	String phone = "";
	String fax = "";
	String email = "";
	boolean selbst_disp = false;
	boolean bet_mittel = false;
	boolean is_human = true;
	
	User() {
		// Struct-like class... 'nough said.
	}
}
