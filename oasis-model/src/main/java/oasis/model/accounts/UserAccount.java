package oasis.model.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

public class UserAccount extends Account implements AccountWithPassword {
  @JsonProperty
  @ApiModelProperty(required = true)
  private String emailAddress;

  @JsonProperty
  @ApiModelProperty(required = true)
  private String identityId;

  @JsonProperty
  @ApiModelProperty(required = true)
  private String password;

  @JsonProperty
  @ApiModelProperty(required = true)
  private String passwordSalt;

  @JsonProperty
  @ApiModelProperty
  private long modified;

  public String getEmailAddress() {
    return emailAddress;
  }

  public void setEmailAddress(String emailAddress) {
    this.emailAddress = emailAddress;
  }

  public String getIdentityId() {
    return identityId;
  }

  public void setIdentityId(String identityId) {
    this.identityId = identityId;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getPasswordSalt() {
    return passwordSalt;
  }

  public void setPasswordSalt(String passwordSalt) {
    this.passwordSalt = passwordSalt;
  }

  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
