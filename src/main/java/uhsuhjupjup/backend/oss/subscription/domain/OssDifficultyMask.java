package uhsuhjupjup.backend.oss.subscription.domain;

import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class OssDifficultyMask {

    private static final int EASY_BIT = 1;
    private static final int MEDIUM_BIT = 2;
    private static final int HARD_BIT = 4;
    private static final int EVERY_BIT = Arrays.stream(OssIssueDifficulty.values())
            .mapToInt(OssDifficultyMask::bitOf)
            .reduce(0, (mask, bit) -> mask | bit);

    private OssDifficultyMask() {
    }

    public static int of(Set<OssIssueDifficulty> difficulties) {
        if (difficulties == null || difficulties.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        int mask = 0;
        for (OssIssueDifficulty difficulty : difficulties) {
            mask |= bitOf(difficulty);
        }
        return mask;
    }

    public static Set<OssIssueDifficulty> difficultiesOf(int mask) {
        if (mask == 0 || (mask & ~EVERY_BIT) != 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        Set<OssIssueDifficulty> difficulties = EnumSet.noneOf(OssIssueDifficulty.class);
        for (OssIssueDifficulty difficulty : OssIssueDifficulty.values()) {
            if ((mask & bitOf(difficulty)) != 0) {
                difficulties.add(difficulty);
            }
        }
        return Collections.unmodifiableSet(difficulties);
    }

    public static int bitOf(OssIssueDifficulty difficulty) {
        if (difficulty == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return switch (difficulty) {
            case EASY -> EASY_BIT;
            case MEDIUM -> MEDIUM_BIT;
            case HARD -> HARD_BIT;
        };
    }
}
