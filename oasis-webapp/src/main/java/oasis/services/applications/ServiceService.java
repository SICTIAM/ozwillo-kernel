package oasis.services.applications;

import javax.inject.Inject;

import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;

public class ServiceService {
  private final ServiceRepository serviceRepository;

  @Inject ServiceService(ServiceRepository serviceRepository) {
    this.serviceRepository = serviceRepository;
  }

  public Iterable<Service> getVisibleServices() {
    return serviceRepository.getVisibleServices();
  }

  public Service getService(String serviceId) {
    return serviceRepository.getService(serviceId);
  }

  public Service createService(Service service) {
    // TODO: check that the application instance exists
    // TODO: index in ElasticSearch (if visible)
    return serviceRepository.createService(service);
  }

  public Iterable<Service> getServicesOfInstance(String instanceId) {
    return serviceRepository.getServicesOfInstance(instanceId);
  }
}
