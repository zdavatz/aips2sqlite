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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.w3c.dom.Document;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;

public class AllDown {
	
	public void downAipsXml(String file_medical_infos_xsd, String file_medical_infos_xml) {
		// http://download.swissmedicinfo.ch/
		boolean disp = false;
		ProgressBar pb = new ProgressBar();
		
		try {
			// Suppress all warnings!
			java.util.logging.Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF); 
			// Start timer 
			long startTime = System.currentTimeMillis();
			if (disp)
				System.out.print("- Downloading AIPS file... ");		
			else {
				pb.init("- Downloading AIPS file... ");
				pb.start();	
			}
			
			WebClient webClient = new WebClient();
			// Get Swissmedic webpage
			HtmlPage currentPage = webClient.getPage("http://download.swissmedicinfo.ch/");
			// Simulate button click on "OK" button
			HtmlSubmitInput acceptBtn = currentPage.getElementByName("ctl00$MainContent$btnOK");			
			currentPage = acceptBtn.click();
			// Simulate button click on "Yes" button
			acceptBtn = currentPage.getElementByName("ctl00$MainContent$BtnYes");	

			InputStream is = acceptBtn.click().getWebResponse().getContentAsStream();
						
			File destination = new File("./downloads/tmp/aips.zip");
			FileUtils.copyInputStreamToFile(is, destination);
			
			is.close();	
			webClient.closeAllWindows();
			
			if (!disp)
				pb.stopp();
			
			unzipToTemp(destination);
			
			// Copy file ./tmp/unzipped_preparations/Preparations.xml to ./xml/bag_preparations_xml.xml
			File folder = new File("./downloads/tmp/unzipped_tmp");
			File[] listOfFiles = folder.listFiles();
			for (int i=0; i<listOfFiles.length; ++i) {
				if (listOfFiles[i].isFile()) {
					String file = listOfFiles[i].getName();
					if (file.endsWith(".xml")) {
				        File src = new File("./downloads/tmp/unzipped_tmp/" + file);
				        File dst = new File(file_medical_infos_xml);
				        FileUtils.copyFile(src, dst);
						// Stop timer 
						long stopTime = System.currentTimeMillis();				        
						System.out.println("\r- Downloading AIPS file... " + dst.length()/1024 + " kB in " + (stopTime-startTime)/1000.0f + " sec");
					} else if (file.endsWith(".xsd")) {
				        File src = new File("./downloads/tmp/unzipped_tmp/" + file);
				        File dst = new File(file_medical_infos_xsd);
				        FileUtils.copyFile(src, dst);
					}
				}
			}
		
	        // Delete folder ./tmp
	        FileUtils.deleteDirectory(new File("./xml/tmp"));	     			
		} catch(Exception e) {
			if (!disp)
				pb.stopp();			
			System.err.println(" Exception: in 'downAipsXml'");
			e.printStackTrace();
			return;
		}		
	}
	
	public void downPackungenXls(String file_packages_xls) {
		boolean disp = false;
		ProgressBar pb = new ProgressBar();
		
		try {
			// Start timer 
			long startTime = System.currentTimeMillis();
			if (disp)
				System.out.print("- Downloading Packungen file... ");	
			else {
				pb.init("- Downloading Packungen file... ");
				pb.start();	
			}
				
			// URL url = new URL("http://www.swissmedic.ch/daten/00080/00251/index.html?lang=de&download=NHzLpZeg7t,lnp6I0NTU042l2Z6ln1acy4Zn4Z2qZpnO2Yuq2Z6gpJCDdH56fWym162epYbg2c_JjKbNoKSn6A--&.xls");
			URL url = new URL("https://www.swissmedic.ch/arzneimittel/00156/00221/00222/00230/index.html?lang=de&download=NHzLpZeg7t,lnp6I0NTU042l2Z6ln1acy4Zn4Z2qZpnO2Yuq2Z6gpJCDdHx7hGym162epYbg2c_JjKbNoKSn6A");
			File destination = new File(file_packages_xls);			
			FileUtils.copyURLToFile(url, destination, 60000, 60000);

			if (!disp)
				pb.stopp();
			long stopTime = System.currentTimeMillis();		
			System.out.println("\r- Downloading Packungen file... " + destination.length()/1024 + " kB in " + (stopTime-startTime)/1000.0f + " sec");
		} catch (Exception e) {
			if (!disp)
				pb.stopp();			
			System.err.println(" Exception: in 'downPackungenXls'");
			e.printStackTrace();
		}		
	}
	
	public void downSwissindexXml(String language, String file_refdata_pharma_xml) {		
		boolean disp = false;
		ProgressBar pb = new ProgressBar();	
		
		try {
			// Start timer 
			long startTime = System.currentTimeMillis();
			if (disp)
				System.out.print("- Downloading Swissindex (" + language + ") file... ");
			else {
				pb.init("- Downloading Swissindex (" + language + ") file... ");
				pb.start();
			}
			
			SOAPMessage soapRequest = MessageFactory.newInstance().createMessage();

			// Setting SOAPAction header line
			MimeHeaders headers = soapRequest.getMimeHeaders();
			headers.addHeader("SOAPAction", "http://swissindex.e-mediat.net/SwissindexPharma_out_V101/DownloadAll");	
	        
			SOAPPart soapPart = soapRequest.getSOAPPart();
			SOAPEnvelope envelope = soapPart.getEnvelope();
			SOAPBody soapBody = envelope.getBody();
			// Construct SOAP request message
			SOAPElement soapBodyElement1 = soapBody.addChildElement("pharmacode");
			soapBodyElement1.addNamespaceDeclaration("", "http://swissindex.e-mediat.net/SwissindexPharma_out_V101");
			soapBodyElement1.addTextNode("DownloadAll");
			SOAPElement soapBodyElement2 = soapBody.addChildElement("lang");
			soapBodyElement2.addNamespaceDeclaration("", "http://swissindex.e-mediat.net/SwissindexPharma_out_V101");
			if (language.equals("DE"))
				soapBodyElement2.addTextNode("DE");
			else if (language.equals("FR"))
				soapBodyElement2.addTextNode("FR");
			else {
				System.err.println("down_swissindex_xml: wrong language!");
				return;
			}
			soapRequest.saveChanges();
			
			SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();			
			SOAPConnection connection = soapConnectionFactory.createConnection();			
			String wsURL = "https://swissindex.refdata.ch/Swissindex/Pharma/ws_Pharma_V101.asmx?WSDL";
			SOAPMessage soapResponse = connection.call(soapRequest, wsURL);

			Document doc = soapResponse.getSOAPBody().extractContentAsDocument();
			String strBody = getStringFromDoc(doc);
			String xmlBody = prettyFormat(strBody);
			// Note: parsing the Document tree and using the removeAttribute function is hopeless! 
			xmlBody = xmlBody.replaceAll("xmlns.*?\".*?\" ", "");			
			long len = writeToFile(xmlBody, file_refdata_pharma_xml);
			
			if (!disp)
				pb.stopp();
			long stopTime = System.currentTimeMillis();	
			System.out.println("\r- Downloading Swissindex (" + language + ") file... " + len/1024 + " kB in " + (stopTime-startTime)/1000.0f + " sec");
			
			connection.close();			
			
		} catch (Exception e) {
			if (!disp)
				pb.stopp();			
			System.err.println(" Exception: in 'downSwissindexXml'");
			e.printStackTrace();
		}		
	}
	
	public void downRefdatabaseXml(String file_refdata_pharma_xml) {		
		boolean disp = false;
		ProgressBar pb = new ProgressBar();	
		
		try {
			// Start timer 
			long startTime = System.currentTimeMillis();
			if (disp)
				System.out.print("- Downloading Refdatabase file... ");
			else {
				pb.init("- Downloading Refdatabase file... ");
				pb.start();
			}
			
			// Create soaprequest
			SOAPMessage soapRequest = MessageFactory.newInstance().createMessage();
			// Set SOAPAction header line
			MimeHeaders headers = soapRequest.getMimeHeaders();
			headers.addHeader("SOAPAction", "http://refdatabase.refdata.ch/Pharma/Download");		        
			// Set SOAP main request part
			SOAPPart soapPart = soapRequest.getSOAPPart();
			SOAPEnvelope envelope = soapPart.getEnvelope();
			SOAPBody soapBody = envelope.getBody();
			// Construct SOAP request message
			SOAPElement soapBodyElement1 = soapBody.addChildElement("DownloadArticleInput");
			soapBodyElement1.addNamespaceDeclaration("", "http://refdatabase.refdata.ch/");				
			SOAPElement soapBodyElement2 = soapBodyElement1.addChildElement("ATYPE");
			soapBodyElement2.addNamespaceDeclaration("", "http://refdatabase.refdata.ch/Article_in");			
			soapBodyElement2.addTextNode("ALL");	
			soapRequest.saveChanges();
			// If needed print out soapRequest in a pretty format
			// prettyFormatSoapXml(soapRequest);
			// Create connection to SOAP server
			SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();			
			SOAPConnection connection = soapConnectionFactory.createConnection();			
			// wsURL contains service end point
			String wsURL = "http://refdatabase.refdata.ch/Service/Article.asmx?WSDL";
			SOAPMessage soapResponse = connection.call(soapRequest, wsURL);
			// Extract response
			Document doc = soapResponse.getSOAPBody().extractContentAsDocument();
			String strBody = getStringFromDoc(doc);
			String xmlBody = prettyFormat(strBody);			
			// Note: parsing the Document tree and using the removeAttribute function is hopeless! 			
			xmlBody = xmlBody.replaceAll("xmlns.*?\".*?\" ", "");			
			
			long len = writeToFile(xmlBody, file_refdata_pharma_xml);
			
			if (!disp)
				pb.stopp();
			long stopTime = System.currentTimeMillis();	
			System.out.println("\r- Downloading Refdatabase file... " + len/1024 + " kB in " + (stopTime-startTime)/1000.0f + " sec");
			
			connection.close();			
			
		} catch (Exception e) {
			if (!disp)
				pb.stopp();			
			System.err.println(" Exception: in 'downRefdatabaseXml'");
			e.printStackTrace();
		}		
	}
	
	public void downPreparationsXml(String file_preparations_xml) {
		// http://bag.e-mediat.net/SL2007.Web.External/File.axd?file=XMLPublications.zip
		boolean disp = false;
		ProgressBar pb = new ProgressBar();	
		
		try {
			// Start timer 
			long startTime = System.currentTimeMillis();
			if (disp)
				System.out.print("- Downloading Preparations file... ");
			else {
				pb.init("- Downloading Preparations file... ");
				pb.start();
			}
			
			URL url = new URL("http://bag.e-mediat.net/SL2007.Web.External/File.axd?file=XMLPublications.zip");
			File destination = new File("./downloads/tmp/preparations.zip");
			FileUtils.copyURLToFile(url, destination, 60000, 60000);
			
			unzipToTemp(destination);
	        
	        // Copy file ./tmp/unzipped_preparations/Preparations.xml to ./xml/bag_preparations_xml.xml
	        File src = new File("./downloads/tmp/unzipped_tmp/Preparations.xml");
	        File dst = new File(file_preparations_xml);
	        FileUtils.copyFile(src, dst);
	        
	        if (!disp)
	        	pb.stopp();
	        long stopTime = System.currentTimeMillis();	
	        System.out.println("\r- Downloading Preparations file... " + dst.length()/1024 + " kB in " + (stopTime-startTime)/1000.0f + " sec");

	        // Delete folder ./tmp
	        FileUtils.deleteDirectory(new File("./downloads/tmp"));	        
		} catch (Exception e) {
			if (!disp)
				pb.stopp();			
			System.err.println(" Exception: in 'downPreparationsXml'");
			e.printStackTrace();
		}			
	}
	
	public void downSwissDRGXlsx(String language, String file_swiss_drg_xlsx) {
		boolean disp = false;
		ProgressBar pb = new ProgressBar();
		
		try {
			// Start timer 
			long startTime = System.currentTimeMillis();
			if (disp)
				System.out.print("- Downloading Swiss DRG (" + language + ") file... ");	
			else {
				pb.init("- Downloading Swiss DRG (" + language + ") file... ");
				pb.start();	
			}
				
			URL url = null;
			if (language.equals("DE"))
				url = new URL("http://www.swissdrg.org/assets/Excel/System_30/131118_SwissDRG-Version_3.0_Fallpauschalenkatalog2014_d_geprueft_CHOP2014.xlsx");
			else if (language.equals("FR"))
				url = new URL("http://www.swissdrg.org/assets/Excel/System_30/131118_SwissDRG-Version_3.0_Fallpauschalenkatalog2014_f_geprueft_CHOP2014_erratum.xlsx");
				
			if (url!=null) {
				File destination = new File(file_swiss_drg_xlsx);			
				FileUtils.copyURLToFile(url, destination, 60000, 60000);		
				if (!disp)
					pb.stopp();
				long stopTime = System.currentTimeMillis();		
				System.out.println("\r- Downloading Swiss DRG (" + language + ") file... " + destination.length()/1024 + " kB in " + (stopTime-startTime)/1000.0f + " sec");
			}
		} catch (Exception e) {
			if (!disp)
				pb.stopp();			
			System.err.println(" Exception: in 'downSwissDRGXls'");
			e.printStackTrace();
		}
	}
			
	public void downEPhaInteractionsCsv(String language, String file_interactions_csv) {
		boolean disp = false;
		ProgressBar pb = new ProgressBar();
		
		try {
			// Ignore validation for https sites
			setNoValidation();			
			
			// Start timer
			long startTime = System.currentTimeMillis();
			if (disp)
				System.out.print("- Downloading EPha interactions (" + language + ") file... ");	
			else {
				pb.init("- Downloading EPha interactions file (" + language + ")... ");
				pb.start();	
			}
			
			URL url = null;
			if (language.equals("DE"))
				url = new URL("http://download.epha.ch/data/matrix/matrix.csv");
			else if (language.equals("FR"))
				url = new URL("http://download.epha.ch/data/matrix/matrix.csv");
				
			if (url!=null) {
				File destination = new File(file_interactions_csv);			
				FileUtils.copyURLToFile(url, destination, 60000, 60000);		
				if (!disp)
					pb.stopp();
				long stopTime = System.currentTimeMillis();		
				System.out.println("\r- Downloading EPha interactions (" + language + ") file... " + destination.length()/1024 + " kB in " + (stopTime-startTime)/1000.0f + " sec");
			}				
		} catch (Exception e) {
			if (!disp)
				pb.stopp();			
			System.err.println(" Exception: in 'downInteractionsCsv'");
			e.printStackTrace();
		}	
	}
	
	public void downEPhaProductsJson(String language, String file_products_json) {
		boolean disp = false;
		ProgressBar pb = new ProgressBar();
		
		try {
			// Ignore validation for https sites
			setNoValidation();

			// Start timer
			long startTime = System.currentTimeMillis();
			if (disp)
				System.out.print("- Downloading EPha products (" + language + ") file... ");	
			else {
				pb.init("- Downloading EPha products (" + language + ") file... ");
				pb.start();	
			}
			
			URL url = null;
			if (language.equals("DE"))
				url = new URL("http://download.epha.ch/cleaned/produkte.json");
			else if (language.equals("FR"))
				url = new URL("http://download.epha.ch/cleaned/produkte.json");
			if (url!=null) {
				File destination = new File(file_products_json);			
				
				Files.copy(url.openStream(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
				
				
				// FileUtils.copyURLToFile(url, destination, 60000, 60000);	
				if (!disp)
					pb.stopp();
				long stopTime = System.currentTimeMillis();		
				System.out.println("\r- Downloading EPha products (" + language + ") file... " + destination.length()/1024 + " kB in " + (stopTime-startTime)/1000.0f + " sec");
			}					
		} catch (Exception e) {
			if (!disp)
				pb.stopp();			
			System.err.println(" Exception: in 'downInteractionsCsv'");
			e.printStackTrace();
		}	
	}
	
	public void downEphaATCCodesCsv(String file_atc_codes_csv) {
		boolean disp = false;
		ProgressBar pb = new ProgressBar();
		
		try {
			// Ignore validation for https sites
			setNoValidation();			
			
			// Start timer
			long startTime = System.currentTimeMillis();
			if (disp)
				System.out.print("- Downloading EPha ATC codes file... ");	
			else {
				pb.init("- Downloading EPha ATC codes file... ");
				pb.start();	
			}
			
			URL url = null;
			url = new URL("http://download.epha.ch/data/atc/atc.csv");
				
			if (url!=null) {
				File destination = new File(file_atc_codes_csv);			
				FileUtils.copyURLToFile(url, destination, 60000, 60000);		
				if (!disp)
					pb.stopp();
				long stopTime = System.currentTimeMillis();		
				System.out.println("\r- Downloading EPha ATC codes file... " + destination.length()/1024 + " kB in " + (stopTime-startTime)/1000.0f + " sec");
			}				
		} catch (Exception e) {
			if (!disp)
				pb.stopp();			
			System.err.println(" Exception: in 'downEphaATCCodesCsv'");
			e.printStackTrace();
		}
	}
	
	public void downGLNCodesXlsx(String file_glncodes_people_xlsx, String file_glncodes_companies_xlsx) {
		boolean disp = false;
		ProgressBar pb = new ProgressBar();
		
		try {
			// Ignore validation for https sites
			// setNoValidation();
			
			// Start timer 
			long startTime = System.currentTimeMillis();
			if (disp)
				System.out.print("- Downloading GLN codes files (Personen + Betriebe)... ");	
			else {
				pb.init("- Downloading GLN codes files (Personen + Betriebe)... ");
				pb.start();	
			}
				
			URL url = null;
			url = new URL("https://www.medregbm.admin.ch/Publikation/CreateExcelListMedizinalPersons");				
			if (url!=null) {
				File destination = new File(file_glncodes_people_xlsx);			
				// FileUtils.copyURLToFile(url, destination);	
				FileUtils.copyURLToFile(url, destination, 60000, 60000);
				if (!disp)
					pb.stopp();
				long stopTime = System.currentTimeMillis();		
				System.out.println("\r- Downloading GLN codes file (people/personen)... " + destination.length()/1024 + " kB in " + (stopTime-startTime)/1000.0f + " sec");
			}

			startTime = System.currentTimeMillis();
			url = new URL("https://www.medregbm.admin.ch/Publikation/CreateExcelListBetriebs");			
			if (url!=null) {
				File destination = new File(file_glncodes_companies_xlsx);			
				FileUtils.copyURLToFile(url, destination, 60000, 60000);		
				if (!disp)
					pb.stopp();
				long stopTime = System.currentTimeMillis();		
				System.out.println("\r- Downloading GLN codes file (companies/betriebe)... " + destination.length()/1024 + " kB in " + (stopTime-startTime)/1000.0f + " sec");
			}
		} catch (Exception e) {
			if (!disp)
				pb.stopp();			
			System.err.println(" Exception: in 'downGLNCodesXlsx'");
			e.printStackTrace();
		}
	}
	
	public void downIBSA() {
		String fl = "";
		String fp = "";
		String fs = "";
		try {
			FileInputStream glnCodesCsv = new FileInputStream(Constants.DIR_SHOPPING + "/access.ami.csv");
			BufferedReader br = new BufferedReader(new InputStreamReader(glnCodesCsv, "UTF-8"));
			String line;
			while ((line=br.readLine()) !=null ) {
				// Semicolon is used as a separator
				String[] gln = line.split(";");
				if (gln.length>2) {
					if (gln[0].equals("IbsaAmiko")) {
						fl = gln[0];
						fp = gln[1];
						fs = gln[2];
					}
				}
			}		
			br.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		FTPClient ftp_client = new FTPClient();				
		try {
			ftp_client.connect(fs, 21);
			ftp_client.login(fl, fp);
			ftp_client.enterLocalPassiveMode();
			ftp_client.changeWorkingDirectory("data");
			ftp_client.setFileType(FTP.BINARY_FILE_TYPE);

			int reply = ftp_client.getReplyCode();
			if (!FTPReply.isPositiveCompletion(reply)) {
				ftp_client.disconnect();
				System.err.println("FTP server refused connection.");
				return;
			}

			System.out.println("- Connected to server " + fs + "...");
			 //get list of filenames
            FTPFile[] ftpFiles = ftp_client.listFiles(); 
            
            List<String> list_remote_files = Arrays.asList("Konditionen.csv", "Targeting_diff.csv", "Address.csv");
            List<String> list_local_files = Arrays.asList(Constants.FILE_CUST_IBSA, Constants.FILE_TARG_IBSA, Constants.FILE_MOOS_ADDR);
            
            if (ftpFiles!=null && ftpFiles.length>0) {
            	int index = 0;
            	for (String remote_file : list_remote_files) {
	            	OutputStream os = new FileOutputStream(Constants.DIR_SHOPPING + "/" + list_local_files.get(index));
	            	System.out.print("- Downloading " + remote_file + " from server " + fs + "... ");
	
	            	boolean done = ftp_client.retrieveFile(remote_file, os);
	            	if (done)
	            		System.out.println("file downloaded successfully.");
	            	else
	            		System.out.println("error.");
	            	os.close();
	            	index++;
            	}
            }
		} catch (IOException ex) {
			System.out.println("Error: " + ex.getMessage());
			ex.printStackTrace();
		} finally {
			try {
				if (ftp_client.isConnected()) {
					ftp_client.logout();
					ftp_client.disconnect();
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public void downZurRose() {
		String fl = "";
		String fp = "";
		String fs = "";
		try {
			FileInputStream access = new FileInputStream(Constants.DIR_ZURROSE + "/access.ami.csv");
			BufferedReader br = new BufferedReader(new InputStreamReader(access, "UTF-8"));
			String line;
			while ((line=br.readLine()) !=null ) {
				// Semicolon is used as a separator
				String[] gln = line.split(";");
				if (gln.length>2) {
					if (gln[0].equals("P_ywesee")) {
						fl = gln[0];
						fp = gln[1];
						fs = gln[2];
					}
				}
			}		
			br.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		FTPClient ftp_client = new FTPClient();				
		try {
			ftp_client.connect(fs, 21);
			ftp_client.login(fl, fp);
			ftp_client.enterLocalPassiveMode();
			ftp_client.setFileType(FTP.BINARY_FILE_TYPE);

			System.out.println("- Connected to server " + fs + "...");

			String[] working_dir = {"ywesee out", "../ywesee in"};
			
			for (int i=0; i<working_dir.length; ++i) {
				// Set working directory
				ftp_client.changeWorkingDirectory(working_dir[i]);
				int reply = ftp_client.getReplyCode();
				if (!FTPReply.isPositiveCompletion(reply)) {
					ftp_client.disconnect();
					System.err.println("FTP server refused connection.");
					return;
				}
				// Get list of filenames
	            FTPFile[] ftpFiles = ftp_client.listFiles();            
	            if (ftpFiles!=null && ftpFiles.length>0) {
	            	// ... then download all csv files
	            	for (FTPFile f : ftpFiles) {
	            		String remote_file = f.getName();
	            		if (remote_file.endsWith("csv")) {
	            			String local_file = remote_file;
	            			if (remote_file.startsWith("Artikelstamm"))
	            				local_file = Constants.CSV_FILE_DISPO_ZR;
	            			OutputStream os = new FileOutputStream(Constants.DIR_ZURROSE + "/" + local_file);
	                    	System.out.print("- Downloading " + remote_file + " from server " + fs + "... ");	
	                    	boolean done = ftp_client.retrieveFile(remote_file, os);
	                    	if (done)
	                    		System.out.println("success.");
	                    	else
	                    		System.out.println("error.");
	                    	os.close();
	            		}
	            	}
	            }
			}            
		} catch (IOException ex) {
			System.out.println("Error: " + ex.getMessage());
			ex.printStackTrace();
		} finally {
			try {
				if (ftp_client.isConnected()) {
					ftp_client.logout();
					ftp_client.disconnect();
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public void downDesitin() {
		String fl = "";
		String fp = "";
		String fs = "";
		try {
			FileInputStream access = new FileInputStream(Constants.DIR_DESITIN + "/access.ami.csv");
			BufferedReader br = new BufferedReader(new InputStreamReader(access, "UTF-8"));
			String line;
			while ((line=br.readLine()) !=null ) {
				// Semicolon is used as a separator
				String[] gln = line.split(";");
				if (gln.length>2) {
					if (gln[0].equals("ftp_amiko")) {
						fl = gln[0];
						fp = gln[1];
						fs = gln[2];
					}
				}
			}		
			br.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		FTPClient ftp_client = new FTPClient();				
		try {
			ftp_client.connect(fs, 21);
			ftp_client.login(fl, fp);
			ftp_client.enterLocalPassiveMode();
			ftp_client.setFileType(FTP.BINARY_FILE_TYPE);

			System.out.println("- Connected to server " + fs + "...");

			// Set working directory
			String working_dir = "ywesee_in";				
			ftp_client.changeWorkingDirectory(working_dir);
			int reply = ftp_client.getReplyCode();
			if (!FTPReply.isPositiveCompletion(reply)) {
				ftp_client.disconnect();
				System.err.println("FTP server refused connection.");
				return;
			}
			// Get list of filenames
            FTPFile[] ftpFiles = ftp_client.listFiles();            
            if (ftpFiles!=null && ftpFiles.length>0) {
            	// ... then download all csv files
            	for (FTPFile f : ftpFiles) {
            		String remote_file = f.getName();
            		if (remote_file.endsWith("csv")) {
            			String local_file = remote_file;
            			if (remote_file.startsWith("Kunden"))
            				local_file = Constants.FILE_CUST_DESITIN;
            			if (remote_file.startsWith("Artikel"))
            				local_file = Constants.FILE_ARTICLES_DESITIN;
            			OutputStream os = new FileOutputStream(Constants.DIR_DESITIN + "/" + local_file);
                    	System.out.print("- Downloading " + remote_file + " from server " + fs + "... ");	
                    	boolean done = ftp_client.retrieveFile(remote_file, os);
                    	if (done)
                    		System.out.println("success.");
                    	else
                    		System.out.println("error.");
                    	os.close();
            		}
            	}
            }
		} catch (IOException ex) {
			System.out.println("Error: " + ex.getMessage());
			ex.printStackTrace();
		} finally {
			try {
				if (ftp_client.isConnected()) {
					ftp_client.logout();
					ftp_client.disconnect();
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}
	
    /*
     *  fix for exception 
     *  javax.net.ssl.SSLHandshakeException: sun.security.validator.ValidatorException:
     *  PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException:
     *  unable to find valid certification path to requested target
     */	
	private void setNoValidation() throws Exception {
		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			@Override
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			@Override
			public void checkClientTrusted(X509Certificate[] certs, String authType) {
				// Do nothing
			}

			@Override
			public void checkServerTrusted(X509Certificate[] certs,	String authType) {
				// Do nothing
			}
		} };

		// Install the all-trusting trust manager		
		SSLContext sc = SSLContext.getInstance("SSL"); 
		sc.init(null, trustAllCerts, new java.security.SecureRandom());
		HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		
		// Create all-trusting host name verifier
		HostnameVerifier allHostsValid = new HostnameVerifier() {
			@Override
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}
		};

		// Install the all-trusting host verifier
		HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
	}
	
	private void unzipToTemp(File dst) {
		try {
			ZipInputStream zin = new ZipInputStream(new FileInputStream(dst));
			String workingDir = "./downloads/tmp" + File.separator + "unzipped_tmp";
			byte buffer[] = new byte[4096];
		    int bytesRead;	
		    
			ZipEntry entry = null;		    
	        while ((entry = zin.getNextEntry()) != null) {
	            String dirName = workingDir;
	
	            int endIndex = entry.getName().lastIndexOf(File.separatorChar);
	            if (endIndex != -1) {
	                dirName += entry.getName().substring(0, endIndex);
	            }
	
	            File newDir = new File(dirName);
	            // If the directory that this entry should be inflated under does not exist, create it
	            if (!newDir.exists() && !newDir.mkdir()) { 
	            	throw new ZipException("Could not create directory " + dirName + "\n"); 
	            }
	
	            // Copy data from ZipEntry to file
	            FileOutputStream fos = new FileOutputStream(workingDir + File.separator + entry.getName());
	            while ((bytesRead = zin.read(buffer)) != -1) {
	                fos.write(buffer, 0, bytesRead);
	            }
	            fos.close();
	        }
	        zin.close();	
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	private long writeToFile(String stringToWrite, String filename) {
        try {
			File wfile = new File(filename);
			if (!wfile.exists())
				wfile.createNewFile();
			BufferedWriter bw = new BufferedWriter
					(new OutputStreamWriter(new FileOutputStream(wfile.getAbsoluteFile()),"UTF-8"));   			
			bw.write(stringToWrite);
			bw.close();
			return wfile.length();
 		} catch (IOException e) {
			e.printStackTrace();
 		}		
        return 0;
	}	
	
	private String getStringFromDoc(Document doc)    {
	    DOMImplementationLS domImplementation = (DOMImplementationLS) doc.getImplementation();
	    LSSerializer lsSerializer = domImplementation.createLSSerializer();
	    return lsSerializer.writeToString(doc);   
	}
	
	private String prettyFormat(String input) {
	    try {
	        Source xmlInput = new StreamSource(new StringReader(input));
	        StringWriter stringWriter = new StringWriter();
	        StreamResult xmlOutput = new StreamResult(stringWriter);
	        Transformer transformer = TransformerFactory.newInstance().newTransformer(); 
	        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
	        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
	        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
	        transformer.transform(xmlInput, xmlOutput);
	        return xmlOutput.getWriter().toString();
	    } catch (Exception e) {
	        throw new RuntimeException(e); // simple exception handling, please review it
	    }
	}
	
	private String prettyFormatSoapXml(SOAPMessage soap) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();		
			soap.writeTo(out);
			String msg = new String(out.toByteArray());
			return prettyFormat(msg);
		} catch(SOAPException | IOException e) {
			return "";
		}
	}
}
