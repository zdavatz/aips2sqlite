package com.maxl.java.aips2sqlite.refdata;

import com.maxl.java.aips2sqlite.FileOps;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

public class ArticalDocument {
	public class Article {
		public String gtin_13;
		public String gtin_5;
		public String phar;
		public String name;
		public String atc;
		public String authorisation_identifier;
	};

	public class Paragraph {
		public String content;
		public Element element;
		public boolean is_italic;
	};

	public class Section {
		public String title;
		public String id;
		public ArrayList<Paragraph> paragraphs = new ArrayList<>();
	};

	public ArrayList<Section> sections = new ArrayList<>();

	public ArticalDocument(String path) {
		String documentContent = FileOps.readFromFile(path);
		{

			documentContent = unescapeHtmlEntity(documentContent);
			documentContent = documentContent.replace("\uFEFF", "");

			// Somehow the html files are so broken they has 2 xml declaration,
			// remove them all and add one back at last
			documentContent = documentContent.replace("<?xml version=\"1.0\" encoding=\"utf-8\"?>", "");

			// Somehow the html files are so broken, it starts with <div> instead of <html>
			// but interestingly with </html>
			String regex = "^\\s*<div ";
			if (documentContent.matches(regex)) {
				System.out.println("Malformed html found(1), trying to fix: " + path);
			}
			documentContent = documentContent.replaceAll(regex, "<html ");

			// Somehow the html files are so broken, the <html> tag sometimes have weird quotes
			regex = "<html \\S*\">\"";
			if (documentContent.matches(regex)) {
				System.out.println("Malformed html found(2), trying to fix: " + path);
			}
			documentContent = documentContent.replaceAll(regex, "<html>");
		}
		Document doc = Jsoup.parse(documentContent);
		HashSet<String> italicClasses = getItalicClasses(doc);
		Element body = doc.body();

		if (body.children().size() == 1 && body.children().get(0).tagName().equals("div")) {
			body = body.children().get(0);
		}
		cleanUpEmptyElements(body);

		int sectionNumber = 1;
		Section currentSection = new Section();
		for (Element elementInBody : body.children()) {
			if (elementInBody.tagName().equals("p")) {
				String id = elementInBody.id();
				boolean needNewSection = currentSection.title == null || (id != null && !id.isEmpty());
				if (needNewSection && currentSection.title != null) {
					sections.add(currentSection);
					currentSection = new Section();
					sectionNumber++;
				}

				if (needNewSection) {
					currentSection.id = "section" + sectionNumber;
					currentSection.title = elementInBody.text();
				} else {
					HashSet<String> selfClasses = allClassesOfElement(elementInBody);
					selfClasses.retainAll(italicClasses);

					Paragraph paragraph = new Paragraph();
					paragraph.content = elementInBody.text();
					paragraph.is_italic = !selfClasses.isEmpty();
					currentSection.paragraphs.add(paragraph);
				}
			} else {
				Paragraph paragraph = new Paragraph();
				paragraph.element = elementInBody;
				currentSection.paragraphs.add(paragraph);
			}
		}
		if (currentSection.title != null) {
			sections.add(currentSection);
		}

		this.sections = sections;
	}

	public static String unescapeHtmlEntity(String html) {
		html = html.replace("&nbsp;",   " ");
		html = html.replace("&ge;",     "≥");
		html = html.replace("&le;",     "≤");
		html = html.replace("&plusmn;", "±"); // used in rn 58868 table 6
		html = html.replace("&agrave;", "à");
		html = html.replace("&Agrave;", "À");
		html = html.replace("&acirc;",  "â");
		html = html.replace("&Acirc;",  "Â");
		html = html.replace("&auml;",   "ä");
		html = html.replace("&Auml;",   "Ä");
		html = html.replace("&egrave;", "è");
		html = html.replace("&Egrave;", "È");
		html = html.replace("&eacute;", "é");
		html = html.replace("&Eacute;", "É");
		html = html.replace("&ecirc;",  "ê");
		html = html.replace("&euml;",   "ë");
		html = html.replace("&iuml;",   "ï");
		html = html.replace("&icirc;",  "î");
		html = html.replace("&ouml;",   "ö");
		html = html.replace("&ocirc;",  "ô");
		html = html.replace("&Ouml;",   "Ö");
		html = html.replace("&Ograve;", "Ò");
		html = html.replace("&uuml;",   "ü");
		html = html.replace("&Uuml;",   "Ü");
		html = html.replace("&oelig;",  "œ");
		html = html.replace("&OElig;",  "Œ");
		html = html.replace("&middot;", "–"); // the true middot is "·"
		html = html.replace("&bdquo;",  "„");
		html = html.replace("&ldquo;",  "“");
		html = html.replace("&lsquo;",  "‘");
		html = html.replace("&rsquo;",  "’");
		html = html.replace("&alpha;",  "α");
		html = html.replace("&beta;",   "β");
		html = html.replace("&gamma;",  "γ");
		html = html.replace("&kappa;",  "κ");
		html = html.replace("&micro;",  "µ");
		html = html.replace("&mu;",     "μ");
		html = html.replace("&phi;",    "φ");
		html = html.replace("&Phi;",    "Φ");
		html = html.replace("&tau;",    "τ");
		html = html.replace("&frac12;", "½");
		html = html.replace("&minus;",  "−");
		html = html.replace("&mdash;",  "—");
		html = html.replace("&ndash;",  "–");
		html = html.replace("&bull;",   "•"); // See rn 63182. Where is this in the Java code ?
		html = html.replace("&reg;",    "®");
		html = html.replace("&copy;",   "©");
		html = html.replace("&trade;",  "™");
		html = html.replace("&laquo;",  "«");
		html = html.replace("&raquo;",  "»");
		html = html.replace("&deg;",    "°");
		html = html.replace("&sup1;",   "¹");
		html = html.replace("&sup2;",   "²");
		html = html.replace("&sup3;",   "³");
		html = html.replace("&times;",  "×");
		html = html.replace("&pi;",     "π");
		html = html.replace("&szlig;",  "ß");
		html = html.replace("&infin;",  "∞");
		html = html.replace("&dagger;", "†");
		html = html.replace("&Dagger;", "‡");
		html = html.replace("&sect;",   "§");
		html = html.replace("&spades;", "♠"); // rn 63285, table 2
		html = html.replace("&THORN;",  "Þ");
		html = html.replace("&Oslash;", "Ø");
		html = html.replace("&para;",   "¶");

		html = html.replace("&frasl;",  "⁄"); // see rn 36083
		html = html.replace("&curren;", "¤");
		html = html.replace("&yen;",    "¥");
		html = html.replace("&pound;",  "£");
		html = html.replace("&ordf;",   "ª");
		html = html.replace("&ccedil;", "ç");

		html = html.replace("&larr;", "←");
		html = html.replace("&uarr;", "↑");
		html = html.replace("&rarr;", "→");
		html = html.replace("&darr;", "↓");
		html = html.replace("&harr;", "↔");
		return html;
	}

	public static void cleanUpEmptyElements(Element element) {
		for (Element child : element.children()) {
			cleanUpEmptyElements(child);
		}
		ArrayList<Element> copied = element.children();
		for (Element child : copied) {
			if ((child.tagName().equals("span") || child.tagName().equals("p") || child.tagName().equals("div")) && child.text().trim().isEmpty()) {
				child.remove();
			} else if (child.tagName().equals("span") && child.attributesSize() == 0) {
				child.replaceWith(new TextNode(child.text()));
			}
		}
	}

	private HashSet<String> getItalicClasses(Document doc) {
		HashSet<String> result = new HashSet<String>();
		Element headElement = doc.head();
		Element styleElement = null;
		for (Element e : headElement.children()) {
			if (e.tagName().equals("style")) {
				styleElement = e;
				break;
			}
		}
		if (styleElement == null) {
			return result;
		}
		String styleText = styleElement.outerHtml();
		Pattern stylePattern = Pattern.compile("\\.(s\\d+)\\{([^}]*)\\}");
		Matcher m = stylePattern.matcher(styleText);
		while (m.find()) {
			String className = m.group(1);
			String classContent = m.group(2);
			if (classContent.contains("font-style:italic")) {
				result.add(className);
			}
		}
		return result;
	}

	private HashSet<String> allClassesOfElement(Element element) {
		HashSet<String> result = new HashSet<String>();
		String classString = element.attr("class");
		if (classString != null) {
			result.addAll(Arrays.asList(classString.split(" ")));
		}
		for (Element child : element.children()) {
			result.addAll(allClassesOfElement(child));
		}
		return result;
	}
}
