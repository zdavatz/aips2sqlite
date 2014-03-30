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

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities.EscapeMode;

public class RealPatientInfo {

	List<MedicalInformations.MedicalInformation> m_med_list = null;
	
	/*
	 * Constructors
	 */
	public RealPatientInfo(List<MedicalInformations.MedicalInformation> med_list) {
		m_med_list = med_list;
	}
	
	/*
	 * Getters / setters
	 */
	public void setMedList(List<MedicalInformations.MedicalInformation> med_list) {
		m_med_list = med_list;
	}
	
	public List<MedicalInformations.MedicalInformation> getMedList() {
		return m_med_list;
	}
	
	public void process() {
		// Load CSS file
		String amiko_style_v1_str = FileOps.readCSSfromFile(Constants.FILE_STYLE_CSS_BASE + "v1.css");
		
		// Initialize counters for different languages
		int tot_med_counter = 0;
		
		HtmlUtils html_utils = null;
		
		System.out.println("Processing Patient Infos...");	
		
		for( MedicalInformations.MedicalInformation m : m_med_list ) {
			// --> Read PATIENTENINFOS! <--				
			if (m.getLang().equals(CmlOptions.DB_LANGUAGE) && m.getType().equals("pi")) {
				if (tot_med_counter<5000) {									
					if (m.getTitle().toLowerCase().startsWith(CmlOptions.OPT_MED_TITLE.toLowerCase())  
							&& m.getAuthHolder().toLowerCase().startsWith(CmlOptions.OPT_MED_OWNER.toLowerCase())) {	
						
						// Extract section titles and section ids
						MedicalInformations.MedicalInformation.Sections med_sections = m.getSections();
						List<MedicalInformations.MedicalInformation.Sections.Section> med_section_list = med_sections.getSection();
						String ids_str = "";
						String titles_str = "";
						for( MedicalInformations.MedicalInformation.Sections.Section s : med_section_list ) {
							ids_str += (s.getId() + ",");
							titles_str += (s.getTitle() + ";");
							// System.out.println(s.getTitle());
						}	
						
						System.out.println(tot_med_counter + " - " + m.getTitle() + ": " + m.getAuthNrs() + " ver -> "+ m.getVersion());						
											
						// Clean html
						html_utils = new HtmlUtils(m.getContent());
						html_utils.setLanguage(CmlOptions.DB_LANGUAGE);
						// Remove spans 
						html_utils.clean();	
																		
						// Sanitize html, the function returns nicely formatted html												
						String html_sanitized = html_utils.sanitizePatient(m.getTitle(), m.getAuthHolder(), CmlOptions.DB_LANGUAGE);				

						// System.out.println(html_sanitized);
						
						if (CmlOptions.XML_FILE==true) {
							// Add header to html file							
							String mContent_str = html_sanitized;
							mContent_str = mContent_str.replaceAll("<head>", "<head>" + 
									"<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />" +
									"<style>" + amiko_style_v1_str + "</style>");												
							m.setContent(mContent_str);
													
							// Write to html and xml files to disk
							String name = m.getTitle();
							// Replace all "Sonderzeichen"
							name = name.replaceAll("[/%:]", "_");									
							if (CmlOptions.DB_LANGUAGE.equals("de")) {
								FileOps.writeToFile(mContent_str, Constants.FILE_XML_BASE + "pi_de_html/", name + "_pi_de.html");
							} else if (CmlOptions.DB_LANGUAGE.equals("fr")) {
								FileOps.writeToFile(mContent_str, Constants.FILE_XML_BASE + "pi_fr_html/", name + "_pi_fr.html");										
							} else if (CmlOptions.DB_LANGUAGE.equals("it")) {
								FileOps.writeToFile(mContent_str, Constants.FILE_XML_BASE + "pi_it_html/", name + "_pi_it.html");
							} else if (CmlOptions.DB_LANGUAGE.equals("en")) {
								FileOps.writeToFile(mContent_str, Constants.FILE_XML_BASE + "pi_en_html/", name + "_pi_en.html");
							}								
						}
					}
				}
				tot_med_counter++;
			}
		}	
	}
}
