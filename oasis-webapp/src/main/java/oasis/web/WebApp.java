package oasis.web;

import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.wordnik.swagger.config.ConfigFactory;
import com.wordnik.swagger.config.ScannerFactory;
import com.wordnik.swagger.config.SwaggerConfig;
import com.wordnik.swagger.jaxrs.config.DefaultJaxrsScanner;
import com.wordnik.swagger.jaxrs.reader.DefaultJaxrsApiReader;
import com.wordnik.swagger.reader.ClassReaders;
import java.io.InputStream;
import java.util.Properties;
import javax.ws.rs.core.NewCookie;
import oasis.web.providers.NewCookieHeaderDelegate;
import oasis.web.guice.GuiceInjectorFactory;
import oasis.web.guice.OasisGuiceModule;
import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import static com.google.inject.name.Names.named;

public class WebApp {

  public static void main(String[] args) throws Throwable {
    final NettyJaxrsServer server = new NettyJaxrsServer();
    server.getDeployment().setApplication(new Application());
    // port defaults to 8080

    // Guice configuration
    final Injector injector = Guice.createInjector(new OasisGuiceModule());
    ResteasyProviderFactory providerFactory = new ResteasyProviderFactory();
    providerFactory.setInjectorFactory(new GuiceInjectorFactory(injector));
    server.getDeployment().setProviderFactory(providerFactory);
    // workaround for https://java.net/jira/browse/JAX_RS_SPEC-430
    providerFactory.addHeaderDelegate(NewCookie.class, new NewCookieHeaderDelegate());

    // Swagger configuration
    Properties swaggerProps = new Properties();
    try (InputStream in = Resources.getResource("swagger.properties").openStream()) {
      swaggerProps.load(in);
    }
    ConfigFactory.setConfig(new SwaggerConfig(
        // apiVersion
        swaggerProps.getProperty("api.version"),
        // swaggerVersion
        swaggerProps.getProperty("swagger.version"),
        // basePath: the server where the API can be found
        injector.getInstance(Key.get(String.class, named("swagger.basePath"))),
        // apiPath: relative path to API resources
        "api-docs",
        // authorizations list: https://github.com/wordnik/swagger-core/wiki/authorizations
        null,
        // information object: title, description, termsOfServiceUrl, contact, license, licenseUrl
        null
    ));
    ScannerFactory.setScanner(new DefaultJaxrsScanner());
    ClassReaders.setReader(new DefaultJaxrsApiReader());

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        System.err.print("stopping");
        server.stop();
      }
    });

    server.start();

    System.out.println(String.format("JAX-RS app started on port %d;", server.getPort()));
  }

  private WebApp() { /* non instantiable */ }
}
