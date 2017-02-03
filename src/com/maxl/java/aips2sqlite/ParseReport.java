package com.maxl.java.aips2sqlite;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities.EscapeMode;

public class ParseReport {

	private boolean mHeaderAdded = false;
	private StringBuilder mReport;
	private String mFileName = "";	
	private String mLanguage = "";
	private String mStyleSheet = "";
	private BufferedWriter mBWriter = null;

	public ParseReport(String reportBase, String language, String extension) {

		mReport = new StringBuilder();
		DateFormat df = new SimpleDateFormat("ddMMyy");
		String date_str = df.format(new Date());
		mFileName = reportBase + "_" + date_str + "_" + language + "." + extension;
		mLanguage = language;

		try {
			File report_file = new File(mFileName);
			if (!report_file.exists()) {
				report_file.getParentFile().mkdirs();
				report_file.createNewFile();
			}
			mBWriter = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(report_file.getAbsoluteFile()), "UTF-8"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	public BufferedWriter getBWriter() {		
		return mBWriter;
	}
	
	public String getLanguage() {
		return mLanguage;
	}	
	
	public void addStyleSheet(String styleSheet) {
		mStyleSheet = styleSheet;
	}
	
	public void append(String str) {
		mReport.append(str);
	}
	
	public void addHtmlHeader(String title, String version) {
		DateFormat df = new SimpleDateFormat("dd.MM.yy");
		String date_str = df.format(new Date());

		mReport.append("<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" /></head>");
		if (!mStyleSheet.isEmpty())
			mReport.append("<style>" + mStyleSheet + "</style>");
		if (mLanguage.equals("de")) {
			mReport.append("<h3>" + title + "</h3>");
			mReport.append("<p>Version " + version + " - " + date_str + "</p>");
			mReport.append("<p>Lizenz: GPL v3.0</p>");
			mReport.append("<br/>");
			mReport.append("<p>Konzept: Zeno R.R. Davatz - <a target=\"_new\" href=\"http://www.ywesee.com\">ywesee GmbH</a></p>"); 
			mReport.append("<p>Entwicklung: Dr. Max Lungarella - <a target=\"_new\" href=\"http://www.dynamicdevices.ch\">Dynamic Devices AG</a></p>");
			mReport.append("<br/>");
			mReport.append("<p>Verwendete Files:</p>");
			mReport.append("<p>- <a target=\"_new\" href=\"http://download.swissmedicinfo.ch\">AIPS.xml</a> (Stand: " + date_str + ")</p>");
			mReport.append("<p>- <a target=\"_new\" href=\"http://bag.e-mediat.net/SL2007.Web.External/Default.aspx?webgrab=ignore\">Preparations.xml</a> (Stand: " + date_str + ")</p>");
			mReport.append("<p>- <a target=\"_new\" href=\"http://www.refdata.ch/downloads/company/download/swissindex_TechnischeBeschreibung.pdf\">swissindex.xml</a> (Stand: " + date_str + ")</p>");
			mReport.append("<p>- <a target=\"_new\" href=\"http://www.swissmedic.ch/daten/00080/00251/index.html?lang=de&download=NHzLpZeg7t,lnp6I0NTU042l2Z6ln1acy4Zn4Z2qZpnO2Yuq2Z6gpJCDdH56fWym162epYbg2c_JjKbNoKSn6A--&.xls\">" +
					"Packungen.xls</a> (Stand: " + date_str + ")</p>");
			mReport.append("<br/>");
			mHeaderAdded = true;			
		} else if (mLanguage.equals("fr")){
			mReport.append("<h3>Compendium des M�dicaments Suisse</h3>");
			mReport.append("<p>Version " + version + " - " + date_str + "</p>");
			mReport.append("<p>Licence: GPL v3.0</p>");
			mReport.append("<br/>");
			mReport.append("<p>Concept: Zeno R.R. Davatz - <a target=\"_new\" href=\"http://www.ywesee.com\">ywesee GmbH</a></p>"); 
			mReport.append("<p>D�veloppement: Dr. Max Lungarella - <a target=\"_new\" href=\"http://www.dynamicdevices.ch\">Dynamic Devices AG</a></p>");
			mReport.append("<br/>");
			mReport.append("<p>Fichiers utilis�s:</p>");
			mReport.append("<p>- <a target=\"_new\" href=\"http://download.swissmedicinfo.ch\">AIPS.xml</a> (actualis�: " + date_str + ")</p>");
			mReport.append("<p>- <a target=\"_new\" href=\"http://bag.e-mediat.net/SL2007.Web.External/Default.aspx?webgrab=ignore\">Preparations.xml</a> (actualis�: " + date_str + ")</p>");
			mReport.append("<p>- <a target=\"_new\" href=\"http://www.refdata.ch/downloads/company/download/swissindex_TechnischeBeschreibung.pdf\">swissindex.xml</a> (actualis�: " + date_str + ")</p>");
			mReport.append("<p>- <a target=\"_new\" href=\"http://www.swissmedic.ch/daten/00080/00251/index.html?lang=de&download=NHzLpZeg7t,lnp6I0NTU042l2Z6ln1acy4Zn4Z2qZpnO2Yuq2Z6gpJCDdH56fWym162epYbg2c_JjKbNoKSn6A--&.xls\">" +
					"Packungen.xls</a> (actualis�: " + date_str + ")</p>");
			mReport.append("<br/>");			
			mHeaderAdded = true;			
		} 
	}
		
	public String treemapToHtmlTable(TreeMap<String, ArrayList<String>> error_map) {
		StringBuilder html = new StringBuilder();
		html.append("<table>");
		html.append("<tr><th>Owner</th>" +
				"<th>Swissmedic-No5 fehlt im Packungen.xls von Swissmedic</th>" +
				"<th>ATC-Code fehlt im AIPS-XML</th>" +
				"<th>Reg. Nr. fehlerhaft im AIPS-XML</th></th>");
		String med_author = "";
		String med_title = "";
		int td_counter = 0;
		String td_append_str = "";		
		int len_td_append_str = 0;
		for (Map.Entry<String, ArrayList<String>> entry : error_map.entrySet()) {
			for (String error : entry.getValue()) {		// Medications;error_code
				html.append("<tr>");				
				// First column: list authors
				List<String> l = Arrays.asList(error.split("\\s*;\\s*"));
				if (entry.getKey().equals(med_author)==false) {
					med_author = entry.getKey();	// author, owner
					td_append_str = "<td><b>" + med_author + "</b></td>";
				} else
					td_append_str = "<td></td>";		// empty cell
				// Other columns: list errors
				if (l.get(0).equals(med_title)==false) {
					td_counter = 0;
					med_title = l.get(0);				// title
					if (l.get(1).equals("swissmedic5"))
						td_append_str += "<td><p style=\"color:#bb0000\">" + med_title + "</p></td><td></td><td></td>";
					else if (l.get(1).equals("atccode"))
						td_append_str += "<td></td><td><p style=\"color:#0000bb\">" + med_title + "</p></td><td></td>";
					else if (l.get(1).equals("regnr"))
						td_append_str += "<td></td><td></td><td><p style=\"color:#00bb00\">" + med_title + "</p></td>";			
				} else {
					if (td_counter==1) {	// swissmedic5 error already appended
						html.delete(html.length()-len_td_append_str-("<td></td>").length(), html.length());
						td_append_str += "<td><p style=\"color:#bb0000\">" + med_title + "</p></td><td><p style=\"color:#0000bb\">" + med_title + 
								"</p></td><td></td>";
					} else if (td_counter==2) {	// swissmedic5 + atccode errors already appended
						html.delete(html.length()-len_td_append_str-("<td></td>").length(), html.length());
						td_append_str += "<td><p style=\"color:#bb0000\">" + med_title + "</p></td><td><p style=\"color:#0000bb\">" + med_title + 
								"</p></td><td><p style=\"color:#00bb00\">" + med_title + "</p></td>";
					}			
				}
				td_counter++;
				len_td_append_str = td_append_str.length();	// Length of the whole row
				html.append(td_append_str);				
				html.append("</tr>");
			}
		}		
		html.append("</table>");
		return html.toString();
	}
	
	public void writeHtmlToFile() {
		// Pretty formatting
		Document mDoc = Jsoup.parse(mReport.toString());
		mDoc.outputSettings().escapeMode(EscapeMode.xhtml);
		mDoc.outputSettings().prettyPrint(true);
		mDoc.outputSettings().indentAmount(4);
		try {
			mBWriter.write(mDoc.outerHtml());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
