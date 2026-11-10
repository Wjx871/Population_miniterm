package com.wjx871.population.export;

import java.util.ArrayList;
import java.util.List;

/** Harmless formula-shaped text only; shared by file and HTTP export tests. */
final class ExportFormulaPayloads {
    record Payload(String label, String input, String expected) {}

    static List<Payload> cases() {
        List<Payload> cases = new ArrayList<>();
        List<String> prefixes = List.of("", " ", "\t", "\r", "\n", "\r\n", "\u00a0",
                "\u3000", "\u2003", "\u0085", "\u200b", "\u200c", "\u200d",
                "\u200e", "\u200f", "\u202a", "\u202e", "\u2060", "\u2066", "\ufeff",
                " \t\r\n\u00a0\u3000\u200b");
        List<String> expressions = List.of("=1+1", "+1+1", "-1+1", "@SUM(1,1)");
        for (int p = 0; p < prefixes.size(); p++) {
            for (int e = 0; e < expressions.size(); e++) {
                String input = prefixes.get(p) + expressions.get(e);
                cases.add(new Payload("prefix-" + p + "-expression-" + e, input,
                        p == 0 ? "'" + input : input));
            }
        }
        for (String text : List.of("中文姓名：王小明", "00123", "12.50", "-12.50", "'=1+1",
                " ", "\t", "文本\r\n=1+1", "<f>1+1</f>")) {
            cases.add(new Payload("ordinary-" + cases.size(), text,
                    text.startsWith("-") ? "'" + text : text));
        }
        // Characterize existing POI 5.4.1 escape decoding, not a formula-injection RED.
        cases.add(new Payload("literalOoxmlEscape", "_x000D_=1+1", "\r=1+1"));
        return List.copyOf(cases);
    }

    private ExportFormulaPayloads() {}
}
