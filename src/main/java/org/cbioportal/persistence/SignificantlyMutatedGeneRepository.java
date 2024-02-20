package org.cbioportal.persistence;

import java.util.List;
import org.cbioportal.model.MutSig;
import org.cbioportal.model.meta.BaseMeta;
import org.springframework.cache.annotation.Cacheable;

public interface SignificantlyMutatedGeneRepository {

  @Cacheable(
      cacheResolver = "generalRepositoryCacheResolver",
      condition = "@cacheEnabledConfig.getEnabled()")
  List<MutSig> getSignificantlyMutatedGenes(
      String studyId,
      String projection,
      Integer pageSize,
      Integer pageNumber,
      String sortBy,
      String direction);

  @Cacheable(
      cacheResolver = "generalRepositoryCacheResolver",
      condition = "@cacheEnabledConfig.getEnabled()")
  BaseMeta getMetaSignificantlyMutatedGenes(String studyId);
}
