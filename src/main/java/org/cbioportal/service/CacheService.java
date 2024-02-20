package org.cbioportal.service;

import org.cbioportal.service.exception.CacheOperationException;

public interface CacheService {
  void clearCaches(boolean springManagedCache) throws CacheOperationException;

  void clearCachesForStudy(String studyId, boolean springManagedCache)
      throws CacheOperationException;
}
