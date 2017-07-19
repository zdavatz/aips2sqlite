package com.maxl.java.aips2sqlite;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public class Utilities {

	final protected static char[] hexArray = "0123456789abcdef".toCharArray();

	static public final Map<String, Integer> doctorPreferences;
	static {
		// LinkedHashMap preserves insertion order
		doctorPreferences = new LinkedHashMap<>();
		// doctorPreferences.put("actavis", 1);	// actavis switzerland ag, 7601001376618
		doctorPreferences.put("helvepharm", 1);	// helvepharm ag, 7601001003736
		doctorPreferences.put("mepha", 2);		// mepha schweiz ag, 7601001396685
		doctorPreferences.put("sandoz", 3);		// sandoz pharmaceuticals ag, 7601001029439
		doctorPreferences.put("spirig", 4);		// spirig healthcare ag, 7601001394834
	}

	static public final Map<String, Integer> rosePreferences;
	static {
		// LinkedHashMap preserves insertion order
		rosePreferences = new LinkedHashMap<>();
		rosePreferences.put("sandoz", 1);		// sandoz pharmaceuticals ag
		rosePreferences.put("mepha", 2);		// mepha schweiz ag
		rosePreferences.put("teva", 3);			// teva pharma ag
	}

	static public int getChecksum(String eanStr) {
		int val=0;
		for (int i=0; i<eanStr.length(); i++)
			val += (Integer.parseInt(eanStr.charAt(i)+""))*((i%2==0)?1:3);

		int checksum_digit = 10 - (val % 10);
		if (checksum_digit == 10)
			checksum_digit = 0;

		return checksum_digit;
	}

	static public String removeSpaces(String name) {
		// Replace multiple spaces with single space
		return name.replaceAll("\\s\\s+", " ");
	}

	static public String removeAllCommas(String name) {
		return name.replaceAll(",", "");
	}

	static public String removeMultipleCommas(String str) {
		return str.replaceAll(",{2,}|,\\s,|\\s,", ",");
	}

	static public String removeTrailingComma(String str) {
		if (str.endsWith(","))
			return str.substring(0, str.length()-1);
		return str;
	}

	static public String convertDecimalFormat(String str) {
		Pattern regx = Pattern.compile("(\\d+),(\\d+)");
		Matcher match = regx.matcher(str);
		return match.replaceAll("$1.$2");
	}

	static public String removeStringFromString(String str1, String str2)
	{
		if (str1.contains(str2))
			str1 = str1.replaceAll("\\b" + str2 + "\\b", "");
		return str1;
	}

	static public String addStringToString(String str1, String str2, String separator) {
        /*
        str1 = str1.toLowerCase() + " ";
        str2 = str2.toLowerCase() + " ";
        if (str1.contains(str2))
            str1 = str1.replaceAll(str2, "");
        return str1;
        */
		str1 = str1.toLowerCase();
		str2 = str2.toLowerCase();
		if (str1.contains(str2))
			return str1;
		else {
			str1 += separator + str2;
			return str1;
		}
	}

	static public String capitalizeFirstLetter(String str) {
		if (!str.isEmpty()) {
			return str.substring(0, 1).toUpperCase() + str.substring(1);
		}
		return str;
	}

	static public String capitalizeSpacedLetters(String str) {
		// Split string
		String[] tokens = str.split("\\s");
		String ret = "";
		for (String t : tokens) {
			ret += capitalizeFirstLetter(t) + " ";
		}
		return ret.trim();
	}

	static public String capitalizeFully(String s, int N) {
		// Split string
		String[] tokens = s.split("\\s");
		// Capitalize only first word!
		tokens[0] = tokens[0].toUpperCase();
		// Reassemble string
		String full_s = "";
		if (tokens.length > 1) {
			for (int i = 0; i < tokens.length - 1; i++) {
				full_s += (tokens[i] + " ");
			}
			full_s += tokens[tokens.length - 1];
		} else {
			full_s = tokens[0];
		}
		return full_s;
	}

	static public String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j=0; j<bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j*2] = hexArray[v >>> 4];
			hexChars[j*2+1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	static public String calcEAN13Checksum(String ean12_str) {
		// Sum of all uneven digits
		int unevenSum = 0;
		for (int i=0; i<ean12_str.length(); i+=2) {
			unevenSum += Character.getNumericValue(ean12_str.charAt(i));
		}
		// Sum of all even digits
		int evenSum = 0;
		for (int i=1; i<ean12_str.length(); i+=2) {
			evenSum += Character.getNumericValue(ean12_str.charAt(i));
		}
		// Checksum = 90 - total sum
		String checkSum = String.valueOf(90 - (3*evenSum+unevenSum));

		return checkSum;
	}

	static public String prettyFormat(String input) {
		try {
			Source xmlInput = new StreamSource(new StringReader(input));
			StringWriter stringWriter = new StringWriter();
			StreamResult xmlOutput = new StreamResult(stringWriter);
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			transformer.transform(xmlInput, xmlOutput);
			return xmlOutput.getWriter().toString();
		} catch (Exception e) {
			throw new RuntimeException(e); // simple exception handling, please review it
		}
	}
}
