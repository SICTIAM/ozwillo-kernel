package oasis.auditlog.log4j;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.message.Message;

import oasis.auditlog.AuditLogEvent;

public interface Log4JSupplier {
  public Message generateMessage(AuditLogEvent auditLogEvent);

  public Appender createAppender(String appenderName);
}