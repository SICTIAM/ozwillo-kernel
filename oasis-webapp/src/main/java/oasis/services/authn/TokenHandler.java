package oasis.services.authn;

import static com.google.common.base.Preconditions.*;

import javax.inject.Inject;

import org.joda.time.Duration;
import org.joda.time.Instant;

import com.google.common.base.Strings;

import oasis.model.accounts.AccessToken;
import oasis.model.accounts.AuthorizationCode;
import oasis.model.accounts.RefreshToken;
import oasis.model.accounts.Token;
import oasis.model.authn.TokenRepository;

public class TokenHandler {
  private final TokenRepository tokenRepository;

  @Inject TokenHandler(TokenRepository tokenRepository) {
    this.tokenRepository = tokenRepository;
  }

  public boolean checkTokenValidity(Token token) {
    // A null token is not valid !
    if (token == null) {
      return false;
    }

    // Compute the token Expiration Date
    Instant tokenExpirationDate = token.getCreationTime().plus(token.getTimeToLive());
    if (tokenExpirationDate.isBefore(Instant.now())) {
      // Token is expired
      return false;
    }

    // The token is valid
    return true;
  }

  public AccessToken createAccessToken(String accountId, RefreshToken refreshToken) {
    return this.createAccessToken(accountId, Duration.standardHours(1), refreshToken);
  }

  public AccessToken createAccessToken(String accountId, Duration ttl, RefreshToken refreshToken) {
    checkArgument(!Strings.isNullOrEmpty(accountId));

    AccessToken newAccessToken = new AccessToken();

    newAccessToken.setCreationTime(Instant.now());
    newAccessToken.setTimeToLive(ttl);

    if (refreshToken != null) {
      if (refreshToken instanceof AuthorizationCode) {
          if (!revokeToken(accountId, refreshToken)) {
            return null;
          }
        } else {
        newAccessToken.setRefreshTokenId(refreshToken.getId());
      }
    }

    if (!registerToken(accountId, newAccessToken)) {
      return null;
    }

    // Return the new access token
    return newAccessToken;
  }

  public AuthorizationCode createAuthorizationCode(String accountId) {
    checkArgument(!Strings.isNullOrEmpty(accountId));

    AuthorizationCode newAuthorizationCode = new AuthorizationCode();

    newAuthorizationCode.setCreationTime(Instant.now());
    // A AuthorizationCode is available only for 1 minute
    newAuthorizationCode.setTimeToLive(Duration.standardMinutes(1));

    // Register the new access token in memory
    if (!registerToken(accountId, newAuthorizationCode)) {
      return null;
    }

    // Return the new token
    return newAuthorizationCode;
  }

  public boolean registerToken(String accountId, Token token) {
    checkArgument(!Strings.isNullOrEmpty(accountId));
    checkNotNull(token);

    return tokenRepository.registerToken(accountId, token);
  }

  public boolean revokeToken(String accountId, Token token) {
    checkArgument(!Strings.isNullOrEmpty(accountId));
    checkNotNull(token);

    return tokenRepository.revokeToken(accountId, token);
  }
}