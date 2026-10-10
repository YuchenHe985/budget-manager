package com.yuchenhe.budget.service;

import java.util.ArrayList;
import java.util.List;

/** Minimal RFC 4180 CSV: quoted fields may hold commas, quotes ("") and line breaks. */
final class CsvCodec {
  private CsvCodec() {}

  /** Parses a whole document into records; each record remembers its first line number. */
  static List<Record> parse(String text) {
    List<Record> out = new ArrayList<>();
    List<String> fields = new ArrayList<>();
    StringBuilder f = new StringBuilder();
    boolean quoted = false;
    int line = 1;
    int start = 1;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (quoted) {
        if (ch == '"') {
          if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
            f.append('"');
            i++;
          } else {
            quoted = false;
          }
        } else if (ch == '\r') {
          // A line break inside a quoted field is stored as '\n' whatever wrote the file
          // (Excel on Windows writes CRLF), so an export/import round trip changes nothing.
          if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
            i++;
          }
          line++;
          f.append('\n');
        } else {
          if (ch == '\n') {
            line++;
          }
          f.append(ch);
        }
      } else if (ch == '"' && f.length() == 0) {
        quoted = true;
      } else if (ch == ',') {
        fields.add(f.toString());
        f.setLength(0);
      } else if (ch == '\n' || ch == '\r') {
        if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
          i++;
        }
        fields.add(f.toString());
        f.setLength(0);
        if (!(fields.size() == 1 && fields.get(0).isEmpty())) {
          out.add(new Record(start, List.copyOf(fields)));
        }
        fields.clear();
        line++;
        start = line;
      } else {
        f.append(ch);
      }
    }
    if (quoted) {
      throw new IllegalArgumentException("line " + start + ": a quoted field is never closed");
    }
    if (f.length() > 0 || !fields.isEmpty()) {
      fields.add(f.toString());
      out.add(new Record(start, List.copyOf(fields)));
    }
    return out;
  }

  static String quote(String v) {
    if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
      return "\"" + v.replace("\"", "\"\"") + "\"";
    }
    return v;
  }

  record Record(int line, List<String> fields) {}
}
