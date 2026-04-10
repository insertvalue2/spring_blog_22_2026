package org.example.demo_ssr_v1_1._core.utils;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 파일 업로드 유틸리티 클래스
 *
 * [이 클래스의 역할]
 * 사용자가 올린 파일(MultipartFile)을 서버 디스크에 저장하고, 필요 시 삭제한다.
 *
 * [핵심 개념]
 * 1. 유틸리티 클래스 : static 메서드만 모아둔 클래스 (인스턴스 만들지 않음)
 * 2. UUID 기반 파일명 : 여러 사용자가 같은 이름("profile.jpg")으로 올려도 덮어쓰기 방지
 * 3. DB 에는 "파일명"만 저장, 실제 파일은 디스크에 저장 (= DB 와 디스크를 분리)
 * 4. 이미지 여부 검증 : Content-Type 이 "image/" 로 시작하는지 확인
 *
 * [저장 경로 구조]
 *   프로젝트루트/images/UUID_원본파일명.jpg
 *   -> WebMvcConfig.addResourceHandlers() 에서 /images/** 를 file:images/ 에 매핑하여
 *      브라우저에서 /images/파일명.jpg 로 접근할 수 있게 해준다.
 */
public final class FileUtil {

    /** 프로필 이미지 저장 디렉터리 (프로젝트 루트 기준 상대 경로) */
    public static final String IMAGES_DIR = "images/";

    private FileUtil() {
        // 유틸리티 클래스는 인스턴스화 방지
    }

    // =========================================================================
    // SAVE
    // =========================================================================

    /**
     * 기본 디렉터리(images/) 에 파일을 저장한다. (하위 호환용 편의 메서드)
     */
    public static String saveFile(MultipartFile file) throws IOException {
        return saveFile(file, IMAGES_DIR);
    }

    /**
     * 파일을 저장하고 저장된 파일명을 반환한다.
     *
     * [동작 흐름]
     * 1) 파일이 비어있으면 null 반환 (업로드는 '선택 사항' 이므로 예외 대신 null 로 처리)
     * 2) 업로드 디렉터리가 없으면 만들어준다
     * 3) UUID + 원본 파일명 으로 고유 파일명을 만든다
     * 4) 실제 디스크에 스트림을 복사한다
     * 5) DB 에 저장할 용도로 "파일명만" 반환 (경로는 포함하지 않음)
     *
     * @param file      업로드된 파일
     * @param uploadDir 저장할 디렉터리 (예: "images/")
     * @return 저장된 파일명 (예: "a1b2-c3d4-..._profile.jpg"), 파일이 없으면 null
     * @throws IOException 디스크 저장 실패 시
     */
    public static String saveFile(MultipartFile file, String uploadDir) throws IOException {
        // 1. 파일 유효성 검사
        //    isEmpty() 는 파일이 null 이 아니더라도 "내용이 비어있으면" true.
        if (file == null || file.isEmpty()) {
            return null;
        }

        // 2. 업로드 디렉터리가 없으면 생성 (상위 디렉터리까지 한번에 만들어줌)
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 3. 원본 파일명 검사
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IOException("파일명이 없습니다");
        }

        // 4. 고유 파일명 생성 : UUID 를 앞에 붙여 중복 방지
        //    예) 550e8400-e29b-41d4-a716-446655440000_profile.jpg
        String uuid = UUID.randomUUID().toString();
        String savedFilename = uuid + "_" + originalFilename;

        // 5. 디스크에 실제 파일 저장
        //    Files.copy(InputStream, Path) -> 스트림을 읽어서 해당 경로에 그대로 써준다.
        Path filePath = uploadPath.resolve(savedFilename);
        Files.copy(file.getInputStream(), filePath);

        // 6. DB 에는 "파일명" 만 저장한다. (경로는 WebMvcConfig 에서 이미 매핑돼 있음)
        return savedFilename;
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    /**
     * 기본 디렉터리(images/) 에서 파일을 삭제한다.
     */
    public static void deleteFile(String filename) throws IOException {
        deleteFile(filename, IMAGES_DIR);
    }

    /**
     * 디스크에서 파일을 삭제한다.
     *
     * [왜 필요한가?]
     * - DB 에서만 파일명을 지우면, 디스크에는 쓰레기 파일이 쌓인다.
     * - 따라서 "DB 업데이트 + 디스크 삭제" 를 짝으로 수행해야 한다.
     *
     * [주의]
     * Files.delete() 는 파일이 없으면 예외를 던지므로 exists() 로 먼저 확인한다.
     */
    public static void deleteFile(String filename, String uploadDir) throws IOException {
        // 1. 잘못된 입력 방어
        if (filename == null || filename.isEmpty()) {
            return;
        }

        // 2. 삭제 대상 경로 구성
        Path filePath = Paths.get(uploadDir, filename);

        // 3. 파일이 실제로 있을 때만 삭제 (없으면 이미 지워진 것으로 간주)
        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }
    }

    // =========================================================================
    // VALIDATE
    // =========================================================================

    /**
     * 업로드된 파일이 이미지인지 확인한다.
     *
     * [동작 원리]
     * MultipartFile.getContentType() 은 브라우저가 보낸 MIME 타입을 반환한다.
     * 예) "image/jpeg", "image/png", "application/pdf" ...
     * 우리는 "image/" 로 시작하는지만 본다.
     *
     * [학습용 단계의 한계]
     * - Content-Type 은 클라이언트가 쉽게 조작할 수 있다.
     *   -> 실제 운영에서는 "파일 시그니처(매직 넘버)" 까지 확인해야 하지만,
     *      이 프로젝트는 학습 예제이므로 단순 검증에 그친다.
     */
    public static boolean isImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }
}
