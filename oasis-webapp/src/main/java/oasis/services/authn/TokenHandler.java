package oasis.services.authn;

import static com.google.common.base.Preconditions.*;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

import com.google.api.client.util.Clock;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;

import oasis.model.authn.AccessToken;
import oasis.model.authn.AccountActivationToken;
import oasis.model.authn.AuthorizationCode;
import oasis.model.authn.ChangePasswordToken;
import oasis.model.authn.MembershipInvitationToken;
import oasis.model.authn.RefreshToken;
import oasis.model.authn.SidToken;
import oasis.model.authn.Token;
import oasis.model.authn.TokenRepository;
import oasis.auth.AuthModule;
import oasis.model.authz.Scopes;
import oasis.services.authn.login.PasswordHasher;
import oasis.userdirectory.UserDirectoryModule;

public class TokenHandler {
  private static final BaseEncoding BASE_ENCODING = BaseEncoding.base64Url().omitPadding();
  // Note: make sure BASE_ENCODING won't ever produce such a character
  private static final String SEPARATOR = "/";

  private final TokenRepository tokenRepository;
  private final AuthModule.Settings authSettings;
  private final UserDirectoryModule.Settings userDirectorySettings;
  private final PasswordHasher passwordHasher;
  private final SecureRandom secureRandom;
  private final Clock clock;

  @Inject TokenHandler(TokenRepository tokenRepository, AuthModule.Settings oidcSettings,
      UserDirectoryModule.Settings userDirectorySettings, PasswordHasher passwordHasher,
      SecureRandom secureRandom, Clock clock) {
    this.tokenRepository = tokenRepository;
    this.authSettings = oidcSettings;
    this.userDirectorySettings = userDirectorySettings;
    this.passwordHasher = passwordHasher;
    this.secureRandom = secureRandom;
    this.clock = clock;
  }

  public String generateRandom() {
    byte[] bytes = new byte[16]; // 128bits
    secureRandom.nextBytes(bytes);
    return BASE_ENCODING.encode(bytes);
  }

  public AccountActivationToken createAccountActivationToken(String accountId, @Nullable URI continueUrl, String pass) {
    AccountActivationToken accountActivationToken = new AccountActivationToken();
    accountActivationToken.setAccountId(accountId);
    accountActivationToken.expiresIn(authSettings.accountActivationTokenDuration);
    accountActivationToken.setContinueUrl(continueUrl);

    secureToken(accountActivationToken, pass);

    tokenRepository.revokeTokensForAccountAndTokenType(accountId, AccountActivationToken.class);

    if (!tokenRepository.registerToken(accountActivationToken)) {
      return null;
    }
    return accountActivationToken;
  }

  public ChangePasswordToken createChangePasswordToken(String accountId, String pass) {
    ChangePasswordToken changePasswordToken = new ChangePasswordToken();
    changePasswordToken.setAccountId(accountId);
    changePasswordToken.expiresIn(authSettings.changePasswordTokenDuration);

    secureToken(changePasswordToken, pass);

    tokenRepository.revokeTokensForAccountAndTokenType(accountId, ChangePasswordToken.class);

    if (!tokenRepository.registerToken(changePasswordToken)) {
      return null;
    }
    return changePasswordToken;
  }

  public AccessToken createAccessToken(AuthorizationCode authorizationCode, String pass) {
    if (!tokenRepository.revokeToken(authorizationCode.getId())) {
      return null;
    }
    AccessToken accessToken = new AccessToken();
    accessToken.setAccountId(authorizationCode.getAccountId());
    accessToken.expiresIn(authSettings.accessTokenDuration);
    accessToken.setScopeIds(authorizationCode.getScopeIds());
    accessToken.setServiceProviderId(authorizationCode.getServiceProviderId());
    // Note: store the authorizationCode ID although it's been revoked to be able to
    // detect code reuse, and revoke all previously issued tokens based on the reused code.
    // (see http://tools.ietf.org/html/rfc6749#section-4.1.2)
    accessToken.setParent(authorizationCode);

    secureToken(accessToken, pass);

    if (!tokenRepository.registerToken(accessToken)) {
      return null;
    }
    return accessToken;
  }

  public AccessToken createAccessToken(RefreshToken refreshToken, Set<String> scopeIds, String pass) {
    assert refreshToken.getScopeIds().containsAll(scopeIds);

    AccessToken accessToken = new AccessToken();
    accessToken.setAccountId(refreshToken.getAccountId());
    accessToken.expiresIn(authSettings.accessTokenDuration);
    accessToken.setScopeIds(scopeIds);
    accessToken.setServiceProviderId(refreshToken.getServiceProviderId());
    accessToken.setParent(refreshToken);

    secureToken(accessToken, pass);

    if (!tokenRepository.registerToken(accessToken)) {
      return null;
    }

    // Return the new access token
    return accessToken;
  }

  public AuthorizationCode createAuthorizationCode(SidToken sidToken, Set<String> scopeIds, String serviceProviderId,
      @Nullable String nonce, String redirectUri, String pass) {
    AuthorizationCode authorizationCode = new AuthorizationCode();
    authorizationCode.setAccountId(sidToken.getAccountId());
    authorizationCode.expiresIn(authSettings.authorizationCodeDuration);
    authorizationCode.setScopeIds(scopeIds);
    authorizationCode.setServiceProviderId(serviceProviderId);
    authorizationCode.setNonce(nonce);
    authorizationCode.setRedirectUri(redirectUri);
    if (!scopeIds.contains(Scopes.OFFLINE_ACCESS)) {
      authorizationCode.setParent(sidToken);
    }

    secureToken(authorizationCode, pass);

    // Register the new token
    if (!tokenRepository.registerToken(authorizationCode)) {
      return null;
    }

    // Return the new token
    return authorizationCode;
  }

  public RefreshToken createRefreshToken(AuthorizationCode authorizationCode, String pass) {
    if (!tokenRepository.revokeToken(authorizationCode.getId())) {
      return null;
    }

    RefreshToken refreshToken = new RefreshToken();
    refreshToken.setAccountId(authorizationCode.getAccountId());
    refreshToken.expiresIn(authSettings.refreshTokenDuration);
    refreshToken.setScopeIds(authorizationCode.getScopeIds());
    refreshToken.setServiceProviderId(authorizationCode.getServiceProviderId());
    // Note: store the authorizationCode ID although it's been revoked to be able to
    // detect code reuse, and revoke all previously issued tokens based on the reused code.
    // (see http://tools.ietf.org/html/rfc6749#section-4.1.2)
    refreshToken.setParent(authorizationCode);

    secureToken(refreshToken, pass);

    // Register the new token
    if (!tokenRepository.registerToken(refreshToken)) {
      return null;
    }

    return refreshToken;
  }

  public SidToken createSidToken(String accountId, byte[] userAgentFingerprint, String pass) {
    checkArgument(!Strings.isNullOrEmpty(accountId));

    SidToken sidToken = new SidToken();
    sidToken.setAccountId(accountId);
    sidToken.expiresIn(authSettings.sidTokenDuration);
    // TODO: remember me
    sidToken.setAuthenticationTime(sidToken.getCreationTime());
    sidToken.setUserAgentFingerprint(userAgentFingerprint);

    secureToken(sidToken, pass);

    if (!tokenRepository.registerToken(sidToken)) {
      return null;
    }

    return sidToken;
  }

  public MembershipInvitationToken createInvitationToken(String organizationMembershipId, String pass) {
    checkArgument(!Strings.isNullOrEmpty(organizationMembershipId));

    MembershipInvitationToken invitationToken = new MembershipInvitationToken();
    invitationToken.setOrganizationMembershipId(organizationMembershipId);
    invitationToken.expiresIn(userDirectorySettings.invitationTokenDuration());

    secureToken(invitationToken, pass);

    if (!tokenRepository.registerToken(invitationToken)) {
      return null;
    }

    return invitationToken;
  }

  private <T extends Token> T getCheckedToken(TokenInfo tokenInfo, Class<T> tokenClass) {
    if (tokenInfo == null) {
      return null;
    }

    // If someone fakes a token, at least they should ensure it hasn't expired
    // It saves us a database lookup.
    if (tokenInfo.getExp().isBefore(clock.currentTimeMillis())) {
      return null;
    }

    String[] splitId = splitId(tokenInfo.getId());
    if (splitId == null) {
      return null;
    }
    Token realToken = tokenRepository.getToken(splitId[0]);
    if (realToken == null || !checkTokenValidity(realToken, splitId[1])) {
      // token does not exist, has expired, or is otherwise invalid
      return null;
    }

    if (!tokenClass.isInstance(realToken)) {
      return null;
    }

    return tokenClass.cast(realToken);
  }

  public <T extends Token> T getCheckedToken(String tokenSerial, Class<T> tokenClass) {
    return this.getCheckedToken(TokenSerializer.deserialize(tokenSerial), tokenClass);
  }

  private void secureToken(Token token, String pass) {
    byte[] salt = passwordHasher.createSalt();
    byte[] hash = passwordHasher.hashPassword(pass, salt);

    token.setHash(hash);
    token.setSalt(salt);
  }

  // Used by TokenInfo
  static String makeId(String id, String pass) {
    assert pass != null;
    return id + SEPARATOR + pass;
  }

  private static String[] splitId(String id) {
    if (id == null) {
      return null;
    }
    int pos = id.indexOf(SEPARATOR);
    if (pos < 0) {
      return null;
    }
    return new String[] {
        id.substring(0, pos),
        id.substring(pos + 1)
    };
  }

  private boolean checkTokenValidity(Token token, String pass) {
    // A null token is not valid !
    if (token == null) {
      return false;
    }

    if (token.getExpirationTime().isBefore(clock.currentTimeMillis())) {
      // Token is expired
      return false;
    }

    return passwordHasher.checkPassword(pass, token.getHash(), token.getSalt());
  }
}
