package ca.utoronto.lms.auth.service;

import ca.utoronto.lms.auth.dto.TokenDTO;
import ca.utoronto.lms.auth.mapper.UserMapper;
import ca.utoronto.lms.auth.model.User;
import ca.utoronto.lms.auth.repository.UserRepository;
import ca.utoronto.lms.auth.security.TokenGenerator;
import ca.utoronto.lms.shared.dto.UserDTO;
import ca.utoronto.lms.shared.dto.UserDetailsDTO;
import ca.utoronto.lms.shared.exception.ForbiddenException;
import ca.utoronto.lms.shared.exception.NotFoundException;
import ca.utoronto.lms.shared.security.SecurityUtils;
import ca.utoronto.lms.shared.service.BaseService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class UserService extends BaseService<User, UserDetailsDTO, Long> {
    private final UserRepository repository;
    private final UserMapper mapper;
    private final UserDetailsService userDetailsService;
    private final TokenGenerator tokenGenerator;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository repository,
            UserMapper mapper,
            UserDetailsService userDetailsService,
            TokenGenerator tokenGenerator,
            AuthenticationManager authenticationManager,
            PasswordEncoder passwordEncoder) {
        super(repository, mapper);
        this.repository = repository;
        this.mapper = mapper;
        this.userDetailsService = userDetailsService;
        this.tokenGenerator = tokenGenerator;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetailsDTO save(UserDetailsDTO userDetailsDTO) {
        userDetailsDTO.setPassword(passwordEncoder.encode(userDetailsDTO.getPassword()));
        userDetailsDTO.setAccountNonExpired(true);
        userDetailsDTO.setAccountNonLocked(true);
        userDetailsDTO.setCredentialsNonExpired(true);
        userDetailsDTO.setEnabled(true);
        return super.save(userDetailsDTO);
    }

    public UserDetailsDTO update(UserDetailsDTO userDetailsDTO) {
        User existingUser =
                repository
                        .findById(userDetailsDTO.getId())
                        .orElseThrow(() -> new NotFoundException("User not found"));
        if (userDetailsDTO.getUsername() != null) {
            existingUser.setUsername(userDetailsDTO.getUsername());
        }
        if (userDetailsDTO.getPassword() != null) {
            existingUser.setPassword(passwordEncoder.encode(userDetailsDTO.getPassword()));
        }

        return this.mapper.toDTO(this.repository.save(existingUser));
    }

    public List<UserDTO> findByIdPublic(Set<Long> id) {
        List<User> users = (List<User>) this.repository.findAllById(id);
        if (users.isEmpty()) {
            throw new NotFoundException("User id not found");
        }
        return this.mapper.userToUserDTOList(users);
    }

    public UserDetailsDTO findByUsername(String username) throws UsernameNotFoundException {
        if (!SecurityUtils.getUsername().equals(username)
                && !SecurityUtils.hasAuthority(SecurityUtils.ROLE_ADMIN)) {
            throw new ForbiddenException("You are not allowed to view this user's details");
        }

        return (UserDetailsDTO) userDetailsService.loadUserByUsername(username);
    }

    public Long findIdByUsername(String username) {
        return this.repository
                .findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Username not found"))
                .getId();
    }

    public TokenDTO login(UserDetailsDTO userDetailsDTO) {
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(
                        userDetailsDTO.getUsername(), userDetailsDTO.getPassword());
        Authentication authentication = authenticationManager.authenticate(token);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetails userDetails =
                userDetailsService.loadUserByUsername(userDetailsDTO.getUsername());
        String jwt = tokenGenerator.generateToken(userDetails);
        return new TokenDTO(jwt);
    }
}
