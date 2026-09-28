package uhsuhjupjup.backend.oss.repo.application.dto;

import java.util.Arrays;

public enum OssRepoSort {

    STARS("stars"),
    NAME("name");

    private final String parameter;

    OssRepoSort(String parameter) {
        this.parameter = parameter;
    }

    public static OssRepoSort fromParameter(String parameter) {
        return Arrays.stream(values())
                .filter(sort -> sort.parameter.equals(parameter))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 정렬입니다: " + parameter));
    }
}
