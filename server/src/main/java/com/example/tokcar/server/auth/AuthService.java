package com.example.tokcar.server.auth;

import com.example.tokcar.server.chat.Views.LoginResult;
import com.example.tokcar.server.chat.Views.Me;
import com.example.tokcar.server.common.ApiException;
import com.example.tokcar.server.domain.User;
import com.example.tokcar.server.domain.UserRepository;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** 연속으로 이만큼 틀리면 잠긴다(앱의 FamilyServer.LOCK_N 과 같음). */
    public static final int LOCK_N = 5;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenStore tokens;

    public AuthService(UserRepository users, PasswordEncoder encoder, TokenStore tokens) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
    }

    /**
     * 아이디 · 비밀번호 확인. 틀리면 실패 횟수를 올리고 남은 시도를 알려 준다.
     * 없는 아이디는 남은 시도 없이 같은 오류만(실패 횟수를 셀 계정이 없음).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult login(String loginId, String password) {
        if (loginId == null || password == null) throw ApiException.badRequest("아이디와 비밀번호를 넣어 주세요");
        User u = users.findByLoginId(loginId.strip().toLowerCase(Locale.ROOT)).orElseThrow(AuthService::bad);
        if (u.getFailedLogins() >= LOCK_N) throw locked();
        if (!encoder.matches(password, u.getPasswordHash())) {
            u.setFailedLogins(u.getFailedLogins() + 1);
            int left = LOCK_N - u.getFailedLogins();
            if (left <= 0) throw locked();
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "아이디 또는 비밀번호가 맞지 않아요", left);
        }
        u.setFailedLogins(0);
        return new LoginResult(tokens.issue(u.getId()), Me.of(u));
    }

    @Transactional(readOnly = true)
    public Me me(long uid) {
        return users.findById(uid).map(Me::of).orElseThrow(ApiException::unauthorized);
    }

    private static ApiException bad() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "아이디 또는 비밀번호가 맞지 않아요");
    }

    private static ApiException locked() {
        return new ApiException(HttpStatus.LOCKED, "LOCKED", "비밀번호를 " + LOCK_N + "번 틀려서 잠겼어요");
    }
}
