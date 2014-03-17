package oasis.services.authn;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.api.client.testing.http.FixedClock;
import com.google.api.client.util.Clock;
import com.google.inject.Inject;
import com.google.inject.Provides;

import oasis.model.authn.AccessToken;
import oasis.model.authn.Token;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.login.PasswordHasher;

@RunWith(JukitoRunner.class)
public class TokenHandlerTest {
  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(Clock.class).to(FixedClock.class);
    }

    @Provides @TestSingleton FixedClock providesFixedClock() {
      return new FixedClock(now.getMillis());
    }
  }

  @Inject TokenHandler sut;

  static final Instant now = new DateTime(2014, 7, 17, 14, 30).toInstant();

  static final Token validToken = new Token() {{
    setId("validToken");
    setCreationTime(now.minus(Duration.standardHours(1)));
    expiresIn(Duration.standardHours(2));
    setHash("valid".getBytes());
    setSalt("salt".getBytes());
  }};
  static final Token expiredToken = new Token() {{
    setId("expiredToken");
    setCreationTime(new DateTime(2008, 1, 20, 11, 10).toInstant());
    setExpirationTime(new DateTime(2013, 10, 30, 19, 42).toInstant());
    setHash("valid".getBytes());
    setSalt("salt".getBytes());
  }};

  @Before public void setUpMocks(TokenRepository tokenRepository, PasswordHasher passwordHasher) {
    when(tokenRepository.getToken(validToken.getId())).thenReturn(validToken);
    when(tokenRepository.getToken(expiredToken.getId())).thenReturn(expiredToken);

    when(passwordHasher.checkPassword("valid", "valid".getBytes(), "salt".getBytes())).thenReturn(true);
    when(passwordHasher.checkPassword("expired", "expired".getBytes(), "salt".getBytes())).thenReturn(true);
    when(passwordHasher.checkPassword("counterfeit", "counterfeit".getBytes(), "salt".getBytes())).thenReturn(false);
  }

  @Test public void testGetCheckedToken_validToken() {
    // when
    Token token = sut.getCheckedToken(TokenSerializer.serialize(validToken, "valid"), Token.class);

    // then
    assertThat(token).isSameAs(validToken);
  }

  @Test public void testGetCheckedToken_validToken_badType() {
    // when
    AccessToken accessToken = sut.getCheckedToken(TokenSerializer.serialize(validToken, "valid"), AccessToken.class);

    // then
    assertThat(accessToken).isNull();
  }

  @Test public void testGetCheckedToken_invalidToken() {
    // when
    Token token = sut.getCheckedToken("invalid", Token.class);

    // then
    assertThat(token).isNull();
  }

  @Test public void testGetCheckedToken_expiredToken() {
    // when
    Token token = sut.getCheckedToken(TokenSerializer.serialize(expiredToken, "expired"), Token.class);

    // then
    assertThat(token).isNull();
  }

  @Test public void testGetCheckedToken_fakeExpiredToken() {
    // given
    Token fakeExpiredToken = new Token() {{
      setId(expiredToken.getId());
      setCreationTime(validToken.getCreationTime());
      setExpirationTime(validToken.getExpirationTime());
    }};

    // when
    Token token = sut.getCheckedToken(TokenSerializer.serialize(fakeExpiredToken, "expired"), Token.class);

    // then
    assertThat(token).isNull();
  }

  @Test public void testGetCheckedToken_counterfeitToken() {
    // when
    Token token = sut.getCheckedToken(TokenSerializer.serialize(validToken, "counterfeit"), Token.class);

    // then
    assertThat(token).isNull();
  }

  // TODO: create* methods.
}