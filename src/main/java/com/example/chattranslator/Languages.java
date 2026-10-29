package com.example.chattranslator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every language and fun style the picker offers. Each has a readable name plus country words,
 * so you can search "germany", "japan" or "brazil" as well as "german".
 */
public final class Languages {
    private Languages() {}

    /** {@code aliases} is lowercase, space separated, e.g. "germany deutschland austria". */
    public record Lang(String code, String name, String aliases) {}

    // code=Name|country words
    private static final String[] REAL = {
        "en=English|united states usa america uk england britain australia canada new zealand",
        "de=German|germany deutschland austria switzerland", "es=Spanish|spain mexico argentina colombia chile peru espana",
        "fr=French|france canada quebec belgium", "it=Italian|italy italia", "pt=Portuguese|portugal brazil brasil",
        "nl=Dutch|netherlands holland belgium", "sv=Swedish|sweden", "no=Norwegian|norway", "da=Danish|denmark",
        "fi=Finnish|finland", "pl=Polish|poland", "ru=Russian|russia", "uk=Ukrainian|ukraine", "tr=Turkish|turkey turkiye",
        "ja=Japanese|japan nippon", "ko=Korean|korea south korea", "zh-CN=Chinese (Simplified)|china mandarin",
        "zh-TW=Chinese (Traditional)|taiwan hong kong cantonese", "ar=Arabic|saudi arabia egypt uae emirates morocco iraq",
        "hi=Hindi|india", "id=Indonesian|indonesia", "vi=Vietnamese|vietnam", "th=Thai|thailand", "cs=Czech|czech republic czechia",
        "sk=Slovak|slovakia", "hu=Hungarian|hungary", "ro=Romanian|romania moldova", "bg=Bulgarian|bulgaria",
        "el=Greek|greece", "iw=Hebrew|israel", "hr=Croatian|croatia", "sr=Serbian|serbia", "sl=Slovenian|slovenia",
        "lt=Lithuanian|lithuania", "lv=Latvian|latvia", "et=Estonian|estonia", "is=Icelandic|iceland", "ga=Irish|ireland",
        "cy=Welsh|wales", "gd=Scottish Gaelic|scotland", "la=Latin|vatican rome", "eo=Esperanto|", "af=Afrikaans|south africa",
        "sw=Swahili|kenya tanzania", "fa=Persian|iran persia farsi", "ur=Urdu|pakistan", "bn=Bengali|bangladesh",
        "ta=Tamil|tamil nadu sri lanka", "te=Telugu|", "ms=Malay|malaysia", "tl=Filipino|philippines tagalog",
        "ca=Catalan|catalonia", "eu=Basque|basque country", "gl=Galician|galicia", "sq=Albanian|albania kosovo",
        "mk=Macedonian|north macedonia", "be=Belarusian|belarus", "hy=Armenian|armenia", "ka=Georgian|georgia",
        "kk=Kazakh|kazakhstan", "mn=Mongolian|mongolia", "ne=Nepali|nepal", "my=Burmese|myanmar burma",
        "km=Khmer|cambodia", "lo=Lao|laos", "si=Sinhala|sri lanka", "am=Amharic|ethiopia", "yo=Yoruba|nigeria",
        "zu=Zulu|south africa", "xh=Xhosa|south africa", "so=Somali|somalia", "mt=Maltese|malta", "lb=Luxembourgish|luxembourg"
    };

    private static final String[] FUN = {
        "rouxls=Rouxls Kaard (fun)|deltarune undertale duke puzzles", "british=British (fun)|uk england britain london",
        "cockney=Cockney (fun)|london england", "posh=Posh (fun)|england queen", "scottish=Scottish English (fun)|scotland",
        "northern=Northern / Yorkshire (fun)|yorkshire england", "welsh=Welsh English (fun)|wales"
    };

    public static final List<Lang> ALL = build();

    private static Lang parse(String e) {
        int eq = e.indexOf('=');
        String rest = e.substring(eq + 1);
        int bar = rest.indexOf('|');
        String name = bar < 0 ? rest : rest.substring(0, bar);
        String aliases = bar < 0 ? "" : rest.substring(bar + 1).toLowerCase(Locale.ROOT);
        return new Lang(e.substring(0, eq), name, aliases);
    }

    private static List<Lang> build() {
        List<Lang> real = new ArrayList<>();
        for (String e : REAL) real.add(parse(e));
        List<Lang> rest = new ArrayList<>(real.subList(1, real.size()));
        rest.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        List<Lang> out = new ArrayList<>();
        out.add(real.get(0)); // English first
        out.addAll(rest);
        for (String e : FUN) out.add(parse(e));
        return List.copyOf(out);
    }

    public static Lang find(String code) {
        for (Lang l : ALL) if (l.code().equalsIgnoreCase(code)) return l;
        return null;
    }

    /** Readable name for a code, or the code itself if unknown. */
    public static String name(String code) {
        Lang l = find(code);
        return l != null ? l.name() : code;
    }

    /** Short text for small buttons: "EN", "DE", "Rouxls". */
    public static String shortName(String code) {
        if (code.length() <= 5) return code.toUpperCase(Locale.ROOT);
        String c = code.substring(0, 1).toUpperCase(Locale.ROOT) + code.substring(1);
        return c.length() > 8 ? c.substring(0, 8) : c;
    }

    private static boolean wordStarts(String text, String q) {
        for (String w : text.split("[ /()]+")) if (!w.isEmpty() && w.startsWith(q)) return true;
        return false;
    }

    /** Best matches first: name or country starting with the text, then anything containing it. */
    public static List<Lang> search(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return ALL;
        List<Lang> starts = new ArrayList<>();
        List<Lang> contains = new ArrayList<>();
        for (Lang l : ALL) {
            String n = l.name().toLowerCase(Locale.ROOT);
            String c = l.code().toLowerCase(Locale.ROOT);
            if (c.equals(q) || wordStarts(n, q) || wordStarts(l.aliases(), q)) starts.add(l);
            else if (n.contains(q) || c.contains(q) || l.aliases().contains(q)) contains.add(l);
        }
        starts.addAll(contains);
        return starts;
    }
}
