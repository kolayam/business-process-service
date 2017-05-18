package eu.nimble.service.bp.impl;

import eu.nimble.service.bp.hyperjaxb.model.*;
import eu.nimble.service.bp.impl.util.CamundaRestClient;
import eu.nimble.service.bp.impl.util.DAOUtility;
import eu.nimble.service.bp.impl.util.HibernateSwaggerConverter;
import eu.nimble.service.bp.swagger.api.BusinessprocessApi;
import eu.nimble.service.bp.swagger.model.*;
import eu.nimble.utility.HibernateUtility;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by yildiray on 5/15/2017.
 */
public class BusinessProcessController implements BusinessprocessApi {
    @Override
    public ResponseEntity<ModelApiResponse> addBusinessProcessDefinition(BusinessProcess body) {
        BusinessProcessDAO businessProcessDAO = HibernateSwaggerConverter.createBusinessProcess_DAO(body);
        HibernateUtility.getInstance("bp-data-model").persist(businessProcessDAO);

        return getApiResponse();
    }

    @Override
    public ResponseEntity<ModelApiResponse> addBusinessProcessPartnerApplicationPreference(BusinessProcessApplicationConfigurations body) {
        BusinessProcessApplicationConfigurationsDAO businessProcessApplicationConfigurationsDAO = HibernateSwaggerConverter.createBusinessProcessApplicationConfigurations_DAO(body);
        HibernateUtility.getInstance("bp-data-model").persist(businessProcessApplicationConfigurationsDAO);
        return getApiResponse();
    }

    @Override
    public ResponseEntity<ModelApiResponse> addBusinessProcessPartnerPreference(BusinessProcessPreferences body) {
        BusinessProcessPreferencesDAO businessProcessPreferencesDAO = HibernateSwaggerConverter.createBusinessProcessPreferences_DAO(body);
        HibernateUtility.getInstance("bp-data-model").persist(businessProcessPreferencesDAO);
        return getApiResponse();
    }

    @Override
    public ResponseEntity<ModelApiResponse> updateBusinessProcessDefinition(String businessProcessID, BusinessProcess body) {
        BusinessProcessDAO businessProcessDAO = DAOUtility.getBusinessProcessDAOByID(businessProcessID);
        BusinessProcessDAO businessProcessDAONew = HibernateSwaggerConverter.createBusinessProcess_DAO(body);
        businessProcessDAONew.setHjid(businessProcessDAO.getHjid());

        HibernateUtility.getInstance("bp-data-model").update(businessProcessDAONew);
        return getApiResponse();
    }

    @Override
    public ResponseEntity<ModelApiResponse> updateBusinessProcessPartnerApplicationPreference(String partnerID, BusinessProcessApplicationConfigurations body) {
        BusinessProcessApplicationConfigurationsDAO businessProcessApplicationConfigurationsDAO = DAOUtility.getBusinessProcessApplicationConfigurationsDAOByPartnerID(partnerID);
        BusinessProcessApplicationConfigurationsDAO businessProcessApplicationConfigurationsDAONew = HibernateSwaggerConverter.createBusinessProcessApplicationConfigurations_DAO(body);
        businessProcessApplicationConfigurationsDAONew.setHjid(businessProcessApplicationConfigurationsDAO.getHjid());
        HibernateUtility.getInstance("bp-data-model").update(businessProcessApplicationConfigurationsDAONew);
        return getApiResponse();
    }

    @Override
    public ResponseEntity<ModelApiResponse> updateBusinessProcessPartnerPreference(String partnerID, BusinessProcessPreferences body) {
        BusinessProcessPreferencesDAO businessProcessPreferencesDAO = DAOUtility.getBusinessProcessPreferencesDAOByPartnerID(partnerID);
        BusinessProcessPreferencesDAO businessProcessPreferencesDAONew = HibernateSwaggerConverter.createBusinessProcessPreferences_DAO(body);
        businessProcessPreferencesDAONew.setHjid(businessProcessPreferencesDAO.getHjid());
        HibernateUtility.getInstance("bp-data-model").update(businessProcessPreferencesDAONew);
        return getApiResponse();
    }

    @Override
    public ResponseEntity<BusinessProcessInstance> continueBusinessProcessInstance(BusinessProcessInstanceInputMessage body) {
        BusinessProcessInstanceInputMessageDAO businessProcessInstanceInputMessageDAO = HibernateSwaggerConverter.createBusinessProcessInstanceInputMessage_DAO(body);
        HibernateUtility.getInstance("bp-data-model").persist(businessProcessInstanceInputMessageDAO);

        BusinessProcessInstance businessProcessInstance = CamundaRestClient.continueProcessInstance(body);

        return new ResponseEntity<>(businessProcessInstance, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<ModelApiResponse> deleteBusinessProcessDefinition(String businessProcessID) {
        BusinessProcessDAO businessProcessDAO = DAOUtility.getBusinessProcessDAOByID(businessProcessID);
        HibernateUtility.getInstance("bp-data-model").delete(BusinessProcessDAO.class, businessProcessDAO.getHjid());
        return getApiResponse();
    }

    @Override
    public ResponseEntity<ModelApiResponse> deleteBusinessProcessPartnerApplicationPreference(String partnerID) {
        BusinessProcessApplicationConfigurationsDAO businessProcessApplicationConfigurationsDAO = DAOUtility.getBusinessProcessApplicationConfigurationsDAOByPartnerID(partnerID);
        HibernateUtility.getInstance("bp-data-model").delete(BusinessProcessApplicationConfigurationsDAO.class, businessProcessApplicationConfigurationsDAO.getHjid());
        return getApiResponse();
    }

    @Override
    public ResponseEntity<ModelApiResponse> deleteBusinessProcessPartnerPreference(String partnerID) {
        BusinessProcessPreferencesDAO businessProcessPreferencesDAO = DAOUtility.getBusinessProcessPreferencesDAOByPartnerID(partnerID);
        HibernateUtility.getInstance("bp-data-model").delete(BusinessProcessPreferencesDAO.class, businessProcessPreferencesDAO.getHjid());
        return getApiResponse();
    }

    @Override
    public ResponseEntity<BusinessProcess> getBusinessProcessDefinition(String businessProcessID) {
        BusinessProcessDAO businessProcessDAO = DAOUtility.getBusinessProcessDAOByID(businessProcessID);
        BusinessProcess businessProcess = HibernateSwaggerConverter.createBusinessProcess(businessProcessDAO);
        return new ResponseEntity<>(businessProcess, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<BusinessProcess>> getBusinessProcessDefinitions() {
        List<BusinessProcessDAO> businessProcessDAOs = DAOUtility.getBusinessProcessDAOs();
        List<BusinessProcess> businessProcesses = new ArrayList<>();
        for(BusinessProcessDAO businessProcessDAO: businessProcessDAOs) {
            BusinessProcess businessProcess = HibernateSwaggerConverter.createBusinessProcess(businessProcessDAO);
            businessProcesses.add(businessProcess);
        }

        return new ResponseEntity<>(businessProcesses, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<BusinessProcessApplicationConfigurations> getBusinessProcessPartnerApplicationPreference(String partnerID) {
        BusinessProcessApplicationConfigurationsDAO businessProcessApplicationConfigurationsDAO = DAOUtility.getBusinessProcessApplicationConfigurationsDAOByPartnerID(partnerID);
        BusinessProcessApplicationConfigurations businessProcessApplicationConfigurations = HibernateSwaggerConverter.createBusinessProcessApplicationConfigurations(businessProcessApplicationConfigurationsDAO);

        return new ResponseEntity<>(businessProcessApplicationConfigurations, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<BusinessProcessPreferences> getBusinessProcessPartnerPreference(String partnerID) {
        BusinessProcessPreferencesDAO businessProcessPreferencesDAO = DAOUtility.getBusinessProcessPreferencesDAOByPartnerID(partnerID);
        BusinessProcessPreferences businessProcessPreferences = HibernateSwaggerConverter.createBusinessProcessPreferences(businessProcessPreferencesDAO);

        return new ResponseEntity<>(businessProcessPreferences, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<BusinessProcessDocument> getDocument(String documentID) {
        BusinessProcessDocumentDAO businessProcessDocumentDAO = DAOUtility.getBusinessProcessDocument(documentID);
        BusinessProcessDocument businessProcessDocument = HibernateSwaggerConverter.createBusinessProcessDocument(businessProcessDocumentDAO);
        return new ResponseEntity<>(businessProcessDocument, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<BusinessProcessDocument>> getDocuments(String partnerID, String typeID) {
        List<BusinessProcessDocumentDAO> businessProcessDocumentsDAO = DAOUtility.getBusinessProcessDocuments(partnerID, typeID);
        List<BusinessProcessDocument> businessProcessDocuments = new ArrayList<>();
        for(BusinessProcessDocumentDAO businessProcessDocumentDAO: businessProcessDocumentsDAO) {
            BusinessProcessDocument businessProcessDocument = HibernateSwaggerConverter.createBusinessProcessDocument(businessProcessDocumentDAO);
            businessProcessDocuments.add(businessProcessDocument);
        }

        return new ResponseEntity<>(businessProcessDocuments, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<BusinessProcessDocument>> getDocuments(String partnerID, String typeID, String source) {
        List<BusinessProcessDocumentDAO> businessProcessDocumentsDAO = DAOUtility.getBusinessProcessDocuments(partnerID, typeID, source);
        List<BusinessProcessDocument> businessProcessDocuments = new ArrayList<>();
        for(BusinessProcessDocumentDAO businessProcessDocumentDAO: businessProcessDocumentsDAO) {
            BusinessProcessDocument businessProcessDocument = HibernateSwaggerConverter.createBusinessProcessDocument(businessProcessDocumentDAO);
            businessProcessDocuments.add(businessProcessDocument);
        }
        return new ResponseEntity<>(businessProcessDocuments, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<BusinessProcessDocument>> getDocuments(String partnerID, String typeID, String status, String source) {
        List<BusinessProcessDocumentDAO> businessProcessDocumentsDAO = DAOUtility.getBusinessProcessDocuments(partnerID, typeID, status, source);
        List<BusinessProcessDocument> businessProcessDocuments = new ArrayList<>();
        for(BusinessProcessDocumentDAO businessProcessDocumentDAO: businessProcessDocumentsDAO) {
            BusinessProcessDocument businessProcessDocument = HibernateSwaggerConverter.createBusinessProcessDocument(businessProcessDocumentDAO);
            businessProcessDocuments.add(businessProcessDocument);
        }
        return new ResponseEntity<>(businessProcessDocuments, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<BusinessProcessInstance> startBusinessProcessInstance(BusinessProcessInstanceInputMessage body) {
        BusinessProcessInstanceInputMessageDAO businessProcessInstanceInputMessageDAO = HibernateSwaggerConverter.createBusinessProcessInstanceInputMessageDAO(body);
        HibernateUtility.getInstance("bp-data-model").persist(businessProcessInstanceInputMessageDAO);

        BusinessProcessInstance businessProcessInstance = CamundaRestClient.startProcessInstance(body);

        return new ResponseEntity<>(businessProcessInstance, HttpStatus.OK);
    }

    private ResponseEntity<ModelApiResponse> getApiResponse() {
        ModelApiResponse apiResponse = new ModelApiResponse();
        apiResponse.setType("SUCCESS");
        apiResponse.setMessage("Successful operation");
        apiResponse.setCode(200);
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }
}
