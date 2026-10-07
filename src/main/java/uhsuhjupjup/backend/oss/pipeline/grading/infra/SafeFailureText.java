package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import org.springframework.core.NestedExceptionUtils;

import java.util.Set;

final class SafeFailureText {

    private static final int MAX_API_MESSAGE_CODE_POINTS = 200;
    private static final Set<Integer> CREDENTIAL_FAILURES = Set.of(401, 403);

    private SafeFailureText() {
    }

    static boolean showsApiMessage(int status) {
        return status >= 400 && status < 500 && !CREDENTIAL_FAILURES.contains(status);
    }

    static String excerptOf(String apiMessage) {
        StringBuilder excerpt = new StringBuilder();
        apiMessage.codePoints()
                .limit(MAX_API_MESSAGE_CODE_POINTS)
                .map(codePoint -> isLineBreakOrControl(codePoint) ? ' ' : codePoint)
                .forEach(excerpt::appendCodePoint);
        return excerpt.toString();
    }

    static String classNamesOf(Throwable failure) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(failure);
        String name = failure.getClass().getSimpleName();
        return root == failure ? name : name + ", " + root.getClass().getSimpleName();
    }

    private static boolean isLineBreakOrControl(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }
}
