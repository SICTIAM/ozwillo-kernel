package oasis.model.authn;

public interface TokenRepository {
  Token getToken(String tokenId);

  boolean registerToken(Token token);

  boolean revokeToken(String tokenId);

  boolean renewToken(String tokenId);

  boolean reAuthSidToken(String tokenId);
}
