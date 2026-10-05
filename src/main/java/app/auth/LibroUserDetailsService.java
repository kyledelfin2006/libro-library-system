package app.auth;

import app.user.entity.User;
import app.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class LibroUserDetailsService implements UserDetailsService{

    private final UserRepository userRepository;

    public LibroUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    @Override
    public UserDetails loadUserByUsername(String universityId) throws UsernameNotFoundException {
        User user = userRepository.findByUniversityId(universityId).orElseThrow
                (() -> new UsernameNotFoundException("User of that university id not found."));


        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUniversityId())
                .password(user.getPasswordHash())
                .roles(user.getUserRole().name())
                .build();

    }
}
