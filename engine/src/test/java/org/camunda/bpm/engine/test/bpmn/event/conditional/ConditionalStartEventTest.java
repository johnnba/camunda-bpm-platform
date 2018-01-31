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

package org.camunda.bpm.engine.test.bpmn.event.conditional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.impl.event.EventType;
import org.camunda.bpm.engine.impl.persistence.entity.EventSubscriptionEntity;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.EventSubscription;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.VariableInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class ConditionalStartEventTest {

  private static final String SINGLE_CONDITIONAL_XML = "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testSingleConditionalStartEvent1.bpmn20.xml";
  private static final String MULTIPLE_CONDITIONS = "multipleConditions";
  private static final String TRUE_CONDITION_PROCESS = "trueConditionProcess";
  private static final String CONDITIONAL_EVENT_PROCESS = "conditionalEventProcess";
  private static final BpmnModelInstance MODEL_WITHOUT_CONDITION = Bpmn.createExecutableProcess(CONDITIONAL_EVENT_PROCESS)
      .startEvent()
      .userTask()
      .endEvent()
      .done();

  protected ProcessEngineRule engineRule = new ProvidedProcessEngineRule();

  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  @Rule
  public ExpectedException thrown= ExpectedException.none();

  protected RepositoryService repositoryService;
  protected RuntimeService runtimeService;

  @Before
  public void setUp() throws Exception {
    repositoryService = engineRule.getRepositoryService();
    runtimeService = engineRule.getRuntimeService();
  }

  @Test
  @Deployment(resources = "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testSingleConditionalStartEvent.bpmn20.xml")
  public void testDeploymentCreatesSubscriptions() {
    // given a deployed process
    String processDefinitionId = repositoryService.createProcessDefinitionQuery().processDefinitionKey(CONDITIONAL_EVENT_PROCESS).singleResult().getId();

    // when
    List<EventSubscription> eventSubscriptions = runtimeService.createEventSubscriptionQuery().list();

    // then
    assertEquals(1, eventSubscriptions.size());
    EventSubscriptionEntity conditionalEventSubscription = (EventSubscriptionEntity) eventSubscriptions.get(0);
    assertEquals(EventType.CONDITONAL.name(), conditionalEventSubscription.getEventType());
    assertEquals(processDefinitionId, conditionalEventSubscription.getConfiguration());
    assertNull(conditionalEventSubscription.getEventName());
    assertNull(conditionalEventSubscription.getExecutionId());
    assertNull(conditionalEventSubscription.getProcessInstanceId());
  }

  @Test
  @Deployment(resources = "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testSingleConditionalStartEvent.bpmn20.xml")
  public void testUpdateProcessVersionCancelsSubscriptions() {
    // given a deployed process
    List<EventSubscription> eventSubscriptions = runtimeService.createEventSubscriptionQuery().list();
    List<ProcessDefinition> processDefinitions = repositoryService.createProcessDefinitionQuery().list();

    assertEquals(1, eventSubscriptions.size());
    assertEquals(1, processDefinitions.size());

    // when
    testRule.deploy("org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testSingleConditionalStartEvent.bpmn20.xml");

    // then
    List<EventSubscription> newEventSubscriptions = runtimeService.createEventSubscriptionQuery().list();
    List<ProcessDefinition> newProcessDefinitions = repositoryService.createProcessDefinitionQuery().list();

    assertEquals(1, newEventSubscriptions.size());
    assertEquals(2, newProcessDefinitions.size());
    for (ProcessDefinition processDefinition : newProcessDefinitions) {
      if (processDefinition.getVersion() == 1) {
        for (EventSubscription subscription : newEventSubscriptions) {
          EventSubscriptionEntity subscriptionEntity = (EventSubscriptionEntity) subscription;
          assertFalse(subscriptionEntity.getConfiguration().equals(processDefinition.getId()));
        }
      } else {
        for (EventSubscription subscription : newEventSubscriptions) {
          EventSubscriptionEntity subscriptionEntity = (EventSubscriptionEntity) subscription;
          assertTrue(subscriptionEntity.getConfiguration().equals(processDefinition.getId()));
        }
      }
    }
    assertFalse(eventSubscriptions.equals(newEventSubscriptions));
  }

  @Test
  @Deployment(resources = "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testSingleConditionalStartEvent.bpmn20.xml")
  public void testEventSubscriptionAfterDeleteLatestProcessVersion() {
    // given a deployed process
    ProcessDefinition processDefinitionV1 = repositoryService.createProcessDefinitionQuery().singleResult();
    assertNotNull(processDefinitionV1);

    // deploy second version of the process
    String deploymentId = testRule.deploy(SINGLE_CONDITIONAL_XML).getId();

    // when
    repositoryService.deleteDeployment(deploymentId, true);

    // then
    ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionKey(CONDITIONAL_EVENT_PROCESS).singleResult();
    assertEquals(processDefinitionV1.getId(), processDefinition.getId());

    EventSubscriptionEntity eventSubscription = (EventSubscriptionEntity) runtimeService.createEventSubscriptionQuery().singleResult();
    assertNotNull(eventSubscription);
    assertEquals(processDefinitionV1.getId(), eventSubscription.getConfiguration());
  }

  @Test
  @Deployment(resources = "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testSingleConditionalStartEvent.bpmn20.xml")
  public void testStartInstanceAfterDeleteLatestProcessVersion() {
    // given a deployed process

    // deploy second version of the process
    String deploymentId = testRule.deploy(SINGLE_CONDITIONAL_XML).getId();
    org.camunda.bpm.engine.repository.Deployment deployment = repositoryService.createDeploymentQuery().deploymentId(deploymentId).singleResult();

    // delete it
    repositoryService.deleteDeployment(deployment.getId(), true);

    // when
    List<ProcessInstance> conditionInstances = runtimeService
        .createConditionEvaluation()
        .setVariable("foo", 1)
        .evaluateStartConditions();

    // then
    assertEquals(1, conditionInstances.size());
    assertNotNull(conditionInstances.get(0));
  }

  @Test
  public void testVersionWithoutConditionAfterDeleteLatestProcessVersionWithCondition() {
    // given a process
    testRule.deploy(MODEL_WITHOUT_CONDITION);

    // deploy second version of the process
    String deploymentId = testRule.deploy(SINGLE_CONDITIONAL_XML).getId();
    org.camunda.bpm.engine.repository.Deployment deployment = repositoryService.createDeploymentQuery().deploymentId(deploymentId).singleResult();

    // delete it
    repositoryService.deleteDeployment(deployment.getId(), true);

    thrown.expect(ProcessEngineException.class);
    thrown.expectMessage("No subscriptions were found during evaluation of the conditional start events.");

    // when
    runtimeService
      .createConditionEvaluation()
      .setVariable("foo", 1)
      .evaluateStartConditions();
  }

  @Test
  public void testSubscriptionsWhenDeletingProcessDefinitionsInOneTransactionByKeys() {
    // given three versions of the process
    testRule.deploy(SINGLE_CONDITIONAL_XML);
    testRule.deploy(SINGLE_CONDITIONAL_XML);
    testRule.deploy(SINGLE_CONDITIONAL_XML);

    // when
    repositoryService.deleteProcessDefinitions()
      .byKey(CONDITIONAL_EVENT_PROCESS)
      .delete();

    // then
    assertEquals(0, runtimeService.createEventSubscriptionQuery().count());
  }

  @Test
  public void testSubscriptionsWhenDeletingProcessDefinitionsInOneTransactionByIdOrdered() {
    // given
    String definitionId1 = deployProcess(SINGLE_CONDITIONAL_XML);
    String definitionId2 = deployProcess(SINGLE_CONDITIONAL_XML);
    String definitionId3 = deployProcess(SINGLE_CONDITIONAL_XML);

    // when
    repositoryService.deleteProcessDefinitions()
        .byIds(definitionId1, definitionId2, definitionId3)
        .delete();

    // then
    assertEquals(0, runtimeService.createEventSubscriptionQuery().count());
  }

  @Test
  public void testSubscriptionsWhenDeletingProcessDefinitionsInOneTransactionByIdReverseOrder() {
    // given
    String definitionId1 = deployProcess(SINGLE_CONDITIONAL_XML);
    String definitionId2 = deployProcess(SINGLE_CONDITIONAL_XML);
    String definitionId3 = deployProcess(SINGLE_CONDITIONAL_XML);

    // when
    repositoryService.deleteProcessDefinitions()
        .byIds(definitionId3, definitionId2, definitionId1)
        .delete();

    // then
    assertEquals(0, runtimeService.createEventSubscriptionQuery().count());
  }

  @Test
  public void testMixedSubscriptionsWhenDeletingProcessDefinitionsInOneTransactionById1() {
    // given first version without condition
    String definitionId1 = deployModel(MODEL_WITHOUT_CONDITION);
    String definitionId2 = deployProcess(SINGLE_CONDITIONAL_XML);
    String definitionId3 = deployProcess(SINGLE_CONDITIONAL_XML);

    // when
    repositoryService.deleteProcessDefinitions()
        .byIds(definitionId1, definitionId2, definitionId3)
        .delete();

    // then
    assertEquals(0, runtimeService.createEventSubscriptionQuery().count());
  }

  @Test
  public void testMixedSubscriptionsWhenDeletingProcessDefinitionsInOneTransactionById2() {
    // given second version without condition
    String definitionId1 = deployProcess(SINGLE_CONDITIONAL_XML);
    String definitionId2 = deployModel(MODEL_WITHOUT_CONDITION);
    String definitionId3 = deployProcess(SINGLE_CONDITIONAL_XML);

    // when
    repositoryService.deleteProcessDefinitions()
        .byIds(definitionId1, definitionId2, definitionId3)
        .delete();

    // then
    assertEquals(0, runtimeService.createEventSubscriptionQuery().count());
  }

  @Test
  public void testMixedSubscriptionsWhenDeletingProcessDefinitionsInOneTransactionById3() {
    // given third version without condition
    String definitionId1 = deployProcess(SINGLE_CONDITIONAL_XML);
    String definitionId2 = deployProcess(SINGLE_CONDITIONAL_XML);
    String definitionId3 = deployModel(MODEL_WITHOUT_CONDITION);

    // when
    repositoryService.deleteProcessDefinitions()
        .byIds(definitionId1, definitionId2, definitionId3)
        .delete();

    // then
    assertEquals(0, runtimeService.createEventSubscriptionQuery().count());
  }

  @Test
  public void testMixedSubscriptionsWhenDeletingTwoProcessDefinitionsInOneTransaction1() {
    // given first version without condition
    String definitionId1 = deployModel(MODEL_WITHOUT_CONDITION);
    String definitionId2 = deployProcess(SINGLE_CONDITIONAL_XML);
    String definitionId3 = deployProcess(SINGLE_CONDITIONAL_XML);

    // when
    repositoryService.deleteProcessDefinitions()
        .byIds(definitionId2, definitionId3)
        .delete();

    // then
    assertEquals(0, runtimeService.createEventSubscriptionQuery().count());
    assertEquals(definitionId1, repositoryService.createProcessDefinitionQuery().singleResult().getId());
  }

  @Test
  public void testMixedSubscriptionsWhenDeletingTwoProcessDefinitionsInOneTransaction2() {
    // given second version without condition
    String definitionId1 = deployProcess(SINGLE_CONDITIONAL_XML);
    String definitionId2 = deployModel(MODEL_WITHOUT_CONDITION);
    String definitionId3 = deployProcess(SINGLE_CONDITIONAL_XML);

    // when
    repositoryService.deleteProcessDefinitions()
        .byIds(definitionId2, definitionId3)
        .delete();

    // then
    assertEquals(1, runtimeService.createEventSubscriptionQuery().count());
    assertEquals(definitionId1, ((EventSubscriptionEntity) runtimeService.createEventSubscriptionQuery().singleResult()).getConfiguration());
  }

  @Test
  public void testMixedSubscriptionsWhenDeletingTwoProcessDefinitionsInOneTransaction3() {
    // given third version without condition
    String definitionId1 = deployProcess(SINGLE_CONDITIONAL_XML);
    String definitionId2 = deployProcess(SINGLE_CONDITIONAL_XML);
    String definitionId3 = deployModel(MODEL_WITHOUT_CONDITION);

    // when
    repositoryService.deleteProcessDefinitions()
        .byIds(definitionId2, definitionId3)
        .delete();

    // then
    assertEquals(1, runtimeService.createEventSubscriptionQuery().count());
    assertEquals(definitionId1, ((EventSubscriptionEntity) runtimeService.createEventSubscriptionQuery().singleResult()).getConfiguration());
  }

  @Test
  public void testDeploymentOfTwoEqualConditionalStartEvent() {
    // expect
    thrown.expect(ProcessEngineException.class);
    thrown.expectMessage("Error while parsing process");

    // when
    testRule.deploy("org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testTwoEqualConditionalStartEvent.bpmn20.xml");

    List<EventSubscription> eventSubscriptions = runtimeService.createEventSubscriptionQuery().list();
    assertEquals(0, eventSubscriptions.size());
  }

  @Test
  @Deployment
  public void testStartInstanceWithTrueConditionalStartEvent() {
    // given a deployed process

    // when
    List<ProcessInstance> conditionInstances = runtimeService
        .createConditionEvaluation()
        .evaluateStartConditions();

    // then
    assertEquals(1, conditionInstances.size());

    List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery().processDefinitionKey(TRUE_CONDITION_PROCESS).list();
    assertEquals(1, processInstances.size());

    assertEquals(processInstances.get(0).getId(), conditionInstances.get(0).getId());
  }

  @Test
  @Deployment(resources = "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testSingleConditionalStartEvent.bpmn20.xml")
  public void testStartInstanceWithVariableCondition() {
    // given a deployed process

    // when
    List<ProcessInstance> instances = runtimeService
        .createConditionEvaluation()
        .setVariable("foo", 1)
        .evaluateStartConditions();

    // then
    assertEquals(1, instances.size());

    VariableInstance vars = runtimeService.createVariableInstanceQuery().singleResult();
    assertEquals(vars.getProcessInstanceId(), instances.get(0).getId());
    assertEquals(1, vars.getValue());
  }

  @Test
  @Deployment(resources = "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testSingleConditionalStartEvent.bpmn20.xml")
  public void testStartInstanceWithTransientVariableCondition() {
    // given a deployed process
    VariableMap variableMap = Variables.createVariables()
        .putValueTyped("foo", Variables.integerValue(1, true));

    // when
    List<ProcessInstance> instances = runtimeService
        .createConditionEvaluation()
        .setVariables(variableMap)
        .evaluateStartConditions();

    // then
    assertEquals(1, instances.size());

    VariableInstance vars = runtimeService.createVariableInstanceQuery().singleResult();
    assertNull(vars);
  }

  @Test
  @Deployment(resources = "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testSingleConditionalStartEvent.bpmn20.xml")
  public void testStartInstanceWithoutResult() {
    // given a deployed process

    // when
    List<ProcessInstance> processes = runtimeService
      .createConditionEvaluation()
      .setVariable("foo", 0)
      .evaluateStartConditions();

    assertNotNull(processes);
    assertEquals(0, processes.size());

    assertNull(runtimeService.createVariableInstanceQuery().singleResult());
    assertNull(runtimeService.createProcessInstanceQuery().processDefinitionKey(CONDITIONAL_EVENT_PROCESS).singleResult());
  }

  @Test
  @Deployment(resources = "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testMultipleCondition.bpmn20.xml")
  public void testStartInstanceWithMultipleConditions() {
    // given a deployed process with three conditional start events
    List<EventSubscription> eventSubscriptions = runtimeService.createEventSubscriptionQuery().list();

    assertEquals(3, eventSubscriptions.size());
    for (EventSubscription eventSubscription : eventSubscriptions) {
      assertEquals(EventType.CONDITONAL.name(), eventSubscription.getEventType());
    }

    Map<String, Object> variableMap = new HashMap<String, Object>();
    variableMap.put("foo", 1);
    variableMap.put("bar", true);

    // when
    List<ProcessInstance> resultInstances = runtimeService
        .createConditionEvaluation()
        .setVariables(variableMap)
        .evaluateStartConditions();

    // then
    assertEquals(2, resultInstances.size());

    List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery().processDefinitionKey(MULTIPLE_CONDITIONS).list();
    assertEquals(2, instances.size());
  }

  @Test
  @Deployment(resources = { "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testSingleConditionalStartEvent.bpmn20.xml",
                            "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testMultipleCondition.bpmn20.xml",
                            "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testStartInstanceWithTrueConditionalStartEvent.bpmn20.xml" })
  public void testStartInstanceWithMultipleSubscriptions() {
    // given three deployed processes
    List<EventSubscription> eventSubscriptions = runtimeService.createEventSubscriptionQuery().list();

    assertEquals(5, eventSubscriptions.size());

    Map<String, Object> variableMap = new HashMap<String, Object>();
    variableMap.put("foo", 1);
    variableMap.put("bar", true);

    // when
    List<ProcessInstance> instances = runtimeService
        .createConditionEvaluation()
        .setVariables(variableMap)
        .evaluateStartConditions();

    // then
    assertEquals(4, instances.size());
  }

  @Test
  @Deployment(resources = { "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testSingleConditionalStartEvent.bpmn20.xml",
                            "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testMultipleCondition.bpmn20.xml",
                            "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testStartInstanceWithTrueConditionalStartEvent.bpmn20.xml" })
  public void testStartInstanceWithMultipleSubscriptionsWithoutProvidingAllVariables() {
    // given three deployed processes
    List<EventSubscription> eventSubscriptions = runtimeService.createEventSubscriptionQuery().list();

    assertEquals(5, eventSubscriptions.size());

    Map<String, Object> variableMap = new HashMap<String, Object>();
    variableMap.put("foo", 1);

    // when, it should not throw PropertyNotFoundException
    List<ProcessInstance> instances = runtimeService
        .createConditionEvaluation()
        .setVariables(variableMap)
        .evaluateStartConditions();

    // then
    assertEquals(3, instances.size());
  }

  @Test
  @Deployment(resources = { "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testSingleConditionalStartEvent.bpmn20.xml",
                            "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testMultipleCondition.bpmn20.xml"})
  public void testStartInstanceWithBusinessKey() {
    // given two deployed processes
    List<EventSubscription> eventSubscriptions = runtimeService.createEventSubscriptionQuery().list();

    assertEquals(4, eventSubscriptions.size());

    // when
    List<ProcessInstance> instances = runtimeService
        .createConditionEvaluation()
        .setVariable("foo", 1)
        .processInstanceBusinessKey("humuhumunukunukuapua")
        .evaluateStartConditions();

    // then
    assertEquals(2, instances.size());
    assertEquals(2, runtimeService.createProcessInstanceQuery().processInstanceBusinessKey("humuhumunukunukuapua").count());
  }

  @Test
  @Deployment(resources = { "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testSingleConditionalStartEvent.bpmn20.xml",
                            "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testStartInstanceWithTrueConditionalStartEvent.bpmn20.xml" })
  public void testStartInstanceByProcessDefinitionId() {
    // given two deployed processes
    List<EventSubscription> eventSubscriptions = runtimeService.createEventSubscriptionQuery().list();

    assertEquals(2, eventSubscriptions.size());

    String processDefinitionId = repositoryService.createProcessDefinitionQuery().processDefinitionKey(TRUE_CONDITION_PROCESS).singleResult().getId();

    // when
    List<ProcessInstance> instances = runtimeService
        .createConditionEvaluation()
        .setVariable("foo", 1)
        .processDefinitionId(processDefinitionId)
        .evaluateStartConditions();

    // then
    assertEquals(1, instances.size());
    assertEquals(processDefinitionId, instances.get(0).getProcessDefinitionId());
  }

  @Test
  @Deployment(resources = { "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testSingleConditionalStartEvent.bpmn20.xml",
                            "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testMultipleCondition.bpmn20.xml"})
  public void testStartInstanceByProcessDefinitionFirstVersion() {
    // given two deployed processes
    String processDefinitionId = repositoryService.createProcessDefinitionQuery().processDefinitionKey(CONDITIONAL_EVENT_PROCESS).singleResult().getId();

    // assume
    List<EventSubscription> eventSubscriptions = runtimeService.createEventSubscriptionQuery().list();
    assertEquals(4, eventSubscriptions.size());

    // when deploy another version
    testRule.deploy("org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testSingleConditionalStartEvent.bpmn20.xml");

    List<ProcessInstance> instances = runtimeService
        .createConditionEvaluation()
        .setVariable("foo", 1)
        .processDefinitionId(processDefinitionId)
        .evaluateStartConditions();

    // then
    assertEquals(1, instances.size());
    assertEquals(processDefinitionId, instances.get(0).getProcessDefinitionId());
  }

  @Test
  @Deployment(resources = { "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testSingleConditionalStartEvent.bpmn20.xml",
                            "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testStartInstanceWithTrueConditionalStartEvent.bpmn20.xml" })
  public void testStartInstanceByNonExistingProcessDefinitionId() {
    // given two deployed processes
    List<EventSubscription> eventSubscriptions = runtimeService.createEventSubscriptionQuery().list();

    assertEquals(2, eventSubscriptions.size());

    thrown.expect(ProcessEngineException.class);
    thrown.expectMessage("no deployed process definition found with id 'nonExistingId': processDefinition is null");

    // when
    runtimeService
        .createConditionEvaluation()
        .setVariable("foo", 1)
        .processDefinitionId("nonExistingId")
        .evaluateStartConditions();
  }

  @Test
  @Deployment(resources = {"org/camunda/bpm/engine/test/api/oneTaskProcess.bpmn20.xml"})
  public void testStartInstanceByProcessDefinitionIdWithoutCondition() {
    // given deployed process without conditional start event
    String processDefinitionId = repositoryService.createProcessDefinitionQuery().processDefinitionKey("oneTaskProcess").singleResult().getId();

    List<EventSubscription> eventSubscriptions = runtimeService.createEventSubscriptionQuery().list();

    assertEquals(0, eventSubscriptions.size());

    thrown.expect(ProcessEngineException.class);
    thrown.expectMessage("No conditional start events were found during evaluation of the conditions by process definition with id: " + processDefinitionId);


    // when
    runtimeService
        .createConditionEvaluation()
        .processDefinitionId(processDefinitionId)
        .evaluateStartConditions();
  }

  @Test
  @Deployment
  public void testStartInstanceWithVariableName() {
    // given deployed process with two conditional start events:
    // ${true} variableName="foo"
    // ${true}

    // assume
    List<EventSubscription> eventSubscriptions = runtimeService.createEventSubscriptionQuery().list();
    assertEquals(2, eventSubscriptions.size());

    // when
    List<ProcessInstance> instances = runtimeService
        .createConditionEvaluation()
        .setVariable("foo", 42)
        .evaluateStartConditions();

    // then
    assertEquals(2, instances.size());
  }

  @Test
  @Deployment(resources = "org/camunda/bpm/engine/test/bpmn/event/conditional/ConditionalStartEventTest.testStartInstanceWithVariableName.bpmn20.xml")
  public void testStartInstanceWithVariableNameNotFullfilled() {
    // given deployed process with two conditional start events:
    // ${true} variableName="foo"
    // ${true}

    // assume
    List<EventSubscription> eventSubscriptions = runtimeService.createEventSubscriptionQuery().list();
    assertEquals(2, eventSubscriptions.size());

    // when
    List<ProcessInstance> instances = runtimeService
        .createConditionEvaluation()
        .setVariable("bar", 42)
        .evaluateStartConditions();

    // then
    assertEquals(1, instances.size());
  }

  protected String deployProcess(String resourcePath) {
    List<ProcessDefinition> deployedProcessDefinitions = testRule.deploy(resourcePath).getDeployedProcessDefinitions();
    assertEquals(1, deployedProcessDefinitions.size());
    return deployedProcessDefinitions.get(0).getId();
  }

  protected String deployModel(BpmnModelInstance model) {
    List<ProcessDefinition> deployedProcessDefinitions = testRule.deploy(model).getDeployedProcessDefinitions();
    assertEquals(1, deployedProcessDefinitions.size());
    String definitionId2 = deployedProcessDefinitions.get(0).getId();
    return definitionId2;
  }
}