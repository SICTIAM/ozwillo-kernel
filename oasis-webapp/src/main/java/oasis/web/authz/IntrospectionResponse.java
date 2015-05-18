/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.web.authz;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

class IntrospectionResponse {
  @JsonProperty
  @ApiModelProperty
  private boolean active;
  @JsonProperty
  @ApiModelProperty
  private Long exp;
  @JsonProperty
  @ApiModelProperty
  private Long iat;
  @JsonProperty
  @ApiModelProperty
  private String scope;
  @JsonProperty
  @ApiModelProperty
  private String client_id;
  @JsonProperty
  @ApiModelProperty
  private String sub;
  @JsonProperty
  @ApiModelProperty
  private String aud;
  @JsonProperty
  @ApiModelProperty
  private String token_type;
  @JsonProperty
  @ApiModelProperty
  private List<String> sub_groups;

  public boolean isActive() {
    return active;
  }

  IntrospectionResponse setActive(boolean active) {
    this.active = active;
    return this;
  }

  public Long getExp() {
    return exp;
  }

  IntrospectionResponse setExp(Long exp) {
    this.exp = exp;
    return this;
  }

  public Long getIat() {
    return iat;
  }

  IntrospectionResponse setIat(Long iat) {
    this.iat = iat;
    return this;
  }

  public String getScope() {
    return scope;
  }

  IntrospectionResponse setScope(String scope) {
    this.scope = scope;
    return this;
  }

  public String getClient_id() {
    return client_id;
  }

  IntrospectionResponse setClient_id(String client_id) {
    this.client_id = client_id;
    return this;
  }

  public String getSub() {
    return sub;
  }

  IntrospectionResponse setSub(String sub) {
    this.sub = sub;
    return this;
  }

  public String getAud() {
    return aud;
  }

  IntrospectionResponse setAud(String aud) {
    this.aud = aud;
    return this;
  }

  public String getToken_type() {
    return token_type;
  }

  IntrospectionResponse setToken_type(String token_type) {
    this.token_type = token_type;
    return this;
  }

  public List<String> getSub_groups() {
    return sub_groups;
  }

  IntrospectionResponse setSub_groups(List<String> sub_groups) {
    this.sub_groups = sub_groups;
    return this;
  }
}
