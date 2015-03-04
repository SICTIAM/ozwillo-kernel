package oasis.jongo.directory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.Instant;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Longs;
import com.mongodb.DuplicateKeyException;
import com.mongodb.WriteResult;

import oasis.jongo.JongoBootstrapper;
import oasis.model.InvalidVersionException;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Group;
import oasis.model.directory.Organization;

public class JongoDirectoryRepository implements DirectoryRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(DirectoryRepository.class);

  public static final String ORGANIZATION_PROJECTION = "{ groups: 0 }";
  public static final String GROUP_PROJECTION = "{ id:1, groups: {$elemMatch: {id: #} } }";
  public static final String GROUPS_PROJECTION = "{ id: 1, groups: 1 }";

  private final Jongo jongo;

  @Inject
  JongoDirectoryRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public Organization getOrganization(String organizationId) {
    return getOrganizationCollection()
        .findOne("{ id: # }", organizationId)
        .projection(ORGANIZATION_PROJECTION)
        .as(JongoOrganization.class);
  }

  @Override
  public Organization getOrganizationFromGroup(String groupId) {
    return getOrganizationCollection()
        .findOne("{ groups.id: # }", groupId)
        .projection(ORGANIZATION_PROJECTION)
        .as(JongoOrganization.class);
  }

  @Override
  public Iterable<Organization> getOrganizations() {
    return (Iterable<Organization>) (Iterable<?>) getOrganizationCollection().find().projection(ORGANIZATION_PROJECTION)
        .as(JongoOrganization.class);
  }

  @Override
  public Organization createOrganization(Organization organization) {
    JongoOrganization jongoOrganization = new JongoOrganization(organization);
    jongoOrganization.initCreated();
    try {
      getOrganizationCollection().insert(jongoOrganization);
    } catch (DuplicateKeyException e) {
      return null;
    }
    return jongoOrganization;
  }

  @Override
  public Organization updateOrganization(String organizationId, Organization organization, long[] versions) throws InvalidVersionException {
    long modified = System.currentTimeMillis();
    List<Object> updateParameters = new ArrayList<>(2);
    StringBuilder updateObject = new StringBuilder("modified:#");
    updateParameters.add(modified);

    if (organization.getName() != null) {
      updateObject.append(",name:#");
      updateParameters.add(organization.getName());
    }
    if (organization.getType() != null) {
      updateObject.append(",type:#");
      updateParameters.add(organization.getType());
    }

    JongoOrganization res = getOrganizationCollection()
        .findAndModify("{ id: #, modified: { $in: # } }", organizationId, Longs.asList(versions))
        .returnNew()
        .with("{ $set: {" + updateObject.toString() + " } }", updateParameters.toArray())
        .projection(ORGANIZATION_PROJECTION)
        .as(JongoOrganization.class);

    if (res == null) {
      if (getOrganizationCollection().count("{ id: # }", organizationId) != 0) {
        throw new InvalidVersionException("organization", organizationId);
      }
      logger.warn("The organization {} does not exist", organizationId);
    }

    return res;
  }

  @Override
  public Organization changeOrganizationStatus(String organizationId, Organization.Status newStatus, String requesterId) {
    Instant now = Instant.now();
    return getOrganizationCollection()
        .findAndModify("{ id: # }", organizationId)
        .returnNew()
        .with("{ $set: { status: #, status_changed: #, status_change_requester_id: #, modified: # } }", newStatus, now.toDate(), requesterId, now.getMillis())
        .as(JongoOrganization.class);
  }

  @Override
  public Organization changeOrganizationStatus(String organizationId, Organization.Status newStatus, String requesterId, long[] versions) throws InvalidVersionException {
    Instant now = Instant.now();
    JongoOrganization organization = getOrganizationCollection()
        .findAndModify("{id: #, modified: { $in: # } }", organizationId, Longs.asList(versions))
        .returnNew()
        .with("{ $set: { status: #, status_changed: #, status_change_requester_id: #, modified: # } }", newStatus, now.toDate(), requesterId, now.getMillis())
        .as(JongoOrganization.class);
    if (organization == null) {
      if (getOrganizationCollection().count("{ id: # }", organizationId) != 0) {
        throw new InvalidVersionException("organization", organizationId);
      }
      logger.warn("The organization {} does not exist", organizationId);
    }

    return organization;
  }

  @Override
  public Group getGroup(String groupId) {
    JongoOrganization organization = getOrganizationCollection()
        .findOne("{ groups.id: # }", groupId)
        .projection(GROUP_PROJECTION, groupId)
        .as(JongoOrganization.class);

    if (organization == null) {
      return null;
    }

    if (organization.getGroups() == null || organization.getGroups().isEmpty()) {
      return null;
    }

    return Iterables.getOnlyElement(organization.getGroups());
  }

  @Override
  public Collection<Group> getGroups(String organizationId) {
    JongoOrganization organization = getOrganizationCollection()
        .findOne("{ id: # }", organizationId)
        .projection(GROUPS_PROJECTION)
        .as(JongoOrganization.class);

    if (organization == null) {
      return null;
    }

    if (organization.getGroups() == null) {
      return Collections.emptyList();
    }

    return (Collection<Group>) (Collection<?>) organization.getGroups();
  }

  @Override
  public Group createGroup(String organizationId, Group group) {
    JongoGroup jongoGroup = new JongoGroup(group);

    WriteResult wr = getOrganizationCollection()
        .update("{ id: # }", organizationId)
        .with("{ $push: { groups: # } }", jongoGroup);
    if (wr.getN() != 1) {
      // TODO: more precise message
      logger.warn("The organization {} does not exist", organizationId);
    }
    return jongoGroup;
  }

  @Override
  public Group updateGroup(String groupId, Group group, long[] versions) throws InvalidVersionException {
    long modified = System.currentTimeMillis();
    List<Object> updateParameters = new ArrayList<>(3);
    StringBuilder updateObject = new StringBuilder("groups.$.modified:#");
    updateParameters.add(modified);

    if (group.getName() != null) {
      updateObject.append(",groups.$.name:#");
      updateParameters.add(group.getName());
    }

    JongoOrganization res = getOrganizationCollection()
        .findAndModify("{ groups: { $elemMatch: { id: #, modified: { $in: # } } } }", groupId, Longs.asList(versions))
        .returnNew()
        .with("{ $set: {" + updateObject.toString() + " } }", updateParameters.toArray())
        .projection(GROUP_PROJECTION, groupId)
        .as(JongoOrganization.class);

    if (res == null) {
      if (getOrganizationCollection().count("{ groups.id: # }", groupId) != 0) {
        throw new InvalidVersionException("group", groupId);
      }
      logger.warn("The group {} does not exist", groupId);
      return null;
    }

    if (res.getGroups() == null || res.getGroups().isEmpty()) {
      return null;
    }

    return res.getGroups().get(0);
  }

  @Override
  public boolean deleteGroup(String groupId, long[] versions) throws InvalidVersionException {
    WriteResult wr = getOrganizationCollection()
        .update("{ groups: { $elemMatch: { id: #, modified: { $in: # } } } }", groupId, Longs.asList(versions))
        .with("{ $pull: { groups: { id: #, modified: { $in: # } } } }", groupId, Longs.asList(versions));

    int n = wr.getN();
    if (n == 0) {
      if (getOrganizationCollection().count("{ groups.id: # }", groupId) != 0) {
        throw new InvalidVersionException("group", groupId);
      }
      return false;
    }

    if (n > 1) {
      logger.error("Deleted {} groups with ID {}, that shouldn't have happened", n, groupId);
    }
    return true;
  }

  @Override
  public Collection<Group> getGroupsForAgent(final String agentId) {
    JongoOrganization organization = getOrganizationCollection()
        .findOne("{ groups.agentIds : # }", agentId)
        .projection(GROUPS_PROJECTION)
        .as(JongoOrganization.class);
    if (organization == null) {
      return null;
    }
    if (organization.getGroups() == null) {
      return Collections.emptyList();
    }

    return (Collection<Group>) (Collection<?>) Collections2.filter(organization.getGroups(), new Predicate<JongoGroup>() {
      @Override
      public boolean apply(@Nullable JongoGroup input) {
        return input != null && input.getAgentIds() != null && input.getAgentIds().contains(agentId);
      }
    });
  }

  @Override
  public Collection<String> getGroupMembers(String groupId) {
    JongoGroup group = (JongoGroup) getGroup(groupId);
    if (group == null) {
      return null;
    }
    if (group.getAgentIds() == null) {
      return Collections.emptyList();
    }

    return group.getAgentIds();
  }

  @Override
  public void addGroupMember(String groupId, String agentId) {
    WriteResult wr = getOrganizationCollection()
        .update("{ groups: { $elemMatch: { agentIds: { $ne: # }, id: # } } }", agentId, groupId)
        .with("{ $addToSet: { groups.$.agentIds: # } }", agentId);

    if (wr.getN() != 1) {
      // TODO: more precise message
      logger.warn("The group {} does not exist or the agent {} is already in the group", groupId, agentId);
    }
  }

  @Override
  public boolean removeGroupMember(String groupId, String agentId) {
    WriteResult wr = getOrganizationCollection()
        .update("{ groups: { $elemMatch: { agentIds: #, id: # } } }", agentId, groupId)
        .with("{ $pull: { groups.$.agentIds: # } }", agentId);

    if (wr.getN() != 1) {
      // TODO: more precise message
      logger.warn("The group {} does not exist or the agent {} is not in the group", groupId, agentId);
    }
    return wr.getN() > 0;
  }

  private MongoCollection getOrganizationCollection() {
    return jongo.getCollection("organization");
  }

  @Override
  public void bootstrap() {
    getOrganizationCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    // XXX: we'd need indexes for groups and agents, but we'll refactor everything soon so we don't bother
  }
}
