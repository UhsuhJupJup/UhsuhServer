package uhsuhjupjup.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // AUTH
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "요청 본문을 해석할 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 메서드입니다."),
    RESOURCE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 리소스입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 오류가 발생했습니다."),

    INVALID_ID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 토큰입니다."),
    EXPIRED_ID_TOKEN(HttpStatus.UNAUTHORIZED, "인증 토큰이 만료되었습니다."),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "세션이 종료되었습니다. 다시 로그인해 주세요."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "검증되지 않은 이메일입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),

    TOPIC_NOT_FOUND(HttpStatus.NOT_FOUND, "토픽을 찾을 수 없습니다."),
    KEYWORD_NOT_FOUND(HttpStatus.NOT_FOUND, "키워드를 찾을 수 없습니다."),
    BLOG_NOT_FOUND(HttpStatus.NOT_FOUND, "블로그를 찾을 수 없습니다."),
    ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 글을 찾을 수 없습니다."),
    NOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "노트를 찾을 수 없습니다."),

    CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "수신 동의가 필요합니다."),
    INVALID_UNSUBSCRIBE_TOKEN(HttpStatus.NOT_FOUND, "유효하지 않은 수신거부 링크입니다."),
    EMAIL_ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 가입된 이메일입니다. 로그인 후 구독해 주세요."),
    INVALID_VERIFICATION_TOKEN(HttpStatus.NOT_FOUND, "유효하지 않거나 만료된 확인 링크입니다."),
    EMAIL_ALREADY_SUBSCRIBED(HttpStatus.CONFLICT, "이미 구독 중인 이메일입니다. 관리 링크로 변경해 주세요."),
    INVALID_MANAGE_TOKEN(HttpStatus.NOT_FOUND, "유효하지 않거나 만료된 관리 링크입니다."),

    BLOG_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 블로그 도메인입니다."),

    OSS_REPO_PRIVATE(HttpStatus.BAD_REQUEST, "비공개 레포는 등록할 수 없습니다."),
    OSS_REPO_ISSUES_DISABLED(HttpStatus.BAD_REQUEST, "이슈를 꺼 둔 레포는 등록할 수 없습니다."),
    OSS_REPO_ARCHIVED(HttpStatus.BAD_REQUEST, "보관(archived)된 레포는 등록할 수 없습니다."),
    GITHUB_REPO_NOT_FOUND(HttpStatus.NOT_FOUND, "GitHub에서 레포를 찾을 수 없습니다."),
    OSS_REPO_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 레포입니다."),
    GITHUB_TOKEN_REQUIRED(HttpStatus.SERVICE_UNAVAILABLE, "GitHub 토큰이 설정되지 않아 조회할 수 없습니다."),
    GITHUB_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "GitHub를 지금 조회할 수 없습니다. 잠시 뒤 다시 시도해 주세요."),

    OSS_CATEGORY_NOT_FOUND(HttpStatus.BAD_REQUEST, "없는 카테고리 코드가 있습니다."),
    OSS_REPO_NOT_FOUND(HttpStatus.NOT_FOUND, "레포를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
