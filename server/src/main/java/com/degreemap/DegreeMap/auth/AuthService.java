package com.degreemap.DegreeMap.auth;

import com.degreemap.DegreeMap.auth.jwt.AuthResponseDto;
import com.degreemap.DegreeMap.auth.jwt.JwtGenerator;
import com.degreemap.DegreeMap.users.User;
import com.degreemap.DegreeMap.users.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final JpaUserDetailsService userDetailsService;
    private final JwtGenerator jwtGenerator;
    private final UserRepository userRepository;

    public AuthService(PasswordEncoder passwordEncoder, JpaUserDetailsService userDetailsService, JwtGenerator jwtGenerator, UserRepository userRepository) {
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.jwtGenerator = jwtGenerator;
        this.userRepository = userRepository;
    }

    /**
     * Attempts to authenticate a user based on a given email and password.
     * @param email email to authenticate with
     * @param password password to authenticate with
     * @return the UserDetails of that user.
     * @throws ResponseStatusException in two cases: <ul>
     *     <li>
     *         If a user with the given email is not found, throws with a 404 NOT FOUND
     *     </li>
     *     <li>
     *         If a user with the given email is found, but the given password is incorrect,
     *         throws with a 401 UNAUTHORIZED
     *     </li>
     * </ul>
     */
    private UserDetails authenticateUser(String email, String password) {
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (!passwordEncoder.matches(password, userDetails.getPassword())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Incorrect password");
            }

            return userDetails;

        } catch (UsernameNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Attempts to register a new user with the given email and password.
     * @param email email to register with
     * @param password password to register with
     * @return the UserDetails of the new user.
     * @throws ResponseStatusException with a 409 CONFLICT if a user with the given email already exists.
     */
    private UserDetails registerUser(String email, String password) {
        try {
            userDetailsService.loadUserByUsername(email);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Registration failed: Duplicate email '"+email+"'. Please use a different email.");

        } catch (UsernameNotFoundException e) {
            // User doesn't exist, so we register a new one
            User user = new User(
                    email,
                    passwordEncoder.encode(password)
            );
            userRepository.save(user);
            return userDetailsService.loadUserByUsername(email);
        }
    }

    private void addRefreshTokenCookieToResponse(UserDetails userDetails,
                                                 HttpServletResponse response) {

        String refreshToken = jwtGenerator.generateRefreshToken(userDetails);

        Cookie cookie = new Cookie("refreshToken", refreshToken);

        // Makes cookie inaccessible to client-side (java)scripts.
        // Helps mitigate certain attacks, like cross-site scripting (XSS)
        cookie.setHttpOnly(true);

        // Makes it so the cookie is only sent to the client if it's being
        // requested over a secure HTTPS connection
        cookie.setSecure(true);

        // Cookie will last 15 days. Note that the refresh token is also set
        // to last 15 days; make sure they match.
        cookie.setMaxAge(15 * 24 * 60 * 60);

        // I have to set this explicitly because the login and register endpoints have different paths.
        // The login endpoint will set the cookie's path to /api/users, while the register
        // endpoint will set it to /api.
        // Cookies are (partially) identified by path; two cookies with the same name but different
        // paths will be treated as two different cookies. However, we want our refresh tokens (whether
        // they are obtained through registration or logging in) to be treated the same.
        // So, I just standardize the path here so the user will only have one refresh token cookie at a time.
        cookie.setPath("/api/users");

        response.addCookie(cookie);
    }

    private AuthResponseDto makeAccessTokenResponse(UserDetails userDetails) {
        String accessToken = jwtGenerator.generateAccessToken(userDetails);

        return new AuthResponseDto(
                accessToken,
                15 * 60, // 15 mins
                userDetails.getUsername()
        );
    }

    public AuthResponseDto getAccessTokenFromCredentials(String email, String password,
                                                         HttpServletResponse response) {

        UserDetails userDetails = authenticateUser(email, password);
        addRefreshTokenCookieToResponse(userDetails, response);
        return makeAccessTokenResponse(userDetails);
    }

    public AuthResponseDto registerUserAndGetAccessToken(String email, String password,
                                                         HttpServletResponse response) {

        UserDetails userDetails = registerUser(email, password);
        addRefreshTokenCookieToResponse(userDetails, response);
        return makeAccessTokenResponse(userDetails);
    }
}