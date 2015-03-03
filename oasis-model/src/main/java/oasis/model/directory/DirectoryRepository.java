package oasis.model.directory;

import java.util.Collection;

import oasis.model.InvalidVersionException;

public interface DirectoryRepository {
  Organization getOrganization(String organizationId);

  Organization getOrganizationFromGroup(String groupId);

  Collection<Group> getGroups(String organizationId);

  Organization createOrganization(Organization organization);

  Organization updateOrganization(String organizationId, Organization organization, long[] versions) throws InvalidVersionException;

  Organization changeOrganizationStatus(String organizationId, Organization.Status newStatus, String requesterId);

  Organization changeOrganizationStatus(String organizationId, Organization.Status newStatus, String requesterId, long[] versions)
      throws InvalidVersionException;

  Iterable<Organization> getOrganizations();

  Group getGroup(String groupId);

  Collection<String> getGroupMembers(String groupId);

  void addGroupMember(String groupId, String agentId);

  boolean removeGroupMember(String groupId, String agentId);

  Group createGroup(String organizationId, Group group);

  Group updateGroup(String groupId, Group group, long[] versions) throws InvalidVersionException;

  boolean deleteGroup(String groupId, long[] versions) throws InvalidVersionException;

  Collection<Group> getGroupsForAgent(String agentId);
}
