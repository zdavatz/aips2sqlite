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

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by maxl on 22.11.2016.
 */
public class PackageParse extends ArticleNameParse {

    PackageParse() {
        super();
    }

    private boolean isNotNullNotEmpty(String s) {
        return (s != null && !s.isEmpty());
    }

    /**
     * Dosisstärke, Angabe von Wirkstoffmenge und Einheit
     */
    public DrugDosage extractDrugDosageFromName(String name) {
        DrugDosage dd = new DrugDosage("", "", "");

        name = name.replaceAll("2015/2016|2016/2017", "");

        // Pattern 1, e.g. 1.35mg
        Pattern regx = Pattern.compile("(\\d+)([\\.,]\\d+)?\\s*(ug|mcg|mg|g|kg|ie|e|ml|mmol|mg/ml)(\\s+|\\b)");
        Matcher match = regx.matcher(name);
        if (match.find()) {
            dd.match = match.group(0).trim();
            String n = match.group(1);  // 1
            String m = match.group(2);  // .35
            if (isNotNullNotEmpty(n)) {
                if (isNotNullNotEmpty(m)) {
                    m = m.replaceAll(",", ".");
                    dd.dose = n + m;     // 1.35
                    dd.unit = match.group(3);           // mg
                } else {
                    dd.dose = n;         // 1
                    dd.unit = match.group(3);           // mg
                }
            }
        }

        // Pattern 2, e.g. 50mg/12.5mg
        regx = Pattern.compile("(\\d+)(\\.\\d+)?\\s*(mg)?\\s*/\\s*(\\d+)(\\.\\d+)?\\s*(mg)?(\\s+|\\b)");
        match = regx.matcher(name);
        if (match.find()) {
            dd.match = match.group(0).trim();
            String a1 = match.group(1);
            String a2 = match.group(2);
            String b1 = match.group(4);
            String b2 = match.group(5);
            if (a2 == null) a2 = "";
            if (b2 == null) b2 = "";
            if (isNotNullNotEmpty(a1)) {
                if (isNotNullNotEmpty(b1)) {
                    dd.dose = (a1 + a2) + "/" + (b1 + b2);
                    dd.unit = "mg";
                } else {
                    dd.dose = (a1 + a2);
                    dd.unit = "mg";
                }
            }
        }

        // Pattern 3, e.g. 50/12.5/200mg
        regx = Pattern.compile("(\\d+)(\\.\\d+)?\\s*(mg)?\\s*/\\s*(\\d+)(\\.\\d+)?\\s*(mg)?\\s*/\\s*(\\d+)(\\.\\d+)?\\s*(mg)(\\s+|\\b)");
        match = regx.matcher(name);
        if (match.find()) {
            dd.match = match.group(0).trim();
            String a1 = match.group(1);
            String a2 = match.group(2);
            String b1 = match.group(4);
            String b2 = match.group(5);
            String c1 = match.group(7);
            String c2 = match.group(8);
            if (a2 == null) a2 = "";
            if (b2 == null) b2 = "";
            if (c2 == null) c2 = "";
            if (isNotNullNotEmpty(a1) && isNotNullNotEmpty(b1))
                if (isNotNullNotEmpty(c1)) {
                    dd.dose = (a1 + a2) + "/" + (b1 + b2) + "/" + (c1 + c2);
                    dd.unit = "mg";
                } else {
                    dd.dose = (a1 + a2) + "/" + (b1 + b2);
                    dd.unit = "mg";
                }
        }

        // Pattern 4, e.g. 500 mg/2ml or 500 ug/h
        regx = Pattern.compile("(\\d+)(\\.\\d+)?\\s*(ug|mcg|mg|g|kg|ie|e)\\s*/\\s*(\\d+)?(\\.\\d+)?\\s*(ml|g|h)(\\s+|\\b)");
        match = regx.matcher(name);
        if (match.find()) {
            dd.match = match.group(0).trim();
            String n1 = match.group(1);
            String n2 = match.group(2);
            String d1 = match.group(4);
            String d2 = match.group(5);
            String u1 = match.group(3); // unit 1
            String u2 = match.group(6); // unit 2
            if (n2 == null)
                n2 = "";
            if (isNotNullNotEmpty(n1) && isNotNullNotEmpty(u1) && isNotNullNotEmpty(u2)) {
                if (isNotNullNotEmpty(d1)) {
                    if (isNotNullNotEmpty(d2)) {
                        dd.dose = n1 + n2;
                        dd.unit = u1 + "/" + (d1 + d2) + u2;
                    } else {
                        dd.dose = n1 + n2;
                        dd.unit = u1 + "/" + d1 + u2;
                    }
                } else {
                    dd.dose = n1 + n2;
                    dd.unit = u1 + "/" + u2;
                }
            }
        }

        return dd;
    }

    /**
     * Packungsgrösse, Anzahl der jeweiligen Darreichungsform
     */
    public PackSize extractPackSizeFromName(String name) {
        name = name.replaceAll("2015/2016|2016/2017", "");

        // First identify more complex patterns, e.g. 6x 10 Stk or 3x 60 Dosen
        Pattern regx = Pattern.compile("(\\d+)*\\s*x\\s*(\\d+)\\s*(stk|dosen|ml)\\b");
        Matcher match = regx.matcher(name);
        if (match.find()) {
            String n = match.group(1);    // group(0) -> whole regular expression
            String m = match.group(2);
            String u = match.group(3);
            if (isNotNullNotEmpty(n) && isNotNullNotEmpty(m) && isNotNullNotEmpty(u))
                return new PackSize("", "", String.valueOf(Integer.valueOf(n) * Integer.valueOf(m)), u);
        }
        // Tee, beutel, sachets
        regx = Pattern.compile("(btl|fertspr)\\s*(\\d+)\\s*(stk)");
        match = regx.matcher(name);
        if (match.find()) {
            String u = match.group(1);
            String n = match.group(2);
            if (isNotNullNotEmpty(u) && isNotNullNotEmpty(n)) {
                return new PackSize("", "", n, u);
            }
        }
        // Identify less complex, but more common patterns
        regx = Pattern.compile("\\s+(\\d+)*\\s*(stk|dos|amp|spritzamp|minibag|glasfl|fl(asche)*|durchstf|fert(ig)*spr|monodos|fert(ig)*pen|btl)(\\s+|\\b)");
        match = regx.matcher(name);
        if (match.find()) {
            String n = match.group(1);    // group(0) -> whole regular expression
            String u = match.group(2);
            if (isNotNullNotEmpty(u)) {
                if (isNotNullNotEmpty(n))
                    return new PackSize("", "", n, u);
                else
                    return new PackSize("", "", "1", u);
            }
        }
        // Tubes and dispensers are special, e.g. 3 Disp 80 g
        regx = Pattern.compile("(\\d+)?\\s*(disp|tb)");
        match = regx.matcher(name);
        if (match.find()) {
            String n = match.group(1);    // group(0) -> whole regular expression
            String u = match.group(2);
            if (isNotNullNotEmpty(u)) {
                if (isNotNullNotEmpty(n))
                    return new PackSize("", "", n, u);
                else
                    return new PackSize("", "", "1", u);
            }
        }
        return new PackSize("", "", "", "");
    }

    public String extractGalensFromName(String name) {
        String galens = "";
        GalenForms galen_forms = extractGalenFromName(name);
        ArrayList<String> list_of_galens = galen_forms.list_of_galens;
        for (String g : list_of_galens) {
            name = name.replaceAll("\\b" + g + "\\b", " ");
            // If possible find short form
            g = g.trim();
            String short_g = findGalenShort(g.trim());
            // Add to list of galenische formen
            if (!short_g.isEmpty())
                galens += short_g + ",";
            else if (!g.isEmpty())
                galens += g + ",";
        }
        return galens;
    }

    String cleanName(SimpleArticle article) {
        ArrayList<String> list_of_matchers = createDosageMatchers(article);
        // This is the package name we will work with
        String clean_name = article.name;
        for (String m : list_of_matchers) {
            clean_name = clean_name.replaceAll("\\s+" + m + "\\b", "");
        }
        return clean_name;
    }

    void extractPackageSizeFromSwissmedicFile(SimpleArticle a, PackSize ps, String galens) {
        if (ps.size.isEmpty()) {
            String q = a.quantity;
            Pattern regx = Pattern.compile("\\b(\\d+)([,]\\d+)?\\s*");
            Matcher match = regx.matcher(q);
            if (match.find())
                q = q.replaceAll(",", ".");
            ps.size = q;
        }
        if (ps.s_unit.isEmpty()) {
            String u = a.pack_unit;
            if (!u.isEmpty()) {
                // Remove plurals...
                u = u.toLowerCase().replaceAll("\\(n\\)|\\(s\\)", "");
                u = u.replaceAll("\\.", "");  // case: i.e.
                ps.s_unit = u.toUpperCase();
                // First make sure this string is not a "unit" (Swissmedic has a mess...)
                Pattern regx = Pattern.compile("\\b(mg|g|kg|l|ml|ie|mbq)\\b");
                Matcher match = regx.matcher(u);
                if (match.find()) {
                    ps.s_unit = u.toUpperCase();
                    return;
                }
                // Else...
                u = u.trim();
                u = findGalenShort(u);
                u = m_map_of_short_to_pack_units.get(u);
                if (u != null)
                    ps.s_unit = u.toUpperCase();
            } else {
                String[] all_galens = galens.split(",", -1);

                // System.out.println(galens);

                for (String g : all_galens) {
                    String g_trimmed = g.trim();
                    if (m_map_of_short_to_pack_units.containsKey(g_trimmed))
                        ps.s_unit = m_map_of_short_to_pack_units.get(g_trimmed).toUpperCase();
                }
            }
        }

        // System.out.println(a.quantity + " / " + a.pack_unit + " -> " + ps.size + " /" + ps.s_unit);
    }

    /**
     * main function where data are processed
     */
    public void process() {

        try {
            BaseDataParser bdp = new BaseDataParser();
            // Read core info from files
            TreeMap<String, SimpleArticle> swissmedic_gtin_to_article_map = bdp.parseSwissmedicPackagesFile_Gtin();
            TreeMap<String, String> refdata_gtin_to_name_map = bdp.parseRefdataPharmaFile();

            String csv_str = "";
            for (Map.Entry<String, SimpleArticle> e : swissmedic_gtin_to_article_map.entrySet()) {
                String gtin = e.getKey();
                SimpleArticle article = e.getValue();
                String name = article.name.toLowerCase();
                // If name exists in refdata data, use it. Else proceed with swissmedic name
                if (refdata_gtin_to_name_map.containsKey(gtin)) {
                    name = refdata_gtin_to_name_map.get(gtin).toLowerCase();
                }

                if (/*gtin.equals("7680329200126")*/true) {
                    article.name = name;
                    // Process only non animal products
                    if (!isAnimalMed(name)) {
                        // Extract drug dosage
                        DrugDosage dd = extractDrugDosageFromName(name);
                        // Extract package size
                        PackSize ps = extractPackSizeFromName(name);
                        // Cleaning 1st pass
                        String clean_name = cleanName(article);
                        // Extract galenic forms (csv)
                        String galens = extractGalensFromName(clean_name);

                        // If pack size and/or units are missing in 'ps', then check info in file 'Constants.FILE_PACKAGES_XLSX'
                        if (ps.size.isEmpty() || ps.s_unit.isEmpty())
                            extractPackageSizeFromSwissmedicFile(article, ps, galens);

                        if (!galens.isEmpty()) {
                            galens = galens.split(",")[0].trim();
                        } else {
                            galens = ps.s_unit;
                        }

                        // Cleaning: 2nd pass
                        clean_name = cleanName(clean_name, true);
                        clean_name = Utilities.removeSpaces(clean_name);
                        clean_name = Utilities.capitalizeFully(clean_name, 1);
                        // Add "galenische Form" to clean name
                        clean_name = Utilities.addStringToString(clean_name, Utilities.capitalizeFirstLetter(galens), " ");

                        if (dd.dose.toLowerCase().equals(ps.size.toLowerCase()) && dd.unit.toLowerCase().equals(ps.s_unit.toLowerCase())) {
                            dd.dose = "";
                            dd.unit = "";
                        }

                        csv_str += gtin + ";"
                                + article.name + ";"
                                // + clean_name + ";"
                                + galens + ";"
                                + ps.size + ";"
                                + ps.s_unit + ";"
                                + dd.dose + ";"
                                + dd.unit + ";"
                                + article.smn5_plus + ";" + "\n";
                    }
                }
            }
            if (!csv_str.isEmpty())
                FileOps.writeToFile(csv_str, Constants.DIR_OUTPUT, "package_data.csv");
        } catch (IOException | JAXBException e) {
            e.printStackTrace();
        }
    }
}
