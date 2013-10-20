/*
Copyright (c) 2013 Max Lungarella

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

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities.EscapeMode;
import org.jsoup.select.Elements;

public class HtmlUtils {

	private String mHtmlStr;
	private Document mDoc;
	
	static private String ListOfKeywordsDE[] = {"Wirkstoffe","Wirkstoff\\(e\\)","Wirkstoff","Hilfsstoffe","Hilfsstoff\\(e\\)","Hilfsstoff",
		"Kindern","Kinder","Sonstige Hinweise","Hinweis","Lagerungshinweise","Erwachsene","ATC-Code","Inkompatibilitäten","Haltbarkeit",
		"Ältere Patienten","Schwangerschaft","Stillzeit","Jugendliche","Jugendlichen"};

	static private String ListOfKeywordsFR[] = {"Principe actif","Excipient","Excipients",
		"Enfant","Enfants","Adolescents","Adultes","Posologie usuelle","Remarques particulières", "Remarques concernant la manipulation",
		"Remarques concernant le stockage","Conseils d'utilisation","Code ATC","Incompatibilités","Stabilité",
		"Conservation","Patients âgés","Grossesse","Allaitement","Population spéciales","Absorption","Distribution","Métabolisme",
		"Elimination"};

	public HtmlUtils(String htmlStr) {
		mHtmlStr = htmlStr;
	}
	
	public String getClean() {
		return mHtmlStr;
	}
	
	/**
	 * Removes all <span> and </span> and other weird characters and symbols
	 */
	public void clean() {
		// Remove all <span> and </span>
		mHtmlStr = mHtmlStr.replaceAll("\\<span.*?\\>", "");		
		// mHtmlStr = mHtmlStr.replaceAll("\\<span\\>", "");
		mHtmlStr = mHtmlStr.replaceAll("<\\/span\\>", "");
		// &lt;[a-zA-z] -> &lt; [a-zA-Z]
		mHtmlStr = mHtmlStr.replaceAll("&lt;", "&lt; ");
	}
	
	/**
	 * Extracts Swissmedic registration number(s) for given German med title
	 * @param med title
	 * @return comma-separated list of registration numbers
	 */
	public String extractRegNrDE(String title) {
		mDoc = Jsoup.parse(mHtmlStr);
		mDoc.outputSettings().escapeMode(EscapeMode.xhtml);	

		// Five digit pattern
		Pattern d5_pattern = Pattern.compile("\\b\\d{5}\\b");
		
		// Extract registration numbers (use JSoup)	
		String regnr_str = "";
		Element elem_first = mDoc.select("[id=Section7750]").select("p[class=noSpacing]").first();	
		if (elem_first!=null) {
			regnr_str = elem_first.ownText();
		} else {
			// Find section 17
			elem_first = mDoc.select("[id=section17]").first();
			if (elem_first!=null) {
				String h = "";				
				// Better solution (01/05/2013)!
				Element pe = elem_first;
				Element elem_last = mDoc.select("[id=section18]").first();
				while (pe!=elem_last) {
					h = h + pe.toString() + " ";
					pe = pe.nextElementSibling();
				}

				/* Seemingly less power solution (17/04/2013)
				Elements elems = mDoc.select("p:contains(Swissmedic)");
				for (Element e : elems) {
					h = h + e.toString();  
				}
				*/
				// Element elem = elem_first.nextElementSibling();
				if (h!=null) {
					h = h.replaceAll("\\<p.*?\\>", "");
					h = h.replaceAll("<\\/p\\>", "");					
					// Remove anything after "Packungen"
					h = h.replaceAll("(?<=Packungen).*", "");
					// Remove all parenthesized stuff, e.g. Vistagan ... !
					h = h.replaceAll("\\(.*?\\)","");
					// Special character found in some files							
					h = h.replaceAll("[–-]",",");
					h = h.replaceAll("&apos;", "");
					h = h.replaceAll("‘", "");
					h = h.replaceAll("’", "");		
					h = h.replaceAll("`", "");						
					Matcher m = d5_pattern.matcher(h);
					h = "";
					while (m.find())
						h += (m.group(0) + ",");

					regnr_str = h;
					if (regnr_str.length()>0)
						regnr_str = regnr_str.substring(0, regnr_str.length()-1);
				}									

				if (h.isEmpty()) {
					while (h.isEmpty()) {
						elem_first = elem_first.nextElementSibling();
						h = elem_first.toString();
						h = h.replaceAll("\\<p.*?\\>", "");
						h = h.replaceAll("<\\/p\\>", "");
						// Remove all parenthesized stuff, e.g. Vistagan ... !
						h = h.replaceAll("\\(.+\\)","");
						// Special character found in some files
						h = h.replaceAll("[–-]",",");											
						h = h.replaceAll("&apos;", "");
						// Keep numbers and possible separators between numbers
						h = h.replaceAll("[^;0-9,]", "");
					}
					regnr_str = h;
				}
			} else {
				// Find "Packungen" und "Zulassungsnummer"
				Element start_elem = mDoc.select("p:contains(Zulassungsnummer)").first();			
				Element stop_elem = mDoc.select("p:contains(Packungen)").first();	
				Element pe = start_elem; 
				String h = "";
				if (start_elem!=null && stop_elem!=null) {
					h = "";
					// Parse everything until "Swissmedic";
					String a = start_elem.toString().split("Swissmedic")[0];					
					// Remove all parenthesized stuff, e.g. Vistagan ... !
					a = a.replaceAll("\\(.*?\\)","");
					// Special character found in some files		
					a = a.replaceAll("[–-]",",");										
					a = a.replaceAll("&apos;", "");
					a = a.replaceAll("‘", "");
					a = a.replaceAll("’", "");
					h = h.replaceAll("`", "");											
					if (start_elem==stop_elem) {
						Matcher m = d5_pattern.matcher(a);
						while (m.find())
							h += (m.group(0) + ",");			
					} else {
						while (pe!=stop_elem) {
							Matcher m = d5_pattern.matcher(start_elem.toString());
							while (m.find())
								h += (m.group(0) + ",");								
							pe = pe.nextElementSibling();			
						}
					}
					regnr_str = h;
					if (regnr_str.length()>0)
						regnr_str = regnr_str.substring(0, regnr_str.length()-1);						
				}		

				if (h.isEmpty()) {
					elem_first = mDoc.select("[id=Section7860]").select("p[class=noSpacing]").first();
					if (elem_first!=null) {
						regnr_str = elem_first.ownText();
					} else {				
						// System.err.println(">> ERROR: None of the sections 17, 7750, 7860 exist: " + title);
					}
				}
			}
		}
		// Get rid of all "(Swissmedic)." instances
		regnr_str = regnr_str.replaceAll("\\(Swissmedic\\).","");
		// This is a special - character ...
		regnr_str = regnr_str.replaceAll("[–-]",",");
		// Remove all parenthesized stuff, e.g. Vistagan ... !
		regnr_str = regnr_str.replaceAll("\\(.+\\)","");
		// Replace all semicolons with commas
		regnr_str = regnr_str.replaceAll(";", ",");
		// Get rid of all non-numeric characters, keep "," and "-"
		regnr_str = regnr_str.replaceAll("[^0-9,]", "");
		// Add comma with SPACE after number
		regnr_str = regnr_str.replaceAll(",",", ");		
		// Replace "-" with ","
		// regnr_str = regnr_str.replaceAll("-",", ");		

		// Some other (redundant REGEXs)
		/*
		// Special parsing for Lopresor-Retard
		h = h.replaceAll("\\s?[0-9]+:", ",");
		h = h.replaceAll("[^;0-9,]", "");
		*/
		
		return regnr_str;
	}

	/**
	 * Extracts Swissmedic registration number(s) for given French med title
	 * @param med title
	 * @return comma-separated list of registration numbers
	 */	
	public String extractRegNrFR(String title) {
		mDoc = Jsoup.parse(mHtmlStr);
		mDoc.outputSettings().escapeMode(EscapeMode.xhtml);	

		// Five digit pattern
		Pattern d5_pattern = Pattern.compile("\\b\\d{5}\\b");
		
		// Extract registration numbers (use JSoup)	
		String regnr_str = "";
		Element elem_first = mDoc.select("[id=Section7750]").select("p[class=noSpacing]").first();	
		if (elem_first!=null) {
			regnr_str = elem_first.ownText();
		} else {
			// Find section 17
			elem_first = mDoc.select("[id=section17]").first();
			if (elem_first!=null) {
				String h = "";				
				// Better solution (01/05/2013)!
				Element pe = elem_first;
				Element elem_last = mDoc.select("[id=section18]").first();
				while (pe!=elem_last) {
					h = h + pe.toString() + " ";
					pe = pe.nextElementSibling();
				}
				/* Seemingly less power solution (17/04/2013)
				Elements elems = mDoc.select("p:contains(Swissmedic)");
				for (Element e : elems) {
					h = h + e.toString();  
				}
				*/
				// Element elem = elem_first.nextElementSibling();
				if (h!=null) {
					h = h.replaceAll("\\<p.*?\\>", "");
					h = h.replaceAll("<\\/p\\>", "");					
					// Remove anything after "Packungen"
					h = h.replaceAll("(?<=Présentation).*", "");
					// Remove all parenthesized stuff, e.g. Vistagan ... !
					h = h.replaceAll("\\(.*?\\)","");
					// Special character found in some files							
					h = h.replaceAll("&apos;", "");
					// This is a special - character ...
					h = h.replaceAll("[–-]",",");					
					h = h.replaceAll("‘", "");
					h = h.replaceAll("’", "");
					h = h.replaceAll("`", "");		
					Matcher m = d5_pattern.matcher(h);
					h = "";
					while (m.find())
						h += (m.group(0) + ",");

					regnr_str = h;
					if (regnr_str.length()>0)
						regnr_str = regnr_str.substring(0, regnr_str.length()-1);
				}									
				
				if (h.isEmpty()) {
					while (h.isEmpty()) {				
						elem_first = elem_first.nextElementSibling();						
						h = elem_first.toString();
						h = h.replaceAll("\\<p.*?\\>", "");
						h = h.replaceAll("<\\/p\\>", "");
						// This is a special - character ...
						regnr_str = regnr_str.replaceAll("[–-]",",");											
						// Remove all parenthesized stuff, e.g. Vistagan ... !
						h = h.replaceAll("\\(.+\\)","");
						// Special character found in some files
						h = h.replaceAll("&apos;", "");
						// Keep numbers and possible separators between numbers
						h = h.replaceAll("[^;0-9,]", "");
					}
					regnr_str = h;
				}
			} else {
				// Find "Packungen" und "Zulassungsnummer"
				Element start_elem = mDoc.select("p:contains(Numéro d'autorisation)").first();			
				String h = "";
				if (start_elem!=null) {
					h = "";
					// Parse everything until "Swissmedic";
					String a = start_elem.toString().replaceAll("(?<=Présentation).*", "");
					a = a.replaceAll("\\<table.*?\\>", "");
					a = a.replaceAll("<\\/table\\>", "");	
					// Remove all parenthesized stuff, e.g. Vistagan ... !
					a = a.replaceAll("\\(.*?\\)","");
					// Special character found in some files							
					a = a.replaceAll("[–-]",",");					
					a = a.replaceAll("&apos;", "");
					a = a.replaceAll("‘", "");
					a = a.replaceAll("’", "");
					a = a.replaceAll("`", "");
					
					Element stop_elem = mDoc.select("p:contains(Présentation)").first();						
					if (start_elem==stop_elem) {
						Matcher m = d5_pattern.matcher(a);
						while (m.find())
							h += (m.group(0) + ",");			
					} else {
						Element pe = start_elem; 						
						while (pe!=stop_elem) {
							Matcher m = d5_pattern.matcher(start_elem.toString());
							while (m.find())
								h += (m.group(0) + ",");								
							pe = pe.nextElementSibling();			
						}
					}
					regnr_str = h;
					if (regnr_str.length()>0)
						regnr_str = regnr_str.substring(0, regnr_str.length()-1);						
				}		

				if (h.isEmpty()) {												
					elem_first = mDoc.select("[id=Section7860]").select("p[class=noSpacing]").first();
					if (elem_first!=null) {
						regnr_str = elem_first.ownText();
					} else {				
						// System.err.println(">> ERROR: None of the sections 17, 7750, 7860 exist: " + title);
					}
				}
			}
		}
		
		// Get rid of all "(Swissmedic)." instances
		regnr_str = regnr_str.replaceAll("\\(Swissmedic\\).","");
		// This is a special - character ...
		regnr_str = regnr_str.replaceAll("[–-]",",");
		// Remove all parenthesized stuff, e.g. Vistagan ... !
		regnr_str = regnr_str.replaceAll("\\(.+\\)","");
		// Replace all semicolons with commas
		regnr_str = regnr_str.replaceAll(";", ",");
		// Get rid of all non-numeric characters, keep "," and "-"
		regnr_str = regnr_str.replaceAll("[^0-9,]", "");
		// Add comma with SPACE after number
		regnr_str = regnr_str.replaceAll(",",", ");		
		
		return regnr_str;
	}
	
	public String extractPackSection() {	
		mDoc = Jsoup.parse(mHtmlStr);
		mDoc.outputSettings().escapeMode(EscapeMode.xhtml);	
		
		String pack_section = "";
		// Find all information between X and X+1
		Element start_elem = mDoc.select("p:contains(Packungen)").first();			
		Element stop_elem = mDoc.select("p:contains(Zulassungsinhaberin)").first();	
		// Alternative:
		/*
		Element start_elem = mDoc.select("p[id=section18]").first();			
		Element stop_elem = mDoc.select("p[id=section19]").first();	
		*/
		Element pe = start_elem.nextElementSibling(); 
		if (pe!=null && start_elem!=null && stop_elem!=null) {
			while (pe!=stop_elem) {
				System.out.println(pe.text());
				pe = pe.nextElementSibling();			
			}
		}		
		return pack_section;
	}
	
	/**
	 * Cleans a given SectionX for a given med title of a given language
	 * Note: not the most efficient implementations, html file is parsed always
	 * @param sectionX
	 * @param med_title
	 * @param language
	 * @return
	 */
	public String sanitizeSection(int sectionX, String med_title, String language) {
		mDoc = Jsoup.parse(mHtmlStr);
		mDoc.outputSettings().escapeMode(EscapeMode.xhtml);		
		mDoc.outputSettings().charset("UTF-8");
		Document newDoc = Jsoup.parse("");
		newDoc.outputSettings().escapeMode(EscapeMode.xhtml);
		newDoc.outputSettings().charset("UTF-8");
		
		if (sectionX>1) {
			// Create brand new div for section with id=sectionN
			newDoc.html("<div class=\"paragraph\" id=\"section"+sectionX+"\">");
			Element paraDiv = newDoc.select("div[class=paragraph]").first();
			
			// Find sectionX 
			Element section_header = mDoc.select("p[id=section"+sectionX+"]").first();		
			if (section_header!=null) {
				// Sanitize header (*section title*)
				// Remove all <br /> in the title! 
				int substr_index = section_header.html().indexOf("<br />");
				int tot_len = section_header.html().length();
				if (substr_index>0) {
					paraDiv.html("<div class=\"absTitle\">"+section_header.html().substring(0, substr_index)+"</div>" 
						+ "<p class=\"spacing1\">"+ section_header.html().substring(substr_index+6, tot_len)+"</p>");				
				} else {
					paraDiv.html("<div class=\"absTitle\">"+section_header.html()+"</div>");
				}
				// Find all information between X and X+1
				Elements elems = mDoc.select("p[id=section"+sectionX+"] ~ *");			
				Element elemXp1 = mDoc.select("p[id=section"+(sectionX+1)+"]").first();			
								
				// Loop through the content
				if (elems!=null) {
					for (Element e : elems) {
						// Sanitize
						Element img = null;
						if (e.tagName().equals("p")) {
							if (e.select("img[src]")!=null) 
								img = e.select("img[src]").first();
							String re = e.html();  //e.text(); -> the latter solution removes all <sup> and <sub>
							if (language.equals("de")) {
								for (int i=0; i<ListOfKeywordsDE.length; ++i) {
									// Exact match through keyword "\\b" for boundary					
									re = re.replaceAll("\\b"+ListOfKeywordsDE[i]+" \\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsDE[i]+"</span> ");
									// Try also versions with ":" or "," or "."
									re = re.replaceAll("\\b"+ListOfKeywordsDE[i]+": \\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsDE[i]+":</span> ");
									re = re.replaceAll("\\b"+ListOfKeywordsDE[i]+", \\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsDE[i]+"</span>, ");
									// re = re.replaceAll("\\b"+ListOfKeywordsDE[i]+". \\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsDE[i]+"</span>. ");
									// Words at the end of the line
									re = re.replaceAll("\\b"+ListOfKeywordsDE[i]+".$", "<span style=\"font-style:italic;\">"+ListOfKeywordsDE[i]+"</span>.");									
									re = re.replaceAll("\\b"+ListOfKeywordsDE[i]+"$", "<span style=\"font-style:italic;\">"+ListOfKeywordsDE[i]+"</span>");	
									re = re.replaceAll("\\b"+ListOfKeywordsDE[i]+"\\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsDE[i]+"</span>");
								}
							} else if (language.equals("fr")) {
								for (int i=0; i<ListOfKeywordsFR.length; ++i) {
									// Exact match through keyword "\\b" for boundary					
									re = re.replaceAll("\\b"+ListOfKeywordsFR[i]+" \\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsFR[i]+"</span> ");
									// Try also versions with ":" or "," or "."
									re = re.replaceAll("\\b"+ListOfKeywordsFR[i]+": \\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsFR[i]+":</span> ");
									re = re.replaceAll("\\b"+ListOfKeywordsFR[i]+", \\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsFR[i]+"</span>, ");
									// re = re.replaceAll("\\b"+ListOfKeywordsDE[i]+". \\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsDE[i]+"</span>. ");
									// Words at the end of the line
									re = re.replaceAll("\\b"+ListOfKeywordsFR[i]+".$", "<span style=\"font-style:italic;\">"+ListOfKeywordsFR[i]+"</span>.");									
									re = re.replaceAll("\\b"+ListOfKeywordsFR[i]+"$", "<span style=\"font-style:italic;\">"+ListOfKeywordsFR[i]+"</span>");								
									re = re.replaceAll("\\b"+ListOfKeywordsFR[i]+"\\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsFR[i]+"</span>");									
								}
							}							
							// Important step: add the section content!
							if (img==null)
								paraDiv.append("<p class=\"spacing1\">" + re + "</p>");
							else 
								paraDiv.append("<p class=\"spacing1\">" + img + "</p>");
						}
						else if (e.tagName().equals("table")) {
							// Important: deal with the tables
							Elements col_styles = e.select("col[style]");
							float sum = 0.0f;
							for (Element cs : col_styles) {
								String attr_str = (cs.attributes().toString()).replaceAll("[^0-9].[^0-9]+", ""); 
								sum += Float.valueOf(attr_str);
							}
							for (Element cs : col_styles) {
								String attr_str = (cs.attributes().toString()).replaceAll("[^0-9].[^0-9]+", ""); 	// e.g. style="width:1.75347in"
								float width = 100.0f*Float.valueOf(attr_str)/sum;
								// Save new attribute!								
								attr_str = "width:" + width + "%25;"; // "%;"; 
								// Set new attribute
								cs.attr("style", attr_str);
							}
							// Change all fonts
							paraDiv.append(e.outerHtml());
						}
							
						if (e.nextElementSibling()==elemXp1)
							break;
					}
				}
			}
		} else {
			// SectionX==1, that's the "title"
			Element title = mDoc.select("p[id=section1]").first();
			String clean_title = "";
			// Some med titles have a 'Ò' which on Windows and Android systems is not translated into a '®' (reserved symbol)
			if (title!=null)
				clean_title = title.text().replace("Ò","®");
			// Some German medications have wrong characters in the titles, this is a fix
			if (language.equals("de"))
				clean_title = clean_title.replace("â","®");
			else if (language.equals("fr"))
				clean_title = med_title;
			newDoc.html("<div class=\"MonTitle\" id=\"section"+sectionX+"\">"+clean_title+"</div>");
			Element elem = null;
			if (language.equals("de"))
				elem = mDoc.select("p:contains(Zulassungsinhaberin)").first();
			else if (language.equals("fr"))
				elem = mDoc.select("p:contains(Titulaire de l'autorisation)").first();	
			if (elem!=null) {
				if (elem.nextElementSibling()!=null) {
					List<String> company_str = Arrays.asList(elem.nextElementSibling().text().split("\\s*,\\s*"));
					if (company_str.size()>0)
						newDoc.append("<div class=\"ownerCompany\"><div style=\"text-align: right;\">"+company_str.get(0)+"</div></div>");
				}
			}
		}	
				
		// Fools the jsoup-parser
		String html_str = newDoc.html().replaceAll("&lt; ", "&lt;");
		// Replaces all supscripted � in the main text with �
		html_str = html_str.replaceAll(">â</sup>", ">®</sup>");

		// Remove multiple instances of <p class="spacing1"> </p>
		Scanner scanner = new Scanner(html_str);
		html_str = "";
		int counter = 0;
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			// System.out.println(line.trim());
			if (line.trim().contains("<p class=\"spacing1\"> </p>")) {
				counter++;
			} else 
				counter = 0;
			if (counter<2)
				html_str += (line + '\n');
		}	
		scanner.close();
		
		// Cosmetic upgrades. Use with care!
		html_str = html_str.replaceAll("<p class=\"spacing1\"> </p>\n</div>", "</div>");
		html_str = html_str.replaceAll("</div>\n <p class=\"spacing1\"> </p>", "</div>");
		
		return html_str;
	}
}

