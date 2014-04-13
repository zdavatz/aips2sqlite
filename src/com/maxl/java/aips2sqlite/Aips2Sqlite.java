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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class Aips2Sqlite {

	/**
	 * Adds an option into the command line parser
	 * 
	 * @param optionName - the option name
	 * @param description - option descriptiuon
	 * @param hasValue - if set to true, --option=value, otherwise, --option is a boolean
	 * @param isMandatory - if set to true, the option must be provided.
	 */
	@SuppressWarnings("static-access")
	static void addOption(Options opts, String optionName, String description,
			boolean hasValue, boolean isMandatory) {
		OptionBuilder opt = OptionBuilder.withLongOpt(optionName);
		opt = opt.withDescription(description);
		if (hasValue)
			opt = opt.hasArg();
		if (isMandatory)
			opt = opt.isRequired();
		opts.addOption(opt.create());
	}

	static void commandLineParse(Options opts, String[] args) {
		CommandLineParser parser = new GnuParser();
		try {
			CommandLine cmd = parser.parse(opts, args);
			if (cmd.hasOption("help")) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("aips2sqlite", opts);
				System.exit(0);
			}
			if (cmd.hasOption("version")) {
				System.out.println("Version of aips2slite: " + CmlOptions.APP_VERSION);
			}
			if (cmd.hasOption("lang")) {
				if (cmd.getOptionValue("lang").equals("de"))
					CmlOptions.DB_LANGUAGE = "de";
				else if (cmd.getOptionValue("lang").equals("fr"))
					CmlOptions.DB_LANGUAGE = "fr";
				else if (cmd.getOptionValue("lang").equals("it"))
					CmlOptions.DB_LANGUAGE = "it";
				else if (cmd.getOptionValue("lang").equals("en"))
					CmlOptions.DB_LANGUAGE = "en";
			}
			if (cmd.hasOption("verbose")) {
				CmlOptions.SHOW_ERRORS = true;
				CmlOptions.SHOW_LOGS = true;
			}
			if (cmd.hasOption("quiet")) {
				CmlOptions.SHOW_ERRORS = false;
				CmlOptions.SHOW_LOGS = false;
			}
			if (cmd.hasOption("zip")) {
				CmlOptions.ZIP_BIG_FILES = true;
			}
			if (cmd.hasOption("alpha")) {
				CmlOptions.OPT_MED_TITLE = cmd.getOptionValue("alpha");
			}
			if (cmd.hasOption("regnr")) {
				CmlOptions.OPT_MED_REGNR = cmd.getOptionValue("regnr");
			}
			if (cmd.hasOption("owner")) {
				CmlOptions.OPT_MED_OWNER = cmd.getOptionValue("owner");
			}
			if (cmd.hasOption("nopack")) {
				CmlOptions.NO_PACK = true;
			}
			if (cmd.hasOption("pseudo")) {
				CmlOptions.ADD_PSEUDO_FI = true;
			}
			if (cmd.hasOption("inter")) {
				CmlOptions.ADD_INTERACTIONS = true;
			}
			if (cmd.hasOption("pinfo")) {
				CmlOptions.GENERATE_PI = true;
			}
			if (cmd.hasOption("xml")) {
				CmlOptions.XML_FILE = true;
			}
			if (cmd.hasOption("nodown")) {
				CmlOptions.DOWNLOAD_ALL = false;
			}
			if (cmd.hasOption("reports")) {
				CmlOptions.GENERATE_REPORTS = true;
			}
			if (cmd.hasOption("indications")) {
				CmlOptions.INDICATIONS_REPORT = true;
			}
		} catch (ParseException e) {
			System.err.println("Parsing failed: " + e.getMessage());
		}
	}

	public static void main(String[] args) {
		Options options = new Options();
		addOption(options, "help", "print this message", false, false);
		addOption(options, "version", "print the version information and exit",	false, false);
		addOption(options, "quiet", "be extra quiet", false, false);
		addOption(options, "verbose", "be extra verbose", false, false);
		addOption(options, "nodown", "no download, parse only", false, false);		
		addOption(options, "lang", "use given language", true, false);		
		addOption(options, "alpha",	"only include titles which start with option value", true, false);
		addOption(options, "regnr", "only include medications which start with option value", true, false);
		addOption(options, "owner", "only include medications owned by option value", true, false);
		addOption(options, "nopack", "does not update the package section", false, false);
		addOption(options, "pseudo", "adds pseudo expert infos to db", false, false);
		addOption(options, "inter", "adds drug interactions to db", false, false);
		addOption(options, "pinfo", "generate patient info htmls", false, false);
		addOption(options, "xml", "generate xml file", false, false);	
		addOption(options, "zip", "generate zipped big files (sqlite or xml)", false, false);
		addOption(options, "reports", "generates various reports", false, false);
		addOption(options, "indications", "generates indications section keywords report", false, false);

		// Parse command line options
		commandLineParse(options, args);

		// Download all files and save them in appropriate directories
		// XML + XSD -> ./xml, XLS -> ./xls
		if (CmlOptions.DOWNLOAD_ALL) {
			System.out.println("");
			allDown();
		}
		
		System.out.println("");
		if (!CmlOptions.DB_LANGUAGE.isEmpty()) {
			if (CmlOptions.SHOW_LOGS) {
				System.out.println("");
				System.out.println("- Generating sqlite database... ");
			}
						
			long startTime = System.currentTimeMillis();
			
			// Extract drug interactions information
			if (CmlOptions.ADD_INTERACTIONS==true) {
				Interactions inter = new Interactions(CmlOptions.DB_LANGUAGE);
				inter.generateSqlDatabase();
			}			
			
			// Generates SQLite database - function should return the number of entries
			generateSQLiteDB();
			
			if (CmlOptions.SHOW_LOGS) {
				long stopTime = System.currentTimeMillis();
				System.out.println("- Generated sqlite database in " + (stopTime - startTime) / 1000.0f + " sec");
			}
		}

		System.exit(0);
	}

	static void allDown() {
		AllDown a = new AllDown();

		a.downAipsXml(Constants.FILE_MEDICAL_INFOS_XSD, Constants.FILE_MEDICAL_INFOS_XML);
		// a.downPackungenXml(FILE_PACKAGES_XLS);
		a.downPackungenXls(Constants.FILE_PACKAGES_XLSX);
		a.downSwissindexXml("DE", Constants.FILE_REFDATA_PHARMA_DE_XML);
		a.downSwissindexXml("FR", Constants.FILE_REFDATA_PHARMA_FR_XML);
		a.downPreparationsXml(Constants.FILE_PREPARATIONS_XML);
		a.downSwissDRGXlsx("DE", Constants.FILE_SWISS_DRG_DE_XLSX);
		a.downSwissDRGXlsx("FR", Constants.FILE_SWISS_DRG_FR_XLSX);
		a.downInteractionsCsv("DE", Constants.FILE_INTERACTIONS_CSV);
	}

	static void generateSQLiteDB() {						
		// Read Aips file			
		List<MedicalInformations.MedicalInformation> med_list = readAipsFile();

		if (CmlOptions.GENERATE_PI==false) {
			// Process Fachinfos
			RealExpertInfo fi = new RealExpertInfo(med_list);
			fi.process();
		} else {
			// Process Patienten Info
			RealPatientInfo pi = new RealPatientInfo(med_list);
			pi.process();
		}
	}	
			
	static List<MedicalInformations.MedicalInformation> readAipsFile() {
		List<MedicalInformations.MedicalInformation> med_list = null;
		try {
			JAXBContext context = JAXBContext.newInstance(MedicalInformations.class);

			// Validation
			SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema schema = sf.newSchema(new File(Constants.FILE_MEDICAL_INFOS_XSD));
			Validator validator = schema.newValidator();
			validator.setErrorHandler(new MyErrorHandler());

			// Marshaller
			/*
			 * Marshaller ma = context.createMarshaller();
			 * ma.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			 * MedicalInformations medi_infos = new MedicalInformations();
			 * ma.marshal(medi_infos, System.out);
			 */
			// Unmarshaller
			long startTime = System.currentTimeMillis();
			if (CmlOptions.SHOW_LOGS)
				System.out.print("- Unmarshalling Swissmedic xml... ");

			FileInputStream fis = new FileInputStream(new File(Constants.FILE_MEDICAL_INFOS_XML));
			Unmarshaller um = context.createUnmarshaller();
			MedicalInformations med_infos = (MedicalInformations)um.unmarshal(fis);
			med_list = med_infos.getMedicalInformation();

			long stopTime = System.currentTimeMillis();
			if (CmlOptions.SHOW_LOGS)
				System.out.println(med_list.size() + " medis in " + (stopTime - startTime) / 1000.0f + " sec");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JAXBException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		}

		return med_list;
	}

	static class MyErrorHandler implements ErrorHandler {
		public void warning(SAXParseException exception) throws SAXException {
			System.out.println("\nWARNING");
			exception.printStackTrace();
		}

		public void error(SAXParseException exception) throws SAXException {
			System.out.println("\nERROR");
			exception.printStackTrace();
		}

		public void fatalError(SAXParseException exception) throws SAXException {
			System.out.println("\nFATAL ERROR");
			exception.printStackTrace();
		}
	}
}
