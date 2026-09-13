package com.atta.mcpanel.aternos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * پارسر JSON کوچک و بدون وابستگی — برای خواندن پاسخ‌های API خودِ پنل Aternos
 * (lastStatus، پاسخ actions و فهرست سرورها).
 * فقط خواندن (parse)؛ خروجی: Map/List/String/Double/Boolean/null
 */
public final class MiniJson {

    private MiniJson() {}

    public static Object parse(String s) {
        if (s == null) return null;
        P p = new P(s.trim());
        try {
            Object v = p.value();
            p.ws();
            return v;
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object o) {
        if (o instanceof Map) return (Map<String, Object>) o;
        return null;
    }

    public static String str(Object o, String key, String def) {
        Map<String, Object> m = object(o);
        if (m == null) return def;
        Object v = m.get(key);
        return v == null ? def : String.valueOf(v);
    }

    public static double num(Object o, String key, double def) {
        Map<String, Object> m = object(o);
        if (m == null) return def;
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            try { return Double.parseDouble((String) v); } catch (Exception e) { return def; }
        }
        return def;
    }

    private static final class P {
        final String s;
        int i = 0;

        P(String s) { this.s = s; }

        void ws() { while (i < s.length()) { char c = s.charAt(i); if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++; else break; } }

        char peek() { if (i >= s.length()) return 0; return s.charAt(i); }

        Object value() {
            ws();
            char c = peek();
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == '"') return string();
            if (c == 't') { i += 4; return Boolean.TRUE; }
            if (c == 'f') { i += 5; return Boolean.FALSE; }
            if (c == 'n') { i += 4; return null; }
            return number();
        }

        Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            i++; // {
            ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                String k = string();
                ws();
                i++; // :
                Object v = value();
                m.put(k, v);
                ws();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; break; }
                break;
            }
            return m;
        }

        List<Object> arr() {
            List<Object> l = new ArrayList<Object>();
            i++; // [
            ws();
            if (peek() == ']') { i++; return l; }
            while (true) {
                l.add(value());
                ws();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; break; }
                break;
            }
            return l;
        }

        String string() {
            StringBuilder sb = new StringBuilder();
            i++; // "
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\' && i < s.length()) {
                    char e = s.charAt(i++);
                    if (e == 'n') sb.append('\n');
                    else if (e == 'r') sb.append('\r');
                    else if (e == 't') sb.append('\t');
                    else if (e == 'u' && i + 4 <= s.length()) {
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (Exception ex) { sb.append('?'); }
                        i += 4;
                    } else sb.append(e);
                } else sb.append(c);
            }
            return sb.toString();
        }

        Double number() {
            int st = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') i++;
                else break;
            }
            try { return Double.parseDouble(s.substring(st, i)); } catch (Exception e) { return Double.valueOf(0); }
        }
    }
}
