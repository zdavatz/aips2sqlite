/*
Copyright (c) 2014 Max Lungarella

This file is part of Aips2SQLite.

Aips2SQLite is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.maxl.java.aips2sqlite;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities.EscapeMode;

public class PseudoExpertInfo {

	// This is the location of the directory with the pseudo "Fachinfos"
	private static final String FILE_PSEUDO_INFO_DIR = "./input/pseudo/";

	private SqlDatabase mSqlDB = null;
	private String mAmikoCSS_str = "";
	
	private ArrayList<String> mSectionContent;
	private ArrayList<String> mSectionTitles;
	private MedicalInformations.MedicalInformation mMedi;
	private String mEanCodes_str = "";
	private String mSectionIds_str = "";
	private String mSectionTitles_str = "";
	private String mSectionPackungen_str = "";
	private int mCustomerId;
	
	public PseudoExpertInfo(SqlDatabase sqlDB, String amikoCSS_str) {		
		mSqlDB = sqlDB;
		mAmikoCSS_str = amikoCSS_str;
		// This sets the customer id (as of yet unused)
		mCustomerId = 2;
	}
	
	public void download() {
		// TODO: Life connection to OneDrive
	}
	
	/*
	 * 	Loads all filenames from directory into a list
	 */
	public void process() {
		try {
			File[] files = new File(FILE_PSEUDO_INFO_DIR).listFiles();
			// Loop through list
			System.out.println("\nFound " + files.length + " pseudo Fachinfos");
			int idxPseudo = 1;
			for (File pseudo : files) {
				FileInputStream pseudoInfoFile = new FileInputStream(pseudo.getAbsoluteFile());
				extractInfo(idxPseudo++, pseudoInfoFile);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Extracts all the important information from the pseudo "Fachinfo" file
	 * @param pseudo_info_file
	 */
	public void extractInfo(int idx, FileInputStream pseudo_info_file) {
		mMedi = new MedicalInformations.MedicalInformation();
		
		mSectionContent = new ArrayList<String>();
		mSectionTitles = new ArrayList<String>();
		
		String mediTitle = "";
		String mediAuthor = "";
		String mediPseudoTag = "";
		String mediHtmlContent = "";
		
		StringBuilder content = new StringBuilder();

		try {
			// Read in docx file
			XWPFDocument docx = new XWPFDocument(pseudo_info_file);
			// Get iterator through all paragraphs
			Iterator<XWPFParagraph> para = docx.getParagraphsIterator();

			// Pre-process input stream to extract paragraph titles
			boolean goodToGo = false;
			while (para.hasNext()) {				
				List<XWPFRun> runs = para.next().getRuns();
				if (!runs.isEmpty()) {
					for (XWPFRun r : runs) {
						// bold and italics identifies section title!
						if (r.isBold())	{ // && r.isItalic()) {
							String pText = r.getParagraph().getText();
							// These are the first chapter titles (DE and FR)
							if (pText.equals("Zusammensetzung") || pText.equals("Composition"))
								goodToGo = true;
							if (goodToGo==true)
								mSectionTitles.add(pText);
						}
					}
				}
			}
			// Add "nil" at the end
			mSectionTitles.add("nil");
			
			// Reset iterator
			para = docx.getParagraphsIterator();
			
			// Init list for section content 
			for (int i=0; i<mSectionTitles.size(); ++i)
				mSectionContent.add(i, "");
			
			// Get title
			if (para.hasNext())
				mediTitle = para.next().getParagraphText();
			// Get author while using "Medizinprodukt" as tag
			String prevParaText = "";			
			while (para.hasNext()) {
				String paraText = para.next().getParagraphText();
				// If this word is not found, then no pseudo FI will be produced
				if (paraText.equals("Medizinprodukt") || paraText.equals("Dispositif médical")) {
					mediPseudoTag = paraText;
					mediAuthor = prevParaText;
					break;
				}
				prevParaText = paraText;
			}
		
			// Get section titles + sections + ean codes
			boolean isSectionPackungen = false;
			int numSection = 0;
			// Init with section1 and title
			String sectionId_str = "";
			String sectionTitle_str = "";
			mEanCodes_str = "";
			mSectionIds_str = "section1,";
			mSectionTitles_str = mediTitle + ",";
			mSectionPackungen_str = "";
			// This is the EAN code pattern
			Pattern pattern = Pattern.compile("^[0-9]{13}");			
			// Loop through it, identifying medication title, author, section titles and corresponding titles
			while (para.hasNext()) {
				String paraText = para.next().getParagraphText();			
				if (paraText.equals(mSectionTitles.get(numSection))) {		
					// ->> Get section title
					isSectionPackungen = false;
					// Get section title
					if (numSection<mSectionTitles.size())
						numSection++;
					// Section "Packungen" is special
					if (paraText.equals("Packungen") || paraText.equals("Présentation")) {
						isSectionPackungen = true;
					}
					// Close previous div
					if (numSection>1)
						content.append("</div>");					
					// Create html
					sectionId_str = "section" + (numSection+1);	// section1 is reserved for the MonTitle
					sectionTitle_str = mSectionTitles.get(numSection-1);
					content.append("<div class=\"paragraph\" id=\"" + sectionId_str + "\">");
					content.append("<div class=\"absTitle\">" + sectionTitle_str + "</div>");
					// Generate section id string
					mSectionIds_str += (sectionId_str + ",");
					// Generate titles string
					mSectionTitles_str += (sectionTitle_str + ";");
				} else {
					// ->> Get section content
					String s = mSectionContent.get(numSection-1);
					mSectionContent.set(numSection-1, s+paraText+" ");
					// Create html
					content.append("<p class=\"spacing1\">" + paraText + "</p>");
					// Extract EAN codes and start positions
					Matcher matcher = pattern.matcher(paraText);
					while (matcher.find())
						mEanCodes_str += (matcher.group() + ", ");
					// Generate section Packungen for search result
					if (isSectionPackungen)
						mSectionPackungen_str += (paraText + "\n");
				}
			}			
			// Remove last comma from mEanCodes_str
			if (!mEanCodes_str.isEmpty())
				mEanCodes_str = mEanCodes_str.substring(0, mEanCodes_str.length()-2);	
			// Remove last \n from mSectionPackungen_str
			if (!mSectionPackungen_str.isEmpty())
				mSectionPackungen_str = mSectionPackungen_str.substring(0, mSectionPackungen_str.length()-1);
			
			// Set title, autor
			mMedi.setTitle(mediTitle);
			mMedi.setAuthHolder(mediAuthor);
			mMedi.setAtcCode("PSEUDO");
			mMedi.setSubstances(mediTitle);
			
			System.out.println(idx + " - " + mediTitle + ": " + mEanCodes_str);
			
			// Close previous div + monographie div
			content.append("</div></div>");
			String title = "<div class=\"MonTitle\" id=\"section1\">" + mediTitle + "</div>";
			String author = "<div class=\"ownerCompany\"><div style=\"text-align: right;\">" + mediAuthor + "</div></div>";
			// Set "Medizinprodukt" label
			String pseudo = "<p class=\"spacing1\">" + mediPseudoTag + "</p>";
			// Set medi content			
			mediHtmlContent = "<html><head></head><body><div id=\"monographie\">" + title + author + pseudo + content.toString() + "</div></body></html>";
			
			// Generate clean html file
			Document doc = Jsoup.parse(mediHtmlContent);
			doc.outputSettings().escapeMode(EscapeMode.xhtml);		
			doc.outputSettings().charset("UTF-8");
			doc.outputSettings().prettyPrint(true);
			doc.outputSettings().indentAmount(1);
			mediHtmlContent = doc.html();
			
			// Set html content
			mMedi.setContent(mediHtmlContent);				
			
			// Add to DB
			addToDB();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void addToDB() {
		// orggen_str = "P" (=pseudo)
		List<String> emptyList = new ArrayList<String>();
		emptyList.add("PSEUDO");
		emptyList.add("PSEUDO");
		try {
			mSqlDB.addDB(mMedi, mAmikoCSS_str, mEanCodes_str, mSectionIds_str, mSectionTitles_str, mEanCodes_str, "", 
					mSectionPackungen_str, "P", mCustomerId, emptyList, "");
		} catch (SQLException e ) {
			System.out.println("SQLException!");
		}
	}
}
