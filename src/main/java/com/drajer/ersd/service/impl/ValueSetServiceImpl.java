package com.drajer.ersd.service.impl;

import ca.uhn.fhir.parser.IParser;
import com.drajer.ecrapp.config.ValueSetSingleton;
import com.drajer.ecrapp.util.ApplicationUtils;
import com.drajer.ersd.dao.ValueSetDao;
import com.drajer.ersd.model.ValueSetGrouperModel;
import com.drajer.ersd.model.ValueSetModel;
import com.drajer.ersd.service.ValueSetService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.DataRequirement;
import org.hl7.fhir.r4.model.DataRequirement.DataRequirementCodeFilterComponent;
import org.hl7.fhir.r4.model.TriggerDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetComposeComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service("valueSetServiceImpl")
public class ValueSetServiceImpl implements ValueSetService {

  private final Logger logger = LoggerFactory.getLogger(ValueSetServiceImpl.class);

  @Autowired
  @Qualifier("jsonParser")
  IParser jsonParser;

  @Autowired
  @Qualifier("valueSetDaoImpl")
  ValueSetDao valueSetDao;

  @Override
  public void createValueSet(ValueSet valueSet) {
    ValueSetModel valueSetModel = new ValueSetModel();
    valueSetModel.setValueSetId(valueSet.getId());
    valueSetModel.setData(jsonParser.encodeResourceToString(valueSet));

    valueSetDao.createValueset(valueSetModel);
  }

  @Override
  public void createValueSetGrouper(ValueSet valueSet) {

    List<CanonicalType> valuesetList = null;
    ValueSetGrouperModel valueSetGrouperModel = null;
    ValueSetComposeComponent valueSetComposeComponent = valueSet.getCompose();
    List<ConceptSetComponent> conceptSetComponentList = valueSetComposeComponent.getInclude();
    for (ConceptSetComponent conceptSetComponent : conceptSetComponentList) {
      valuesetList = conceptSetComponent.getValueSet();
      for (CanonicalType canonicalType : valuesetList) {
        valueSetGrouperModel = new ValueSetGrouperModel();
        valueSetGrouperModel.setValueSetGrouper(valueSet.getUrl());
        valueSetGrouperModel.setValueSetId(canonicalType.getValueAsString());
        valueSetDao.createValuesetGrouper(valueSetGrouperModel);
      }
    }
  }

  @Override
  public void createPlanDefinitionAction(TriggerDefinition triggerDefinition) {

    List<DataRequirement> datareqs = triggerDefinition.getData();

    Set<ValueSet> grouperToValueSets = new HashSet<>();
    Set<ValueSet> grouperToEmergentValueSets = new HashSet<>();

    for (DataRequirement d : datareqs) {

      DataRequirementCodeFilterComponent codeFilter = d.getCodeFilterFirstRep();

      logger.info(" Getting Value Set List for Grouper {}", codeFilter.getValueSet());

      List<CanonicalType> valueSetIdList =
          ApplicationUtils.getValueSetListFromGrouper(codeFilter.getValueSet());

      logger.info(
          " Size of valueSetIdList = {}",
          ((valueSetIdList == null) ? "Null" : valueSetIdList.size()));

      grouperToValueSets = ApplicationUtils.getValueSetByIds(valueSetIdList);

      logger.info(" Size of Value Sets for Grouper : {}", grouperToValueSets.size());

      grouperToEmergentValueSets = ApplicationUtils.getEmergentValueSetByIds(valueSetIdList);

      logger.debug(" Size of Covid Value Sets for Grouper : {}", grouperToEmergentValueSets.size());
    }

    DataRequirement dataRequirement = triggerDefinition.getDataFirstRep();
    DataRequirementCodeFilterComponent codeFilter = dataRequirement.getCodeFilterFirstRep();

    List<CanonicalType> valueSetIdList =
        ApplicationUtils.getValueSetListFromGrouper(codeFilter.getValueSet());
    Set<ValueSet> valueSets = ApplicationUtils.getValueSetByIds(valueSetIdList);

    ValueSet valuSetGrouper = ApplicationUtils.getValueSetGrouperFromId(codeFilter.getValueSet());

    String path = dataRequirement.getType() + "." + codeFilter.getPath();
    logger.debug(
        " Trigger Path to Grouper Map {} , Grouper {}",
        path,
        valuSetGrouper == null ? "NULL" : valuSetGrouper.getId());

    ValueSetSingleton.getInstance().getTriggerPathToValueSetsMap().put(path, valueSets);

    if (ValueSetSingleton.getInstance().getTriggerPathToGrouperMap().containsKey(path)) {
      logger.debug(" Found Path in Grouper Map for {}", path);
      if (Boolean.FALSE.equals(
          ApplicationUtils.isSetContainsValueSet(
              ValueSetSingleton.getInstance().getTriggerPathToGrouperMap().get(path),
              valuSetGrouper))) {
        ValueSetSingleton.getInstance().getTriggerPathToGrouperMap().get(path).add(valuSetGrouper);
      }
    } else {
      logger.debug(" Did not Find Path in Grouper Map for {}", path);
      Set<ValueSet> vs = new HashSet<>();
      vs.add(valuSetGrouper);
      ValueSetSingleton.getInstance().getTriggerPathToGrouperMap().put(path, vs);
    }

    if (valuSetGrouper != null) {

      logger.debug(" Adding Grouper Id {} to map", codeFilter.getValueSet());
      ValueSetSingleton.getInstance()
          .addGrouperToValueSetMap(valuSetGrouper.getId(), grouperToValueSets);
      ValueSetSingleton.getInstance()
          .addGrouperToEmergentValueSetMap(valuSetGrouper.getId(), grouperToEmergentValueSets);
    }
  }
}
