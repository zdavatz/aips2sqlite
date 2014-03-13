package com.maxl.java.aips2sqlite;

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

/*
 * Relevant SQLite DB columns (the rest is empty)
 * id, title, auth, regnrs, customer id, pack_info_str, add_info_str, ids_str, title_str, content, style_str
 */

public class PseudoExpertInfo {

	private static final String FILE_PSEUDO_INFO_DOCX = "./input/pseudo/Sinovial_0.8_FR.docx";
	
	private static String SectionTitle_DE[] = {"Zusammensetzung", "Indikationen/Anwendungsmöglichkeiten", "Dosierung/Anwendung", 
		"Kontraindikationen", "Warnhinweise und Vorsichtsmassnahmen", "Interaktionen", "Unerwünschte Wirkungen",
		"Eigenschaften/Wirkungen", "Sonstige Hinweise", "Packungen", "Herstellerin", "Vertriebsfirma", "Stand der Information", "nil"};
	private static String SectionTitle_FR[] = {
		"Composition", "Indications/Possibilités d'emploi", "Posologie/Mode d'emploi", "Contre-indications", 
		"Mises en garde et précautions", "Interactions", "Effets indésirables", "Propriétés/Effets", 
		"Remarques particulières", "Présentation", "Fabricant", "Distributeur", "Mise à jour de l'information", "nil"};
	
	private ArrayList<String> mSectionContent;
	private MedicalInformations.MedicalInformation mMedi;
	private String mEanCodes_str = "";
	private String mSectionIds_str = "";
	private String mSectionTitles_str = "";
	private String mSectionPackungen_str = "";
	private int mCustomerId;
	
	public PseudoExpertInfo() {
		mSectionContent = new ArrayList<String>();
		for (int i=0; i<SectionTitle_DE.length; ++i)
			mSectionContent.add(i, "");
		
		mMedi = new MedicalInformations.MedicalInformation();
		
		// This sets the customer id (as of yet unused)
		mCustomerId = 2;
	}
	
	private void setup() {
		
	}
	
	public void download() {
		// TODO: Life connection to OneDrive
	}
	
	public void process() {
		String mediTitle = "";
		String mediAuthor = "";
		String mediHtmlContent = "";
		StringBuilder content = new StringBuilder();

		try {
			// Read in docx file
			FileInputStream pseudo_info_file = new FileInputStream(FILE_PSEUDO_INFO_DOCX);		
			XWPFDocument docx = new XWPFDocument(pseudo_info_file);						
			// Loop through it, identifying medication title, author, section titles and corresponding titles
			String prevParaText = "";
			Iterator<XWPFParagraph> para = docx.getParagraphsIterator();

			while (para.hasNext()) {				
				List<XWPFRun> runs = para.next().getRuns();
				if (!runs.isEmpty()) {
					for (XWPFRun r : runs) {
						if (r.isBold())							
							System.out.println(r.getParagraph().getText());
					}
				}
			}
			
			System.out.println("----------------------------");
			
			// Get title
			if (para.hasNext())
				mediTitle = para.next().getParagraphText();
			// Get author
			while (para.hasNext()) {
				String paraText = para.next().getParagraphText();
				if (paraText.equals("Medizinprodukt")) {
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
			mSectionIds_str = "section1,";
			mSectionTitles_str = mediTitle + ",";
			// This is the EAN code pattern
			Pattern pattern = Pattern.compile("^[0-9]{13}");			
			// Loop through all paragraphs...
			while (para.hasNext()) {
				String paraText = para.next().getParagraphText();
				if (paraText.equals(SectionTitle_DE[numSection])) {		
					isSectionPackungen = false;
					// Get section title
					if (numSection<SectionTitle_DE.length)
						numSection++;
					// Section "Packungen" is special
					if (paraText.equals("Packungen")) {
						isSectionPackungen = true;
					}
					// Close previous div
					if (numSection>1)
						content.append("</div>");					
					// Create html
					sectionId_str = "section" + (numSection+1);	// section1 is reserved for the MonTitle
					sectionTitle_str = SectionTitle_DE[numSection-1];
					content.append("<div class=\"paragraph\" id=\"" + sectionId_str + "\">");
					content.append("<div class=\"absTitle\">" + sectionTitle_str + "</div>");
					// Generate section id string
					mSectionIds_str += (sectionId_str + ",");
					// Generate titles string
					mSectionTitles_str += (sectionTitle_str + ";");
				} else {
					// Get section content
					String s = mSectionContent.get(numSection-1);
					mSectionContent.set(numSection-1, s+paraText+" ");
					// Create html
					content.append("<p class=\"spacing1\">" + paraText + "</p>");
					// Extract EAN codes and start positions
					Matcher matcher = pattern.matcher(paraText);
					while (matcher.find()) {
						mEanCodes_str += (matcher.group() + ", ");
					}					
					if (isSectionPackungen) {
						mSectionPackungen_str += (paraText + '\n');
					}
				}
			}			
			// Remove last comma from mEanCodes_str
			if (!mEanCodes_str.isEmpty())
				mEanCodes_str = mEanCodes_str.substring(0, mEanCodes_str.length()-1);	
			// Remove last \n from mSectionPackungen_str
			if (!mSectionPackungen_str.isEmpty())
				mSectionPackungen_str = mSectionPackungen_str.substring(0, mSectionPackungen_str.length()-1);
			
			// Set title, autor
			mMedi.setTitle(mediTitle);
			mMedi.setAuthHolder(mediAuthor);
			mMedi.setAtcCode("");
			mMedi.setSubstances("");
			
			// Close previous div + monographie div
			content.append("</div></div>");
			String title = "<div class=\"MonTitle\" id=\"section1\">" + mediTitle + "</div>";
			String author = "<div class=\"ownerCompany\"><div style=\"text-align: right;\">" + mediAuthor + "</div></div>";
			// Set medi content			
			mediHtmlContent = "<html><head></head><body><div id=\"monographie\">" + title + author + content.toString() + "</div></body></html>";
			
			Document doc = Jsoup.parse(mediHtmlContent);
			doc.outputSettings().escapeMode(EscapeMode.xhtml);		
			doc.outputSettings().charset("UTF-8");
			doc.outputSettings().prettyPrint(true);
			doc.outputSettings().indentAmount(1);
			mediHtmlContent = doc.html();
			
			// Set html content
			mMedi.setContent(mediHtmlContent);				
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void addToDB(SqlDatabase sqlDB, String amikoCSS_str) {
		// orggen_str = "P" (=pseudo)
		List<String> emptyList = new ArrayList<String>();
		emptyList.add("");
		emptyList.add("");
		try {
			sqlDB.addDB(mMedi, amikoCSS_str, mEanCodes_str, mSectionIds_str, mSectionTitles_str, "", "", 
					mSectionPackungen_str, "P", mCustomerId, emptyList, "");
		} catch (SQLException e ) {
			System.out.println("SQLException!");
		}
	}
}
