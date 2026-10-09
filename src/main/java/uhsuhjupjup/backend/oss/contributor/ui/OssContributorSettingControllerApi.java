package uhsuhjupjup.backend.oss.contributor.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.common.exception.ErrorResponse;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.oss.contributor.ui.dto.OssContributorSettingResponse;
import uhsuhjupjup.backend.oss.contributor.ui.dto.OssContributorSettingUpdateRequest;

@Tag(name = "오픈소스 - 알림 설정", description = "오픈소스 이슈 알림을 받을 언어와 채널(이메일, 푸시)을 정하는 API")
public interface OssContributorSettingControllerApi {

    @Operation(
            summary = "내 알림 설정 조회",
            description = """
                    ### 인증

                    - 로그인이 필요합니다.

                    ### 조회 결과

                    - 오픈소스 이슈 알림을 받을 언어(`language`), 이메일 알림(`emailEnabled`), 푸시 알림(`pushEnabled`), 알림을 받을 이메일 주소(`notificationEmail`)를 반환합니다.
                    - 설정을 한 번도 저장하지 않았으면 기본값을 반환합니다. 기본값은 한국어(`ko`), 이메일 켬(`true`), 푸시 끔(`false`)입니다. 조회는 기본값을 저장하지 않습니다.
                    - `language`는 소문자 `ko`, `en` 중 하나입니다. 알림 메일과 푸시를 이 언어로 보냅니다.
                    - `emailEnabled`와 `pushEnabled`가 모두 `false`이면 오픈소스 이슈 알림을 받지 않습니다.
                    - `notificationEmail`은 로그인한 계정의 이메일입니다. 지금은 다른 주소로 바꿀 수 없습니다.

                    ### 발송 반영

                    - 오픈소스 이슈 알림은 아직 보내지 않습니다. 설정은 지금 저장만 되고, 이메일은 알림 발송이 생기는 단계부터, 푸시는 모바일 푸시가 생기는 단계부터 이 설정을 따릅니다.

                    ### 에러

                    - 토큰이 없거나 가입하지 않은 계정이면 `UNAUTHORIZED`로 401을 반환합니다.
                    - 토큰이 잘못됐으면 `INVALID_ID_TOKEN`, 만료됐으면 `EXPIRED_ID_TOKEN`, 모든 기기에서 로그아웃하기 전에 받은 토큰이면 `SESSION_REVOKED`로 401을 반환합니다.
                    - 토큰에 이메일이 없거나 Firebase에서 이메일이 검증되지 않은 계정이면 `EMAIL_NOT_VERIFIED`로 403을 반환합니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = OssContributorSettingResponse.class),
                            examples = {
                                    @ExampleObject(name = "저장한 적 없음(기본값)", value = """
                                            {
                                              "language": "ko",
                                              "emailEnabled": true,
                                              "pushEnabled": false,
                                              "notificationEmail": "user@example.com"
                                            }
                                            """),
                                    @ExampleObject(name = "저장한 설정", value = """
                                            {
                                              "language": "en",
                                              "emailEnabled": false,
                                              "pushEnabled": true,
                                              "notificationEmail": "user@example.com"
                                            }
                                            """)})),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "UNAUTHORIZED",
                                      "message": "인증이 필요합니다.",
                                      "status": 401,
                                      "path": "/api/oss/me/settings",
                                      "timestamp": "2026-10-08T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Firebase에서 이메일이 검증되지 않은 계정",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "EMAIL_NOT_VERIFIED",
                                      "message": "검증되지 않은 이메일입니다.",
                                      "status": 403,
                                      "path": "/api/oss/me/settings",
                                      "timestamp": "2026-10-08T14:20:11.482"
                                    }
                                    """)))
    })
    OssContributorSettingResponse mySettings(@Parameter(hidden = true) Member member);

    @Operation(
            summary = "내 알림 설정 바꾸기",
            description = """
                    ### 인증

                    - 로그인이 필요합니다.

                    ### 동작

                    - 보낸 세 값으로 알림 설정 전체를 바꾸고, 200과 함께 저장한 설정을 반환합니다. 응답 모양은 조회와 같습니다.
                    - 저장한 설정이 없으면 새로 만들고, 있으면 고칩니다.
                    - 같은 값을 다시 보내도 결과가 같습니다.
                    - 이메일과 푸시를 모두 끈 채로 저장할 수 있습니다. 그러면 오픈소스 이슈 알림을 받지 않습니다.
                    - 오픈소스 이슈 알림은 아직 보내지 않습니다. 저장한 설정은 이메일은 알림 발송이 생기는 단계부터, 푸시는 모바일 푸시가 생기는 단계부터 반영됩니다. 그 전까지는 `pushEnabled`를 켜도 푸시가 오지 않습니다.

                    ### 요청 값

                    - `language`, `emailEnabled`, `pushEnabled` 세 값이 모두 필요합니다. 빠지거나 `null`인 값이 있으면 `VALIDATION_ERROR`로 400을 반환하고, `fieldErrors`에 그 필드 이름을 모두 담습니다.
                    - `language`는 소문자 `ko`, `en`만 받습니다. 대소문자가 다르거나(`KO`), 모르는 값이거나(`jp`), 빈 문자열이거나 공백이 섞이면 `VALIDATION_ERROR`로 400을 반환합니다.
                    - `emailEnabled`와 `pushEnabled`는 `true`, `false`로 보냅니다. 불리언으로 읽을 수 없는 값(`"yes"` 등)이 오거나 본문을 JSON으로 읽을 수 없으면 `MALFORMED_REQUEST`로 400을 반환합니다. 이때는 `fieldErrors`가 없습니다.
                    - 세 값 밖의 필드는 무시합니다. `notificationEmail`을 보내도 바뀌지 않습니다. 알림 이메일은 로그인 이메일로 정해져 있습니다.

                    ### 동시 요청

                    - 처음 저장하는 요청이 동시에 여러 번 와도 설정은 한 줄만 생기고 모두 200을 받습니다. 가장 나중에 저장된 요청의 값이 남습니다.

                    ### 에러

                    - 인증을 요청 값보다 먼저 확인합니다. 로그인하지 않았으면 본문이 잘못됐어도 401이고, 이메일이 검증되지 않은 계정이면 본문이 잘못됐어도 403입니다.
                    - 401과 403의 코드는 조회와 같습니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "저장 성공. 저장한 설정을 반환한다",
                    content = @Content(
                            schema = @Schema(implementation = OssContributorSettingResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "language": "en",
                                      "emailEnabled": false,
                                      "pushEnabled": true,
                                      "notificationEmail": "user@example.com"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "빠지거나 null인 값, ko, en이 아닌 언어, 또는 읽을 수 없는 본문",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "빠진 값", value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/oss/me/settings",
                                      "timestamp": "2026-10-08T14:20:11.482",
                                      "fieldErrors": [
                                        {
                                          "field": "emailEnabled",
                                          "reason": "필수 값입니다."
                                        }
                                      ]
                                    }
                                    """),
                            @ExampleObject(name = "지원하지 않는 언어", value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/oss/me/settings",
                                      "timestamp": "2026-10-08T14:20:11.482",
                                      "fieldErrors": [
                                        {
                                          "field": "language",
                                          "reason": "ko, en 중 하나여야 합니다."
                                        }
                                      ]
                                    }
                                    """),
                            @ExampleObject(name = "읽을 수 없는 본문", value = """
                                    {
                                      "code": "MALFORMED_REQUEST",
                                      "message": "요청 본문을 해석할 수 없습니다.",
                                      "status": 400,
                                      "path": "/api/oss/me/settings",
                                      "timestamp": "2026-10-08T14:20:11.482"
                                    }
                                    """)})),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "UNAUTHORIZED",
                                      "message": "인증이 필요합니다.",
                                      "status": 401,
                                      "path": "/api/oss/me/settings",
                                      "timestamp": "2026-10-08T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Firebase에서 이메일이 검증되지 않은 계정",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "EMAIL_NOT_VERIFIED",
                                      "message": "검증되지 않은 이메일입니다.",
                                      "status": 403,
                                      "path": "/api/oss/me/settings",
                                      "timestamp": "2026-10-08T14:20:11.482"
                                    }
                                    """)))
    })
    OssContributorSettingResponse replace(@Parameter(hidden = true) Member member,
                                          OssContributorSettingUpdateRequest request);
}
