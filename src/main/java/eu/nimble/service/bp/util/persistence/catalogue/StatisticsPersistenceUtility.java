package eu.nimble.service.bp.util.persistence.catalogue;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.nimble.service.bp.model.hyperjaxb.CollaborationGroupDAO;
import eu.nimble.service.bp.model.hyperjaxb.ProcessDocumentMetadataDAO;
import eu.nimble.service.bp.model.hyperjaxb.ProcessInstanceGroupDAO;
import eu.nimble.service.bp.model.statistics.FulfilmentStatistics;
import eu.nimble.service.bp.model.statistics.NonOrderedProducts;
import eu.nimble.service.bp.util.persistence.bp.CollaborationGroupDAOUtility;
import eu.nimble.service.bp.util.persistence.bp.ProcessDocumentMetadataDAOUtility;
import eu.nimble.service.bp.util.spring.SpringBridge;
import eu.nimble.service.model.ubl.commonaggregatecomponents.*;
import eu.nimble.service.model.ubl.commonbasiccomponents.TextType;
import eu.nimble.service.model.ubl.commonaggregatecomponents.CompletedTaskType;
import eu.nimble.service.model.ubl.commonaggregatecomponents.PartyType;
import eu.nimble.service.model.ubl.commonaggregatecomponents.QualifyingPartyType;
import eu.nimble.service.model.ubl.despatchadvice.DespatchAdviceType;
import eu.nimble.service.model.ubl.receiptadvice.ReceiptAdviceType;
import eu.nimble.utility.JsonSerializationUtility;
import eu.nimble.utility.persistence.GenericJPARepository;
import eu.nimble.utility.persistence.JPARepositoryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.DatatypeFactory;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

/**
 * Created by suat on 12-Jun-18.
 */
public class StatisticsPersistenceUtility {
    private static final Logger logger = LoggerFactory.getLogger(StatisticsPersistenceUtility.class);

    public static long getActionRequiredProcessCount(String partyID,String federationId,String role,Boolean archived){
        List<String> parameterNames = new ArrayList<>();
        List<Object> parameterValues = new ArrayList<>();
        String query = "SELECT metadataDAO.processInstanceID FROM " +
                "ProcessDocumentMetadataDAO metadataDAO,ProcessInstanceDAO instanceDAO,ProcessInstanceGroupDAO groupDAO join groupDAO.processInstanceIDsItems ids " +
                "WHERE groupDAO.archived = :archived AND " +
                "groupDAO.partyID = :partyId AND groupDAO.federationID = :federationId AND " +
                "metadataDAO.processInstanceID = ids.item AND " +
                "metadataDAO.processInstanceID = instanceDAO.processInstanceID AND " +
                "instanceDAO.status <> 'CANCELLED' AND instanceDAO.processID ";
        if(role.equals("seller")){
            query += " <> 'Fulfilment' AND metadataDAO.responderID = :partyId AND metadataDAO.responderFederationID = :federationId GROUP BY metadataDAO.processInstanceID HAVING count(*) = 1";
        }
        else{
            query += " = 'Fulfilment' AND metadataDAO.responderID = :partyId AND metadataDAO.responderFederationID = :federationId AND instanceDAO.status = 'STARTED' GROUP BY metadataDAO.processInstanceID HAVING count(*) = 1";
        }
        parameterNames.add("archived");
        parameterNames.add("partyId");
        parameterNames.add("federationId");
        parameterValues.add(archived);
        parameterValues.add(partyID);
        parameterValues.add(federationId);
        List<String> count = new JPARepositoryFactory().forBpRepository().getEntities(query, parameterNames.toArray(new String[parameterNames.size()]), parameterValues.toArray());
        return count.size();
    }

    public static List<FulfilmentStatistics> getFulfilmentStatistics(String orderId){
        GenericJPARepository catalogRepository = new JPARepositoryFactory().forCatalogueRepository(true);
        // get lines for the given order id
        String query = "SELECT orderLine " +
                "FROM OrderType orderType join orderType.orderLine orderLine " +
                "WHERE orderType.ID = :orderId";
        List<String> parameterNames = Collections.singletonList("orderId");;
        List<Object> parameterValues = Collections.singletonList(orderId);
        List<OrderLineType> orderLines = catalogRepository.getEntities(query, parameterNames.toArray(new String[parameterNames.size()]), parameterValues.toArray());

        // get dispatch advices for the specified order
        query = "select despatchAdvice " +
                "from DespatchAdviceType despatchAdvice join despatchAdvice.despatchLine despatchLine join despatchAdvice.orderReference orderReference " +
                "where orderReference.documentReference.ID = :orderId";
        List<DespatchAdviceType> dispatchAdvices = catalogRepository.getEntities(query, parameterNames.toArray(new String[parameterNames.size()]), parameterValues.toArray());

        // for each line item in the order, there should be a FulfilmentStatistics
        // we use the SortedMap to keep the order of lines
        SortedMap<Long,FulfilmentStatistics> lineHjidFulfilmentStatisticsMap = new TreeMap<>();
        for (OrderLineType orderLine : orderLines) {
            FulfilmentStatistics fulfilmentStatistic = new FulfilmentStatistics();
            fulfilmentStatistic.setLineItemHjid(orderLine.getHjid());
            fulfilmentStatistic.setRequestedQuantity(orderLine.getLineItem().getQuantity().getValue());
            fulfilmentStatistic.setRejectedQuantity(BigDecimal.ZERO);
            fulfilmentStatistic.setDispatchedQuantity(BigDecimal.ZERO);
            fulfilmentStatistic.setAcceptedQuantity(BigDecimal.ZERO);
            lineHjidFulfilmentStatisticsMap.put(orderLine.getHjid(),fulfilmentStatistic);
        }

        for (DespatchAdviceType despatchAdvice : dispatchAdvices) {
            // get corresponding receipt advice
            ReceiptAdviceType receiptAdvice = DocumentPersistenceUtility.getReceiptAdviceByDispatchId(despatchAdvice.getID());

            int numberOfItems = despatchAdvice.getDespatchLine().size();
            for(int i = 0; i < numberOfItems; i++){
                DespatchLineType despatchLineType = despatchAdvice.getDespatchLine().get(i);

                Long lineHjid = Long.parseLong(despatchLineType.getOrderLineReference().getLineID());

                BigDecimal dispatchedQuantity = BigDecimal.ZERO;
                BigDecimal rejectedQuantity = BigDecimal.ZERO;
                BigDecimal deliveredQuantity = despatchLineType.getDeliveredQuantity().getValue();
                if(deliveredQuantity != null){
                    dispatchedQuantity = dispatchedQuantity.add(deliveredQuantity);
                }
                BigDecimal receiptLineRejectedQuantity = receiptAdvice != null ? receiptAdvice.getReceiptLine().get(i).getRejectedQuantity().getValue() : null;
                if(receiptLineRejectedQuantity != null){
                    rejectedQuantity = rejectedQuantity.add(receiptLineRejectedQuantity);
                }
                BigDecimal acceptedQuantity = receiptAdvice != null ? dispatchedQuantity.subtract(rejectedQuantity):BigDecimal.ZERO;

                FulfilmentStatistics fulfilmentStatistics = lineHjidFulfilmentStatisticsMap.get(lineHjid);

                fulfilmentStatistics.setDispatchedQuantity(fulfilmentStatistics.getDispatchedQuantity().add(dispatchedQuantity));
                fulfilmentStatistics.setRejectedQuantity(fulfilmentStatistics.getRejectedQuantity().add(rejectedQuantity));
                fulfilmentStatistics.setAcceptedQuantity(fulfilmentStatistics.getAcceptedQuantity().add(acceptedQuantity));
            }
        }

        return new ArrayList<FulfilmentStatistics>(lineHjidFulfilmentStatisticsMap.values());
    }

    public static double getTradingVolume(Integer partyId, String federationId, String role, String startDate, String endDate, String status) {
        String query = "select sum(order_.anticipatedMonetaryTotal.payableAmount.value) from OrderType order_ where " +
                "order_.anticipatedMonetaryTotal.payableAmount.value is not null";
        List<String> parameterNames = new ArrayList<>();
        List<Object> parameterValues = new ArrayList<>();

        List<String> documentTypes = new ArrayList<>();
        documentTypes.add("ORDER");
        List<String> orderIds = ProcessDocumentMetadataDAOUtility.getDocumentIds(partyId,federationId, documentTypes, role, startDate, endDate, status, true);

        // no orders for the specified criteria
        if (orderIds.size() == 0) {
            logger.info("No orders for the specified criteria");
            return 0;
        }

        query += " and (";
        for (int i = 0; i < orderIds.size() - 1; i++) {
            query += " order_.ID = :id" + i + " or ";

            parameterNames.add("id" + i);
            parameterValues.add(orderIds.get(i));
        }
        query += " order_.ID = :id" + (orderIds.size()-1) + ")";

        parameterNames.add("id" + (orderIds.size()-1));
        parameterValues.add(orderIds.get(orderIds.size()-1));

        return ((BigDecimal) new JPARepositoryFactory().forCatalogueRepository().getSingleEntity(query, parameterNames.toArray(new String[parameterNames.size()]), parameterValues.toArray())).doubleValue();
    }

    public static NonOrderedProducts getNonOrderedProducts(String bearerToken,Integer partyId) throws IOException {
        String query = "select distinct new list(partyIdentification.ID, item.manufacturersItemIdentification.ID, itemName.value) from ItemType item join item.manufacturerParty.partyIdentification partyIdentification JOIN item.name itemName" +
                " where item.transportationServiceDetails is null ";
        List<String> parameterNames = new ArrayList<>();
        List<Object> parameterValues = new ArrayList<>();

        if (partyId != null) {
            query += " and partyIdentification.ID = :partyId";
            parameterNames.add("partyId");
            parameterValues.add(partyId.toString());
        }

        List<String> orderIds = ProcessDocumentMetadataDAOUtility.getOrderIdsBelongToCompletedCollaborations();
        if(orderIds.size() > 0){
            query += " and item.manufacturersItemIdentification.ID not in " +
                    "(select line.lineItem.item.manufacturersItemIdentification.ID from OrderType order_ join order_.orderLine line join line.lineItem.item.manufacturerParty.partyIdentification orderPartyIdentification " +
                    " where orderPartyIdentification.ID = partyIdentification.ID and (";
            for (int i = 0; i < orderIds.size() - 1; i++) {
                query += " order_.ID = '" + orderIds.get(i) + "' or";
            }
            query += " order_.ID = '" +orderIds.get(orderIds.size()-1) + "'))";
        }

        NonOrderedProducts nonOrderedProducts = new NonOrderedProducts();
        List<Object> results = new JPARepositoryFactory().forCatalogueRepository().getEntities(query, parameterNames.toArray(new String[parameterNames.size()]), parameterValues.toArray());
        for (Object result : results) {
            List<String> dataArray = (List<String>) result;
            // get party information from identity service
            PartyType party = SpringBridge.getInstance().getiIdentityClientTyped().getParty(bearerToken,dataArray.get(0));
            nonOrderedProducts.addProduct(dataArray.get(0), party.getPartyName().get(0).getName().getValue(), dataArray.get(1), dataArray.get(2));
        }

        return nonOrderedProducts;
    }

    public static List<PartyType> getInactiveCompanies(String bearerToken) throws IOException {
        // get active party ids
        // get parties for a process that have not completed yet. Therefore return only the initiatorID
        String query = "select docMetadata.initiatorID from ProcessDocumentMetadataDAO docMetadata where docMetadata.status = 'WAITINGRESPONSE'";

        Set<String> activePartyIds = new HashSet<>();
        List<String> results = new JPARepositoryFactory().forBpRepository().getEntities(query);

        activePartyIds.addAll(results);

        // get parties for a process that have completed already. Therefore return both the initiatorID and responderID
        query = "select distinct new list(docMetadata.initiatorID, docMetadata.responderID) from ProcessDocumentMetadataDAO docMetadata where docMetadata.status <> 'WAITINGRESPONSE'";
        List<List<String>> secondResults = new JPARepositoryFactory().forBpRepository(true).getEntities(query);
        for (List<String> processPartyIds : secondResults) {
            activePartyIds.add(processPartyIds.get(0));
            activePartyIds.add(processPartyIds.get(1));
        }

        // get inactive companies
        List<PartyType> inactiveParties = new ArrayList<>();
        InputStream parties = SpringBridge.getInstance().getiIdentityClientTyped().getAllPartyIds(bearerToken, new ArrayList<>()).body().asInputStream();
        ObjectMapper mapper = JsonSerializationUtility.getObjectMapper();
        JsonFactory factory = mapper.getFactory();
        JsonParser parser = factory.createParser(parties);
        JsonNode allParties = mapper.readTree(parser);
        Iterable<JsonNode> iterable = () -> allParties.elements();
        iterable.forEach(partyResult -> {
            JsonNode idNode = partyResult.get("identifier");
            if(idNode == null) {
                return;
            }
            String partyId = idNode.asText();
            if(!activePartyIds.contains(partyId)) {
                PartyType party = new PartyType();
                PartyIdentificationType partyIdentificationType = new PartyIdentificationType();
                partyIdentificationType.setID(partyId);
                party.setPartyIdentification(Arrays.asList(partyIdentificationType));
                TextType textType = new TextType();
                textType.setValue(partyResult.get("name").asText());
                textType.setLanguageID("en");
                PartyNameType partyNameType = new PartyNameType();
                partyNameType.setName(textType);
                party.setPartyName(Arrays.asList(partyNameType));
                inactiveParties.add(party);
            }
        });
        return inactiveParties;
    }

    public static double calculateAverageCollaborationTime(String partyID,String federationId, String bearerToken, String role){
        int numberOfCollaborations = 0;
        double totalTime = 0;
        QualifyingPartyType qualifyingParty = PartyPersistenceUtility.getQualifyingPartyType(partyID,federationId,bearerToken);

        for (CompletedTaskType completedTask:qualifyingParty.getCompletedTask()){
            if(completedTask.getPeriod().getEndDate() == null || completedTask.getPeriod().getEndTime() == null){
                continue;
            }

            String processInstanceId = completedTask.getAssociatedProcessInstanceID();
            CollaborationGroupDAO collaborationGroup = CollaborationGroupDAOUtility
                    .getCollaborationGroupByProcessInstanceIdAndPartyId(processInstanceId, partyID,federationId);

            if(collaborationGroup != null){
                List<ProcessInstanceGroupDAO> processInstanceGroups =   collaborationGroup.getAssociatedProcessInstanceGroups();
                for(ProcessInstanceGroupDAO pid :processInstanceGroups ){
                    List<String> pidstrs = pid.getProcessInstanceIDs();
                    for(String pidstr : pidstrs){
                        if(pidstr.equals(processInstanceId) && pid.getCollaborationRole().equals(role)){
                            Date startDate = completedTask.getPeriod().getStartDate().toGregorianCalendar().getTime();
                            Date endDate = completedTask.getPeriod().getEndDate().toGregorianCalendar().getTime();
                            Date startTime = completedTask.getPeriod().getStartTime().toGregorianCalendar().getTime();
                            Date endTime = completedTask.getPeriod().getEndTime().toGregorianCalendar().getTime();
                            numberOfCollaborations++;
                            totalTime += ((endDate.getTime()-startDate.getTime())+(endTime.getTime()-startTime.getTime()))/86400000.0;
                        }
                    }
                }
            }

        }
        if(numberOfCollaborations == 0){
            return 0.0;
        }
        return totalTime/numberOfCollaborations;
    }

    public static double calculateAverageCollaborationTimeForPlatform(String role){
        int numberOfCollaborations = 0;
        double totalTime = 0;
        List<CompletedTaskType> completedtasks = PartyPersistenceUtility.getCompletedTasks();
        List<String> processInstanceIds = CollaborationGroupDAOUtility.getProcessInstanceIdsByCollborationRole(role);
        Set set = new HashSet(processInstanceIds);

        for(CompletedTaskType completedtask : completedtasks){
            if(completedtask.getPeriod().getEndDate() == null || completedtask.getPeriod().getEndTime() == null){
                continue;
            }

            if(set.contains(completedtask.getAssociatedProcessInstanceID())){
                Date startDate = completedtask.getPeriod().getStartDate().toGregorianCalendar().getTime();
                Date endDate = completedtask.getPeriod().getEndDate().toGregorianCalendar().getTime();
                Date startTime = completedtask.getPeriod().getStartTime().toGregorianCalendar().getTime();
                Date endTime = completedtask.getPeriod().getEndTime().toGregorianCalendar().getTime();
                numberOfCollaborations++;
                totalTime += ((endDate.getTime()-startDate.getTime())+(endTime.getTime()-startTime.getTime()))/86400000.0;
            }

        }

        if(numberOfCollaborations == 0){
            return 0.0;
        }
        return totalTime/numberOfCollaborations;
    }

    public static double calculateAverageResponseTime(String partyID,String federationId) throws Exception{

        int numberOfResponses = 0;
        double totalTime = 0;
        List<String> processInstanceIDs;

        if(partyID != null){
            processInstanceIDs = CollaborationGroupDAOUtility.getProcessInstanceIdsByParty(partyID,federationId);
        }else {
            processInstanceIDs =  CollaborationGroupDAOUtility.getProcessInstanceIds();
        }

        for (String processInstanceID:processInstanceIDs){
            List<ProcessDocumentMetadataDAO> processDocumentMetadataDAOS = ProcessDocumentMetadataDAOUtility.findByProcessInstanceID(processInstanceID);
            if (processDocumentMetadataDAOS.size() != 2){
                continue;
            }

            ProcessDocumentMetadataDAO docMetadata = processDocumentMetadataDAOS.get(1);
            ProcessDocumentMetadataDAO reqMetadata = processDocumentMetadataDAOS.get(0);

            Date startDate = DatatypeFactory.newInstance().newXMLGregorianCalendar(reqMetadata.getSubmissionDate()).toGregorianCalendar().getTime();
            Date endDate = DatatypeFactory.newInstance().newXMLGregorianCalendar(docMetadata.getSubmissionDate()).toGregorianCalendar().getTime();

            numberOfResponses++;
            totalTime += (endDate.getTime()-startDate.getTime())/86400000.0;
        }

        if(numberOfResponses == 0){
            return 0.0;
        }
        return totalTime/numberOfResponses;
    }

    public static Map<Integer,Double> calculateAverageResponseTimeInMonths(String partyID,String federationId) throws Exception{

        List<String> processInstanceIDs;

        if(partyID != null){
            processInstanceIDs = CollaborationGroupDAOUtility.getProcessInstanceIdsByParty(partyID,federationId);

        }else {
            processInstanceIDs =  CollaborationGroupDAOUtility.getProcessInstanceIds();
        }

        Set<Integer> monthList = new HashSet<>();

        int currentmonth  = new GregorianCalendar().get(Calendar.MONTH);
        int currentyear = new GregorianCalendar().get(Calendar.YEAR);

        while(monthList.size() < 6){
            if(currentmonth < 0){
                currentmonth = 11;
            }

            monthList.add(currentmonth);
            currentmonth--;
        }

        Map<Integer,Double> storeMonth = new HashMap<>();
        Map<Integer,Integer> storeResponseTime = new HashMap<>();

        for (String processInstanceID:processInstanceIDs){
            List<ProcessDocumentMetadataDAO> processDocumentMetadataDAOS = ProcessDocumentMetadataDAOUtility.findByProcessInstanceID(processInstanceID);
            if (processDocumentMetadataDAOS.size() != 2){
                continue;
            }

            ProcessDocumentMetadataDAO docMetadata = processDocumentMetadataDAOS.get(1);
            ProcessDocumentMetadataDAO reqMetadata = processDocumentMetadataDAOS.get(0);

            int month = DatatypeFactory.newInstance().newXMLGregorianCalendar(reqMetadata.getSubmissionDate()).toGregorianCalendar().get(Calendar.MONTH);
            int year = DatatypeFactory.newInstance().newXMLGregorianCalendar(reqMetadata.getSubmissionDate()).toGregorianCalendar().get(Calendar.YEAR);

            if(monthList.contains(month) && year== currentyear) {
                Date startDate = DatatypeFactory.newInstance().newXMLGregorianCalendar(reqMetadata.getSubmissionDate())
                        .toGregorianCalendar().getTime();
                Date endDate = DatatypeFactory.newInstance().newXMLGregorianCalendar(docMetadata.getSubmissionDate())
                        .toGregorianCalendar().getTime();

                double timeAll = 0;
                int responseno = 0;

                if (storeMonth.containsKey(month)) {
                    timeAll = storeMonth.get(month);
                    responseno = storeResponseTime.get(month);
                }

                timeAll += (endDate.getTime() - startDate.getTime()) / 86400000.0;
                responseno += 1;
                storeMonth.put(month,timeAll);
                storeResponseTime.put(month,responseno);

            }
        }

        for (int itra : monthList) {
            if (storeResponseTime.containsKey(itra)) {
                storeMonth.put(itra, (storeMonth.get(itra) / storeResponseTime.get(itra)));
            } else {
                storeMonth.put(itra, 0.0);
            }
        }

        return storeMonth;
    }
}