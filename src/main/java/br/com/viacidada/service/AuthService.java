package br.com.viacidada.service;

import br.com.viacidada.dto.AuthDTO;
import br.com.viacidada.entity.Role;
import br.com.viacidada.entity.Usuario;
import br.com.viacidada.exception.BusinessRuleException;
import br.com.viacidada.exception.ResourceNotFoundException;
import br.com.viacidada.repository.UsuarioRepository;
import br.com.viacidada.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthDTO.TokenResponse registrar(AuthDTO.RegisterRequest request) {
        if (usuarioRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BusinessRuleException("Ja existe um usuario cadastrado com o e-mail: " + request.getEmail());
        }

        Usuario novoUsuario = Usuario.builder()
                .nome(request.getNome())
                .email(request.getEmail().toLowerCase().trim())
                .senha(passwordEncoder.encode(request.getSenha()))
                .role(Role.ROLE_CIDADAO)
                .possuiMobilidadeReduzida(Boolean.TRUE.equals(request.getPossuiMobilidadeReduzida()))
                .build();

        Usuario salvo = usuarioRepository.save(novoUsuario);
        String token = jwtService.gerarToken(salvo);

        return AuthDTO.TokenResponse.builder()
                .token(token)
                .tipo("Bearer")
                .expiraEmMs(jwtService.getExpirationMs())
                .usuarioId(salvo.getId())
                .nome(salvo.getNome())
                .email(salvo.getEmail())
                .role(salvo.getRole().name())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthDTO.TokenResponse login(AuthDTO.LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getSenha())
        );

        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado"));

        String token = jwtService.gerarToken(usuario);

        return AuthDTO.TokenResponse.builder()
                .token(token)
                .tipo("Bearer")
                .expiraEmMs(jwtService.getExpirationMs())
                .usuarioId(usuario.getId())
                .nome(usuario.getNome())
                .email(usuario.getEmail())
                .role(usuario.getRole().name())
                .build();
    }
}
