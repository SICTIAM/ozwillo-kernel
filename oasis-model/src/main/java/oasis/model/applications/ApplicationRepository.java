package oasis.model.applications;

public interface ApplicationRepository {
  Iterable<Application> getCatalogApplications(int start, int limit);

  Iterable<Application> getApplicationInstances(int start, int limit);

  Application getApplication(String appId);

  Application createApplication(Application app);

  Application instanciateApplication(String appId, String organizationId);

  void updateApplication(String appId, Application app);

  void deleteApplication(String appId);

  Iterable<DataProvider> getDataProviders(String appId);

  DataProvider getDataProvider(String dataProviderId);

  DataProvider createDataProvider(String appId, DataProvider dataProvider);

  void updateDataProvider(String dataProviderId, DataProvider dataProvider);

  void deleteDataProvider(String dataProviderId);

  ServiceProvider getServiceProviderFromApplication(String appId);

  ServiceProvider getServiceProvider(String serviceProviderId);

  ServiceProvider createServiceProvider(String appId, ServiceProvider serviceProvider);

  void updateServiceProvider(String serviceProviderId, ServiceProvider serviceProvider);

  void deleteServiceProvider(String serviceProviderId);
}
