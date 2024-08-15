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
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import com.strobel.decompiler.languages.LineNumberPosition;

class LineNumberFormatter {
    private final List<LineNumberPosition> _positions;
    private final EnumSet<LineNumberOption> _options;
    private final String _context;

    public enum LineNumberOption {
        LEADING_COMMENTS,
        STRETCHED,
    }

    public LineNumberFormatter(String context, List<LineNumberPosition> lineNumberPositions, EnumSet<LineNumberOption> options) {
        _context = context;
        _positions = lineNumberPositions;
        _options = (options == null ? EnumSet.noneOf(LineNumberOption.class) : options);
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

                // Read the line that contains the next line number annotations, but don't write
                // it yet.
                String line = r.readLine();
                numLinesRead++;

                // See if there are two original line annotations on the same emitted line.
                LineNumberPosition nextPos;
                int prevPartLen = 0;
                char[] indent = {};
                do {
                    nextPos = (posIndex < _positions.size() - 1) ? _positions.get(posIndex + 1) : null;
                    if (nextPos != null
                            && nextPos.getEmittedLine() == pos.getEmittedLine()
                            && nextPos.getOriginalLine() > pos.getOriginalLine()) {
                        // Two different source line numbers on the same emitted line!
                        posIndex++;
                        lineOffset++;
                        String firstPart = line.substring(0, nextPos.getEmittedColumn() - prevPartLen - 1);
                        brokenLines.add(new String(indent) + firstPart);
                        prevPartLen += firstPart.length();
                        indent = new char[prevPartLen];
                        Arrays.fill(indent, ' ');
                        line = line.substring(firstPart.length(), line.length());

                        // Alter the position while adding it.
                        o_LineBrokenPositions.add(new LineNumberPosition(
                                nextPos.getOriginalLine(), nextPos.getEmittedLine() + lineOffset, nextPos
                                        .getEmittedColumn()));
                    } else {
                        nextPos = null;
                    }
                } while (nextPos != null);

                // Nothing special here-- just emit the line.
                brokenLines.add(new String(indent) + line);
            }

            // Copy out the remainder of the file.
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
        if (!_options.contains(LineNumberOption.LEADING_COMMENTS)) {
            w.suppressLineNumbers();
        }

        boolean doStretching = true;//(_options.contains(LineNumberOption.STRETCHED));

        for (LineNumberPosition pos : lineBrokenPositions) {
            int nextTarget = pos.getOriginalLine();
            int nextActual = pos.getEmittedLine();
            int requiredAdjustment = (nextTarget - nextActual - globalOffset);

            if (doStretching && requiredAdjustment < 0) {
                // We currently need to remove newlines to squeeze things together.
                // prefer to remove empty lines,
                // 1. read all lines before nextActual and remove empty lines as needed
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
                // 2. print non empty lines while stripping further as needed
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
                // 3. read and print next actual
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

                    if (requiredAdjustment > 0 && doStretching) {
                        // We currently need to inject newlines to space things out.
                        do {
                            w.println("");
                            requiredAdjustment--;
                            globalOffset++;
                        } while (isLast && requiredAdjustment > 0);
                        w.println(lineNoToPrint, line);
                    } else {
                        // No tweaks needed-- we are on the ball.
                        w.println(lineNoToPrint, line);
                    }
                }
            }
        }

        // Finish out the file.
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
        private final String _emptyPrefix;
        private final String _format;
        private boolean _needsPrefix;
        private boolean _suppressLineNumbers;

        public LineNumberPrintWriter(int maxLineNo, Writer w) {
            super(w);
            String maxNumberString = String.format("%d", maxLineNo);
            int numberWidth = maxNumberString.length();
            _format = "/*%" + numberWidth + "d*/";
            String samplePrefix = String.format(_format, maxLineNo);
            char[] prefixChars = samplePrefix.toCharArray();
            Arrays.fill(prefixChars, ' ');
            _emptyPrefix = new String(prefixChars);
            _needsPrefix = true;
        }

        public void suppressLineNumbers() {
            _suppressLineNumbers = true;
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
            this.doPrefix(lineNumber);
            super.println(s);
            _needsPrefix = true;
        }

        public void print(int lineNumber, String s) {
            this.doPrefix(lineNumber);
            super.print(s);
        }

        private void doPrefix(int lineNumber) {
            if (_needsPrefix && !_suppressLineNumbers) {
                if (lineNumber == NO_LINE_NUMBER) {
                    super.print(_emptyPrefix);
                } else {
                    String prefix = String.format(_format, lineNumber);
                    super.print(prefix);
                }
            }
            _needsPrefix = false;
        }
    }
}
