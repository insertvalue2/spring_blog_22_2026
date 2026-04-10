package org.example.demo_ssr_v1_1._core.utils;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;

/**
 * 날짜/시간 포맷 유틸리티
 *
 * [학습 포인트]
 * - DateTimeFormatter 는 "스레드 세이프" 하므로 static final 로 한 번만 만들고 재사용한다.
 *   (구버전의 SimpleDateFormat 은 스레드 세이프가 아니라서 매번 new 해야 했다)
 */
public final class MyDateUtil {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private MyDateUtil() {
        // 유틸리티 클래스는 인스턴스화 방지
    }

    /**
     * Timestamp 를 "yyyy-MM-dd HH:mm" 문자열로 변환.
     *
     * @param time 변환 대상 (null 허용)
     * @return 포맷된 문자열, 입력이 null 이면 null
     */
    public static String timestampFormat(Timestamp time) {
        if (time == null) {
            return null;
        }
        // Timestamp -> LocalDateTime 으로 변환한 뒤 포맷 적용
        return time.toLocalDateTime().format(FORMATTER);
    }
}
