package aa.web;

import aa.shibboleth.ShibbolethPreAuthenticatedProcessingFilter;
import aa.shibboleth.ShibbolethUserDetailService;
import aa.shibboleth.mock.MockShibbolethFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * Protect endpoints for the internal API with Shibboleth AbstractPreAuthenticatedProcessingFilter.
 * <p>
 * Protect the internal endpoint for EB with basic authentication.
 * <p>
 * Protect all other endpoints - except the public ones - with OAuth2 with support for both Authz and OIDC.
 * <p>
 * Do not protect public endpoints like /health, /info and /ServiceProviderConfig
 * <p>
 * Protect the /Me endpoint with an OAuth2 access_token associated with an User authentication
 * <p>
 * Protect the /Schema endpoint with an OAuth2 client credentials access_token
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfigurer {

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        //because Autowired this will end up in the global ProviderManager
        PreAuthenticatedAuthenticationProvider authenticationProvider = new PreAuthenticatedAuthenticationProvider();
        authenticationProvider.setPreAuthenticatedUserDetailsService(new ShibbolethUserDetailService());
        auth.authenticationProvider(authenticationProvider);
    }

    @Order(1)
    @Configuration
    public static class InternalSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {

        @Autowired
        private Environment environment;

        @Override
        public void configure(WebSecurity web) throws Exception {
            web.ignoring().antMatchers("/health", "/info");
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http
                .antMatcher("/internal/**")
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .and()
                .csrf()
                .requireCsrfProtectionMatcher(new CsrfProtectionMatcher())
                .and()
                .addFilterAfter(new CsrfTokenResponseHeaderBindingFilter(), CsrfFilter.class)
                .addFilterBefore(new SessionAliveFilter(), CsrfFilter.class)
                .addFilterBefore(
                    new ShibbolethPreAuthenticatedProcessingFilter(authenticationManagerBean()),
                    AbstractPreAuthenticatedProcessingFilter.class
                )
                .authorizeRequests()
                .antMatchers("/internal/**").hasRole("ADMIN");

            if (environment.acceptsProfiles("no-csrf")) {
                http.csrf().disable();
            }
            if (environment.acceptsProfiles("dev", "no-csrf")) {
                //we can't use @Profile, because we need to add it before the real filter
                http.addFilterBefore(new MockShibbolethFilter(), ShibbolethPreAuthenticatedProcessingFilter.class);
            }
        }
    }

    @Configuration
    @Order
    public static class SecurityConfigurationAdapter extends WebSecurityConfigurerAdapter  {

        @Value("${attribute.aggregation.user.name}")
        private String attributeAggregationUserName;

        @Value("${attribute.aggregation.user.password}")
        private String attributeAggregationPassword;

        @Override
        public void configure(HttpSecurity http) throws Exception {
            http
                .antMatcher("/**")
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .csrf()
                .disable()
                .addFilterBefore(
                    new BasicAuthenticationFilter(
                        new BasicAuthenticationManager(attributeAggregationUserName, attributeAggregationPassword)),
                    BasicAuthenticationFilter.class
                )
                .authorizeRequests()
                .antMatchers("/attribute/**").hasRole("ADMIN")
                .antMatchers("/**").hasRole("USER");
        }

    }

}
