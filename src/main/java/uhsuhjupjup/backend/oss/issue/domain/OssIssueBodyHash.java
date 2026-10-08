package uhsuhjupjup.backend.oss.issue.domain;

import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

public final class OssIssueBodyHash {

    private static final String ALGORITHM = "SHA-256";
    private static final String CRLF = "\r\n";
    private static final char CR = '\r';
    private static final char LF = '\n';
    private static final String LINE_BREAK = String.valueOf(LF);

    private OssIssueBodyHash() {
    }

    public static String of(String body) {
        byte[] bytes = normalize(body).getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(sha256().digest(bytes));
    }

    private static String normalize(String body) {
        if (body == null) {
            return "";
        }
        List<String> lines = Arrays.stream(body.replace(CRLF, LINE_BREAK).replace(CR, LF).split(LINE_BREAK, -1))
                .map(OssIssueBodyHash::stripTrailingSpacesAndTabs)
                .toList();
        int first = 0;
        int end = lines.size();
        while (first < end && lines.get(first).isEmpty()) {
            first++;
        }
        while (end > first && lines.get(end - 1).isEmpty()) {
            end--;
        }
        return String.join(LINE_BREAK, lines.subList(first, end));
    }

    private static String stripTrailingSpacesAndTabs(String line) {
        int end = line.length();
        while (end > 0 && isSpaceOrTab(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }

    private static boolean isSpaceOrTab(char character) {
        return character == ' ' || character == '\t';
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
