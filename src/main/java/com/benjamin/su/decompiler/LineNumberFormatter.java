package com.benjamin.su.decompiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class LineNumberFormatter {

    // matches Vineflower's "// N" or "// N M" appended at end of line
    private static final Pattern LINE_NUM_PATTERN =
            Pattern.compile("^(.*)//\\s*(\\d+(?:\\s+\\d+)*)\\s*$");

    private final String source;

    public LineNumberFormatter(String source) {
        this.source = source;
    }

    public String reformatFile() {
        String[] rawLines = source.split("\n", -1);
        List<String> clean = new ArrayList<>(rawLines.length);
        // outputLine (1-based) -> smallest original line number from the comment
        Map<Integer, Integer> lineMap = new LinkedHashMap<>();

        for (int i = 0; i < rawLines.length; i++) {
            Matcher m = LINE_NUM_PATTERN.matcher(rawLines[i]);
            if (m.matches()) {
                clean.add(rawLines[i]); // keep the // N comment
                int minLine = Arrays.stream(m.group(2).trim().split("\\s+"))
                        .mapToInt(Integer::parseInt).min().orElse(0);
                if (minLine > 0) lineMap.put(i + 1, minLine);
            } else {
                clean.add(rawLines[i]);
            }
        }

        if (lineMap.isEmpty()) return source;

        List<String> result = new ArrayList<>();
        int sourceIdx = 0;

        for (Map.Entry<Integer, Integer> entry : lineMap.entrySet()) {
            int mappedOutputLine = entry.getKey(); // 1-based index in clean[]
            int targetLine = entry.getValue();     // desired line in result (1-based)

            // emit all clean lines before this mapped line
            while (sourceIdx < mappedOutputLine - 1) {
                result.add(clean.get(sourceIdx++));
            }

            // insert blank lines to reach the target
            int gap = targetLine - (result.size() + 1);
            for (int i = 0; i < gap; i++) {
                result.add("");
            }

            result.add(clean.get(sourceIdx++));
        }

        // emit remaining lines
        while (sourceIdx < clean.size()) {
            result.add(clean.get(sourceIdx++));
        }

        return String.join("\n", result);
    }
}
