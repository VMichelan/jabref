package org.jabref.logic.importer.fileformat;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jabref.logic.importer.Importer;
import org.jabref.logic.importer.ParserResult;
import org.jabref.logic.util.OS;
import org.jabref.logic.util.StandardFileType;
import org.jabref.model.entry.AuthorList;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibtexEntryTypes;
import org.jabref.model.entry.FieldName;
import org.jabref.model.entry.Month;

public class RisImporter extends Importer {

    private static final Pattern RECOGNIZED_FORMAT_PATTERN = Pattern.compile("TY  - .*");
    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy");
    //stores all the date tags from highest to lowest priority
    private final String[] dateTags = {"Y1", "PY", "DA", "Y2"};

    @Override
    public String getName() {
        return "RIS";
    }

    @Override
    public StandardFileType getFileType() {
        return StandardFileType.RIS;
    }

    @Override
    public String getDescription() {
        return "Imports a Biblioscape Tag File.";
    }

    @Override
    public boolean isRecognizedFormat(BufferedReader reader) throws IOException {
        // Our strategy is to look for the "TY  - *" line.
        return reader.lines().anyMatch(line -> RECOGNIZED_FORMAT_PATTERN.matcher(line).find());
    }

    @Override
    public ParserResult importDatabase(BufferedReader reader) throws IOException {
        List<BibEntry> bibitems = new ArrayList<>();

        //use optional here, so that no exception will be thrown if the file is empty
        String linesAsString = reader.lines().reduce((line, nextline) -> line + "\n" + nextline).orElse("");

        String[] entries = linesAsString.replace("\u2013", "-").replace("\u2014", "--").replace("\u2015", "--")
                                        .split("ER  -.*\\n");

        for (String entry1 : entries) {

            String dateTag = "";
            String dateValue = "";
            int datePriority = dateTags.length;

            String type = "";
            String author = "";
            String editor = "";
            String startPage = "";
            String endPage = "";
            String comment = "";
            Optional<Month> month = Optional.empty();
            Map<String, String> fields = new HashMap<>();

            String[] lines = entry1.split("\n");

            for (int j = 0; j < lines.length; j++) {
                StringBuilder current = new StringBuilder(lines[j]);
                boolean done = false;
                while (!done && (j < (lines.length - 1))) {
                    if ((lines[j + 1].length() >= 6) && !"  - ".equals(lines[j + 1].substring(2, 6))) {
                        if ((current.length() > 0) && !Character.isWhitespace(current.charAt(current.length() - 1))
                            && !Character.isWhitespace(lines[j + 1].charAt(0))) {
                            current.append(' ');
                        }
                        current.append(lines[j + 1]);
                        j++;
                    } else {
                        done = true;
                    }
                }
                String entry = current.toString();
                if (entry.length() < 6) {
                    continue;
                } else {
                    String tag = entry.substring(0, 2);
                    String value = entry.substring(6).trim();
                    if ("TY".equals(tag)) {
                        if ("BOOK".equals(value)) {
                            type = "book";
                        } else if ("JOUR".equals(value) || "MGZN".equals(value)) {
                            type = "article";
                        } else if ("THES".equals(value)) {
                            type = "phdthesis";
                        } else if ("UNPB".equals(value)) {
                            type = "unpublished";
                        } else if ("RPRT".equals(value)) {
                            type = "techreport";
                        } else if ("CONF".equals(value)) {
                            type = "inproceedings";
                        } else if ("CHAP".equals(value)) {
                            type = "incollection";//"inbook";
                        } else if ("PAT".equals(value)) {
                            type = "patent";
                        } else {
                            type = "other";
                        }
                    } else if ("T1".equals(tag) || "TI".equals(tag)) {
                        String oldVal = fields.get(FieldName.TITLE);
                        if (oldVal == null) {
                            fields.put(FieldName.TITLE, value);
                        } else {
                            if (oldVal.endsWith(":") || oldVal.endsWith(".") || oldVal.endsWith("?")) {
                                fields.put(FieldName.TITLE, oldVal + " " + value);
                            } else {
                                fields.put(FieldName.TITLE, oldVal + ": " + value);
                            }
                        }
                        fields.put(FieldName.TITLE, fields.get(FieldName.TITLE).replaceAll("\\s+", " ")); // Normalize whitespaces
                    } else if ("BT".equals(tag)) {
                        fields.put(FieldName.BOOKTITLE, value);
                    } else if (("T2".equals(tag) || "J2".equals(tag) || "JA".equals(tag)) && ((fields.get(FieldName.JOURNAL) == null) || "".equals(fields.get(FieldName.JOURNAL)))) {
                        //if there is no journal title, then put second title as journal title
                        fields.put(FieldName.JOURNAL, value);
                    } else if ("JO".equals(tag) || "J1".equals(tag) || "JF".equals(tag)) {
                        //if this field appears then this should be the journal title
                        fields.put(FieldName.JOURNAL, value);
                    } else if ("T3".equals(tag)) {
                        fields.put(FieldName.SERIES, value);
                    } else if ("AU".equals(tag) || "A1".equals(tag) || "A2".equals(tag) || "A3".equals(tag) || "A4".equals(tag)) {
                        if ("".equals(author)) {
                            author = value;
                        } else {
                            author += " and " + value;
                        }
                    } else if ("ED".equals(tag)) {
                        if (editor.isEmpty()) {
                            editor = value;
                        } else {
                            editor += " and " + value;
                        }
                    } else if ("JA".equals(tag) || "JF".equals(tag)) {
                        if ("inproceedings".equals(type)) {
                            fields.put(FieldName.BOOKTITLE, value);
                        } else {
                            fields.put(FieldName.JOURNAL, value);
                        }
                    } else if ("LA".equals(tag)) {
                        fields.put(FieldName.LANGUAGE, value);
                    } else if ("CA".equals(tag)) {
                        fields.put("caption", value);
                    } else if ("DB".equals(tag)) {
                        fields.put("database", value);
                    } else if ("IS".equals(tag) || "AN".equals(tag) || "C7".equals(tag) || "M1".equals(tag)) {
                        fields.put(FieldName.NUMBER, value);
                    } else if ("SP".equals(tag)) {
                        startPage = value;
                    } else if ("PB".equals(tag)) {
                        if ("phdthesis".equals(type)) {
                            fields.put(FieldName.SCHOOL, value);
                        } else {
                            fields.put(FieldName.PUBLISHER, value);
                        }
                    } else if ("AD".equals(tag) || "CY".equals(tag) || "PP".equals(tag)) {
                        fields.put(FieldName.ADDRESS, value);
                    } else if ("EP".equals(tag)) {
                        endPage = value;
                        if (!endPage.isEmpty()) {
                            endPage = "--" + endPage;
                        }
                    } else if ("ET".equals(tag)) {
                        fields.put(FieldName.EDITION, value);
                    } else if ("SN".equals(tag)) {
                        fields.put(FieldName.ISSN, value);
                    } else if ("VL".equals(tag)) {
                        fields.put(FieldName.VOLUME, value);
                    } else if ("N2".equals(tag) || "AB".equals(tag)) {
                        String oldAb = fields.get(FieldName.ABSTRACT);
                        if (oldAb == null) {
                            fields.put(FieldName.ABSTRACT, value);
                        } else {
                            fields.put(FieldName.ABSTRACT, oldAb + OS.NEWLINE + value);
                        }
                    } else if ("UR".equals(tag) || "L2".equals(tag) || "LK".equals(tag)) {
                        fields.put(FieldName.URL, value);
                    } else if (isDateTag(tag) && value.length() >= 4) {
                        int tagPriority = getDatePriority(tag);

                        if (tagPriority < datePriority) {
                            String year = value.substring(0, 4);

                            try {
                                    Year.parse(year, formatter);
                                    //if the year is parsebale we have found a higher priority date
                                    dateTag = tag;
                                    dateValue = value;
                                    datePriority = tagPriority;
                            } catch (DateTimeParseException ex) {
                                //We can't parse the year, we ignore it
                            }
                        }
                    } else if ("KW".equals(tag)) {
                        if (fields.containsKey(FieldName.KEYWORDS)) {
                            String kw = fields.get(FieldName.KEYWORDS);
                            fields.put(FieldName.KEYWORDS, kw + ", " + value);
                        } else {
                            fields.put(FieldName.KEYWORDS, value);
                        }
                    } else if ("U1".equals(tag) || "U2".equals(tag) || "N1".equals(tag)) {
                        if (!comment.isEmpty()) {
                            comment = comment + OS.NEWLINE;
                        }
                        comment = comment + value;
                    } else if ("M3".equals(tag) || "DO".equals(tag)) {
                        addDoi(fields, value);
                    } else if ("C3".equals(tag)) {
                        fields.put(FieldName.EVENTTITLE, value);
                    } else if ("N1".equals(tag) || "RN".equals(tag)) {
                        fields.put(FieldName.NOTE, value);
                    } else if ("ST".equals(tag)) {
                        fields.put(FieldName.SHORTTITLE, value);
                    } else if ("C2".equals(tag)) {
                        fields.put(FieldName.EPRINT, value);
                        fields.put(FieldName.EPRINTTYPE, "pubmed");
                    } else if ("TA".equals(tag)) {
                        fields.put(FieldName.TRANSLATOR, value);
                    }
                    // fields for which there is no direct mapping in the bibtext standard
                    else if ("AV".equals(tag)) {
                        fields.put("archive_location", value);
                    } else if ("CN".equals(tag) || "VO".equals(tag)) {
                        fields.put("call-number", value);
                    } else if ("DB".equals(tag)) {
                        fields.put("archive", value);
                    } else if ("NV".equals(tag)) {
                        fields.put("number-of-volumes", value);
                    } else if ("OP".equals(tag)) {
                        fields.put("original-title", value);
                    } else if ("RI".equals(tag)) {
                        fields.put("reviewed-title", value);
                    } else if ("RP".equals(tag)) {
                        fields.put("status", value);
                    } else if ("SE".equals(tag)) {
                        fields.put("section", value);
                    } else if ("ID".equals(tag)) {
                        fields.put("refid", value);
                    }
                }
                // fix authors
                if (!author.isEmpty()) {
                    author = AuthorList.fixAuthorLastNameFirst(author);
                    fields.put(FieldName.AUTHOR, author);
                }
                if (!editor.isEmpty()) {
                    editor = AuthorList.fixAuthorLastNameFirst(editor);
                    fields.put(FieldName.EDITOR, editor);
                }
                if (!comment.isEmpty()) {
                    fields.put(FieldName.COMMENT, comment);
                }

                fields.put(FieldName.PAGES, startPage + endPage);
            }

            // if we found a date
            if (dateTag.length() > 0) {
                fields.put(FieldName.YEAR, dateValue.substring(0, 4));

                String[] parts = dateValue.split("/");
                if ((parts.length > 1) && !parts[1].isEmpty()) {
                    try {
                        int monthNumber = Integer.parseInt(parts[1]);
                        month = Month.getMonthByNumber(monthNumber);
                    } catch (NumberFormatException ex) {
                        // The month part is unparseable, so we ignore it.
                    }
                }
            }

            // Remove empty fields:
            fields.entrySet().removeIf(key -> (key.getValue() == null) || key.getValue().trim().isEmpty());

            // create one here
            // type is set in the loop above
            BibEntry entry = new BibEntry(BibtexEntryTypes.getTypeOrDefault(type));
            entry.setField(fields);
            // month has a special treatment as we use the separate method "setMonth" of BibEntry instead of directly setting the value
            month.ifPresent(entry::setMonth);
            bibitems.add(entry);

        }
        return new ParserResult(bibitems);

    }

    private void addDoi(Map<String, String> hm, String val) {
        String doi = val.toLowerCase(Locale.ENGLISH);
        if (doi.startsWith("doi:")) {
            doi = doi.replaceAll("(?i)doi:", "").trim();
            hm.put(FieldName.DOI, doi);
        }
    }

    private boolean isDateTag(String s) {
        for (String dateTag : dateTags) {
            if (dateTag.equals(s)) {
                return true;
            }
        }
        return false;
    }

    private int getDatePriority(String date) {
        int i;
        for (i = 0; i < dateTags.length; i++) {
            if (dateTags[i].equals(date)) {
                break;
            }
        }
        return i;
    }
}
