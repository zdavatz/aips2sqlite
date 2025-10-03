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

import java.io.UnsupportedEncodingException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities.EscapeMode;
import org.jsoup.select.Elements;

public class HtmlUtils {

	static String whitespace_chars =  ""       /* dummy empty string for homogeneity */
            + "\\u0009" // CHARACTER TABULATION
            + "\\u000A" // LINE FEED (LF)
            + "\\u000B" // LINE TABULATION
            + "\\u000C" // FORM FEED (FF)
            + "\\u000D" // CARRIAGE RETURN (CR)
            + "\\u0020" // SPACE
            + "\\u0085" // NEXT LINE (NEL)
            + "\\u00A0" // NO-BREAK SPACE
            + "\\u00C2"
            + "\\u1680" // OGHAM SPACE MARK
            + "\\u180E" // MONGOLIAN VOWEL SEPARATOR
            + "\\u2000" // EN QUAD
            + "\\u2001" // EM QUAD
            + "\\u2002" // EN SPACE
            + "\\u2003" // EM SPACE
            + "\\u2004" // THREE-PER-EM SPACE
            + "\\u2005" // FOUR-PER-EM SPACE
            + "\\u2006" // SIX-PER-EM SPACE
            + "\\u2007" // FIGURE SPACE
            + "\\u2008" // PUNCTUATION SPACE
            + "\\u2009" // THIN SPACE
            + "\\u200A" // HAIR SPACE
            + "\\u2028" // LINE SEPARATOR
            + "\\u2029" // PARAGRAPH SEPARATOR
            + "\\u202F" // NARROW NO-BREAK SPACE
            + "\\u205F" // MEDIUM MATHEMATICAL SPACE
            + "\\u3000" // IDEOGRAPHIC SPACE
            ;

	static private String ListOfKeywordsDE[] = {"Wirkstoffe","Wirkstoff\\(e\\)","Wirkstoff","Hilfsstoffe","Hilfsstoff\\(e\\)","Hilfsstoff",
		"Kindern","Kinder","Sonstige Hinweise","Hinweis","Lagerungshinweise","Erwachsene","ATC-Code","Inkompatibilitäten","Haltbarkeit",
		"Ältere Patienten","Schwangerschaft","Stillzeit","Jugendliche","Jugendlichen"};

	static private String ListOfKeywordsFR[] = {"Principe actif","Excipient","Excipients",
		"Enfant","Enfants","Adolescents","Adultes","Posologie usuelle","Remarques particulières", "Remarques concernant la manipulation",
		"Remarques concernant le stockage","Conseils d'utilisation","Code ATC","Incompatibilités","Stabilité",
		"Conservation","Patients âgés","Grossesse","Allaitement","Population spéciales","Absorption","Distribution","Métabolisme",
		"Elimination"};

	/**¨
	 * Sanitizes all sections (in one go!)
	 * @param med_title
	 * @param med_author
	 * @param language
	 * @return
	 */
	static public String sanitizePatient(Document mDoc, String med_title, String med_author, String med_regnrs, String language) {
		// Original html string
		mDoc.outputSettings().escapeMode(EscapeMode.xhtml);
		mDoc.outputSettings().charset("UTF-8");

		// Initialize some local variables
		String html_str = "";
		int section_cnt = 0;
		boolean fallback1 = false;

		// Extract med title
		String clean_title = "";
		String monTitle = mDoc.select("div[class=MonTitle]").html();
		if (!monTitle.isEmpty())
			clean_title = monTitle;
		else
			clean_title = med_title;
		if (clean_title!=null) {
			clean_title = clean_title.replace("Ò","®");
			clean_title = clean_title.replace("TM", "<sup>TM</sup>");
			// Some German medications have wrong characters in the titles, this is a fix
			if (language.equals("de"))
				clean_title = clean_title.replace("â","®");
		}
		html_str += ("<div class=\"MonTitle\" id=\"section1\">" + clean_title + "</div>");
		// Add med holder (owner)
		String clean_author = "";
		String ownerCompany = mDoc.select("div[class=ownerCompany]").html();
		if (!ownerCompany.isEmpty())
			clean_author = ownerCompany;
		else
			clean_author = med_author;
		html_str += ("<div class=\"ownerCompany\"><div style=\"text-align: right;\">"+ clean_author + "</div></div>");

		// List all sections of type p[id=section]
		Elements section_titles = mDoc.select("p[id^=section]");
		// 1. Fall back in case previous selector returns nothing
		if (section_titles.isEmpty()) {
			section_titles = mDoc.select("div[id^=Section]");
			fallback1 = true;
		}

		// System.out.println(mDoc.html());

		// 10 section titles is a reasonable lowest number
		if (section_titles.size()>10) {
			for (section_cnt=1; section_cnt<section_titles.size(); ++section_cnt) {
				Element section_title = null;
				// Get section title first
				if (fallback1==true) {
					// Some meds have pre-formatted xml/html, take care of that...
					section_title = section_titles.select("div[class=absTitle]").get(section_cnt-1);
					if (section_cnt==(section_titles.size()-1)) {
						section_title = section_titles.get(section_cnt);
						fallback1 = false;
					}
				} else {
					// Works 80%+ of the time
					section_title = section_titles.get(section_cnt);
				}

				if (section_title!=null) {
					String title = section_title.text().replace("Ò","®");
					// No need to process the med title (see above)
					if (title.equals("Pflanzliches Arzneimittel") || title.equals("Médicament phytothérapeutique")
							|| title.equals("Medicamento fitoterapeutico")) {
						// These are the special cases, where the para is empty and there is only one title!
						html_str += ("<div class=\"paragraph\" id=\"section"+(section_cnt+1)+"\">");
						html_str += ("<div class=\"absTitle\">" + title +"</div>");
					} else if (!med_title.toLowerCase().equals(title.toLowerCase())) {
						// Create brand new div for section with id=sectionN
						html_str += ("<div class=\"paragraph\" id=\"section"+(section_cnt+1)+"\">");
						html_str += ("<div class=\"absTitle\">" + title +"</div>");
						// Find all information between X and X+1
						String tag1 = "p[id=section"+(section_cnt+1)+"] ~ *";
						String tag2 = "p[id=section"+(section_cnt+2)+"]";
						Elements elems = null;
						Element elemXp1 = null;
						if (fallback1==false) {
							if (!tag1.isEmpty()) {
								elems = mDoc.select(tag1);
							}
							if (!tag2.isEmpty()) {
								elemXp1 = mDoc.select(tag2).first();
							}
						} else {
							elems = section_title.parent().select("div[class=absTitle] ~ *");
						}
						// Loop through the content
						if (elems!=null) {
							for (Element e : elems) {
								// Sanitize
								Element img = null;
								if (e.tagName().equals("p") || e.tagName().equals("div")) {
									String re = "";
									if (e.select("img[src]")!=null)
										img = e.select("img[src]").first();

									re = e.html().trim();  //e.text(); -> the latter solution removes all <sup> and <sub>

									// Is last character a period (".")?
									if (!re.endsWith(".") && !re.endsWith(",") && !re.endsWith(":")
											&& !re.startsWith("–") && !re.startsWith("·") && !re.startsWith("-") && !re.startsWith("•")) {
										re = "<span style=\"font-style:italic;\">" + re + "</span>";
									}
									/*
									if (language.equals("de")) {
										for (int k=0; k<ListOfKeywordsDE.length; ++k) {
											// Exact match through keyword "\\b" for boundary
											re = re.replaceAll("\\b"+ListOfKeywordsDE[k]+" \\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsDE[k]+"</span> ");
											// Try also versions with ":" or "," or "."
											re = re.replaceAll("\\b"+ListOfKeywordsDE[k]+": \\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsDE[k]+":</span> ");
											re = re.replaceAll("\\b"+ListOfKeywordsDE[k]+", \\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsDE[k]+"</span>, ");
											// re = re.replaceAll("\\b"+ListOfKeywordsDE[i]+". \\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsDE[i]+"</span>. ");
											// Words at the end of the line
											re = re.replaceAll("\\b"+ListOfKeywordsDE[k]+".$", "<span style=\"font-style:italic;\">"+ListOfKeywordsDE[k]+"</span>.");
											re = re.replaceAll("\\b"+ListOfKeywordsDE[k]+"$", "<span style=\"font-style:italic;\">"+ListOfKeywordsDE[k]+"</span>");
											re = re.replaceAll("\\b"+ListOfKeywordsDE[k]+"\\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsDE[k]+"</span>");
										}
									} else if (language.equals("fr")) {
										for (int k=0; k<ListOfKeywordsFR.length; ++k) {
											// Exact match through keyword "\\b" for boundary
											re = re.replaceAll("\\b"+ListOfKeywordsFR[k]+" \\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsFR[k]+"</span> ");
											// Try also versions with ":" or "," or "."
											re = re.replaceAll("\\b"+ListOfKeywordsFR[k]+": \\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsFR[k]+":</span> ");
											re = re.replaceAll("\\b"+ListOfKeywordsFR[k]+", \\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsFR[k]+"</span>, ");
											// re = re.replaceAll("\\b"+ListOfKeywordsDE[i]+". \\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsDE[i]+"</span>. ");
											// Words at the end of the line
											re = re.replaceAll("\\b"+ListOfKeywordsFR[k]+".$", "<span style=\"font-style:italic;\">"+ListOfKeywordsFR[k]+"</span>.");
											re = re.replaceAll("\\b"+ListOfKeywordsFR[k]+"$", "<span style=\"font-style:italic;\">"+ListOfKeywordsFR[k]+"</span>");
											re = re.replaceAll("\\b"+ListOfKeywordsFR[k]+"\\b", "<span style=\"font-style:italic;\">"+ListOfKeywordsFR[k]+"</span>");
										}
									} else if (language.equals("it")) {
										//
									} else if (language.equals("en")) {
										//
									}
									*/
									// Important step: add the section content!
									if (img==null) {
										Element untertitle_elem = e.select("div[class=untertitle1], div[class=untertitle]").first();
										if (untertitle_elem==null)	// business as usual...
											html_str += ("<p class=\"spacing1\">" + re + "</p>");
										else {
											html_str += "<p class=\"untertitle\">" + untertitle_elem.html() + "</p>";
										}
									} else
										html_str += ("<p class=\"spacing1\">" + img + "</p>");
								} else if (e.tagName().equals("table")) {
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
										attr_str = "width:" + width + "%25;" + "background-color: #EEEEEE; padding-right: 5px; padding-left: 5px"; // "%25;";
										// Set new attribute
										cs.attr("style", attr_str);
									}
									// Change all fonts
									html_str += e.outerHtml();
								}

								if (e.nextElementSibling()==elemXp1 || e==elemXp1) {
									break;
								}
							}
						}
						html_str += ("</div>");
					}
				}
			}
		} else {
			// Less than 10 section titles... something's wrong
			System.out.println("---> HtmlUtils: Krasser Fehler!");
		}
		// Modified (pretty)html string
		Document newDoc = Jsoup.parse("<div id=\"monographie\" name=\"" + med_regnrs + "\">" + html_str + "</div>");
		newDoc.outputSettings().escapeMode(EscapeMode.xhtml);
		newDoc.outputSettings().charset("UTF-8");

		// Fools the jsoup-parser
		html_str = newDoc.html().replaceAll("&lt; ", "&lt;");
		// Replaces all supscripted � in the main text with �
		html_str = html_str.replaceAll(">â</sup>", ">®</sup>");
		// Replaces all &apos; with &quot; (the latter is fine with Html4  and Html5)
		html_str = html_str.replaceAll("&apos;", "’");

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
		/*
		Pattern ptn = Pattern.compile("<p class=\"spacing1\">.*(\\u00c2|\\u00a0).*</p>");
        Matcher mtch = ptn.matcher(html_str);
        while (mtch.find())
        	System.out.println(mtch.group());
        */

		// List items
		html_str = html_str.replaceAll("<p class=\"spacing1\">(–|·|-|•)", "<p class=\"spacing1\">– ");
		// Rest
		html_str = html_str.replaceAll("<p class=\"spacing1\"> </p>", "<p class=\"spacing1\"> </p>");
		html_str = html_str.replaceAll("<p class=\"spacing1\"> </p>\n(\\s+)</div>", "</div>");
		html_str = html_str.replaceAll("</div>\n <p class=\"spacing1\"> </p>", "</div>");

		return html_str;
	}

	static String convertHtmlToXml(String language, String info_type, String med_title, String html_str, String regnr_str) {
		Document mDoc = Jsoup.parse(html_str);
		mDoc.outputSettings().escapeMode(EscapeMode.xhtml);
		mDoc.outputSettings().prettyPrint(true);
		mDoc.outputSettings().indentAmount(4);
		// <div id="monographie"> -> <fi>
		mDoc.select("div[id=monographie]").tagName(info_type).removeAttr("id").removeAttr("name");
		// <div class="MonTitle"> -> <title>
		mDoc.select("div[class=MonTitle]").tagName("title").removeAttr("class").removeAttr("id");
		// Beautify the title to the best of my possibilities ... still not good enough!
		mDoc.select("title").text().trim().replaceAll("<br />","").replaceAll("(\\t|\\r?\\n)+","");
		// Fallback solution: use title from the header AIPS.xml file - the titles look all pretty good!
		mDoc.select("title").first().text(med_title.trim());
		// <div class="ownerCompany"> -> <owner>
		Element owner_elem = mDoc.select("div[class=ownerCompany]").first();
		if (owner_elem!=null) {
			owner_elem.tagName("owner").removeAttr("class");
			String owner_str = mDoc.select("owner").text();
			mDoc.select("owner").first().text(owner_str);
		} else {
			mDoc.select("title").after("<owner></owner>");
			if (language.equals("de"))
				mDoc.select("owner").first().text("k.A.");
			else if (language.equals("fr"))
				mDoc.select("owner").first().text("n.s.");
			else if (language.equals("it"))
				mDoc.select("owner").first().text("n.d.");
			else
				return "";
		}

		// <div class="paragraph"> -> <paragraph>
		mDoc.select("div[class=paragraph]").tagName("paragraph").removeAttr("class").removeAttr("id");
		// <div class="absTitle"> -> <paragraphTitle>
		mDoc.select("div[class=absTitle]").tagName("paragraphtitle").removeAttr("class");
		// <div class="untertitle1"> -> <paragraphSubTitle>
		mDoc.select("div[class=untertitle1]").tagName("paragraphsubtitle").removeAttr("class");
		// <div class="untertitle"> -> <paragraphSubTitle>
		mDoc.select("div[class=untertitle]").tagName("paragraphsubtitle").removeAttr("class");
		// <div class="shortCharacteristic"> -> <characteristic>
		mDoc.select("div[class=shortCharacteristic]").tagName("characteristic").removeAttr("class");
		// <div class="image">
		mDoc.select("div[class=image]").tagName("image").removeAttr("class");

		// <p class="spacing1"> -> <p> / <p class="noSpacing"> -> <p>
		mDoc.select("p[class]").tagName("p").removeAttr("class");
		// <span style="font-style:italic"> -> <i>
		mDoc.select("span").tagName("i").removeAttr("style");
		// <i class="indention1"> -> <i> / <i class="indention2"> -> <b-i>
		mDoc.select("i[class=indention1]").tagName("i").removeAttr("class");
		mDoc.select("i[class=indention2]").tagName("i").removeAttr("class");
		// mDoc.select("p").select("i").tagName("i");
		// mDoc.select("paragraphtitle").select("i").tagName("para-i");
		// mDoc.select("paragraphsubtitle").select("i").tagName("parasub-i");
		Elements elems = mDoc.select("paragraphtitle");
		for (Element e : elems) {
			if (!e.text().isEmpty())
				e.text(e.text());
		}
		elems = mDoc.select("paragraphsubtitle");
		for (Element e : elems) {
			if (!e.text().isEmpty())
				e.text(e.text());
		}

		// Here we take care of tables
		// <table class="s21"> -> <table>
		mDoc.select("table[class]").removeAttr("class");
		mDoc.select("table").removeAttr("cellspacing").removeAttr("cellpadding").removeAttr("border");
		mDoc.select("colgroup").remove();
		mDoc.select("td").removeAttr("class");//.removeAttr("colspan").removeAttr("rowspan");
		mDoc.select("tr").removeAttr("class");
		elems = mDoc.select("div[class]");
		for (Element e : elems) {
			if (e.text().isEmpty())
				e.remove();
		}

		mDoc.select("tbody").unwrap();
		// Remove nested table (a nasty table-in-a-table
		Elements nested_table = mDoc.select("table").select("tr").select("td").select("table");
		if (!nested_table.isEmpty()) {
			nested_table.select("table").unwrap();
		}

		// Here we take care of the images
		mDoc.select("img").removeAttr("align").removeAttr("border");

		// Subs and sups
		mDoc.select("sub[class]").tagName("sub").removeAttr("class");
		mDoc.select("sup[class]").tagName("sup").removeAttr("class");
		mDoc.select("td").select("sub").tagName("td-sub");
		mDoc.select("td").select("sup").tagName("td-sup");
		// Remove floating <td-sup> tags
		mDoc.select("p").select("td-sup").tagName("sup");
		mDoc.select("p").select("td-sub").tagName("sub");

		// Box
		mDoc.select("div[class=box]").tagName("box").removeAttr("class");

		// Insert swissmedicno5 after <owner> tag
		mDoc.select("owner").after("<swissmedicno5></swissmedicno5");
		mDoc.select("swissmedicno5").first().text(regnr_str);

		// Remove html, head and body tags
		String xml_str = mDoc.select("body").first().html();

		//xml_str = xml_str.replaceAll("<tbody>", "").replaceAll("</tbody>", "");
		xml_str = StringUtils.remove(xml_str, "<sup> </sup>");
		xml_str = StringUtils.remove(xml_str, "<sub> </sub>");
		xml_str = xml_str.replace("<p> <i>", "<p><i>");
		xml_str = xml_str.replace("</p> </td>", "</p></td>");
		xml_str = xml_str.replace("<p> </p>", "<p></p>");  // MUST be improved, the space is not a real space!!
		xml_str = xml_str.replace("·", "- ");		// Remove "middot" &middot; = &#183;
		xml_str = StringUtils.removeAll(xml_str, "<br />");
		xml_str = xml_str.replace("<i><i>", "<i>").replace("</i></i>", "</i>");
		xml_str = RegExUtils.removeAll(xml_str, "(?m)^[ \t]*\r?\n");

		// Remove multiple instances of <p></p>
		Scanner scanner = new Scanner(xml_str);
		String new_xml_str = "";
		int counter = 0;
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if (line.trim().equals("<p></p>")) {
				counter++;
			} else
				counter = 0;
			if (counter<3)
				new_xml_str += line;
		}
		scanner.close();

		return new_xml_str;
	}

	static String addHeaderToXml(String language, String header_str, String xml_str, Appendable appendable) {
		// Remove time stamp
		String xml_str_clean = RegExUtils.removeAll(xml_str, "<p>Auto-generated by.*?</p>");
		// Generate hash_code
		String hash_code = calcHashCode(xml_str_clean);
		addHeaderToXml(language, header_str, xml_str_clean, appendable, hash_code);
		return xml_str_clean;
	}

	static void addHeaderToXml(String language, String header_str, String xml_str, Appendable appendable, String hash_code) {
		Document mDoc = Jsoup.parse("<" + header_str +">\n" + xml_str + "</" + header_str + ">");
		mDoc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
		mDoc.outputSettings().escapeMode(EscapeMode.xhtml);
		mDoc.outputSettings().prettyPrint(true);
		mDoc.outputSettings().indentAmount(4);

		// Add date
		Date df = new Date();
		String date_str = df.toString();
		mDoc.select(header_str).first().prependElement("date");
		mDoc.select("date").first().text(date_str);

		// Add hash code
		mDoc.select("date").first().after("<hash>" + hash_code + "</hash>");

		// Add language
		if (language.equals("de"))
			mDoc.select("hash").first().after("<lang>DE</lang>");
		else if (language.equals("fr"))
			mDoc.select("hash").first().after("<lang>FR</lang>");
		else if (language.equals("it"))
			mDoc.select("hash").first().after("<lang>IT</lang>");
		else
			return;

		// Fool jsoup.parse which seems to have its own "life"
		mDoc.select("tbody").unwrap();
		Elements img_elems = mDoc.select("img");
		for (Element img_e : img_elems) {
			if (!img_e.hasAttr("src"))
				img_e.unwrap();
		}
		mDoc.select("img").tagName("image");

		mDoc.select(header_str).first().outerHtml(appendable);
		try {
			appendable.append("\n");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static String calcHashCode(String xml_str) {
		String hash_code = "";
		try {
			MessageDigest mMessageDigest = MessageDigest.getInstance("SHA-256");
			byte[] digest = mMessageDigest.digest(xml_str.getBytes("UTF-8"));
			BigInteger bigInt = new BigInteger(1, digest);
			hash_code = bigInt.toString(16);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return hash_code;
	}
}

