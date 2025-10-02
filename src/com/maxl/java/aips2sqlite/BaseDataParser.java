/*
Copyright (c) 2016 Max Lungarella

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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxl.java.aips2sqlite.refdata.Articles;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by maxl on 22.11.2016.
 */
public class BaseDataParser {

    public TreeMap<String, ArrayList<SimpleArticle>> parseSwissmedicPackagesFile_Sequence() throws IOException {
        TreeMap<String, ArrayList<SimpleArticle>> smn5plus_to_article_map = new TreeMap<>();

        System.out.print("Processing packages xlsx (sequence)... ");

        // Load Swissmedic xls file
        FileInputStream packages_file = new FileInputStream(Constants.FILE_PACKAGES_XLSX);
        // Get workbook instance for XLSX file (XSSF = Horrible SpreadSheet Format)
        XSSFWorkbook packages_workbook = new XSSFWorkbook(packages_file);
        // Get first sheet from workbook
        XSSFSheet packages_sheet = packages_workbook.getSheetAt(0);
        // Iterate through all rows of first sheet
        Iterator<Row> rowIterator = packages_sheet.iterator();
        int num_rows = 0;
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            if (num_rows > 5) {
                String swissmedic_no5 = ""; // SwissmedicNo5 registration number (5 digits)
                String dosage_id = "";
                String package_id = "";
                String name = "";
                String swissmedic_no8 = ""; // SwissmedicNo8 = SwissmedicNo5 + Package id (8 digits)
                String quantity = "";
                String unit = "";
                String atc_code = "";

                // 0: Zulassungsnummer, 1: Dosisstärkenummer, 2: Präparatebezeichnung, 3: Zulassunginhaberin, 4: Heilmittelcode, 5: IT-Nummer, 6: ATC-Code
                // 7: Erstzulassung Präparat, 8: Zulassungsdatum Sequenz, 9: Gültigkeitsdatum, 10: Packungscode, 11: Packungsgrösse
                // 12: Einheit, 13: Abgabekategorie Packung, 14: Abgabekategorie Dosisstärke, 15: Abgabekategorie Präparat,
                // 16: Wirkstoff, 17: Zusammensetzung, 18: Anwendungsgebiet Präparat, 19: Anwendungsgebiet Dosisstärke, 20: Gentechnisch hergestellte Wirkstoffe
                // 21: Kategorie bei Insulinen, 22: Betäubungsmittelhaltigen Präparaten

                if (row.getCell(0) != null)
                    swissmedic_no5 = String.format("%05d", (int) (row.getCell(0).getNumericCellValue()));// Swissmedic registration number (5 digits)
                if (row.getCell(1) != null)
                    dosage_id = String.format("%02d", (int) (row.getCell(1).getNumericCellValue()));    // Sequence name
                if (row.getCell(2) != null)
                    name = row.getCell(2).getStringCellValue();
                if (row.getCell(6) != null) {
                    atc_code = row.getCell(6).getStringCellValue();
                }
                if (row.getCell(10) != null) {
                    package_id = String.format("%03d", (int) (row.getCell(10).getNumericCellValue()));    // Verpackungs ID
                    swissmedic_no8 = swissmedic_no5 + package_id;
                }
                if (row.getCell(11) != null) {
                    quantity = ExcelOps.getCellValue(row.getCell(11));
                    // Numeric and floating, remove trailing zeros (.00)
                    quantity = quantity.replace(".00", "");
                    // System.out.println(quantity);
                }
                if (row.getCell(12) != null) {
                    try {
                        unit = row.getCell(12).getStringCellValue();
                    } catch (Exception e) {
                        // Empty when unit is not string
                        System.out.print("\rWarning: Unit is not a string ("+ swissmedic_no5 +")");
                    }
                }

                String smn5_plus = swissmedic_no5 + dosage_id;  // SEQUENZ

                if (!smn5_plus.isEmpty() && !swissmedic_no5.isEmpty() && !swissmedic_no5.equals("00000")) {
                    ArrayList<SimpleArticle> list_of_articles = new ArrayList<>();
                    if (smn5plus_to_article_map.containsKey(smn5_plus))
                        list_of_articles = smn5plus_to_article_map.get(smn5_plus);

                    SimpleArticle a = new SimpleArticle();
                    a.name = name;
                    a.smn5 = swissmedic_no5;
                    a.smn8 = swissmedic_no8;
                    a.quantity = quantity;
                    a.pack_unit = unit;
                    a.atc_code = atc_code;
                    list_of_articles.add(a);

                    smn5plus_to_article_map.put(smn5_plus, list_of_articles);
                }
            }
            System.out.print("\rProcessing packages xlsx (sequence)... " + num_rows++);
        }
        System.out.println("");

        return smn5plus_to_article_map;
    }

    public TreeMap<String, SimpleArticle> parseSwissmedicPackagesFile_Gtin() throws IOException {
        TreeMap<String, SimpleArticle> swissmedic_gtin_to_article_map = new TreeMap<>();

        System.out.print("Processing packages xlsx (gtin)... ");

        // Load Swissmedic xls file
        FileInputStream packages_file = new FileInputStream(Constants.FILE_PACKAGES_XLSX);
        // Get workbook instance for XLSX file (XSSF = Horrible SpreadSheet Format)
        XSSFWorkbook packages_workbook = new XSSFWorkbook(packages_file);
        // Get first sheet from workbook
        XSSFSheet packages_sheet = packages_workbook.getSheetAt(0);
        // Iterate through all rows of first sheet
        Iterator<Row> rowIterator = packages_sheet.iterator();
        int num_rows = 0;
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            if (num_rows > 5) {
                String swissmedic_no5 = ""; // SwissmedicNo5 registration number (5 digits)
                String dosage_id = "";
                String name = "";
                String swissmedic_no8 = ""; // SwissmedicNo8 = SwissmedicNo5 + Package id (8 digits)
                String quantity = "";
                String unit = "";

                // 0: Zulassungsnummer, 1: Dosisstärkenummer, 2: Präparatebezeichnung, 3: Zulassunginhaberin, 4: Heilmittelcode, 5: IT-Nummer, 6: ATC-Code
                // 7: Erstzulassung Präparat, 8: Zulassungsdatum Sequenz, 9: Gültigkeitsdatum, 10: Packungscode, 11: Packungsgrösse
                // 12: Einheit, 13: Abgabekategorie Packung, 14: Abgabekategorie Dosisstärke, 15: Abgabekategorie Präparat,
                // 16: Wirkstoff, 17: Zusammensetzung, 18: Anwendungsgebiet Präparat, 19: Anwendungsgebiet Dosisstärke, 20: Gentechnisch hergestellte Wirkstoffe
                // 21: Kategorie bei Insulinen, 22: Betäubungsmittelhaltigen Präparaten

                if (row.getCell(0) != null)
                    swissmedic_no5 = String.format("%05d", (int) (row.getCell(0).getNumericCellValue()));// Swissmedic registration number (5 digits)
                if (row.getCell(1) != null)
                    dosage_id = String.format("%02d", (int) (row.getCell(1).getNumericCellValue()));    // Sequence name
                if (row.getCell(2) != null)
                    name = row.getCell(2).getStringCellValue();
                if (row.getCell(10) != null) {
                    String package_id = String.format("%03d", (int) (row.getCell(10).getNumericCellValue()));    // Verpackungs ID
                    swissmedic_no8 = swissmedic_no5 + package_id;
                }
                if (row.getCell(11) != null) {
                    quantity = ExcelOps.getCellValue(row.getCell(11));
                    // Numeric and floating, remove trailing zeros (.00)
                    quantity = quantity.replace(".00", "");
                }
                if (row.getCell(12) != null) {
                    try {
                        unit = row.getCell(12).getStringCellValue();
                    } catch (Exception e) {
                        // Empty when unit is not string
                        System.out.print("\rWarning: Unit is not a string ("+ swissmedic_no5 +")");
                    }
                }

                String smn5_plus = swissmedic_no5 + dosage_id;

                String gtin = "7680" + swissmedic_no8;
                int checksum = Utilities.getChecksum(gtin);
                gtin += checksum;

                if (!gtin.isEmpty()) {
                    SimpleArticle a = new SimpleArticle();
                    a.gtin = gtin;
                    a.smn5 = swissmedic_no5;
                    a.smn8 = swissmedic_no8;
                    a.smn5_plus = smn5_plus;
                    a.name = name;
                    a.quantity = quantity;
                    a.pack_unit = unit;
                    swissmedic_gtin_to_article_map.put(gtin, a);
                }
            }
            System.out.print("\rProcessing packages xlsx (gtin)... " + num_rows++);
        }
        System.out.println("");

        return swissmedic_gtin_to_article_map;
    }

    public TreeMap<String, String> parseRefdataPharmaFile() throws FileNotFoundException, JAXBException {
        TreeMap<String, String> gtin_to_name_map = new TreeMap<>();

        System.out.print("Processing refdata pharma xml file...");

        // Load Refdata xml file
        try {
            File refdata_xml_file = new File(Constants.FILE_REFDATA_PHARMA_XML);
            InputStream refdata_is = new FileInputStream(refdata_xml_file);
            Reader reader = new InputStreamReader(refdata_is, "UTF-8");

            JAXBContext jcontext = JAXBContext.newInstance(Articles.class);
            Unmarshaller um = jcontext.createUnmarshaller();
            Articles refdataArticles = (Articles) um.unmarshal(reader);
            List<Articles.Article> article_list = refdataArticles.getArticle();

            int num_rows = 0;
            for (Articles.Article article : article_list) {
                String product_class = article.getMedicinalProduct().getProductClassification().getProductClass();
                String ean_code;
                if (product_class.equals("PHARMA")) {
                    ean_code = article.getPackagedProduct().getDataCarrierIdentifier();
                } else if (product_class.equals("NONPHARMA")) {
                    ean_code = article.getMedicinalProduct().getIdentifier();
                } else {
                    continue;
                }
                String nameDe = null;
                List<Articles.Article.PackagedProduct.Name> name_list = article.getPackagedProduct().getName();
                for (Articles.Article.PackagedProduct.Name name: name_list) {
                    if (name.getLanguage().equals("DE")) {
                        nameDe = name.getFullName();
                        break;
                    }
                }
                if (nameDe == null) continue;
                if (ean_code.length() == 13) {
                    // Clean name, we need only dosage
                    gtin_to_name_map.put(ean_code, nameDe);
                    System.out.print("\rProcessing refdata pharma xml file... " + num_rows++);
                }
            }
            System.out.println("");
        } catch(UnsupportedEncodingException e) {
            //
        }
        return gtin_to_name_map;
    }

    public TreeMap<String, SimpleArticle> parseBAGXmlFile() throws FileNotFoundException {
        TreeMap<String, SimpleArticle> gtin_to_simple_article_map = new TreeMap<>();

        String tag_content = "";

        XMLInputFactory xml_factory = XMLInputFactory.newInstance();
        // Next instruction allows to read "escape characters", e.g. &amp;
        xml_factory.setProperty("javax.xml.stream.isCoalescing", true);  // Decodes entities into one string

        try {
            InputStream in = new FileInputStream(Constants.FILE_PREPARATIONS_XML);
            XMLStreamReader reader = xml_factory.createXMLStreamReader(in, "UTF-8");

            ArrayList<SimpleArticle> list_of_packs = new ArrayList<>();

            String swissmedicno5 = "";
            String name_de = "";
            String description_de = "";
            String gtin = "";
            String quantity = "";
            String unit = "";
            String state = "";
            String exf_price = "";
            String pub_price = "";
            String flagsb20 = "";

            int num_rows = 0;
            // Keep moving the cursor forward
            while (reader.hasNext()) {
                int event = reader.next();
                // Check if the element that the cursor is currently pointing to is a start element
                switch (event) {
                    case XMLStreamConstants.START_DOCUMENT:
                        break;
                    case XMLStreamConstants.START_ELEMENT:
                        switch (reader.getLocalName().toLowerCase()) {
                            case "preparations":
                                break;
                            case "preparation":
                                list_of_packs = new ArrayList<>();
                                break;
                            case "exfactoryprice":
                                state = "exfactory";
                                break;
                            case "publicprice":
                                state = "public";
                                break;
                        }
                        break;
                    case XMLStreamConstants.CHARACTERS:
                        tag_content = reader.getText().trim();
                        break;
                    case XMLStreamConstants.END_ELEMENT:
                        switch (reader.getLocalName().toLowerCase()) {
                            case "preparation":
                                for (SimpleArticle a : list_of_packs) {
                                    a.smn5 = swissmedicno5;
                                    if (swissmedicno5.length()<5)
                                        a.smn5 = String.format("%05d", Integer.valueOf(a.smn5));
                                    a.quantity = quantity;
                                    a.pi_unit = unit;
                                    gtin_to_simple_article_map.put(a.gtin, a);
                                }
                                break;
                            case "pack":
                                SimpleArticle sa = new SimpleArticle();
                                sa.gtin = gtin;
                                sa.name = name_de;
                                sa.exf_price_CHF = exf_price;
                                sa.pub_price_CHF = pub_price;
                                sa.pack_size = description_de;

                                list_of_packs.add(sa);

                                num_rows++;
                                System.out.print("\rProcessing BAG preparations file... " + num_rows);
                                break;
                            case "namede":
                                name_de = tag_content;
                                break;
                            case "swissmedicno5":
                                swissmedicno5 = tag_content;
                                break;
                            case "orggencode":
                                flagsb20 = tag_content;
                                break;
                            case "flagsb20":
                                flagsb20 += ";" + tag_content;
                                break;
                            case "gtin":
                                gtin = tag_content;
                                break;
                            case "descriptionde":
                                description_de = tag_content;
                                break;
                            case "quantity":
                                String q = tag_content;
                                if (q!=null && !q.isEmpty()) {
                                    q = q.replaceAll("max.|min.|ca.|<|'|\\s+","");
                                    q = q.split("-")[0];
                                    quantity = q;
                                }
                                break;
                            case "quantityunit":
                                unit = tag_content;
                                break;
                            case "price":
                                if (state.equals("exfactory")) {
                                    exf_price = tag_content;
                                } else if (state.equals("public")) {
                                    pub_price = tag_content;
                                }
                                break;
                        }
                        break;
                }
            }
        } catch(XMLStreamException | FileNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("");
        return gtin_to_simple_article_map;
    }

    @SuppressWarnings("unchecked")
    public HashMap<String, String> parseDosageFormsJson() throws IOException {
        ObjectMapper mapper = new ObjectMapper(); // can reuse, share globally
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};

        File json_file = Paths.get(System.getProperty("user.dir"), Constants.DIR_INPUT, Constants.FILE_DOSAGE_FORMS_JSON).toFile();
        if (!json_file.exists())
            System.out.println("ERROR: Could not read file " + json_file);

        Map<String, Object> dosageFormsData = mapper.readValue(json_file, typeRef);
        ArrayList<HashMap<String, String>> dosageList = (ArrayList<HashMap<String, String>>) dosageFormsData.get("dosage_forms");

        HashMap<String, String> map_of_short_to_long_galens = new HashMap<>();
        for (HashMap<String, String> dosage : dosageList) {
            String galen_short = dosage.get("galenic_short");
            String galen_long = dosage.get("galenic_full");
            if (!galen_short.isEmpty()) {
                map_of_short_to_long_galens.put(galen_short, galen_long);
            }
        }

        System.out.println("Number of dosage forms in database: " + map_of_short_to_long_galens.size());

        return map_of_short_to_long_galens;
    }

    @SuppressWarnings("unchecked")
    public HashMap<String, String> parsePackUnitsJson() throws IOException {
        ObjectMapper mapper = new ObjectMapper(); // can reuse, share globally
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};

        File json_file = Paths.get(System.getProperty("user.dir"), Constants.DIR_INPUT, Constants.FILE_DOSAGE_FORMS_JSON).toFile();
        if (!json_file.exists())
            System.out.println("ERROR: Could not read file " + json_file);

        Map<String, Object> dosageFormsData = mapper.readValue(json_file, typeRef);
        ArrayList<HashMap<String, String>> dosageList = (ArrayList<HashMap<String, String>>) dosageFormsData.get("dosage_forms");

        HashMap<String, String> map_of_short_to_pack_units = new HashMap<>();
        for (HashMap<String, String> dosage : dosageList) {
            String galen_short = dosage.get("galenic_short");
            String galen_long = dosage.get("pack_units");
            if (!galen_short.isEmpty()) {
                map_of_short_to_pack_units.put(galen_short, galen_long);
            }
        }

        System.out.println("Number of pack units in database: " + map_of_short_to_pack_units.size());

        return map_of_short_to_pack_units;
    }

}
