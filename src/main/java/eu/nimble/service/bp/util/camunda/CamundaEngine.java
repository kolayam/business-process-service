/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.nimble.service.bp.util.camunda;

import com.fasterxml.jackson.core.JsonProcessingException;
import eu.nimble.service.bp.util.serialization.MixInIgnoreProperties;
import eu.nimble.service.bp.swagger.model.*;
import eu.nimble.service.bp.swagger.model.Process;
import eu.nimble.utility.DateUtility;
import eu.nimble.utility.JsonSerializationUtility;
import eu.nimble.utility.XMLUtility;
import eu.nimble.utility.bp.ClassProcessTypeMap;
import org.camunda.bpm.engine.*;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.transform.dom.DOMSource;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author yildiray
 */
public class CamundaEngine {
    private static ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();
    private static RepositoryService repositoryService = processEngine.getRepositoryService();
    private static RuntimeService runtimeService = processEngine.getRuntimeService();
    private static TaskService taskService = processEngine.getTaskService();
    private static HistoryService historyService = processEngine.getHistoryService();

    private static Logger logger = LoggerFactory.getLogger(CamundaEngine.class);

    public static void continueProcessInstance(String processContextId,ProcessInstanceInputMessage body,String initiatorFederationId,String responderFederationId, String bearerToken) {
        String processInstanceID = body.getProcessInstanceID();
        Task task = taskService.createTaskQuery().processInstanceId(processInstanceID).list().get(0);

        Map<String, Object> data = getVariablesData(body, initiatorFederationId, responderFederationId);
        // add processContextId
        data.put("processContextId",processContextId);
        data.put("bearer_token", bearerToken);

        try {
            logger.info(" Completing business process instance {}, with data {}", processInstanceID, JsonSerializationUtility.getObjectMapperWithMixIn(Map.class, MixInIgnoreProperties.class).writeValueAsString(data));
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize process instance variables: ",e);
        }
        taskService.complete(task.getId(), data);
        logger.info(" Completed business process instance {}", processInstanceID);
    }

    public static ProcessInstance startProcessInstance(String processContextId,ProcessInstanceInputMessage body, String initiatorFederationId,String responderFederationId) {
        Map<String, Object> data = getVariablesData(body, initiatorFederationId, responderFederationId);
        // add processContextId
        data.put("processContextId",processContextId);
        String processID = body.getVariables().getProcessID();

        try {
            logger.info(" Starting business process instance for {}, with data {}", processID, JsonSerializationUtility.getObjectMapperWithMixIn(Map.class, MixInIgnoreProperties.class).writeValueAsString(data));
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize process instance variables: ",e);
        }
        org.camunda.bpm.engine.runtime.ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(processID, data);
        logger.info(" Started business process instance for {}, with instance id {}", processID, processInstance.getProcessInstanceId());

        ProcessInstance businessProcessInstance = new ProcessInstance();
        businessProcessInstance.setProcessID(processID);
        //businessProcessInstance.setProcessInstanceID("prc124");
        businessProcessInstance.setCreationDate(DateUtility.getCurrentTimeStamp());
        businessProcessInstance.setProcessInstanceID(processInstance.getProcessInstanceId());
        businessProcessInstance.setStatus(ProcessInstance.StatusEnum.STARTED);

        return businessProcessInstance;
    }

    public static List<Process> getProcessDefinitions() {
        List<ProcessDefinition> processDefinitions = repositoryService.createProcessDefinitionQuery().list();
        List<Process> processes = new ArrayList<>();
        for (ProcessDefinition processDefinition : processDefinitions) {
            Process process = mapProcess(processDefinition);
            processes.add(process);
        }

        return processes;
    }

    private static Process mapProcess(ProcessDefinition processDefinition) {
        String key = processDefinition.getKey();
        String name = processDefinition.getName();
        String processDefinitionId = processDefinition.getId();

        BpmnModelInstance bpmnModel = repositoryService.getBpmnModelInstance(processDefinitionId);

        DOMSource domSource = bpmnModel.getDocument().getDomSource();
        String bpmnContent = XMLUtility.nodeToString(domSource.getNode());
        String type = XMLUtility.evaluateXPathAndGetAttributeValue(domSource.getNode(), "//camunda:property[@name = 'businessProcessCategory']/@value");
        if (type == null)
            type = "OTHER";

        logger.info(" $$$ Getting BPMN {} {} {} {}", processDefinitionId, key, name, type);

        Process process = new Process();
        process.setProcessID(key);
        process.setProcessName(name);
        //process.setBpmnContent(bpmnContent);
        process.setBpmnContent("");
        process.setProcessType(Process.ProcessTypeEnum.valueOf(type));
        process.setTextContent(getProcessTextContent(key));
        process.setTransactions(getTransactions(key));
        return process;
    }

    private static Map<String, Object> getVariablesData(ProcessInstanceInputMessage body,String initiatorFederationId,String responderFederationId) {
        ProcessVariables variables = body.getVariables();
        String content = variables.getContent();
        String initiatorID = variables.getInitiatorID();
        String responderID = variables.getResponderID();
        String creatorUserID = variables.getCreatorUserID();

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("initiatorID", initiatorID);
        data.put("responderID", responderID);
        data.put("creatorUserID",creatorUserID);
        data.put("content", content);
        data.put("relatedProducts", variables.getRelatedProducts());
        data.put("relatedProductCategories", variables.getRelatedProductCategories());
        data.put("initiatorFederationId",initiatorFederationId);
        data.put("responderFederationId",responderFederationId);
        return data;
    }

    public static List<ProcessDefinition> getCamundaProcessDefinitions() {
        return repositoryService.createProcessDefinitionQuery().list();
    }

    public static void addProcessDefinition(String processID, String bpmnContent) {
        repositoryService.createDeployment().addString(processID + ".bpmn", bpmnContent).deploy();
        //getProcessDefinitions();
    }

    public static void deleteProcessDefinition(String processID) {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionKey(processID).singleResult();
        repositoryService.deleteProcessDefinition(processDefinition.getId(), true);
    }

    public static Process getProcessDefinition(String processID) {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionKey(processID).singleResult();
        Process process = mapProcess(processDefinition);
        return process;
    }

    public static void updateProcessDefinition(String processID, String bpmnContent) {
        deleteProcessDefinition(processID);
        addProcessDefinition(processID, bpmnContent);
    }

    private static String getProcessTextContent(String processID) {
        switch (processID) {
            case ClassProcessTypeMap.CAMUNDA_PROCESS_ID_ORDER:
                return "Title: ORDER\n" +
                        "Buyer -> Seller: Order\n" +
                        "Note right of Seller: Evaluate Order\n" +
                        "Seller -> Buyer: Order Response";
            case ClassProcessTypeMap.CAMUNDA_PROCESS_ID_NEGOTIATION:
                return "Title: NEGOTIATION\n" +
                        "Buyer -> Seller: Request For Quotation\n" +
                        "Note right of Seller: Evaluate RfQ\n" +
                        "Seller -> Buyer: Quotation";
            default:
                return null;
        }
    }

    public static List<Transaction> getTransactions(String processID) {
        List<Transaction> transactions = new ArrayList<>();
        Transaction transaction = null;

        processID = processID.toUpperCase();
        switch (processID) {
            case "ORDER":
                transaction = new Transaction();
                transaction.setInitiatorRole(Transaction.InitiatorRoleEnum.BUYER);
                transaction.setResponderRole(Transaction.ResponderRoleEnum.SELLER);
                transaction.setTransactionID(Transaction.DocumentTypeEnum.ORDER.toString());
                transaction.setDocumentType(Transaction.DocumentTypeEnum.ORDER);
                transactions.add(transaction);
                transaction = new Transaction();
                transaction.setInitiatorRole(Transaction.InitiatorRoleEnum.SELLER);
                transaction.setResponderRole(Transaction.ResponderRoleEnum.BUYER);
                transaction.setTransactionID(Transaction.DocumentTypeEnum.ORDERRESPONSESIMPLE.toString());
                transaction.setDocumentType(Transaction.DocumentTypeEnum.ORDERRESPONSESIMPLE);
                transactions.add(transaction);
                break;
            case "NEGOTIATION":
                transaction = new Transaction();
                transaction.setInitiatorRole(Transaction.InitiatorRoleEnum.BUYER);
                transaction.setResponderRole(Transaction.ResponderRoleEnum.SELLER);
                transaction.setTransactionID(Transaction.DocumentTypeEnum.REQUESTFORQUOTATION.toString());
                transaction.setDocumentType(Transaction.DocumentTypeEnum.REQUESTFORQUOTATION);
                transactions.add(transaction);
                transaction = new Transaction();
                transaction.setInitiatorRole(Transaction.InitiatorRoleEnum.SELLER);
                transaction.setResponderRole(Transaction.ResponderRoleEnum.BUYER);
                transaction.setTransactionID(Transaction.DocumentTypeEnum.QUOTATION.toString());
                transaction.setDocumentType(Transaction.DocumentTypeEnum.QUOTATION);
                transactions.add(transaction);
                break;
            case "PPAP":
                transaction = new Transaction();
                transaction.setInitiatorRole(Transaction.InitiatorRoleEnum.BUYER);
                transaction.setResponderRole(Transaction.ResponderRoleEnum.SELLER);
                transaction.setTransactionID(Transaction.DocumentTypeEnum.PPAPREQUEST.toString());
                transaction.setDocumentType(Transaction.DocumentTypeEnum.PPAPREQUEST);
                transactions.add(transaction);
                transaction = new Transaction();
                transaction.setInitiatorRole(Transaction.InitiatorRoleEnum.SELLER);
                transaction.setResponderRole(Transaction.ResponderRoleEnum.BUYER);
                transaction.setTransactionID(Transaction.DocumentTypeEnum.PPAPRESPONSE.toString());
                transaction.setDocumentType(Transaction.DocumentTypeEnum.PPAPRESPONSE);
                transactions.add(transaction);
                break;
            case "ITEM_INFORMATION_REQUEST":
                transaction = new Transaction();
                transaction.setInitiatorRole(Transaction.InitiatorRoleEnum.BUYER);
                transaction.setResponderRole(Transaction.ResponderRoleEnum.SELLER);
                transaction.setTransactionID(Transaction.DocumentTypeEnum.ITEMINFORMATIONREQUEST.toString());
                transaction.setDocumentType(Transaction.DocumentTypeEnum.ITEMINFORMATIONREQUEST);
                transactions.add(transaction);
                transaction = new Transaction();
                transaction.setInitiatorRole(Transaction.InitiatorRoleEnum.SELLER);
                transaction.setResponderRole(Transaction.ResponderRoleEnum.BUYER);
                transaction.setTransactionID(Transaction.DocumentTypeEnum.ITEMINFORMATIONRESPONSE.toString());
                transaction.setDocumentType(Transaction.DocumentTypeEnum.ITEMINFORMATIONRESPONSE);
                transactions.add(transaction);
                break;
            case "FULFILMENT":
                transaction = new Transaction();
                transaction.setInitiatorRole(Transaction.InitiatorRoleEnum.SELLER);
                transaction.setResponderRole(Transaction.ResponderRoleEnum.BUYER);
                transaction.setTransactionID(Transaction.DocumentTypeEnum.DESPATCHADVICE.toString());
                transaction.setDocumentType(Transaction.DocumentTypeEnum.DESPATCHADVICE);
                transactions.add(transaction);
                transaction = new Transaction();
                transaction.setInitiatorRole(Transaction.InitiatorRoleEnum.BUYER);
                transaction.setResponderRole(Transaction.ResponderRoleEnum.SELLER);
                transaction.setTransactionID(Transaction.DocumentTypeEnum.RECEIPTADVICE.toString());
                transaction.setDocumentType(Transaction.DocumentTypeEnum.RECEIPTADVICE);
                transactions.add(transaction);
                break;
            case "TRANSPORT_EXECUTION_PLAN":
                transaction = new Transaction();
                transaction.setInitiatorRole(Transaction.InitiatorRoleEnum.BUYER);
                transaction.setResponderRole(Transaction.ResponderRoleEnum.SELLER);
                transaction.setTransactionID(Transaction.DocumentTypeEnum.TRANSPORTEXECUTIONPLANREQUEST.toString());
                transaction.setDocumentType(Transaction.DocumentTypeEnum.TRANSPORTEXECUTIONPLANREQUEST);
                transactions.add(transaction);
                transaction = new Transaction();
                transaction.setInitiatorRole(Transaction.InitiatorRoleEnum.SELLER);
                transaction.setResponderRole(Transaction.ResponderRoleEnum.BUYER);
                transaction.setTransactionID(Transaction.DocumentTypeEnum.TRANSPORTEXECUTIONPLAN.toString());
                transaction.setDocumentType(Transaction.DocumentTypeEnum.TRANSPORTEXECUTIONPLAN);
                transactions.add(transaction);
                break;
            default:
                return null;
        }
        return transactions;
    }

    public static void cancelProcessInstance(String processInstanceId){
        runtimeService.deleteProcessInstance(processInstanceId,"",true,true);
    }

    public static List<HistoricVariableInstance> getVariableInstances(String processInstanceId){
        return historyService.createHistoricVariableInstanceQuery().processInstanceId(processInstanceId).list();
    }

    public static String getLastActivityInstanceStartTime(String processInstanceId){
        HistoricActivityInstance historicActivityInstance = historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstanceId).orderByHistoricActivityInstanceStartTime().desc().listPage(0,1).get(0);
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ").format(historicActivityInstance.getStartTime());
    }

    public static HistoricProcessInstance getProcessInstance(String processInstanceId){
        return historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
    }

    public static void deleteProcessInstances(Set<String> processInstanceIds){
        // get HistoricProcessInstances
        List<HistoricProcessInstance> historicProcessInstances = historyService.createHistoricProcessInstanceQuery().processInstanceIds(processInstanceIds).list();

        List<String> historicProcessInstanceIds = new ArrayList<>();
        List<String> runTimeProcessInstanceIds = new ArrayList<>();
        // check whether the process instance is still running or completed
        for(HistoricProcessInstance historicProcessInstance:historicProcessInstances){
            if(historicProcessInstance.getEndTime() != null){
                historicProcessInstanceIds.add(historicProcessInstance.getId());
            }
            else{
                runTimeProcessInstanceIds.add(historicProcessInstance.getId());
            }
        }
        // delete completed process instance via historyService
        if(historicProcessInstanceIds.size() > 0){
            historyService.deleteHistoricProcessInstances(historicProcessInstanceIds);
        }
        // complete the process instances still running and delete them via historyService
        if(runTimeProcessInstanceIds.size() > 0){
            runtimeService.deleteProcessInstances(runTimeProcessInstanceIds,"",true,true);
            historyService.deleteHistoricProcessInstances(runTimeProcessInstanceIds);
        }

    }
}
