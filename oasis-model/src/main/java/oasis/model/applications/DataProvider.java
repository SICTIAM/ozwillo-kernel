package oasis.model.applications;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.annotations.Id;
import oasis.model.i18n.LocalizableString;

@Deprecated
@JsonRootName("dataProvider")
public class DataProvider {
  @Id
  @ApiModelProperty(required = true)
  private String id;

  @JsonProperty
  @ApiModelProperty
  private Set<String> scopeIds = new HashSet<>();

  @JsonProperty
  @ApiModelProperty(required = true)
  private LocalizableString name = new LocalizableString();

  public DataProvider() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public DataProvider(@Nonnull DataProvider other) {
    this.name = new LocalizableString(other.getName());
    this.scopeIds = new HashSet<>(other.getScopeIds());
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public LocalizableString getName() {
    return name.unmodifiable();
  }

  public void setName(LocalizableString name) {
    this.name = new LocalizableString(name);
  }

  public Set<String> getScopeIds() {
    return Collections.unmodifiableSet(scopeIds);
  }

  public void setScopeIds(Set<String> scopeIds) {
    this.scopeIds = new HashSet<>(scopeIds);
  }

}
