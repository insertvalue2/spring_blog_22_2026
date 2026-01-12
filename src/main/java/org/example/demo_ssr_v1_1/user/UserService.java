package org.example.demo_ssr_v1_1.user;

import lombok.RequiredArgsConstructor;
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


@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    // 비밀번호 암호화를 위한 PasswordEncoder 주입
    private final PasswordEncoder passwordEncoder;

    @Value("${oauth.kakao.client-id}")
    private String kakaoClientId;

    @Value("${oauth.kakao.secret}")
    private String kakaoSecretKey;

    @Value("${tenco.key}")
    private String tencoKey;

    @Transactional
    public User 회원가입(UserRequest.JoinDTO joinDTO) {
        // 1. 유효성 검사
        joinDTO.validate();

        // 2. 사용자명 중복 체크
        // Optional의 isPresent(): 값이 있으면 true, 없으면 false
        if (userRepository.findByUsername(joinDTO.getUsername()).isPresent()) {
            throw new Exception400("이미 존재하는 사용자 이름입니다");
        }

        // 2-1. 이메일 중복 체크
        // 회원가입 시 이메일이 이미 등록되어 있는지 확인
        if (userRepository.findByEmail(joinDTO.getEmail()).isPresent()) {
            throw new Exception400("이미 등록된 이메일입니다");
        }

        // 3. 프로필 이미지 저장 (선택사항)
        // 중요: 프로필 이미지는 필수가 아닌 선택사항입니다!
        // 사용자가 이미지를 업로드하지 않아도 회원가입은 정상적으로 진행됩니다.
        // 
        // 파일 업로드 처리 흐름:
        // 1) joinDTO.getProfileImage()가 null이거나 비어있으면 → 이미지 없이 회원가입 진행
        // 2) 파일이 있으면 → 파일 검증 → 파일 저장 → 파일명을 DB에 저장
        String profileImageFilename = null;  // 초기값은 null (이미지 없음)
        
        // 파일이 업로드되었는지 확인
        // MultipartFile의 isEmpty() 메서드: 파일이 없거나 크기가 0이면 true
        if (joinDTO.getProfileImage() != null && !joinDTO.getProfileImage().isEmpty()) {
            try {
                // 3-1. 이미지 파일인지 검증
                // Content-Type이 "image/"로 시작하는지 확인 (예: image/jpeg, image/png)
                if (!FileUtil.isImageFile(joinDTO.getProfileImage())) {
                    throw new Exception400("이미지 파일만 업로드 가능합니다");
                }
                
                // 3-2. 파일을 서버 디스크에 저장
                // FileUtil.saveFile() 메서드가 하는 일:
                // - UUID를 사용하여 고유한 파일명 생성 (중복 방지)
                // - "images/" 디렉토리에 파일 저장
                // - 저장된 파일명 반환 (예: "abc123-456-789-profile.jpg")
                profileImageFilename = FileUtil.saveFile(joinDTO.getProfileImage(), FileUtil.IMAGES_DIR);
                
                // 3-3. profileImageFilename에는 저장된 파일명이 들어있음
                // 이 파일명을 DB의 user_tb.profile_image 컬럼에 저장할 예정
            } catch (IOException e) {
                // 파일 저장 중 오류 발생 시 (예: 디스크 공간 부족, 권한 없음)
                throw new Exception400("파일 저장에 실패했습니다: " + e.getMessage());
            }
        }
        // 파일이 없으면 profileImageFilename은 null로 유지됨
        // → DB에 null로 저장되어 "프로필 이미지 없음" 상태가 됨

        // 4. 비밀번호 암호화 처리
        // 회원가입 요청자가 제출한 password를 BCrypt로 암호화
        // BCrypt는 단방향 해싱 알고리즘이므로 복호화 불가능
        // 검증 시에는 passwordEncoder.matches() 메서드 사용
        String hashPwd = passwordEncoder.encode(joinDTO.getPassword());
        
        // 5. DTO를 엔티티로 변환 (암호화된 비밀번호와 파일명 포함)
        User user = joinDTO.toEntity(profileImageFilename);
        // 암호화된 비밀번호로 교체
        user.setPassword(hashPwd);

        // 6. 기본 권한 추가 (일반 사용자, 생성자에서 넣고 있음)
        // 회원가입 시 기본적으로 USER 역할을 부여합니다.
        // user.addRole(Role.USER);

        // 7. JpaRepository의 save() 메서드: 엔티티 저장 (INSERT)
        return userRepository.save(user);
    }

    /**
     * 로그인 처리
     * 
     * 비즈니스 로직:
     * 1. 유효성 검사 (DTO에서 처리)
     * 2. 사용자명으로 사용자 조회 (+ 역할 정보까지 함께 조회)
     * 3. 비밀번호 검증 (BCrypt matches 메서드 사용)
     * 4. 로그인 성공/실패 처리
     * 
     * 비밀번호 검증 방식:
     * - DB에 저장된 비밀번호는 BCrypt로 암호화된 해시값
     * - 사용자가 입력한 평문 비밀번호와 암호화된 비밀번호를 비교
     * - passwordEncoder.matches(평문비밀번호, 암호화된비밀번호) 사용
     * 
     * 트랜잭션:
     * - 읽기 전용 트랜잭션 (readOnly = true)
     * - 조회만 하므로 읽기 전용으로 설정
     * 
     * @param loginDTO 로그인 DTO
     * @return 로그인한 사용자 엔티티
     * @throws Exception400 로그인 실패 시 (사용자명 또는 비밀번호 불일치)
     */
    @Transactional(readOnly = true)
    public User 로그인(UserRequest.LoginDTO loginDTO) {
        // 1. 유효성 검사
        loginDTO.validate();

        // 2. 사용자명으로 사용자 조회 (+ 역할 정보까지 함께 조회)
        //    findByUsernameWithRoles():
        //    - User 엔티티와 roles 컬렉션을 LEFT JOIN FETCH로 한 번에 가져옵니다.
        //    - 세션에 저장된 User에서 isAdmin(), getRoleDisplay() 등을 사용할 수 있습니다.
        //    - 비밀번호는 DB에서 직접 비교하지 않고 애플리케이션에서 검증
        User user = userRepository.findByUsernameWithRoles(loginDTO.getUsername())
                .orElse(null);

        // 3. 사용자가 존재하지 않으면 로그인 실패
        if (user == null) {
            throw new Exception400("사용자명 또는 비밀번호가 올바르지 않습니다");
        }

        // 4. 비밀번호 검증 (BCrypt matches 메서드 사용)
        // passwordEncoder.matches(평문비밀번호, 암호화된비밀번호)
        // - 사용자가 입력한 평문 비밀번호와 DB에 저장된 암호화된 비밀번호를 비교
        // - BCrypt 알고리즘이 자동으로 Salt를 고려하여 비교
        // - 일치하면 true, 불일치하면 false 반환
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new Exception400("사용자명 또는 비밀번호가 올바르지 않습니다");
        }

        // 5. 로그인 성공: 사용자 엔티티 반환
        return user;
    }

    /**
     * 회원정보 수정 화면용 조회 (인가 검사 포함)
     * 
     * 인가 검사:
     * - 자기 자신의 정보만 조회 가능
     * - isOwner() 메서드로 소유자 확인
     * 
     * @param userId 현재 로그인한 사용자 ID
     * @return 사용자 엔티티
     * @throws Exception404 사용자가 없을 경우
     * @throws Exception403 수정 권한이 없을 경우
     */
    @Transactional(readOnly = true)
    public User 회원정보수정화면(Long userId) {
        // 세션의 사용자 ID로 회원정보 조회
        // JpaRepository의 findById()는 Optional<User>를 반환
        // orElseThrow(): Optional이 비어있으면 예외 발생, 있으면 User 객체 반환
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception404("사용자를 찾을 수 없습니다"));

        // 자기 자신의 정보만 수정 가능한지 확인
        if (!user.isOwner(userId)) {
            throw new Exception403("회원정보 수정 권한이 없습니다");
        }

        return user;
    }

    /**
     * 회원정보 수정 처리 (프로필 이미지 포함)
     * 
     * 더티 체킹 (Dirty Checking):
     * - 엔티티를 조회한 후 필드 값을 변경
     * - 트랜잭션이 끝날 때 자동으로 UPDATE 쿼리 실행
     * - save()를 호출해도 되지만, @Transactional이 있으면 자동으로 UPDATE 됨
     * 
     * 세션 갱신:
     * - 수정된 사용자 정보를 세션에 다시 저장
     * - Controller에서 처리하도록 엔티티 반환
     * 
     * @param updateDTO 회원정보 수정 DTO (프로필 이미지 포함)
     * @param userId 현재 로그인한 사용자 ID
     * @return 수정된 사용자 엔티티
     * @throws Exception400 파일 저장 실패 시
     * @throws Exception404 사용자가 없을 경우
     * @throws Exception403 수정 권한이 없을 경우
     */
    @Transactional
    public User 회원정보수정(UserRequest.UpdateDTO updateDTO, Long userId) {
        // 1. 수정하려는 회원정보 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception404("사용자를 찾을 수 없습니다"));

        // 2. 인가 검사: 자기 자신의 정보만 수정 가능한지 확인
        if (!user.isOwner(userId)) {
            throw new Exception403("회원정보 수정 권한이 없습니다");
        }

        // 2-1. 소셜 로그인 사용자는 이 서비스에서 회원정보를 수정할 수 없음
        // - 소셜 사용자는 비밀번호, 이메일, 프로필 이미지를 외부 서비스에서 관리
        // - 애플리케이션에서는 조회와 일부 표시만 담당
        if (!user.isLocal()) {
            throw new Exception403("소셜 로그인 사용자는 회원정보를 수정할 수 없습니다");
        }

        // 3. 유효성 검사
        updateDTO.validate();

        // 4. 비밀번호 암호화 처리
        // 회원정보 수정 시 입력한 비밀번호를 BCrypt로 암호화
        String hashPwd = passwordEncoder.encode(updateDTO.getPassword());
        // DTO의 비밀번호를 암호화된 비밀번호로 교체
        updateDTO.setPassword(hashPwd);

        // 5. 프로필 이미지 처리 (로컬 로그인 사용자만 허용)
        // - 소셜 로그인 사용자는 프로필 이미지를 외부 URL(예: 카카오 프로필)로 사용하므로,
        //   로컬 파일 업로드/삭제 로직을 태우지 않는다.
        //
        // 프로필 이미지 수정 시나리오 (로컬 사용자 한정):
        // 1) 새 이미지 업로드 → 새 이미지 저장 → 기존 이미지 삭제 → DB 업데이트
        // 2) 이미지 업로드 안 함 → 기존 이미지 유지 → DB 변경 없음
        String oldProfileImage = user.getProfileImage();  // 기존 이미지 파일명 또는 URL

        if (user.isLocal()) {
            // 로컬 로그인 사용자만 파일 업로드 처리
            // 새 이미지가 업로드되었는지 확인
            if (updateDTO.getProfileImage() != null && !updateDTO.getProfileImage().isEmpty()) {
                try {
                    // 이미지 파일인지 검증
                    if (!FileUtil.isImageFile(updateDTO.getProfileImage())) {
                        throw new Exception400("이미지 파일만 업로드 가능합니다");
                    }

                    // 새 이미지를 서버 디스크에 저장
                    String newProfileImageFilename = FileUtil.saveFile(updateDTO.getProfileImage(), FileUtil.IMAGES_DIR);

                    // DTO에 새 파일명 설정 (나중에 엔티티 업데이트 시 사용)
                    updateDTO.setProfileImageFilename(newProfileImageFilename);

                    // 기존 이미지 파일 삭제 (디스크 공간 절약)
                    if (oldProfileImage != null && !oldProfileImage.isEmpty()) {
                        FileUtil.deleteFile(oldProfileImage, FileUtil.IMAGES_DIR);
                    }
                } catch (IOException e) {
                    throw new Exception400("파일 저장에 실패했습니다: " + e.getMessage());
                }
            } else {
                // 새 이미지가 업로드되지 않았으면 기존 이미지 파일명 유지
                // → DB의 profile_image 컬럼 값이 변경되지 않음
                updateDTO.setProfileImageFilename(oldProfileImage);
            }
        } else {
            // 소셜 로그인 사용자는 프로필 이미지 업로드를 허용하지 않으므로,
            // DTO의 profileImageFilename 을 현재 값으로 유지시키고 파일 삭제를 시도하지 않는다.
            updateDTO.setProfileImageFilename(oldProfileImage);
        }

        // 6. 더티 체킹을 활용한 수정 처리
        // 엔티티의 상태 값 변경
        user.update(updateDTO);

        // 7. 변경된 엔티티 저장 (더티 체킹)
        // 참고: @Transactional이 있으면 save() 없이도 자동으로 UPDATE 됨
        // 하지만 명시적으로 save()를 호출하는 것이 더 명확함
        User updateUser = userRepository.save(user);

        // 8. 수정된 사용자 정보 반환 (Controller에서 세션 갱신용)
        return updateUser;
    }

    /**
     * 프로필 이미지 삭제 처리
     * 
     * 비즈니스 로직:
     * 1. 회원정보 조회
     * 2. 인가 검사 (소유자 확인)
     * 3. 프로필 이미지 파일 삭제
     * 4. DB에서 프로필 이미지 필드 null로 업데이트
     * 
     * @param userId 현재 로그인한 사용자 ID
     * @return 프로필 이미지가 삭제된 사용자 엔티티
     * @throws Exception404 사용자가 없을 경우
     * @throws Exception403 삭제 권한이 없을 경우
     */
    @Transactional
    public User 프로필이미지삭제(Long userId) {
        // 1. 회원정보 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception404("사용자를 찾을 수 없습니다"));

        // 2. 인가 검사: 자기 자신의 정보만 삭제 가능한지 확인
        if (!user.isOwner(userId)) {
            throw new Exception403("프로필 이미지 삭제 권한이 없습니다");
        }

        // 3. 프로필 이미지 파일 삭제 (있으면)
        String profileImage = user.getProfileImage();
        if (profileImage != null && !profileImage.isEmpty()) {
            try {
                FileUtil.deleteFile(profileImage, FileUtil.IMAGES_DIR);
            } catch (IOException e) {
                // 파일 삭제 실패해도 DB는 업데이트 (파일이 이미 없을 수도 있음)
                // 로그만 남기고 계속 진행
                System.err.println("프로필 이미지 파일 삭제 실패: " + e.getMessage());
            }
        }

        // 4. DB에서 프로필 이미지 필드 null로 업데이트
        user.setProfileImage(null);
        
        // 5. 변경된 엔티티 저장 (더티 체킹)
        return userRepository.save(user);
    }

    /**
     * 사용자명으로 조회 (소셜 로그인용)
     */
    @Transactional(readOnly = true)
    public User 사용자이름조회(String username) {
        // Optional 처리: 없으면 null 반환
        return userRepository.findByUsername(username).orElse(null);
    }


    public void 소셜회원가입(User user) {
        userRepository.save(user);   // 그냥 저장만 하면 됨
    }


    /**
     * 카카오 프로필 정보로 사용자 생성 또는 조회
     * 
     * 비즈니스 로직:
     * 1. 고유한 username 생성 (닉네임_카카오ID)
     * 2. 기존 회원 여부 확인
     * 3. 신규 회원이면 자동 회원가입 처리
     * 4. 사용자 엔티티 반환
     * 
     * @param kakaoProfile 카카오 프로필 정보
     * @return 사용자 엔티티
     */
    private User 카카오사용자생성또는조회(UserResponse.KakaoProfile kakaoProfile) {
        // 고유한 username 생성 (중복 방지용: 닉네임_카카오ID)
        String username = kakaoProfile.getProperties().getNickname() + "_" + kakaoProfile.getId();
        System.out.println("4. 고유한 username : " + username);

        // 회원 가입 여부 확인
        User user = 사용자이름조회(username);

        if (user == null) {
            System.out.println("4. 기존 회원이 아니므로 자동 회원가입을 진행합니다.");

            // 회원가입용 엔티티 생성
            User newUser = User.builder()
                    .username(username)
                    // 임시 비밀번호도 일반 회원가입과 동일하게 BCrypt 해싱 처리
                    .password(passwordEncoder.encode(tencoKey)) // 임시 비밀번호 (DB Not Null 제약 대응, 해싱 저장)
                    .email(username + "@kakao.com") // 임의의 이메일 (선택사항)
                    .provider(OAuthProvider.KAKAO) // ★ 로그인 경로 설정
                    .build();

            // 프로필 이미지가 있다면 설정
            String profileImage = kakaoProfile.getProperties().getThumbnailImage();
            if (profileImage != null && !profileImage.isEmpty()) {
                newUser.setProfileImage(profileImage); // URL 그대로 저장 (외부 링크)
            }

            소셜회원가입(newUser);
            user = newUser; // !! 필수
        } else {
            System.out.println("4. 이미 가입된 회원입니다. 로그인을 진행합니다.");
        }

        return user;
    }

    /**
     * 카카오 인가 코드로 액세스 토큰 발급
     * 
     * @param code 카카오 인가 코드
     * @return OAuth 액세스 토큰 정보
     */
    private UserResponse.OAuthToken 카카오액세스토큰발급(String code) {
        System.out.println("1. Kakao 인가 코드 수신 완료: " + code);

        RestTemplate restTemplate = new RestTemplate();

        // 헤더 생성 (MIME 타입 설정)
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");
        // application/x-www-form-urlencoded 것은 데이터를 key1=value1&key2=value2 형태(HTML Form 태그 방식)로 보내겠다 의미 입니다.

        // 바디 생성 (MultiValueMap 사용)
        // RestTemplate은 바디(Body)에 MultiValueMap 타입의 객체가 들어오면, "아! 이걸 폼 데이터(key=value) 형식으로 변환해서 보내야겠구나"라고 인식하고 자동으로 변환
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoClientId);
        params.add("redirect_uri", "http://localhost:8080/user/kakao");
        params.add("code", code);
        params.add("client_secret", kakaoSecretKey);

        // 헤더 + 바디 결합
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        // 요청 및 응답 받기
        ResponseEntity<UserResponse.OAuthToken> response = restTemplate.exchange(
                "https://kauth.kakao.com/oauth/token",
                HttpMethod.POST,
                request,
                UserResponse.OAuthToken.class
        );

        UserResponse.OAuthToken oauthToken = response.getBody();
        System.out.println("2. Access Token 발급 완료: " + oauthToken.getAccessToken());

        return oauthToken;
    }

    /**
     * 카카오 액세스 토큰으로 프로필 정보 조회
     * 
     * @param accessToken 카카오 액세스 토큰
     * @return 카카오 프로필 정보
     */
    private UserResponse.KakaoProfile 카카오프로필조회(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();

        // 헤더 생성 (Bearer + Access Token)
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        // 요청 엔티티 생성 (바디 없음)
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // 요청 및 응답 받기
        ResponseEntity<UserResponse.KakaoProfile> response = restTemplate.exchange(
                "https://kapi.kakao.com/v2/user/me",
                HttpMethod.POST,
                request,
                UserResponse.KakaoProfile.class
        );

        UserResponse.KakaoProfile kakaoProfile = response.getBody();
        System.out.println("3. 카카오 프로필 정보 수신 완료: " + kakaoProfile);

        return kakaoProfile;
    }

    /**
     * 카카오 소셜 로그인 처리
     * 
     * 비즈니스 로직:
     * 1. 인가 코드로 액세스 토큰 발급 요청
     * 2. 액세스 토큰으로 카카오 프로필 정보 조회
     * 3. 프로필 정보로 사용자 생성 또는 조회
     * 4. 로그인 처리 (사용자 엔티티 반환)
     * 
     * 트랜잭션:
     * - 기본 트랜잭션 (읽기/쓰기)
     * - 회원가입 시 INSERT 쿼리 실행 가능
     * 
     * @param code 카카오 인가 코드
     * @return 로그인한 사용자 엔티티
     */
    @Transactional
    public User 카카오소셜로그인(String code) {
        // 1. 인가 코드로 액세스 토큰 발급
        UserResponse.OAuthToken oauthToken = 카카오액세스토큰발급(code);

        // 2. 액세스 토큰으로 카카오 프로필 정보 조회
        UserResponse.KakaoProfile kakaoProfile = 카카오프로필조회(oauthToken.getAccessToken());

        // 3. 프로필 정보로 사용자 생성 또는 조회
        User user = 카카오사용자생성또는조회(kakaoProfile);

        // 4. 로그인 처리 (사용자 엔티티 반환)
        return user;
    }

    /**
     * 포인트 충전 처리 (테스트용)
     * 
     * 비즈니스 로직:
     * 1. 사용자 조회
     * 2. 포인트 충전
     * 3. 사용자 정보 저장
     * 
     * 트랜잭션:
     * - 기본 트랜잭션 (읽기/쓰기)
     * - 포인트 충전 후 UPDATE 쿼리 실행
     * 
     * @param userId 사용자 ID
     * @param amount 충전할 포인트
     * @return 포인트가 충전된 사용자 엔티티
     * @throws Exception404 사용자가 없을 경우
     * @throws Exception400 포인트가 0 이하일 경우
     */
    @Transactional
    public User 포인트충전(Long userId, Integer amount) {
        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception404("사용자를 찾을 수 없습니다"));

        // 2. 포인트 충전
        user.chargePoint(amount);

        // 3. 변경된 엔티티 저장 (더티 체킹)
        return userRepository.save(user);
    }
}

