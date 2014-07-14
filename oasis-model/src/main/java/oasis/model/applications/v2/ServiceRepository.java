package oasis.model.applications.v2;

public interface ServiceRepository {
  Iterable<Service> getVisibleServices(); // TODO: remove when we integrate ElasticSearch

  Service createService(Service service);

  Service getService(String serviceId);

  Iterable<Service> getServicesOfInstance(String instanceId);

  Service getServiceByRedirectUri(String instanceId, String redirect_uri);

  Service getServiceByPostLogoutRedirectUri(String instanceId, String post_logout_redirect_uri);
}
