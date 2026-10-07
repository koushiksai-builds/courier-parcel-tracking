package edu.vitap.users;
import edu.vitap.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

record RegisterRequest(@NotBlank String name,@Email @NotBlank String email,@NotBlank @Size(min=8) String password){}
record UserResponse(Long id,String name,String email,Role role){static UserResponse from(AppUser u){return new UserResponse(u.getId(),u.getName(),u.getEmail(),u.getRole());}}

@RestController @RequestMapping("/api/auth")
class AuthApi {
 private final UserRepository users;private final PasswordEncoder encoder;
 AuthApi(UserRepository u,PasswordEncoder e){users=u;encoder=e;}
 @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
 UserResponse register(@Valid @RequestBody RegisterRequest r){String email=r.email().toLowerCase(Locale.ROOT);if(users.findByEmail(email).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered");return UserResponse.from(users.save(new AppUser(email,encoder.encode(r.password()),r.name(),Role.CUSTOMER)));}
 @GetMapping("/login") @PreAuthorize("isAuthenticated()") UserResponse login(Authentication a){return UserResponse.from(CurrentUser.get(users,a));}
}

@RestController @RequestMapping("/api/admin") @PreAuthorize("hasRole('ADMIN')")
class UserAdminApi {
 private final UserRepository users;private final PasswordEncoder encoder;
 UserAdminApi(UserRepository u,PasswordEncoder e){users=u;encoder=e;}
 @GetMapping("/users") List<UserResponse> users(){return users.findAll().stream().map(UserResponse::from).toList();}
 @PostMapping("/couriers") @ResponseStatus(HttpStatus.CREATED) UserResponse createCourier(@Valid @RequestBody RegisterRequest r){String email=r.email().toLowerCase(Locale.ROOT);if(users.findByEmail(email).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered");return UserResponse.from(users.save(new AppUser(email,encoder.encode(r.password()),r.name(),Role.COURIER)));}
}
