package com.maxl.java.aips2sqlite;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities.EscapeMode;

public class InteractionsReport {
	private boolean mHeaderAdded = false;
	private StringBuilder mReport;
	private String mFileName = "";
	private String mLanguage = "";
	private String mStyleSheet = "";
	private BufferedWriter mBWriter = null;

	public InteractionsReport(String reportBase, String language, String extension) {

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
					new FileOutputStream(report_file.getAbsoluteFile()),"UTF-8"));
		} catch(IOException e) {
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
			mReport.append("<p>- <a target=\"_new\" href=\"https://download.epha.ch/cleaned/matrix.csv\">matrix.csv</a> (Stand: " + date_str + ")</p>");
			mReport.append("<br/>");
			mHeaderAdded = true;
		} else if (mLanguage.equals("fr")){
			mReport.append("<h3>Compendium des Médicaments Suisse</h3>");
			mReport.append("<p>Version " + version + " - " + date_str + "</p>");
			mReport.append("<p>Licence: GPL v3.0</p>");
			mReport.append("<br/>");
			mReport.append("<p>Concept: Zeno R.R. Davatz - <a target=\"_new\" href=\"http://www.ywesee.com\">ywesee GmbH</a></p>");
			mReport.append("<p>Développement: Dr. Max Lungarella - <a target=\"_new\" href=\"http://www.dynamicdevices.ch\">Dynamic Devices AG</a></p>");
			mReport.append("<br/>");
			mReport.append("<p>Fichiers utilisés:</p>");
			mReport.append("<p>- <a target=\"_new\" href=\"https://download.epha.ch/cleaned/matrix.csv\">matrix.csv</a> (actualisé: " + date_str + ")</p>");
			mReport.append("<br/>");
			mHeaderAdded = true;
		}
	}

	public String treemapToHtmlTable(Map<String, ArrayList<String>> error_map) {
		StringBuilder html = new StringBuilder();
		html.append("<br/>Total Interaktionen in DB: " + error_map.size());
		html.append("<br/><br/>Folgende Interaktionen wurden im File matrix.csv doppelt gefunden:<br/><br/>");
		html.append("<table>");
		html.append("<tr><th>Id</th><th>Interaktion</th>" + "<th>Info</th></tr>");

		int inter_cnt = 0;
		for (Map.Entry<String, ArrayList<String>> entry : error_map.entrySet()) {
			if (entry.getValue().size()>1) {
				inter_cnt++;
				int counter = 0;
				for (String html_content : entry.getValue()) {		// atc1-atc2, html
					html.append("<tr>");
					if (counter==0) {
						html.append("<td>" + inter_cnt + "</td>");
						html.append("<td>" + entry.getKey() + "</td>");
						html.append("<td>" + html_content + "</td>");
					} else {
						html.append("<td></td>");
						html.append("<td></td>");
						html.append("<td>" + html_content + "</td>");
					}
					html.append("</tr>");
					counter++;
				}
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
