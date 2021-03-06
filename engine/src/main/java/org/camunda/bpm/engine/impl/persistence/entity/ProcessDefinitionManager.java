/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camunda.bpm.engine.impl.persistence.entity;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.Page;
import org.camunda.bpm.engine.impl.ProcessDefinitionQueryImpl;
import org.camunda.bpm.engine.impl.ProcessEngineLogger;
import org.camunda.bpm.engine.impl.ProcessInstanceQueryImpl;
import org.camunda.bpm.engine.impl.bpmn.deployer.BpmnDeployer;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.auth.ResourceAuthorizationProvider;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.db.EnginePersistenceLogger;
import org.camunda.bpm.engine.impl.db.ListQueryParameterObject;
import org.camunda.bpm.engine.impl.event.EventType;
import org.camunda.bpm.engine.impl.jobexecutor.TimerStartEventJobHandler;
import org.camunda.bpm.engine.impl.persistence.AbstractManager;
import org.camunda.bpm.engine.impl.persistence.AbstractResourceDefinitionManager;
import org.camunda.bpm.engine.impl.persistence.deploy.Deployer;
import org.camunda.bpm.engine.impl.persistence.deploy.cache.DeploymentCache;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.Job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author Tom Baeyens
 * @author Falko Menge
 * @author Saeid Mirzaei
 * @author Christopher Zell
 */
public class ProcessDefinitionManager extends AbstractManager implements AbstractResourceDefinitionManager<ProcessDefinitionEntity> {

  protected static final EnginePersistenceLogger LOG = ProcessEngineLogger.PERSISTENCE_LOGGER;

  // insert ///////////////////////////////////////////////////////////

  public void insertProcessDefinition(ProcessDefinitionEntity processDefinition) {
    getDbEntityManager().insert(processDefinition);
    createDefaultAuthorizations(processDefinition);
  }

  // select ///////////////////////////////////////////////////////////

  /**
   * @return the latest version of the process definition with the given key (from any tenant)
   *
   * @throws ProcessEngineException if more than one tenant has a process definition with the given key
   *
   * @see #findLatestProcessDefinitionByKeyAndTenantId(String, String)
   */
  public ProcessDefinitionEntity findLatestProcessDefinitionByKey(String processDefinitionKey) {
    @SuppressWarnings("unchecked")
    List<ProcessDefinitionEntity> processDefinitions = getDbEntityManager().selectList("selectLatestProcessDefinitionByKey", configureParameterizedQuery(processDefinitionKey));

    if (processDefinitions.isEmpty()) {
      return null;

    } else if (processDefinitions.size() == 1) {
      return processDefinitions.iterator().next();

    } else {
      throw LOG.multipleTenantsForProcessDefinitionKeyException(processDefinitionKey);
    }
  }

  /**
   * @return the latest version of the process definition with the given key and tenant id
   *
   * @see #findLatestProcessDefinitionByKeyAndTenantId(String, String)
   */
  public ProcessDefinitionEntity findLatestProcessDefinitionByKeyAndTenantId(String processDefinitionKey, String tenantId) {
    Map<String, String> parameters = new HashMap<String, String>();
    parameters.put("processDefinitionKey", processDefinitionKey);
    parameters.put("tenantId", tenantId);

    if (tenantId == null) {
      return (ProcessDefinitionEntity) getDbEntityManager().selectOne("selectLatestProcessDefinitionByKeyWithoutTenantId", parameters);
    } else {
      return (ProcessDefinitionEntity) getDbEntityManager().selectOne("selectLatestProcessDefinitionByKeyAndTenantId", parameters);
    }
  }

  public ProcessDefinitionEntity findLatestProcessDefinitionById(String processDefinitionId) {
    return getDbEntityManager().selectById(ProcessDefinitionEntity.class, processDefinitionId);
  }

  @SuppressWarnings({ "unchecked" })
  public List<ProcessDefinition> findProcessDefinitionsByQueryCriteria(ProcessDefinitionQueryImpl processDefinitionQuery, Page page) {
    configureProcessDefinitionQuery(processDefinitionQuery);
    return getDbEntityManager().selectList("selectProcessDefinitionsByQueryCriteria", processDefinitionQuery, page);
  }

  public long findProcessDefinitionCountByQueryCriteria(ProcessDefinitionQueryImpl processDefinitionQuery) {
    configureProcessDefinitionQuery(processDefinitionQuery);
    return (Long) getDbEntityManager().selectOne("selectProcessDefinitionCountByQueryCriteria", processDefinitionQuery);
  }

  public ProcessDefinitionEntity findProcessDefinitionByDeploymentAndKey(String deploymentId, String processDefinitionKey) {
    Map<String, Object> parameters = new HashMap<String, Object>();
    parameters.put("deploymentId", deploymentId);
    parameters.put("processDefinitionKey", processDefinitionKey);
    return (ProcessDefinitionEntity) getDbEntityManager().selectOne("selectProcessDefinitionByDeploymentAndKey", parameters);
  }

  public ProcessDefinitionEntity findProcessDefinitionByKeyVersionAndTenantId(String processDefinitionKey, Integer processDefinitionVersion, String tenantId) {
    Map<String, Object> parameters = new HashMap<String, Object>();
    parameters.put("processDefinitionVersion", processDefinitionVersion);
    parameters.put("processDefinitionKey", processDefinitionKey);
    parameters.put("tenantId", tenantId);

    @SuppressWarnings("unchecked")
    List<ProcessDefinitionEntity> results = getDbEntityManager().selectList("selectProcessDefinitionByKeyVersionAndTenantId", parameters);
    if (results.size() == 1) {
      return results.get(0);
    } else if (results.size() > 1) {
      throw LOG.toManyProcessDefinitionsException(results.size(), processDefinitionKey, processDefinitionVersion, tenantId);
    }
    return null;
  }

  public List<ProcessDefinition> findProcessDefinitionsByKey(String processDefinitionKey) {
    ProcessDefinitionQueryImpl processDefinitionQuery = new ProcessDefinitionQueryImpl()
      .processDefinitionKey(processDefinitionKey);
    return  findProcessDefinitionsByQueryCriteria(processDefinitionQuery, null);
  }

  public List<ProcessDefinition> findProcessDefinitionsStartableByUser(String user) {
    return new ProcessDefinitionQueryImpl().startableByUser(user).list();
  }

  public String findPreviousProcessDefinitionId(String processDefinitionKey, Integer version, String tenantId) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("key", processDefinitionKey);
    params.put("version", version);
    params.put("tenantId", tenantId);
    return (String) getDbEntityManager().selectOne("selectPreviousProcessDefinitionId", params);
  }

  @SuppressWarnings("unchecked")
  public List<ProcessDefinition> findProcessDefinitionsByDeploymentId(String deploymentId) {
    return getDbEntityManager().selectList("selectProcessDefinitionByDeploymentId", deploymentId);
  }

  @SuppressWarnings("unchecked")
  public List<ProcessDefinition> findProcessDefinitionsByKeyIn(String... keys) {
    return getDbEntityManager().selectList("selectProcessDefinitionByKeyIn", keys);
  }

  @SuppressWarnings("unchecked")
  public List<ProcessDefinition> findDefinitionsByKeyAndTenantId(String processDefinitionKey, String tenantId, boolean isTenantIdSet) {
    Map<String, Object> parameters = new HashMap<String, Object>();
    parameters.put("processDefinitionKey", processDefinitionKey);
    parameters.put("isTenantIdSet", isTenantIdSet);
    parameters.put("tenantId", tenantId);

    return getDbEntityManager().selectList("selectProcessDefinitions", parameters);
  }

  @SuppressWarnings("unchecked")
  public List<ProcessDefinition> findDefinitionsByIds(List<String> processDefinitionIds) {
    Map<String, Object> parameters = new HashMap<String, Object>();
    parameters.put("processDefinitionIds", processDefinitionIds);
    parameters.put("isTenantIdSet", false);

    return getDbEntityManager().selectList("selectProcessDefinitions", parameters);
  }

  // update ///////////////////////////////////////////////////////////

  public void updateProcessDefinitionSuspensionStateById(String processDefinitionId, SuspensionState suspensionState) {
    Map<String, Object> parameters = new HashMap<String, Object>();
    parameters.put("processDefinitionId", processDefinitionId);
    parameters.put("suspensionState", suspensionState.getStateCode());
    getDbEntityManager().update(ProcessDefinitionEntity.class, "updateProcessDefinitionSuspensionStateByParameters", configureParameterizedQuery(parameters));
  }

  public void updateProcessDefinitionSuspensionStateByKey(String processDefinitionKey, SuspensionState suspensionState) {
    Map<String, Object> parameters = new HashMap<String, Object>();
    parameters.put("processDefinitionKey", processDefinitionKey);
    parameters.put("isTenantIdSet", false);
    parameters.put("suspensionState", suspensionState.getStateCode());
    getDbEntityManager().update(ProcessDefinitionEntity.class, "updateProcessDefinitionSuspensionStateByParameters", configureParameterizedQuery(parameters));
  }

  public void updateProcessDefinitionSuspensionStateByKeyAndTenantId(String processDefinitionKey, String tenantId, SuspensionState suspensionState) {
    Map<String, Object> parameters = new HashMap<String, Object>();
    parameters.put("processDefinitionKey", processDefinitionKey);
    parameters.put("isTenantIdSet", true);
    parameters.put("tenantId", tenantId);
    parameters.put("suspensionState", suspensionState.getStateCode());
    getDbEntityManager().update(ProcessDefinitionEntity.class, "updateProcessDefinitionSuspensionStateByParameters", configureParameterizedQuery(parameters));
  }

  // delete  ///////////////////////////////////////////////////////////

  /**
   * Cascades the deletion of the process definition to the process instances.
   * Skips the custom listeners if the flag was set to true.
   *
   * @param processDefinitionId the process definition id
   * @param skipCustomListeners true if the custom listeners should be skipped at process instance deletion
   */
  protected void cascadeDeleteProcessInstancesForProcessDefinition(String processDefinitionId, boolean skipCustomListeners) {
    getProcessInstanceManager()
        .deleteProcessInstancesByProcessDefinition(processDefinitionId, "deleted process definition", true, skipCustomListeners, false);
  }

  /**
   * Cascades the deletion of a process definition to the history, deletes the history.
   *
   * @param processDefinitionId the process definition id
   */
  protected void cascadeDeleteHistoryForProcessDefinition(String processDefinitionId) {
     // remove historic incidents which are not referenced to a process instance
    getHistoricIncidentManager().deleteHistoricIncidentsByProcessDefinitionId(processDefinitionId);

     // remove historic identity links which are not reference to a process instance
    getHistoricIdentityLinkManager().deleteHistoricIdentityLinksLogByProcessDefinitionId(processDefinitionId);

     // remove historic job log entries not related to a process instance
    getHistoricJobLogManager().deleteHistoricJobLogsByProcessDefinitionId(processDefinitionId);
  }

  /**
   * Deletes the timer start events for the given process definition.
   *
   * @param processDefinition the process definition
   */
  protected void deleteTimerStartEventsForProcessDefinition(ProcessDefinition processDefinition) {
    List<JobEntity> timerStartJobs = getJobManager().findJobsByConfiguration(TimerStartEventJobHandler.TYPE, processDefinition.getKey(), processDefinition.getTenantId());

    ProcessDefinitionEntity latestVersion = getProcessDefinitionManager()
        .findLatestProcessDefinitionByKeyAndTenantId(processDefinition.getKey(), processDefinition.getTenantId());

    // delete timer start event jobs only if this is the latest version of the process definition.
    if(latestVersion != null && latestVersion.getId().equals(processDefinition.getId())) {
      for (Job job : timerStartJobs) {
        ((JobEntity)job).delete();
      }
    }
  }

  /**
   * Deletes the subscriptions for the process definition, which is
   * identified by the given process definition id.
   *
   * @param processDefinitionId the id of the process definition
   */
  public void deleteSubscriptionsForProcessDefinition(String processDefinitionId) {
    List<EventSubscriptionEntity> eventSubscriptionsToRemove = new ArrayList<EventSubscriptionEntity>();
    // remove message event subscriptions:
    List<EventSubscriptionEntity> messageEventSubscriptions = getEventSubscriptionManager()
      .findEventSubscriptionsByConfiguration(EventType.MESSAGE.name(), processDefinitionId);
    eventSubscriptionsToRemove.addAll(messageEventSubscriptions);

    // remove signal event subscriptions:
    List<EventSubscriptionEntity> signalEventSubscriptions = getEventSubscriptionManager()
        .findEventSubscriptionsByConfiguration(EventType.SIGNAL.name(), processDefinitionId);
    eventSubscriptionsToRemove.addAll(signalEventSubscriptions);

    // remove conditional event subscriptions:
    List<EventSubscriptionEntity> conditionalEventSubscriptions = getEventSubscriptionManager()
        .findEventSubscriptionsByConfiguration(EventType.CONDITONAL.name(), processDefinitionId);
    eventSubscriptionsToRemove.addAll(conditionalEventSubscriptions);

    List<EventSubscriptionEntity> cachedEventSubscriptions = getCommandContext().getDbEntityManager().getCachedEntitiesByType(EventSubscriptionEntity.class);

    if (cachedEventSubscriptions != null && !cachedEventSubscriptions.isEmpty()) {
      for (EventSubscriptionEntity entity : cachedEventSubscriptions) {
        if (processDefinitionId.equals(entity.getConfiguration()) && entity.getExecutionId() == null && !eventSubscriptionsToRemove.contains(entity)) {
          eventSubscriptionsToRemove.add(entity);
        }
      }
    }

    for (EventSubscriptionEntity eventSubscriptionEntity : eventSubscriptionsToRemove) {
      eventSubscriptionEntity.delete();
    }
  }

 /**
  * Deletes the given process definition from the database and cache.
  * If cascadeToHistory and cascadeToInstances is set to true it deletes
  * the history and the process instances.
  *
  * *Note*: If more than one process definition, from one deployment, is deleted in
  * a single transaction and the cascadeToHistory and cascadeToInstances flag was set to true it
  * can cause a dirty deployment cache. The process instances of ALL process definitions must be deleted,
  * before every process definition can be deleted! In such cases the cascadeToInstances flag
  * have to set to false!
  *
  * On deletion of all process instances, the task listeners will be deleted as well.
  * Deletion of tasks and listeners needs the redeployment of deployments.
  * It can cause to problems if is done sequential with the deletion of process definition
  * in a single transaction.
  *
  * *For example*:
  * Deployment contains two process definition. First process definition
  * and instances will be removed, also cleared from the cache.
  * Second process definition will be removed and his instances.
  * Deletion of instances will cause redeployment this deploys again
  * first into the cache. Only the second will be removed from cache and
  * first remains in the cache after the deletion process.
  *
  * @param processDefinition the process definition which should be deleted
  * @param processDefinitionId the id of the process definition
  * @param cascadeToHistory if true the history will deleted as well
  * @param cascadeToInstances if true the process instances are deleted as well
  * @param skipCustomListeners if true skips the custom listeners on deletion of instances
  */
  public void deleteProcessDefinition(ProcessDefinition processDefinition, String processDefinitionId, boolean cascadeToHistory, boolean cascadeToInstances, boolean skipCustomListeners) {

    if (cascadeToHistory) {
      cascadeDeleteHistoryForProcessDefinition(processDefinitionId);
      if (cascadeToInstances) {
        cascadeDeleteProcessInstancesForProcessDefinition(processDefinitionId, skipCustomListeners);
      }
    } else {
      ProcessInstanceQueryImpl procInstQuery = new ProcessInstanceQueryImpl().processDefinitionId(processDefinitionId);
      long processInstanceCount = getProcessInstanceManager().findProcessInstanceCountByQueryCriteria(procInstQuery);
      if (processInstanceCount != 0) {
        throw LOG.deleteProcessDefinitionWithProcessInstancesException(processDefinitionId, processInstanceCount);
      }
    }

    // remove related authorization parameters in IdentityLink table
    getIdentityLinkManager().deleteIdentityLinksByProcDef(processDefinitionId);

    // remove timer start events:
    deleteTimerStartEventsForProcessDefinition(processDefinition);

    //delete process definition from database
    getDbEntityManager().delete(ProcessDefinitionEntity.class, "deleteProcessDefinitionsById", processDefinitionId);

    // remove process definition from cache:
    Context
      .getProcessEngineConfiguration()
      .getDeploymentCache()
      .removeProcessDefinition(processDefinitionId);

    deleteSubscriptionsForProcessDefinition(processDefinitionId);

    addSubscriptionsFromPreviousVersion((ProcessDefinitionEntity) processDefinition);

    // delete job definitions
    getJobDefinitionManager().deleteJobDefinitionsByProcessDefinitionId(processDefinition.getId());

    ((ProcessDefinitionEntity) processDefinition).setDeleted(true);
  }

  protected void addSubscriptionsFromPreviousVersion(ProcessDefinitionEntity processDefinition) {
    //we don't want to take the process definition from deployment cache, as it can have inconsistent value in "deleted" flag
    //instead we take it from DbEntityCache (or the database)
    String previousProcessDefinitionId = processDefinition.getPreviousProcessDefinitionId();
    if (previousProcessDefinitionId != null) {
      ProcessDefinitionEntity previousDefinition = findLatestProcessDefinitionById(previousProcessDefinitionId);

      //if not deleted, than add event subscriptions
      if (previousDefinition != null && !previousDefinition.isDeleted()) {
        ProcessEngineConfigurationImpl configuration = Context.getProcessEngineConfiguration();
        DeploymentCache deploymentCache = configuration.getDeploymentCache();
        previousDefinition = deploymentCache.resolveProcessDefinition(previousDefinition);

        List<Deployer> deployers = configuration.getDeployers();
        for (Deployer deployer : deployers) {
          if (deployer instanceof BpmnDeployer) {
            ((BpmnDeployer) deployer).addEventSubscriptions(previousDefinition);
          }
        }
      }
    }
  }

  // helper ///////////////////////////////////////////////////////////

  protected void createDefaultAuthorizations(ProcessDefinition processDefinition) {
    if(isAuthorizationEnabled()) {
      ResourceAuthorizationProvider provider = getResourceAuthorizationProvider();
      AuthorizationEntity[] authorizations = provider.newProcessDefinition(processDefinition);
      saveDefaultAuthorizations(authorizations);
    }
  }

  protected void configureProcessDefinitionQuery(ProcessDefinitionQueryImpl query) {
    getAuthorizationManager().configureProcessDefinitionQuery(query);
    getTenantManager().configureQuery(query);
  }

  protected ListQueryParameterObject configureParameterizedQuery(Object parameter) {
    return getTenantManager().configureQuery(parameter);
  }

  @Override
  public ProcessDefinitionEntity findLatestDefinitionByKey(String key) {
    return findLatestProcessDefinitionByKey(key);
  }

  @Override
  public ProcessDefinitionEntity findLatestDefinitionById(String id) {
    return findLatestProcessDefinitionById(id);
  }

  @Override
  public ProcessDefinitionEntity getCachedResourceDefinitionEntity(String definitionId) {
    return getDbEntityManager().getCachedEntity(ProcessDefinitionEntity.class, definitionId);
  }

  @Override
  public ProcessDefinitionEntity findLatestDefinitionByKeyAndTenantId(String definitionKey, String tenantId) {
    return findLatestProcessDefinitionByKeyAndTenantId(definitionKey, tenantId);
  }

  @Override
  public ProcessDefinitionEntity findDefinitionByKeyVersionAndTenantId(String definitionKey, Integer definitionVersion, String tenantId) {
    return findProcessDefinitionByKeyVersionAndTenantId(definitionKey, definitionVersion, tenantId);
  }

  @Override
  public ProcessDefinitionEntity findDefinitionByDeploymentAndKey(String deploymentId, String definitionKey) {
    return findProcessDefinitionByDeploymentAndKey(deploymentId, definitionKey);
  }
}
