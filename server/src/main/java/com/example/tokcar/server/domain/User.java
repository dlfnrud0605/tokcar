package com.example.tokcar.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 계정. 로그인 아이디는 id 로 쓰지 않는다(바꿀 수 있고, 가족에게 보이지 않게). */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String loginId;

    /** BCrypt. 원문은 저장하지 않는다. */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    /** +821012345678. 번호 하나당 아이디 하나. */
    @Column(name = "phone_e164", nullable = false, unique = true, length = 16)
    private String phoneE164;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(length = 60)
    private String statusMsg;

    /** 프로필 사진 파일 위치(로컬은 서버 폴더, AWS 는 S3 키). 없으면 null. */
    @Column(length = 200)
    private String profileImageKey;

    /** 연속으로 틀린 횟수. LOCK_N 이상이면 잠김. */
    @Column(nullable = false)
    private int failedLogins;

    @Column(nullable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String loginId, String passwordHash, String phoneE164, String country, String name) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.phoneE164 = phoneE164;
        this.country = country;
        this.name = name;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public String getStatusMsg() { return statusMsg; }
    public String getProfileImageKey() { return profileImageKey; }
    public int getFailedLogins() { return failedLogins; }

    public void setStatusMsg(String statusMsg) { this.statusMsg = statusMsg; }
    public void setFailedLogins(int failedLogins) { this.failedLogins = failedLogins; }
}
