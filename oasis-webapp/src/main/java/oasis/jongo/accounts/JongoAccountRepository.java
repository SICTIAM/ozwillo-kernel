package oasis.jongo.accounts;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import com.mongodb.WriteResult;

import oasis.model.InvalidVersionException;
import oasis.model.accounts.Account;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.AgentAccount;
import oasis.model.accounts.UserAccount;

public class JongoAccountRepository implements AccountRepository {

  private final Jongo jongo;

  @Inject
  JongoAccountRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  protected MongoCollection getAccountCollection() {
    return jongo.getCollection("account");
  }

  @Override
  public Account getAccount(String id) {
    return this.getAccountCollection().findOne("{id:#}", id).as(Account.class);
  }

  @Override
  public Account getAccountByTokenId(String tokenid) {
    return this.getAccountCollection().findOne("{tokens.id:#}", tokenid).projection("{id:1,type:1,tokens.$:1}").as(Account.class);
  }

  @Override
  public UserAccount getUserAccountByEmail(String email) {
    return this.getAccountCollection().findOne("{emailAddress:#}", email).as(UserAccount.class);
  }

  @Override
  public UserAccount getUserAccountById(String id) {
    return this.getAccountCollection().findOne("{id:#}", id).as(UserAccount.class);
  }

  @Override
  public AgentAccount getAgentAccountById(String id) {
    return getAccountCollection().findOne("{id:#}", id).as(AgentAccount.class);
  }

  @Override
  public AgentAccount createAgentAccount(String organizationId, AgentAccount agent) {
    agent.setModified(System.currentTimeMillis());
    agent.setOrganizationId(organizationId);
    getAccountCollection().insert(agent);
    return agent;
  }

  @Override
  public boolean deleteAgentAccount(String agentId, long[] versions) throws InvalidVersionException {
    WriteResult wr = getAccountCollection().remove("{id: #, modified: { $in: # } }", agentId, versions);
    if (wr.getN() == 0) {
      if (getAccountCollection().count("{ id: # }", agentId) != 0) {
        throw new InvalidVersionException("agentaccount", agentId);
      }
      return false;
    }

    return true;
  }

  @Override
  public void deleteAgentAccountsFromOrganization(String organizationId) {
    getAccountCollection().remove("{ organizationId: # }", organizationId);
  }

  @Override
  public Iterable<AgentAccount> getAgentsForOrganization(String organizationId, int start, int limit) {
    return getAccountCollection()
        .find("{ organizationId: # }", organizationId)
        .skip(start)
        .limit(limit)
        .as(AgentAccount.class);
  }

  @Override
  public AgentAccount findAndRemove(String agentId, long[] versions) throws InvalidVersionException {
    AgentAccount res = getAccountCollection()
        .findAndModify("{id: #, modified: { $in: # } }", agentId, versions)
        .remove()
        .as(AgentAccount.class);
    if (res == null) {
      if (getAccountCollection().count("{ id: # }", agentId) != 0) {
        throw new InvalidVersionException("agentaccount", agentId);
      }
    }
    return res;
  }

  @Override
  public void updatePassword(String accountId, String passwordHash, String passwordSalt) {
    // FIXME: handle errors
    getAccountCollection()
        .findAndModify("{ id: # }", accountId)
        .with("{ $set: { password: #, passwordSalt: # } }", passwordHash, passwordSalt);
  }
}

