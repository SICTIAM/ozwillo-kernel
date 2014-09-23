package oasis.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;
import com.wordnik.swagger.config.ConfigFactory;
import com.wordnik.swagger.config.ScannerFactory;
import com.wordnik.swagger.jaxrs.config.DefaultJaxrsScanner;
import com.wordnik.swagger.jaxrs.reader.DefaultJaxrsApiReader;
import com.wordnik.swagger.reader.ClassReaders;

import oasis.auditlog.log4j.logstash.LogstashLog4JAuditModule;
import oasis.auditlog.noop.NoopAuditLogModule;
import oasis.http.HttpServer;
import oasis.http.HttpServerModule;
import oasis.jongo.JongoService;
import oasis.jongo.guice.JongoModule;
import oasis.mail.MailModule;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.tools.CommandLineTool;
import oasis.web.guice.OasisGuiceModule;
import oasis.web.kibana.KibanaModule;

public class WebApp extends CommandLineTool {
  // logger is not a static field to be initialized once log4j is configured
  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(WebApp.class);
  }

  public void run(String[] args) throws Throwable {
    final Config config = init(args);

    AbstractModule auditModule = (config.getBoolean("oasis.auditlog.disabled")) ?
        new NoopAuditLogModule() :
        LogstashLog4JAuditModule.create(config.getConfig("oasis.auditlog.logstash"));

    final Injector injector = Guice.createInjector(
        new OasisGuiceModule(),
        JongoModule.create(config.getConfig("oasis.mongo")),
        auditModule,
        HttpServerModule.create(config.getConfig("oasis.http")),
        KibanaModule.create(config.getConfig("oasis.kibana")),
        // TODO: refactor to use a single subtree of the config
        OpenIdConnectModule.create(config.withOnlyPath("oasis.openid-connect")
            .withFallback(config.withOnlyPath("oasis.oauth"))
            .withFallback(config.withOnlyPath("oasis.session"))
            .withFallback(config.withOnlyPath("oasis.conf-dir"))),
        MailModule.create(config.getConfig("oasis.mail"))
    );

    final HttpServer server = injector.getInstance(HttpServer.class);
    final JongoService jongo = injector.getInstance(JongoService.class);

    initSwagger(config);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        server.stop();
        jongo.stop();
      }
    });

    jongo.start();
    server.start();
  }

  private static void initSwagger(Config config) {
    ConfigFactory.config().setApiVersion(config.getString("swagger.api.version"));

    // TODO: authorizations and info
    ScannerFactory.setScanner(new DefaultJaxrsScanner());
    ClassReaders.setReader(new DefaultJaxrsApiReader());
  }

  public static void main(String[] args) throws Throwable {
    new WebApp().run(args);
  }
}
