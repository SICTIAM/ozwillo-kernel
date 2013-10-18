package oasis.web;


import com.google.common.collect.ImmutableSet;
import com.wordnik.swagger.jaxrs.listing.ApiDeclarationProvider;
import com.wordnik.swagger.jaxrs.listing.ResourceListingProvider;
import java.util.Set;
import oasis.web.apidocs.ApiListing;
import oasis.web.authn.ClientAuthenticationFilter;
import oasis.web.authn.Login;
import oasis.web.authn.Logout;
import oasis.web.authn.OAuthAuthenticationFilter;
import oasis.web.authn.UserAuthenticationFilter;
import oasis.web.authz.AuthorizationEndpoint;
import oasis.web.authz.TokenEndpoint;
import oasis.web.example.OpenIdConnect;
import oasis.web.providers.CookieParserRequestFilter;
import oasis.web.providers.SecureFilter;
import oasis.web.providers.UriParamConverterProvider;
import oasis.web.userdirectory.UserDirectoryResource;
import oasis.web.view.HandlebarsBodyWriter;

public class Application extends javax.ws.rs.core.Application {

  @Override
  public Set<Class<?>> getClasses() {
    return ImmutableSet.<Class<?>>of(
        // Hacks and workarounds
        UriParamConverterProvider.class,
        CookieParserRequestFilter.class,
        SecureFilter.class,
        // Views
        HandlebarsBodyWriter.class,
        // Swagger
        ResourceListingProvider.class,
        ApiDeclarationProvider.class,
        SwaggerUI.class,
        ApiListing.class,
        // Authentication
        UserAuthenticationFilter.class,
        ClientAuthenticationFilter.class,
        OAuthAuthenticationFilter.class,
        Login.class,
        Logout.class,
        // Authorization
        AuthorizationEndpoint.class,
        TokenEndpoint.class,
        // Resources
        Home.class,
        UserDirectoryResource.class,
        OpenIdConnect.class);
  }
}
