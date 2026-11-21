package com.nway.system.common.security;

import com.nway.system.common.security.LoginUser;

public class LoginUserContext {

    private static final ThreadLocal<LoginUser> CURRENT_USER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        CURRENT_USER.set(user);
    }

    public static LoginUser current() {
        return CURRENT_USER.get();
    }

    public static void clean() {
        CURRENT_USER.remove();
    }
}
