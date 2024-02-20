package org.cbioportal.web.parameter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.cbioportal.model.AlterationFilter;

public class MolecularProfileCasesGroupAndAlterationTypeFilter {

  private AlterationFilter alterationEventTypes;

  @Size(min = 1, max = PagingConstants.MAX_PAGE_SIZE)
  @Schema
  private List<MolecularProfileCasesGroupFilter> molecularProfileCasesGroupFilter;

  public List<MolecularProfileCasesGroupFilter> getMolecularProfileCasesGroupFilter() {
    return this.molecularProfileCasesGroupFilter;
  }

  public void setMolecularProfileCasesGroupFilter(
      List<MolecularProfileCasesGroupFilter> molecularProfileCasesGroupFilter) {
    this.molecularProfileCasesGroupFilter = molecularProfileCasesGroupFilter;
  }

  public AlterationFilter getAlterationEventTypes() {
    return this.alterationEventTypes;
  }

  public void setAlterationEventTypes(AlterationFilter alterationEventTypes) {
    this.alterationEventTypes = alterationEventTypes;
  }
}
