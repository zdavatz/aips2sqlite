package com.maxl.java.aips2sqlite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by maxl on 22.11.2016.
 */
public class ArticleNameParse {

    public class DrugDosage {
        public DrugDosage(String quantity, String unit) {
            this.dose = quantity;
            this.unit = unit;
        }

        String dose;
        String unit;
    }

    public class PackSize {
        public PackSize(String pack, String p_unit, String size, String s_unit) {
            this.pack = pack;
            this.p_unit = p_unit;
            this.size = size;
            this.s_unit = s_unit;
        }

        String pack;
        String p_unit;
        String size;
        String s_unit;
    }

    public HashMap<String, String> m_map_of_short_to_long_galens = null;
    public HashMap<String, String> m_map_of_short_to_pack_units = null;

    private String[] strings_to_be_cleared = {"c solv", "mit solv", "supp", "o nadel", "rektal", "m nadel(n)", "nadel(n)", "(m|o) kons",
            "(m|o) zucker", "emuls(ion)?", "ecobag", "o aroma", "o konserv", "zuckerfrei", "sans sucre"};

    // Some additional galenic forms go in here...
    private String[] add_galen_forms = {"augensalbe", "nasensalbe", "salbe", "depottabl", "lacktabl", "tabl", "filmtabs", "depotdrag",
            "depot", "drag", "infusionslösung", "injektionslösung", "injektionspräparat", "lös", "injektionssuspension", "susp", "kaps",
            "blist", "ampulle", "balsam", "bals", "emuls", "vial", "creme", "crème", "amp", "tropfen", "paste", "stift", "glob",
            "liq", "tee", "tisane", "supp", "pulver", "lot", "spray", "nebul", "tb", "gaze", "klist", "tinkt", "sirup", "blätter",
            "zäpfchen", "gel", "fertigspr", "gran", "kaugummi", "trockensub", "wasser", "urethrastab", "implant", "generator", "zahnpfasta",
            "elixier", "elixir", "medizinalgas", "inhalationsgas", "badesalz", "brausesalz", "schaum", "gum", "essenz", "sachets", "lehm", "gas"};

    ArticleNameParse() {
        loadBaseDataFiles();
    }

    public void loadBaseDataFiles() {
        try {
            BaseDataParser bdp = new BaseDataParser();
            m_map_of_short_to_long_galens = bdp.parseDosageFormsJson();
            m_map_of_short_to_pack_units = bdp.parsePackUnitsJson();
        } catch(IOException e) {
            //
        }
    }

    public boolean isAnimalMed(String name) {
        Pattern p = Pattern.compile("\\bus(\\.)?\\s*vet(\\.)?");
        Matcher m = p.matcher(name);
        if (m.find())
            return true;
        return false;
    }

    public String cleanName(String name) {
        name = name.toLowerCase();
        // Replace stk, e.g. 6 Fertspr 0.5 ml
        name = name.replaceAll("(\\d+)*\\s+((S|s)tk|(D|d)os|(A|a)mp|(M|m)inibag|(D|d)urchstf|(F|f)ertspr|(E|e)inwegspr|(M|m)onodos|(F|f)ert(ig)?pen|(B|b)tl|(S|s)achets|(S|s)pritzamp|(T|t)rinkamp|(F|f)l|(V|v)ial)(\\s+\\d+(\\.\\d+)?\\s+(ml|mg))*", "");
        for (String s : strings_to_be_cleared)
            name = name.replaceAll("\\b" + s + "\\b", "");
        name = name.replaceAll("\\b(\\d+)*\\s+x\\b", "");
        name = name.replaceAll("\\(.*?\\)", "");
        name = name.replaceAll("i\\.m\\./i\\.v(\\.)?|i\\.v\\./i\\.m\\.|i\\.m\\.|i\\.v\\.|\\bus(\\.)?\\s*vet\\.|\\comp\\.?", "");
        name = name.replaceAll("\\+\\s*\\+", "");
        name = name.split(",")[0];
        return name.trim();
    }

    public ArrayList<String> extractGalenFromName(String name) {
        ArrayList<String> list_of_galens = new ArrayList<>();
        int pos = 1000;		// Big number
        for (Map.Entry<String, String> entry : m_map_of_short_to_long_galens.entrySet()) {
            String short_galen = entry.getKey();
            String long_galen = entry.getValue();
            String[] short_g = short_galen.split(",", -1);
            String[] long_g = long_galen.split(",", -1);
            for (String s : short_g) {
                s = s.trim().toLowerCase();
                // First check if short galenische form is found
                Pattern p = Pattern.compile("\\b" + s + "\\b");
                Matcher m = p.matcher(name);
                if (m.find()) {
                    list_of_galens.add(s);
                    if (m.start()<pos) {
                        pos = m.start();
                        if (list_of_galens.size()>1)
                            Collections.swap(list_of_galens, list_of_galens.size()-1, 0);
                    }
                }
                // Check if long galenische form is found
                if (true /*list_of_galens.isEmpty()*/) {
                    for (String l : long_g) {
                        l = l.trim().toLowerCase();
                        p = Pattern.compile("\\b" + l + "\\b");
                        m = p.matcher(name);
                        if (m.find()) {
                            // Add short galenische form
                            list_of_galens.add(s);
                            if (m.start() < pos) {
                                pos = m.start();
                                if (list_of_galens.size() > 1)
                                    Collections.swap(list_of_galens, list_of_galens.size() - 1, 0);
                            }
                        }
                    }
                }
            }
        }

        if (list_of_galens.isEmpty()) {
            list_of_galens = extractAddGalenFromName(name);
        }
        return list_of_galens;
    }

    public ArrayList<String> extractAddGalenFromName(String name) {
        ArrayList<String> list_of_galens = new ArrayList<>();
        for (String galen : add_galen_forms) {
            Pattern p = Pattern.compile("\\b" + galen + "\\b");
            Matcher m = p.matcher(name);
            if (m.find())
                list_of_galens.add(galen);
            else if (name.toLowerCase().contains(galen.toLowerCase()))
                list_of_galens.add(galen);
        }
        return list_of_galens;
    }

    public String findGalenShort(String galen) {
        for (Map.Entry<String, String> entry : m_map_of_short_to_long_galens.entrySet()) {
            String galen_short = entry.getKey();
            String galen_long = entry.getValue();
            String[] long_g = galen_long.split(",", -1);
            for (String l : long_g) {
                l = l.trim();
                if (l.toLowerCase().startsWith(galen.toLowerCase())
                        || galen.toLowerCase().startsWith(l.toLowerCase()))
                    return galen_short;
            }
        }
        return "";
    }

    public ArrayList<String> createDosageMatchers(SimpleArticle a) {
        String quantity = a.quantity;
        String unit = a.pack_unit;

        ArrayList<String> list_of_matchers = new ArrayList<>();
        // Clean up
        quantity = quantity.toLowerCase().replaceAll(",", ".");
        unit = unit.toLowerCase().replaceAll("\\(.*?\\)", "").trim();

        String q = quantity;
        String u = unit;

        if (q.matches("\\d+") && u.matches("(A|a)mp.*?")) {
            // 0th matcher
            list_of_matchers.add(q + " amp");
            return list_of_matchers;
        }

        // 0th matcher
        list_of_matchers.add(quantity + " " + unit);
        list_of_matchers.add(quantity + unit);

        // 1st matcher for quantity, e.g. 1 x 50 or 1 x 50 ml
        Pattern p = Pattern.compile("(\\d+)\\s*x\\s*(\\d+)\\s*(ml|mg|g)?");
        Matcher m = p.matcher(q);
        if (m.find()) {
            quantity = m.group(1);
            if (m.group(3)!=null && !m.group(3).isEmpty()) {
                list_of_matchers.add(quantity);
            } else {
                quantity = m.group(2);
            }
        }
        // 3rd matcher: unit
        p = Pattern.compile(".*?\\s*(\\d*)\\s*(ml)\\s*.*?");
        m = p.matcher(u);
        if (m.find()) {
            String q2 = m.group(1);
            unit = m.group(2);
            if (q2!=null && !q2.isEmpty()) {
                list_of_matchers.add(q2 + " " + unit);
                list_of_matchers.add(q2 + unit);
            }
            list_of_matchers.add(q + " " + unit);
            list_of_matchers.add(q + unit);
            list_of_matchers.add(quantity + " " + unit);
            list_of_matchers.add(quantity + unit);
        }
        // 4th matcher
        if (q.matches("\\d+\\s*(ml|mg)"))
            list_of_matchers.add(q);
        return list_of_matchers;
    }

    public String removeDosageFromName(String name) {
        name = name.toLowerCase();
        name = name.replaceAll("\\d+(\\.\\d+)?\\s*(mg|g)", "");
        name = name.replaceAll("\\d+\\s\\w+\\/\\d+h", "");
        name = name.replaceAll("\\d+(\\.\\d+)?\\s\\w+\\/\\d*ml", "");
        name = name.replaceAll("\\d+\\s\\w+\\/(\\d+\\.\\d+)?ml", "");
        return name;
    }

}
