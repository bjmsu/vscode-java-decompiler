package com.benjamin.su.decompiler;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import com.strobel.decompiler.languages.LineNumberPosition;

class LineNumberFormatter {
    private final List<LineNumberPosition> _positions;
    private final String _context;

    public LineNumberFormatter(String context, List<LineNumberPosition> lineNumberPositions) {
        _context = context;
        _positions = lineNumberPositions;
    }

    public String  reformatFile() throws IOException{
        List<LineNumberPosition> lineBrokenPositions = new ArrayList<>();
        List<String> brokenLines = breakLines(lineBrokenPositions);
        return emitFormatted(brokenLines, lineBrokenPositions);
    }

    private List<String> breakLines(List<LineNumberPosition> o_LineBrokenPositions) throws IOException{
        int numLinesRead = 0;
        int lineOffset = 0;
        List<String> brokenLines = new ArrayList<>();

        try (BufferedReader r = new BufferedReader(new StringReader(_context))) {
            for (int posIndex = 0; posIndex < _positions.size(); posIndex++) {
                LineNumberPosition pos = _positions.get(posIndex);
                o_LineBrokenPositions.add(new LineNumberPosition( pos.getOriginalLine(), pos.getEmittedLine() + lineOffset, pos.getEmittedColumn()));

                while (numLinesRead < pos.getEmittedLine() - 1) {
                    brokenLines.add(r.readLine());
                    numLinesRead++;
                }
                String line = r.readLine();
                numLinesRead++;
                LineNumberPosition nextPos;
                int prevPartLen = 0;
                char[] indent = {};
                do {
                    nextPos = (posIndex < _positions.size() - 1) ? _positions.get(posIndex + 1) : null;
                    if (nextPos != null
                            && nextPos.getEmittedLine() == pos.getEmittedLine()
                            && nextPos.getOriginalLine() > pos.getOriginalLine()) {
                        posIndex++;
                        lineOffset++;
                        String firstPart = line.substring(0, nextPos.getEmittedColumn() - prevPartLen - 1);
                        brokenLines.add(new String(indent) + firstPart);
                        prevPartLen += firstPart.length();
                        indent = new char[prevPartLen];
                        Arrays.fill(indent, ' ');
                        line = line.substring(firstPart.length(), line.length());

                        o_LineBrokenPositions.add(new LineNumberPosition(
                                nextPos.getOriginalLine(), nextPos.getEmittedLine() + lineOffset, nextPos
                                        .getEmittedColumn()));
                    } else {
                        nextPos = null;
                    }
                } while (nextPos != null);

                brokenLines.add(new String(indent) + line);
            }

            String line;
            while ((line = r.readLine()) != null) {
                brokenLines.add(line);
            }
        }
        return brokenLines;
    }

    private String emitFormatted(List<String> brokenLines, List<LineNumberPosition> lineBrokenPositions){
        int globalOffset = 0;
        int numLinesRead = 0;
        Iterator<String> lines = brokenLines.iterator();

        int maxLineNo = LineNumberPosition.computeMaxLineNumber(lineBrokenPositions);
        StringWriter s=new StringWriter();
        LineNumberPrintWriter w = new LineNumberPrintWriter(maxLineNo, new BufferedWriter(s));
        for (LineNumberPosition pos : lineBrokenPositions) {
            int nextTarget = pos.getOriginalLine();
            int nextActual = pos.getEmittedLine();
            int requiredAdjustment = (nextTarget - nextActual - globalOffset);

            if (requiredAdjustment < 0) {
                List<String> stripped = new ArrayList<>();
                while (numLinesRead < nextActual - 1) {
                    String line = lines.next();
                    numLinesRead++;
                    if ((requiredAdjustment < 0) && line.trim().isEmpty()) {
                        requiredAdjustment++;
                        globalOffset--;
                    } else {
                        stripped.add(line);
                    }
                }
                int lineNoToPrint = (stripped.size() + requiredAdjustment <= 0)
                        ? nextTarget
                        : LineNumberPrintWriter.NO_LINE_NUMBER;
                for (String line : stripped) {
                    if (requiredAdjustment < 0) {
                        w.print(lineNoToPrint, line);
                        w.print("  ");
                        requiredAdjustment++;
                        globalOffset--;
                    } else {
                        w.println(lineNoToPrint, line);
                    }
                }
                String line = lines.next();
                numLinesRead++;
                if (requiredAdjustment < 0) {
                    w.print(nextTarget, line);
                    w.print("  ");
                    globalOffset--;
                } else {
                    w.println(nextTarget, line);
                }

            } else {
                while (numLinesRead < nextActual) {
                    String line = lines.next();
                    numLinesRead++;
                    boolean isLast = (numLinesRead >= nextActual);
                    int lineNoToPrint = isLast ? nextTarget : LineNumberPrintWriter.NO_LINE_NUMBER;
                    if (requiredAdjustment > 0 ) {
                        do {
                            w.println("");
                            requiredAdjustment--;
                            globalOffset++;
                        } while (isLast && requiredAdjustment > 0);
                        w.println(lineNoToPrint, line);
                    } else {
                        w.println(lineNoToPrint, line);
                    }
                }
            }
        }
        String line;
        while (lines.hasNext()) {
            line = lines.next();
            w.println(line);
        }
        w.flush();
        w.close();
        return s.toString();
    }

    private class LineNumberPrintWriter extends PrintWriter {
        public static final int NO_LINE_NUMBER = -1;
        private final String _format;

        public LineNumberPrintWriter(int maxLineNo, Writer w) {
            super(w);
            String maxNumberString = String.format("%d", maxLineNo);
            int numberWidth = maxNumberString.length();
            _format = "/*%" + numberWidth + "d*/";
            String samplePrefix = String.format(_format, maxLineNo);
            char[] prefixChars = samplePrefix.toCharArray();
            Arrays.fill(prefixChars, ' ');
        }

        @Override
        public void print(String s) {
            this.print(NO_LINE_NUMBER, s);
        }

        @Override
        public void println(String s) {
            this.println(NO_LINE_NUMBER, s);
        }

        public void println(int lineNumber, String s) {
            super.println(s);
        }

        public void print(int lineNumber, String s) {
            super.print(s);
        }

    }
}
