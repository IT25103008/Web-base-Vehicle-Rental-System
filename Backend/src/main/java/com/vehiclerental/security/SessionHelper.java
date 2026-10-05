package com.vehiclerental.security;

import com.vehiclerental.dao.UserDao;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Everything the JSON sign-in has to do by hand that a form login would do
 * automatically: start a fresh session id (so a session id planted before
 * sign-in is worthless afterwards), store the security context, and record
 * the session so it can be ended from elsewhere.
 */
@Component
public class SessionHelper {

    private final SecurityContextRepository contextRepository;
    private final SessionRegistry sessionRegistry;
    private final UserDao userDao;

    public SessionHelper(SecurityContextRepository contextRepository, SessionRegistry sessionRegistry, UserDao userDao) {
        this.contextRepository = contextRepository;
        this.sessionRegistry = sessionRegistry;
        this.userDao = userDao;
    }

    public void signIn(AppUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            request.changeSessionId();          // defeats session fixation
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        sessionRegistry.registerNewSession(request.getSession(true).getId(), principal);
    }

    /** Ends every session of this user except the one making the request (null ends them all). */
    public int endOtherSessions(int userId, HttpServletRequest request) {
        HttpSession current = request == null ? null : request.getSession(false);
        String keep = current == null ? null : current.getId();
        int ended = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof AppUserPrincipal p && p.getUserId() == userId) {
                for (SessionInformation info : sessionRegistry.getAllSessions(p, false)) {
                    if (!info.getSessionId().equals(keep)) {
                        info.expireNow();
                        ended++;
                    }
                }
            }
        }
        return ended;
    }

    /** Signed-in sessions this user has open right now. */
    public int countSessions(int userId) {
        int n = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof AppUserPrincipal p && p.getUserId() == userId) {
                n += sessionRegistry.getAllSessions(p, false).size();
            }
        }
        return n;
    }

    /** Re-reads the person from the database so the session reflects edits (name, 2FA...). */
    public void refreshPrincipal(int userId, HttpServletRequest request, HttpServletResponse response) {
        userDao.findById(userId).ifPresent(u -> {
            AppUserPrincipal fresh = new AppUserPrincipal(u);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                fresh, null, fresh.getAuthorities()));
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);
        });
    }
}
