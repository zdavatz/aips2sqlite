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

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBException;

public class SwissMedSequences extends ArticleNameParse {

    class Pair<T, U> {
        public final T first;
        public final U second;

        public Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }
    }

    SwissMedSequences() {
        super();
    }

    private String two_digit_format(String val) {
        return String.format("%.2f", Double.valueOf(val));
    }

    private boolean isNotNullNotEmpty(String s) {
        return (s != null && !s.isEmpty());
    }

    private String extractDosageFromName(String name) {
        String q = "";
        Pattern p = Pattern.compile("(\\d+)(\\.\\d+)?\\s*(mg|g)");
        Matcher m = p.matcher(name);
        if (m.find()) {
            q = m.group(1);
            String q2 = m.group(2);
            if (q2 != null && !q2.isEmpty()) {
                q += q2;
            }
            q += (" " + m.group(3));
        }
        return q;
    }

    private String multiplyPackSize(String str) {
        str = str.toLowerCase();
        // Remove plurals
        str = str.toLowerCase().replaceAll("\\(n\\)|\\(s\\)", "");

        Pattern regx = Pattern.compile("(\\d+)\\s*x\\s*(\\d+)\\s*(ml)\\s*(amp)\\b");
        Matcher match = regx.matcher(str);
        if (match.find()) {
            String n = match.group(1);    // group(0) -> whole regular expression
            String m = match.group(2);
            String u = match.group(3);
            if (isNotNullNotEmpty(n) && isNotNullNotEmpty(m) && isNotNullNotEmpty(u))
                return String.valueOf(Integer.valueOf(n) + " amp " + Integer.valueOf(m)) + " " + u;
        }

        // First identify more complex patterns, e.g. 6x 10 Stk or 3x 60 Dosen
        regx = Pattern.compile("(\\d+)*\\s*x\\s*(\\d+)\\s*(stk|dosen|ml|g)\\b");
        match = regx.matcher(str);
        if (match.find()) {
            String n = match.group(1);    // group(0) -> whole regular expression
            String m = match.group(2);
            String u = match.group(3);
            if (isNotNullNotEmpty(n) && isNotNullNotEmpty(m) && isNotNullNotEmpty(u))
                return String.valueOf(Integer.valueOf(n) * Integer.valueOf(m)) + " " + u;
        }

        return str;
    }

    private Set<String> diff(TreeMap<String, SimpleArticle> gtin_to_bag_map, TreeMap<String, SimpleArticle> gtin_to_swissmedic_map) {
        Set<String> bag_key_set = gtin_to_bag_map.keySet();
        Set<String> swissmedic_key_set = gtin_to_swissmedic_map.keySet();

        bag_key_set.removeAll(swissmedic_key_set);

        return bag_key_set;
    }

    private Pair<String, String> extractNameAndGalen(PackageParse pp, SimpleArticle a, String name) {
        String clean_name = name;
        String galens = "";

        // Change numbers with this format: 0,15 -> 0.15
        clean_name = Utilities.convertDecimalFormat(clean_name);

        // Extract Dosage from name
        DrugDosage dd = pp.extractDrugDosageFromName(clean_name);
        String dosage = dd.dose + " " + dd.unit;

        // Take care of "Beutel"
        if (isNotNullNotEmpty(a.pack_unit)) {
            if (a.pack_unit.equals("Beutel") && !dosage.isEmpty()) {
                a.pack_unit += " à " + dosage;
                clean_name = removeDosageFromName(clean_name);
            }
        }

        // Cleaning: 1st official pass
        ArrayList<String> list_of_matchers = createDosageMatchers(a);
        for (String m : list_of_matchers) {
            clean_name = clean_name.replaceAll("\\s+" + m + "\\b", "");
        }

        // Extract galen forms from name
        GalenForms galen_forms = extractGalenFromName(clean_name);

        ArrayList<String> list_of_galens = galen_forms.list_of_galens;
        for (String g : list_of_galens) {
            galens += g + ",";
            //	clean_name = clean_name.replaceAll("\\b" + g + "\\b", " ");
        }

        if (!galens.isEmpty())
            galens = galens.split(",")[0].trim();    // Take only first one

        /*
        if (!galen_forms.match.isEmpty())
            galens = galen_forms.match;
        */

        // Remove galen forms from name
        clean_name = clean_name.replaceAll("\\b" + galen_forms.match + "\\b", "");

        // Cleaning: 2nd official pass
        clean_name = cleanName(clean_name, false);

        // Cleaning: 3rd official pass
        clean_name = clean_name.replaceAll(dosage, " ");
        clean_name = Utilities.removeSpaces(clean_name);
        clean_name = Utilities.capitalizeFully(clean_name, 1);
        clean_name = Utilities.addStringToString(clean_name, Utilities.capitalizeFirstLetter(galens), ", ");

        // Add dosage (e.g. 320mg) only if not already contained in name
        dosage = dosage.trim();
        if (!clean_name.contains(dd.match))
            clean_name += ", " + dosage;

        // Remove all multiple commas
        clean_name = Utilities.removeMultipleCommas(clean_name);

        return new Pair<>(clean_name, galens);
    }


    /**
     * main function where data are processed
     */
    public void process() {
        try {
            BaseDataParser bdp = new BaseDataParser();

            PackageParse pack_parse = new PackageParse();

            // Read core info from files
            TreeMap<String, SimpleArticle> gtin_to_bag_article_map = bdp.parseBAGXmlFile();
            TreeMap<String, SimpleArticle> gtin_to_swissmedic_article_map = bdp.parseSwissmedicPackagesFile_Gtin();
            TreeMap<String, ArrayList<SimpleArticle>> sequence_to_swissmedic_article_map = bdp.parseSwissmedicPackagesFile_Sequence();
            TreeMap<String, String> gtin_to_refdata_name_map = bdp.parseRefdataPharmaFile();

            // Generate set of differences between bag and swissmedic map (BAG file typically lags behind)
            Set<String> bag_swissmedic_diff_key_set = diff(gtin_to_bag_article_map, gtin_to_swissmedic_article_map);
            // These article are not in the swissmedic packages file...
            TreeMap<String, ArrayList<SimpleArticle>> regnr_to_bag_articles_map = new TreeMap<>();
            ArrayList<SimpleArticle> list_of_bag_packs_for_article = new ArrayList<>();
            ArrayList<String> list_of_bag_articles_gtins = new ArrayList<>();

            // For double-check purposes
            ArrayList<String> list_of_all_gtins = new ArrayList<>();

            for (String gtin : bag_swissmedic_diff_key_set) {
                SimpleArticle a = gtin_to_bag_article_map.get(gtin);
                // Generate map of articles using swissmedic no5 (regnr)
                String regnr = a.smn5;
                if (regnr != null) {
                    if (!regnr_to_bag_articles_map.containsKey(regnr))
                        list_of_bag_packs_for_article = new ArrayList<>();
                    else
                        list_of_bag_packs_for_article = regnr_to_bag_articles_map.get(regnr);
                    list_of_bag_packs_for_article.add(a);
                    regnr_to_bag_articles_map.put(regnr, list_of_bag_packs_for_article);
                }
            }

            int counter = 0;

            /// TEST -> PASSED!
            /*
			for (Map.Entry<String, ArrayList<SimpleArticle>> e : regnr_to_bag_articles_map.entrySet()) {
				counter += e.getValue().size();
				System.out.println(counter + " -> " + e.getKey());
			}
			*/

            String csv_str = "";

            int bag_full_match_counter = 0;
            int bag_partial_match_counter = 0;
            int bag_no_match_counter = 0;

            for (Map.Entry<String, ArrayList<SimpleArticle>> e : sequence_to_swissmedic_article_map.entrySet()) {
                String sequence = e.getKey();    // SEQUENCE
                ArrayList<SimpleArticle> list_of_articles = e.getValue();
                if (list_of_articles != null) {
                    String sub_csv_str = "";
                    boolean animal_med = false;
                    // Loop through articles with IDENTICAL sequence number!
                    // NOTE: These article also have an identical smn5 number!
                    for (SimpleArticle a : list_of_articles) {
                        String gtin = "7680" + a.smn8;
                        int cs = Utilities.getChecksum(gtin);
                        gtin += cs;

                        if (list_of_all_gtins.contains(gtin))
                            System.out.println("ERROR: gtin exists already -> " + gtin + " | " + a.name + " | " + a.pack_size + " | " + a.pack_unit);

                        list_of_all_gtins.add(gtin);

                        // First check refdata gtin to name map, because refdata has the most beautiful names
                        if (gtin_to_refdata_name_map.containsKey(gtin)) {
                            String refdata_name = gtin_to_refdata_name_map.get(gtin);
                            String clean_name = refdata_name.toLowerCase();
                            String galens = "";
                            // Exclude medicaments for animals
                            if (!isAnimalMed(clean_name)) {
                                a.gtin = gtin;
                                Pair<String, String> ret_pair = extractNameAndGalen(pack_parse, a, clean_name);
                                clean_name = ret_pair.first;
                                galens = ret_pair.second;
                                //
                                String pack_unit = a.pack_unit.replace(";", ",");
                                if (isNotNullNotEmpty(gtin)) {
                                    sub_csv_str += refdata_name + ";" + clean_name + ";" + galens + ";" + gtin + ";" + a.quantity + ";" + pack_unit + ";;;";
                                }
                            } else {
                                animal_med = true;
                            }
                        } else {
                            String galens = "";
                            String clean_name = a.name.toLowerCase();
                            //
                            if (!isAnimalMed(clean_name)) {

                                // Change numbers with this format: 0,15 -> 0.15
                                clean_name = Utilities.convertDecimalFormat(clean_name);

                                // Extract Dosage from name
                                DrugDosage dd = pack_parse.extractDrugDosageFromName(clean_name);
                                String dosage = dd.dose + " " + dd.unit;

                                // Cleaning: 2nd pass (compare with quantity and unit)
                                ArrayList<String> list_of_matchers = createDosageMatchers(a);
                                for (String m : list_of_matchers) {
                                    m = m.replaceAll("\\)", "").replaceAll("\\(", "");
                                    clean_name = clean_name.replaceAll("\\s+" + m + "\\b", "");
                                }

                                GalenForms galen_forms = extractGalenFromName(clean_name);
                                ArrayList<String> list_of_galens = galen_forms.list_of_galens;
                                for (String g : list_of_galens) {
                                    galens += g + ",";
                                    clean_name = clean_name.replaceAll("\\b" + g + "\\b", "");
                                }

                                // Find short form if not empty
                                if (!galens.isEmpty())
                                    galens = findGalenShort(galens.split(",")[0].trim());

                                if (galens.isEmpty()) {
                                    String g[] = a.name.split(",");
                                    if (g.length > 1) {
                                        int last_idx = g.length - 1;
                                        if (g[last_idx].matches("\\D+")) {
                                            galen_forms = extractGalenFromName(g[last_idx].toLowerCase());
                                            list_of_galens = galen_forms.list_of_galens;
                                            for (String gg : list_of_galens) {
                                                String g_short = findGalenShort(gg);
                                                if (!g_short.isEmpty())
                                                    galens += g_short + ",";
                                                else
                                                    galens += gg + ",";
                                            }
                                            // Fallback in case no short form can be found
                                            if (galens.isEmpty()) {
                                                galens = g[last_idx].trim();
                                            }
                                        }
                                    }
                                }

                                galens = galens.split(",")[0].trim().toLowerCase();    // Take only first one

                                /*
                                if (!galen_forms.match.isEmpty())
                                    galens = galen_forms.match;
                                */
                                // Remove galen forms
                                clean_name = clean_name.replaceAll("\\b" + galen_forms.match + "\\b", "");

                                // Cleaning: 1st pass
                                clean_name = cleanName(clean_name, false);

                                // String dosage = extractDosageFromName(clean_name);
                                if (a.pack_unit.equals("Beutel") && !dosage.isEmpty()) {
                                    a.pack_unit += " à " + dosage;
                                    clean_name = removeDosageFromName(clean_name);
                                }

                                clean_name = Utilities.removeSpaces(clean_name);
                                clean_name = Utilities.capitalizeFully(clean_name, 1);
                                clean_name = Utilities.addStringToString(clean_name, Utilities.capitalizeFirstLetter(galens), ", ");

                                dosage = dosage.trim();
                                if (!clean_name.contains(dd.match))
                                    clean_name += ", " + dosage;

                                clean_name = Utilities.removeMultipleCommas(clean_name);

                                String pack_unit = a.pack_unit.replace(";", ",");

                                if (isNotNullNotEmpty(gtin))
                                    sub_csv_str += a.name + ";" + clean_name + ";" + galens + ";" + gtin + ";" + a.quantity + ";" + pack_unit + ";;;";
                            } else {
                                animal_med = true;
                            }
                        }

						/*	Check if smn5 is contained in regnr_to_bag_articles_map.
							For each smn5 we have multiple packages!
						*/
                        if (regnr_to_bag_articles_map.containsKey(a.smn5)) {

                            // Extract drug dosage
                            DrugDosage dd = pack_parse.extractDrugDosageFromName(a.name);
                            // Extract package size
                            PackSize ps = pack_parse.extractPackSizeFromName(a.name);
                            // Extract galenic forms (csv)
                            String clean_name = pack_parse.cleanName(a);
                            String galens = pack_parse.extractGalensFromName(clean_name);
                            // If pack size and/or units are missing in 'ps', then check info in file 'Constants.FILE_PACKAGES_XLSX'
                            if (ps.size.isEmpty() || ps.s_unit.isEmpty())
                                pack_parse.extractPackageSizeFromSwissmedicFile(a, ps, galens);
                            if (!galens.isEmpty())
                                galens = galens.split(",")[0].trim();
                            else
                                galens = ps.s_unit;
                            galens = galens.toLowerCase();
                            if (galens.equals("stk"))
                                galens = "tablette(n)";

							/* 	List of all packages with the same registration number found in BAG xml
								NOTE: We are looping through ArrayList with potentially identical a.smn5!!
								NOTE: Which means that "list_of_bag_packs_for_article" is also identical!
							*/
                            list_of_bag_packs_for_article = regnr_to_bag_articles_map.get(a.smn5);

                            // Loop through this list
                            for (SimpleArticle pack : list_of_bag_packs_for_article) {
                                String multPackSize = multiplyPackSize(ps.size + " " + ps.s_unit);
                                // Make sure that this article has not been matched already!
                                if (!list_of_bag_articles_gtins.contains(pack.gtin)) {

                                    String full_name = (pack.name + " " + pack.pack_size).toLowerCase();
                                    // Check if the Swissmedic/Refdata article matches the BAG article
                                    if (gtin_to_refdata_name_map.containsKey(pack.gtin)) {

                                        String refdata_name = gtin_to_refdata_name_map.get(pack.gtin);
                                        DrugDosage refdata_dd = pack_parse.extractDrugDosageFromName(refdata_name);
                                        clean_name = refdata_name;

                                        if (dd.dose.equals(refdata_dd.dose)) {

                                            if (full_name.contains(multPackSize)) { // multPackSize.equals(pack.pack_size.toLowerCase())) {
                                                String d1 = dd.dose + " " + dd.unit;
                                                String d2 = pack.quantity + " " + pack.pi_unit;

                                                // Full match
                                                if (d1.equals(d2)) {
                                                    if (isNotNullNotEmpty(pack.gtin)) {
                                                        list_of_bag_articles_gtins.add(pack.gtin);
                                                        bag_full_match_counter++;
                                                        sub_csv_str += pack.name + ";" + clean_name + ";" + galens + ";" + pack.gtin + ";" + a.quantity + ";" + a.pack_unit + ";"
                                                                + two_digit_format(pack.exf_price_CHF) + ";" + two_digit_format(pack.pub_price_CHF) + ";";
                                                    }
                                                    /*
                                                    System.out.println(bag_full_match_counter + " | " + pack.gtin + " |" + a.smn5 + " | " + a.name
                                                            + " | " + a.quantity + " " + a.pack_unit
                                                            + " | " + multPackSize + " = " + pack.pack_size
                                                            + " | " + d1 + " = " + d2
                                                            + " | " + pack.exf_price_CHF + " | " + pack.pub_price_CHF);
                                                    */
                                                } else {
                                                    if (isNotNullNotEmpty(pack.gtin)) {
                                                        list_of_bag_articles_gtins.add(pack.gtin);
                                                        bag_partial_match_counter++;
                                                        sub_csv_str += pack.name + ";" + clean_name + ";" + galens + ";" + pack.gtin + ";" + a.quantity + ";" + a.pack_unit + ";"
                                                                + two_digit_format(pack.exf_price_CHF) + ";" + two_digit_format(pack.pub_price_CHF) + ";";
                                                    }
                                                    /*
                                                    System.out.println(bag_partial_match_counter + " | " + pack.gtin + " | " + a.smn5 + " | " + a.name
                                                            + " | " + a.quantity + " " + a.pack_unit
                                                            + " | " + multPackSize + " = " + pack.pack_size
                                                            + " | " + pack.exf_price_CHF + " | " + pack.pub_price_CHF);
                                                    */
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!animal_med) {
                        sub_csv_str = sub_csv_str.substring(0, sub_csv_str.length() - 1);
                        csv_str += sequence + ";" + sub_csv_str + "\n";

                        if ((++counter) % 100 == 0) {
                            System.out.println("[" + counter + "] " + sequence);
                        }
                    }
                }
            }

            // gtin_to_refdata_name_map
            for (Map.Entry<String, ArrayList<SimpleArticle>> e : regnr_to_bag_articles_map.entrySet()) {
                ArrayList<SimpleArticle> list_of_articles = e.getValue();
                if (list_of_articles != null) {
                    String old_smn5 = "";
                    int dosage_number = 0;
                    for (SimpleArticle a : list_of_articles) {
                        if (list_of_all_gtins.contains(a.gtin))
                            System.out.println("ERROR: gtin exists already -> " + a.gtin + " | " + a.name + " | " + dosage_number + " | " + a.pack_size);
                        list_of_all_gtins.add(a.gtin);

                        if (!list_of_bag_articles_gtins.contains(a.gtin)) {
                            String refdata_name = "";
                            String clean_name = "";
                            String galens = "";
                            if (gtin_to_refdata_name_map.containsKey(a.gtin)) {
                                refdata_name = gtin_to_refdata_name_map.get(a.gtin);
                                Pair<String, String> ret_pair = extractNameAndGalen(pack_parse, a, refdata_name.toLowerCase());
                                clean_name = ret_pair.first;
                                galens = ret_pair.second;
                            }

                            bag_no_match_counter++;
                            if (a.smn5.equals(old_smn5))
                                dosage_number++;
                            else
                                dosage_number = 0;
                            old_smn5 = a.smn5;

                            String digit_str = String.format("%02d", dosage_number);
                            String alpha_str = digit2alphaConverter(digit_str);

                            String sequence = a.smn5 + alpha_str;
                            if (isNotNullNotEmpty(a.gtin) && !refdata_name.isEmpty()) {
                                // Name (Spalte B) and "sequence name" (Spalte C) are identical
                                csv_str += sequence + ";" + refdata_name + " (alt);" + refdata_name + " (alt);" + galens + ";" + a.gtin + ";" + a.quantity + ";" + a.pi_unit + ";"
                                        + two_digit_format(a.exf_price_CHF) + ";" + two_digit_format(a.pub_price_CHF) + "\n";
                            }
                            if ((++counter) % 5 == 0) {
                                System.out.println("[" + counter + "] " + sequence);
                            }
                        }
                    }
                }
            }

            // Test all (sequence) names in column C that appear twice or more -> the sequence name is UNIQUE
            HashSet<String> set_of_sequence_names = new HashSet<>();
            String[] all_rows = csv_str.split("\n", -1);
            int line = 0;
            for (String row : all_rows) {
                line++;
                String[] r = row.split(";", -1);
                if (r.length > 3) {
                    String sequence_name = r[2];
                    if (set_of_sequence_names.contains(sequence_name))
                        System.out.println(">> Line " + line + ": " + sequence_name);
                    set_of_sequence_names.add(sequence_name);
                }
            }


            if (!csv_str.isEmpty()) {
                System.out.println("Number of mapped gtins: " + list_of_all_gtins.size());

                System.out.println("BAG/Swissmedic/Refdata full match count = " + bag_full_match_counter
                        + " / partial match count = " + bag_partial_match_counter
                        + " / no match count = " + bag_no_match_counter);

                int num_lost_gtins = gtin_to_swissmedic_article_map.size()
                        + (bag_full_match_counter + bag_partial_match_counter + bag_no_match_counter) - list_of_all_gtins.size();
                System.out.println("Number of gtins which were lost on the way: " + num_lost_gtins);

                FileOps.writeToFile(csv_str, Constants.DIR_OUTPUT, "swiss_medic_sequences.csv");
            }
        } catch (IOException | JAXBException e) {
            e.printStackTrace();
        }
    }

    private String digit2alphaConverter(String digit_str) {
        char[] digit_chars = digit_str.toCharArray();
        char[] alpha_chars = new char[digit_chars.length];
        for (int i = 0; i < digit_chars.length; ++i) {
            alpha_chars[i] = (char) (Character.getNumericValue(digit_chars[i]) + 'A');
        }

        return String.valueOf(alpha_chars);
    }

}
