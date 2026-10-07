package edu.vitap.common;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
public final class CurrentUser {
    private CurrentUser(){}
    public static AppUser get(UserRepository users,Authentication auth){if(auth==null||auth.getName()==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return users.findByEmail(auth.getName()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));}
}
