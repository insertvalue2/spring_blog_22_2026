package org.example.demo_ssr_v1_1.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception400;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception403;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception404;
import org.example.demo_ssr_v1_1._core.utils.FileUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

/**
 * 사용자(User) 도메인 Service
 *
 * [이 Service 의 책임]
 *  1) 회원가입 / 로그인 / 회원정보 수정 같은 "비즈니스 규칙" 을 담당한다.
 *  2) 카카오 OAuth 로그인 흐름을 오케스트레이션한다 (토큰 → 프로필 → 가입/조회).
 *  3) 포인트 관련 단순 기능(충전)을 제공한다.
 *
 * [@Transactional 사용 규칙]
 *  · 쓰기 메서드(INSERT/UPDATE/DELETE) : @Transactional
 *  · 읽기 전용 메서드                    : @Transactional(readOnly = true)
 *
 * [학습 포인트 - 더티 체킹]
 *  · JPA 트랜잭션 안에서 조회한 엔티티의 필드 값을 변경하면,
 *    save() 를 명시적으로 부르지 않아도 커밋 시점에 UPDATE 쿼리가 자동으로 나간다.
 *  · 이 Service 의 회원정보수정() 이 그 대표 예시이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${oauth.kakao.client-id}")
    private String kakaoClientId;

    @Value("${oauth.kakao.secret}")
    private String kakaoSecretKey;

    @Value("${tenco.key}")
    private String tencoKey;

    // =========================================================================
    // 회원가입
    // =========================================================================

    /**
     * 회원가입 처리.
     *
     * [동작 흐름]
     *  1) DTO 자체 검증
     *  2) username 중복 확인
     *  3) email 중복 확인
     *  4) (선택) 프로필 이미지 저장 → 파일명 획득
     *  5) 비밀번호 BCrypt 해싱
     *  6) Entity 로 변환 후 INSERT
     */
    @Transactional
    public User 회원가입(UserRequest.JoinDTO joinDTO) {
        // 1. DTO 유효성 검사
        joinDTO.validate();

        // 2. username 중복 검사
        if (userRepository.findByUsername(joinDTO.getUsername()).isPresent()) {
            throw new Exception400("이미 존재하는 사용자 이름입니다");
        }

        // 3. email 중복 검사
        if (userRepository.findByEmail(joinDTO.getEmail()).isPresent()) {
            throw new Exception400("이미 등록된 이메일입니다");
        }

        // 4. 프로필 이미지 저장 (선택)
        //    · 업로드되지 않은 경우에는 그냥 null 로 두고 진행한다.
        String profileImageFilename = saveProfileImageIfPresent(joinDTO.getProfileImage());

        // 5. 비밀번호 해싱
        //    · DB 에는 절대 평문을 저장하지 않는다.
        String hashPwd = passwordEncoder.encode(joinDTO.getPassword());

        // 6. DTO → Entity → DB 저장
        User user = joinDTO.toEntity(profileImageFilename);
        user.setPassword(hashPwd);
        return userRepository.save(user);
    }

    // =========================================================================
    // 로그인
    // =========================================================================

    /**
     * 로그인 처리.
     *
     * [동작 흐름]
     *  1) DTO 검증
     *  2) username 으로 roles 까지 함께 조회 (JOIN FETCH)
     *  3) 사용자가 없으면 실패 메시지 반환
     *  4) passwordEncoder.matches(입력평문, 저장해시) 로 비밀번호 검증
     *  5) 성공 시 엔티티 반환
     *
     * ※ 실패 메시지는 "아이디 혹은 비밀번호가 올바르지 않습니다" 로 통일해 정보 노출을 줄인다.
     */
    @Transactional(readOnly = true)
    public User 로그인(UserRequest.LoginDTO loginDTO) {
        // 1. DTO 검증
        loginDTO.validate();

        // 2. 사용자 조회 (+ roles)
        User user = userRepository.findByUsernameWithRoles(loginDTO.getUsername()).orElse(null);

        // 3. 존재하지 않는 계정
        if (user == null) {
            throw new Exception400("사용자명 또는 비밀번호가 올바르지 않습니다");
        }

        // 4. 비밀번호 매칭
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new Exception400("사용자명 또는 비밀번호가 올바르지 않습니다");
        }

        // 5. 성공
        return user;
    }

    // =========================================================================
    // 회원정보 조회 / 수정
    // =========================================================================

    /**
     * 회원정보 수정 화면에 보여줄 사용자 조회.
     *
     * [동작 흐름]
     *  1) id 로 사용자 조회 (없으면 404)
     *  2) 본인 여부 확인 (IDOR 방지)
     *  3) 엔티티 반환
     */
    @Transactional(readOnly = true)
    public User 회원정보수정화면(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception404("사용자를 찾을 수 없습니다"));

        if (!user.isOwner(userId)) {
            throw new Exception403("회원정보 수정 권한이 없습니다");
        }
        return user;
    }

    /**
     * 회원정보 수정 처리.
     *
     * [동작 흐름]
     *  1) 사용자 조회
     *  2) 본인 여부 확인 (IDOR)
     *  3) 소셜 로그인 사용자면 수정 거부 (외부에서 관리하는 정보라서)
     *  4) DTO 검증
     *  5) 비밀번호 해싱 후 DTO 에 덮어쓰기 (엔티티 업데이트에 전달할 값)
     *  6) 로컬 사용자면 프로필 이미지 처리 (새 이미지 저장 + 기존 파일 삭제)
     *  7) user.update(dto) 호출 → 더티 체킹으로 UPDATE 자동 실행
     */
    @Transactional
    public User 회원정보수정(UserRequest.UpdateDTO updateDTO, Long userId) {
        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception404("사용자를 찾을 수 없습니다"));

        // 2. 본인 여부 확인
        if (!user.isOwner(userId)) {
            throw new Exception403("회원정보 수정 권한이 없습니다");
        }

        // 3. 소셜 로그인 사용자는 이곳에서 수정 불가
        if (!user.isLocal()) {
            throw new Exception403("소셜 로그인 사용자는 회원정보를 수정할 수 없습니다");
        }

        // 4. DTO 검증
        updateDTO.validate();

        // 5. 비밀번호 해싱 후 DTO 에 넣어둔다 (entity.update(dto) 가 그 값을 사용)
        updateDTO.setPassword(passwordEncoder.encode(updateDTO.getPassword()));

        // 6. 프로필 이미지 처리
        //    · 새 이미지가 있으면 저장 후 기존 파일 삭제
        //    · 없으면 기존 파일명 유지
        String oldProfileImage = user.getProfileImage();
        if (updateDTO.getProfileImage() != null && !updateDTO.getProfileImage().isEmpty()) {
            // 6-1. 이미지 형식 검증
            if (!FileUtil.isImageFile(updateDTO.getProfileImage())) {
                throw new Exception400("이미지 파일만 업로드 가능합니다");
            }
            try {
                // 6-2. 새 파일 저장
                String newProfileImageFilename =
                        FileUtil.saveFile(updateDTO.getProfileImage(), FileUtil.IMAGES_DIR);
                updateDTO.setProfileImageFilename(newProfileImageFilename);

                // 6-3. 기존 이미지가 있으면 디스크에서 삭제 (쓰레기 파일 방지)
                if (oldProfileImage != null && !oldProfileImage.isEmpty()) {
                    FileUtil.deleteFile(oldProfileImage, FileUtil.IMAGES_DIR);
                }
            } catch (IOException e) {
                throw new Exception400("파일 저장에 실패했습니다: " + e.getMessage());
            }
        } else {
            // 새 이미지가 없으므로 기존 파일명을 그대로 유지한다
            updateDTO.setProfileImageFilename(oldProfileImage);
        }

        // 7. 엔티티 상태 변경 → 더티 체킹으로 UPDATE 자동 실행
        user.update(updateDTO);

        // 8. save() 는 필수가 아니지만 "반환용" 으로 사용 (영속 상태의 객체 반환)
        return userRepository.save(user);
    }

    /**
     * 프로필 이미지 삭제.
     *
     * [동작 흐름]
     *  1) 사용자 조회
     *  2) 본인 여부 확인
     *  3) 디스크에서 실제 파일 삭제 (실패해도 DB 업데이트는 진행)
     *  4) DB 의 profileImage 필드 null 처리
     */
    @Transactional
    public User 프로필이미지삭제(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception404("사용자를 찾을 수 없습니다"));

        if (!user.isOwner(userId)) {
            throw new Exception403("프로필 이미지 삭제 권한이 없습니다");
        }

        String profileImage = user.getProfileImage();
        if (profileImage != null && !profileImage.isEmpty()) {
            try {
                FileUtil.deleteFile(profileImage, FileUtil.IMAGES_DIR);
            } catch (IOException e) {
                // 파일 삭제는 실패해도 진행한다. (예: 이미 지워진 경우)
                log.warn("프로필 이미지 파일 삭제 실패 : {}", e.getMessage());
            }
        }

        user.setProfileImage(null);
        return userRepository.save(user);
    }

    // =========================================================================
    // 사용자 조회 (내부 용)
    // =========================================================================

    @Transactional(readOnly = true)
    public User 사용자이름조회(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    /**
     * 소셜 로그인 사용자를 DB 에 저장 (신규 가입 시에만 호출).
     */
    public void 소셜회원가입(User user) {
        userRepository.save(user);
    }

    // =========================================================================
    // 카카오 소셜 로그인
    // =========================================================================

    /**
     * 카카오 소셜 로그인 전체 흐름.
     *
     * [동작 흐름]
     *  1) 인가 코드(code) → 액세스 토큰 발급 (POST https://kauth.kakao.com/oauth/token)
     *  2) 액세스 토큰 → 프로필 조회 (POST https://kapi.kakao.com/v2/user/me)
     *  3) 우리 DB 에 이미 있으면 그 사용자 반환, 없으면 신규 생성 후 반환
     */
    @Transactional
    public User 카카오소셜로그인(String code) {
        // 1. 인가 코드로 액세스 토큰 발급
        UserResponse.OAuthToken oauthToken = 카카오액세스토큰발급(code);

        // 2. 액세스 토큰으로 카카오 프로필 조회
        UserResponse.KakaoProfile kakaoProfile = 카카오프로필조회(oauthToken.getAccessToken());

        // 3. 기존 회원 조회 or 신규 가입
        return 카카오사용자생성또는조회(kakaoProfile);
    }

    /**
     * 카카오 프로필을 우리 User 도메인에 맞게 조회/생성한다.
     *
     * [동작 흐름]
     *  1) "닉네임_카카오ID" 로 고유한 username 을 만든다.
     *  2) DB 에 이미 있으면 그 User 반환.
     *  3) 없으면 임시 비밀번호(환경변수 tenco.key)를 BCrypt 해싱해 넣고,
     *     provider=KAKAO 로 Entity 를 만들어 저장한 뒤 반환.
     *  4) 카카오 프로필 이미지가 있으면 외부 URL 그대로 profileImage 에 저장.
     */
    private User 카카오사용자생성또는조회(UserResponse.KakaoProfile kakaoProfile) {
        // 1. 고유 username 생성
        String username = kakaoProfile.getProperties().getNickname() + "_" + kakaoProfile.getId();
        log.debug("카카오 username 생성 : {}", username);

        // 2. 기존 회원 여부 확인
        User user = 사용자이름조회(username);
        if (user != null) {
            log.debug("이미 가입된 카카오 사용자");
            return user;
        }

        // 3. 신규 가입 처리
        log.debug("카카오 신규 사용자 → 자동 가입");
        User newUser = User.builder()
                .username(username)
                // 임시 비밀번호도 BCrypt 해싱 (DB NOT NULL 제약 대응)
                .password(passwordEncoder.encode(tencoKey))
                // 플랫폼 이메일 충돌 방지용 가짜 이메일 (UNIQUE 제약 대응)
                .email(username + "@kakao.com")
                .provider(OAuthProvider.KAKAO)
                .build();

        // 4. 프로필 이미지가 있으면 URL 그대로 저장
        String profileImage = kakaoProfile.getProperties().getThumbnailImage();
        if (profileImage != null && !profileImage.isEmpty()) {
            newUser.setProfileImage(profileImage);
        }

        소셜회원가입(newUser);
        return newUser;
    }

    /**
     * 카카오 토큰 발급 API 호출.
     *
     * [요청 스펙]
     *  POST https://kauth.kakao.com/oauth/token
     *  Content-Type : application/x-www-form-urlencoded
     *  Body :
     *    grant_type=authorization_code
     *    client_id=...
     *    redirect_uri=http://localhost:8080/user/kakao
     *    code=...
     *    client_secret=...
     */
    private UserResponse.OAuthToken 카카오액세스토큰발급(String code) {
        log.debug("카카오 토큰 요청 code 수신");

        RestTemplate restTemplate = new RestTemplate();

        // 1. 헤더 구성
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        // 2. 바디 구성 (MultiValueMap → form-urlencoded 로 자동 변환)
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoClientId);
        params.add("redirect_uri", "http://localhost:8080/user/kakao");
        params.add("code", code);
        params.add("client_secret", kakaoSecretKey);

        // 3. HttpEntity 조립 후 요청
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        ResponseEntity<UserResponse.OAuthToken> response = restTemplate.exchange(
                "https://kauth.kakao.com/oauth/token",
                HttpMethod.POST,
                request,
                UserResponse.OAuthToken.class
        );

        // 4. 토큰 반환 (민감 값이므로 로그에 찍지 말 것)
        log.debug("카카오 토큰 수신 완료");
        return response.getBody();
    }

    /**
     * 카카오 프로필 조회 API 호출.
     *
     * [요청 스펙]
     *  POST https://kapi.kakao.com/v2/user/me
     *  Authorization : Bearer &lt;access_token&gt;
     */
    private UserResponse.KakaoProfile 카카오프로필조회(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<UserResponse.KakaoProfile> response = restTemplate.exchange(
                "https://kapi.kakao.com/v2/user/me",
                HttpMethod.POST,
                request,
                UserResponse.KakaoProfile.class
        );

        log.debug("카카오 프로필 수신 완료");
        return response.getBody();
    }

    // =========================================================================
    // 포인트
    // =========================================================================

    /**
     * 포인트 충전 (테스트/학습용).
     *
     * [동작 흐름]
     *  1) 사용자 조회
     *  2) 도메인 메서드 chargePoint(amount) 로 검증+덧셈
     *  3) save() 호출 (더티 체킹이 있으므로 필수는 아님, 반환값 용도로 사용)
     */
    @Transactional
    public User 포인트충전(Long userId, Integer amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception404("사용자를 찾을 수 없습니다"));
        user.chargePoint(amount);
        return userRepository.save(user);
    }

    // =========================================================================
    // private helpers
    // =========================================================================

    /**
     * 프로필 이미지가 존재하면 검증 후 저장하고 파일명을 반환. 없으면 null.
     *
     * [동작 흐름]
     *  1) 파일이 없거나 비어있으면 바로 null
     *  2) 이미지가 맞는지 검증 (Content-Type)
     *  3) 디스크에 저장 → 파일명 반환
     *  4) 저장 중 오류는 Exception400 으로 변환
     */
    private String saveProfileImageIfPresent(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (!FileUtil.isImageFile(file)) {
            throw new Exception400("이미지 파일만 업로드 가능합니다");
        }
        try {
            return FileUtil.saveFile(file, FileUtil.IMAGES_DIR);
        } catch (IOException e) {
            throw new Exception400("파일 저장에 실패했습니다: " + e.getMessage());
        }
    }
}
