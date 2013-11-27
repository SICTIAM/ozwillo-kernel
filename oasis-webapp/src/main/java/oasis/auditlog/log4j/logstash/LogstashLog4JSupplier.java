package oasis.auditlog.log4j.logstash;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

import javax.inject.Inject;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.message.Message;

import com.atolcd.logging.log4j.logstash.LogstashAppender;
import com.google.common.collect.ImmutableMap;

import oasis.auditlog.AuditLogEvent;
import oasis.auditlog.JsonMessage;
import oasis.auditlog.log4j.Log4JSupplier;

public class LogstashLog4JSupplier implements Log4JSupplier {
  private final LogstashLog4JAuditModule.Settings settings;

  @Inject
  public LogstashLog4JSupplier(LogstashLog4JAuditModule.Settings settings) {
    this.settings = settings;
  }

  @Override
  public Message generateMessage(AuditLogEvent logEvent) {
    ImmutableMap<String, Object> data = ImmutableMap.<String, Object>of(
        "type", logEvent.getEventType(),
        "time", logEvent.getDate(),
        "data", logEvent.getContextMap()
    );

    return new JsonMessage(data, new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"), TimeZone.getTimeZone("UTC"));
  }

  @Override
  public Appender createAppender(String appenderName) {
    return LogstashAppender.createAppender(appenderName, settings.host, settings.port);
  }
}