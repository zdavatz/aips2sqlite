package com.maxl.java.aips2sqlite;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public class Utilities {

	final protected static char[] hexArray = "0123456789abcdef".toCharArray();
	
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
