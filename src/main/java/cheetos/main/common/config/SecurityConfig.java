package cheetos.main.common.config;

import cheetos.main.auth.AppProperties;
import cheetos.main.auth.CorsProperties;
import cheetos.main.auth.exception.RestAuthenticationEntryPoint;
import cheetos.main.auth.filter.TokenAuthenticationFilter;
import cheetos.main.auth.handler.OAuth2AuthenticationFailureHandler;
import cheetos.main.auth.handler.OAuth2AuthenticationSuccessHandler;
import cheetos.main.auth.handler.TokenAccessDeniedHandler;
import cheetos.main.auth.repository.OAuth2AuthorizationRequestBasedOnCookieRepository;
import cheetos.main.auth.service.CustomOAuth2UserService;
import cheetos.main.auth.token.AuthTokenProvider;
import cheetos.main.user.domain.Role;
import cheetos.main.user.repository.UserRefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    private final CorsProperties corsProperties;
    private final AppProperties appProperties;
    private final CustomOAuth2UserService oauth2UserService;
    private final AuthTokenProvider tokenProvider;
    private final TokenAccessDeniedHandler tokenAccessDeniedHandler;
    private final UserRefreshTokenRepository userRefreshTokenRepository;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .cors()
            .and()
                .sessionManagement() // ?????? ??????x
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
                .csrf().disable()
                .formLogin().disable()
                .httpBasic().disable()
                .exceptionHandling()
                .authenticationEntryPoint(new RestAuthenticationEntryPoint())
            .and()
                .authorizeRequests()
                .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                .antMatchers("/*").permitAll()
                .antMatchers("/api/**").hasAnyAuthority(Role.USER.toString())
                .antMatchers("/api/**/admin/**").hasAnyAuthority(Role.ADMIN.toString())
                .anyRequest().authenticated()
            .and()
                .oauth2Login() // OAuth2 ????????? ?????????
                .authorizationEndpoint()
                .baseUri("/oauth2/authorization")
                .authorizationRequestRepository(oAuth2AuthorizationRequestBasedOnCookieRepository())
            .and()
                .redirectionEndpoint()
                .baseUri("/*/oauth2/code/*")
            .and()
                .userInfoEndpoint() // ????????? ?????? ??? ????????? ????????? ????????????
                .userService(oauth2UserService)
            .and()
                .successHandler(oAuth2AuthenticationSuccessHandler())
                .failureHandler(oAuth2AuthenticationFailureHandler());

        // ????????? ????????? ????????? ??? ??????
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);   // AuthenticationManager
    }

    // authenticationManager??? Bean??? ??????
    @Override
    @Bean
    protected AuthenticationManager authenticationManager() throws Exception {
        return super.authenticationManager();
    }

    // ???????????? ????????? PasswordEncoder??? Bean??? ??????
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ?????? ?????? ??????
    @Bean
    public TokenAuthenticationFilter jwtAuthenticationFilter() {
        return new TokenAuthenticationFilter(tokenProvider);
    }

    // ?????? ?????? ?????? Repository
    // ?????? ????????? ???????????? ????????? ??? ?????????
    @Bean
    public OAuth2AuthorizationRequestBasedOnCookieRepository oAuth2AuthorizationRequestBasedOnCookieRepository() {
        return new OAuth2AuthorizationRequestBasedOnCookieRepository();
    }

    /*
     * Oauth ?????? ?????? ?????????
     * */
    @Bean
    public OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler() {
        return new OAuth2AuthenticationSuccessHandler(
                tokenProvider,
                appProperties,
                userRefreshTokenRepository,
                oAuth2AuthorizationRequestBasedOnCookieRepository()
        );
    }

    /*
     * Oauth ?????? ?????? ?????????
     * */
    @Bean
    public OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler() {
        return new OAuth2AuthenticationFailureHandler(oAuth2AuthorizationRequestBasedOnCookieRepository());
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource corsConfigSource = new UrlBasedCorsConfigurationSource();

        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedHeaders(Arrays.asList(corsProperties.getAllowedHeaders().split(",")));
        corsConfig.setAllowedMethods(Arrays.asList(corsProperties.getAllowedMethods().split(",")));
        corsConfig.setAllowedOrigins(Arrays.asList(corsProperties.getAllowedOrigins().split(",")));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(corsConfig.getMaxAge());

        corsConfigSource.registerCorsConfiguration("/**", corsConfig);
        return corsConfigSource;
    }
}