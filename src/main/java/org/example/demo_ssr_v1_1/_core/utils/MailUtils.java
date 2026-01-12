package org.example.demo_ssr_v1_1._core.utils;

import java.util.Random;

/**
 * 메일 발송 유틸리티 클래스
 * 
 * 인증번호 생성 기능을 제공합니다.
 * 
 * 핵심 개념:
 * 1. 유틸리티 클래스: 정적 메서드만 제공하는 클래스
 * 2. 랜덤 숫자 생성: Random 클래스를 사용하여 6자리 숫자 생성
 * 3. 범위 설정: 100000 ~ 999999 (6자리 숫자 보장)
 */
public class MailUtils {
    
    /**
     * @return 6자리 랜덤 숫자 문자열 (예: "123456")
     */
    public static String generateRandomCode() {
        Random random = new Random();
        // 0 ~ 899999 사이의 랜덤 숫자 생성  1 + 100_000 = 100_001
        int code = 100_000 + random.nextInt(900_000);
        return String.valueOf(code);
    }
}