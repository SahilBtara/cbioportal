package org.cbioportal.persistence;

import java.util.List;
import org.cbioportal.model.VariantCount;
import org.springframework.cache.annotation.Cacheable;

public interface VariantCountRepository {

  @Cacheable(
      cacheResolver = "generalRepositoryCacheResolver",
      condition = "@cacheEnabledConfig.getEnabled()")
  List<VariantCount> fetchVariantCounts(
      String molecularProfileId, List<Integer> entrezGeneIds, List<String> keywords);
}
