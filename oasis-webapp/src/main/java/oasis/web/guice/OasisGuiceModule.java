package oasis.web.guice;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Clock;
import com.google.inject.AbstractModule;

import oasis.model.accounts.AccountRepository;
import oasis.model.applications.ApplicationRepository;
import oasis.model.authorizations.AuthorizationRepository;
import oasis.model.directory.DirectoryRepository;
import oasis.services.accounts.JongoAccountRepository;
import oasis.services.applications.DummyApplicationRepository;
import oasis.services.authorizations.JongoAuthorizationRepository;
import oasis.services.directory.JongoDirectoryRepository;

public class OasisGuiceModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(AccountRepository.class).to(JongoAccountRepository.class);
    bind(DirectoryRepository.class).to(JongoDirectoryRepository.class);
    bind(ApplicationRepository.class).to(DummyApplicationRepository.class);
    bind(AuthorizationRepository.class).to(JongoAuthorizationRepository.class);
    bind(JsonFactory.class).to(JacksonFactory.class);
    bind(Clock.class).toInstance(Clock.SYSTEM);
  }
}
