package uhsuhjupjup.backend.oss.pipeline.grading.infra;

final class HtmlComments {

    private static final String OPENING = "<!--";
    private static final String CLOSING = "-->";
    private static final String OPENING_BEFORE_DASHES = "<!";
    private static final int MAX_FENCE_INDENT = 3;
    private static final int MIN_FENCE_LENGTH = 3;
    private static final int NOT_SEARCHED = Integer.MAX_VALUE;

    private final String markdown;
    private final StringBuilder visible;
    private int closingSearchedFrom = NOT_SEARCHED;
    private int closingFound = -1;
    private int paragraphFrom = NOT_SEARCHED;
    private int paragraphLimit = -1;

    private HtmlComments(String markdown) {
        this.markdown = markdown;
        this.visible = new StringBuilder(markdown.length());
    }

    static String removeFrom(String markdown) {
        return new HtmlComments(markdown).remove();
    }

    private String remove() {
        Fence fence = null;
        int lineStart = 0;
        while (lineStart < markdown.length()) {
            int lineEnd = lineEnd(lineStart);
            String line = markdown.substring(lineStart, lineEnd);
            if (fence != null) {
                visible.append(line);
                fence = fence.isClosedBy(line) ? null : fence;
                lineStart = lineEnd;
                continue;
            }
            fence = Fence.openedBy(line);
            if (fence != null) {
                visible.append(line);
                lineStart = lineEnd;
                continue;
            }
            lineStart = appendOutsideFence(lineStart);
        }
        return visible.toString();
    }

    private int appendOutsideFence(int lineStart) {
        boolean textBefore = false;
        int position = lineStart;
        int lineEnd = lineEnd(position);
        while (true) {
            int commentStart = indexWithin(OPENING, position, lineEnd);
            if (commentStart < 0) {
                visible.append(markdown, position, lineEnd);
                return lineEnd;
            }
            visible.append(markdown, position, commentStart);
            textBefore = textBefore || hasText(position, commentStart);
            int closing = closingFrom(commentStart + OPENING_BEFORE_DASHES.length());
            if (!textBefore && closing < 0) {
                return markdown.length();
            }
            if (!textBefore || (closing >= 0 && closing < paragraphLimitFrom(lineEnd))) {
                position = closing + CLOSING.length();
                lineEnd = lineEnd(position);
                continue;
            }
            visible.append(OPENING);
            position = commentStart + OPENING.length();
        }
    }

    private int closingFrom(int from) {
        boolean known = from >= closingSearchedFrom && (closingFound < 0 || closingFound >= from);
        if (!known) {
            closingSearchedFrom = from;
            closingFound = markdown.indexOf(CLOSING, from);
        }
        return closingFound;
    }

    private int paragraphLimitFrom(int from) {
        if (from >= paragraphFrom && from <= paragraphLimit) {
            return paragraphLimit;
        }
        int lineStart = from;
        while (lineStart < markdown.length()) {
            int lineEnd = lineEnd(lineStart);
            String line = markdown.substring(lineStart, lineEnd);
            if (line.isBlank() || Fence.openedBy(line) != null) {
                break;
            }
            lineStart = lineEnd;
        }
        paragraphFrom = from;
        paragraphLimit = lineStart;
        return paragraphLimit;
    }

    private boolean hasText(int from, int to) {
        for (int index = from; index < to; index++) {
            if (!Character.isWhitespace(markdown.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private int indexWithin(String target, int from, int to) {
        for (int index = from; index + target.length() <= to; index++) {
            if (markdown.startsWith(target, index)) {
                return index;
            }
        }
        return -1;
    }

    private int lineEnd(int from) {
        for (int index = from; index < markdown.length(); index++) {
            char character = markdown.charAt(index);
            if (character == '\n') {
                return index + 1;
            }
            if (character == '\r') {
                boolean crlf = index + 1 < markdown.length() && markdown.charAt(index + 1) == '\n';
                return crlf ? index + 2 : index + 1;
            }
        }
        return markdown.length();
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static int runLength(String line, int from, char marker) {
        int end = from;
        while (end < line.length() && line.charAt(end) == marker) {
            end++;
        }
        return end - from;
    }

    private record Fence(char marker, int length) {

        static Fence openedBy(String line) {
            int indent = leadingSpaces(line);
            if (indent > MAX_FENCE_INDENT || indent >= line.length()) {
                return null;
            }
            char marker = line.charAt(indent);
            if (marker != '`' && marker != '~') {
                return null;
            }
            int length = runLength(line, indent, marker);
            if (length < MIN_FENCE_LENGTH) {
                return null;
            }
            if (marker == '`' && line.indexOf('`', indent + length) >= 0) {
                return null;
            }
            return new Fence(marker, length);
        }

        boolean isClosedBy(String line) {
            int indent = leadingSpaces(line);
            if (indent > MAX_FENCE_INDENT) {
                return false;
            }
            int run = runLength(line, indent, marker);
            return run >= length && line.substring(indent + run).isBlank();
        }
    }
}
