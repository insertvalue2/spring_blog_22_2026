package org.example.demo_ssr_v1_1._core.utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 메일 발송 관련 유틸리티
 *
 * [핵심 개념]
 * 1. 유틸리티 클래스 : static 메서드만 제공하며 인스턴스화하지 않는다.
 * 2. 랜덤 숫자 생성 : ThreadLocalRandom 을 사용 (멀티 스레드에서도 안전하고 Random 보다 빠르다).
 * 3. 범위 : 100000 ~ 999999 (항상 6자리를 보장)
 *
 * [학습 포인트 - nextInt(bound) 동작]
 *   random.nextInt(900_000) 는 0 이상 900,000 "미만" 의 정수를 반환한다. (상한 미포함)
 *   여기에 100_000 을 더하면 100,000 이상 999,999 이하가 된다.
 *   -> 결과적으로 항상 6자리 숫자만 나온다.
 */
public final class MailUtils {

    private MailUtils() {
        // 유틸리티 클래스는 인스턴스화 방지
    }

    /**
     * 6자리 인증번호를 생성한다.
     *
     * @return 6자리 숫자 문자열 (예: "482091")
     */
    public static String generateRandomCode() {
        // 1. 0 ~ 899,999 범위의 랜덤 정수 생성
        // 2. 100,000 을 더해 100,000 ~ 999,999 범위로 이동
        // 3. 문자열로 변환 (앞자리 0 이 없으므로 항상 6자리가 보장된다)
        int code = 100_000 + ThreadLocalRandom.current().nextInt(900_000);
        return String.valueOf(code);
    }
}
