package aps.application.impl;

import aps.application.*;
import aps.application.util.ScrapeResult;
import aps.domain.exception.ApsException;
import aps.domain.model.customer.Customer;
import aps.domain.model.customer.CustomerBillingAccount;
import aps.domain.model.scrape.ScrapeObject;
import aps.domain.model.scrape.ScrapeRepository;
import aps.domain.model.statement.Statement;
import aps.domain.shared.ApplicationConstants;
import aps.domain.shared.GenericXmlParser;
import aps.domain.shared.ScrapeRequest;
import aps.domain.shared.ScrapeResponse;

public class ApsServiceImpl implements IApsService {

    IScrapService scrapService;
    IScrapErrorService scrapErrorService;
    ICustomerService customerService;
    ScrapeRepository scrapeRepository;
    IMappingService mappingService;

    @Override
    public Customer getCustomer(String username) throws ApsException {
        customerService = new CustomerServiceImpl();
        return customerService.getCustomer(username);
    }

    public String scrapeWebsite(Customer customer, String billingCompany) {
        scrapService = new ScrapServiceImpl();
        String scrapeResult = null;

        for (CustomerBillingAccount customerBillingAccount : customer.getCustomerBillingAccounts()) {
            if (customerBillingAccount.getBillingCompany().getName().equals(billingCompany)) {
                scrapeResult = scrapeWebsite(customerBillingAccount);
                break;
            }
        }
        return scrapeResult;
    }

    private String scrapeWebsite(CustomerBillingAccount customerBillingAccount) {
        ScrapeRequest scrapeRequest = new ScrapeRequest();
        //Scraping for the first credential details.
        scrapeRequest.setUserIdentification(customerBillingAccount.getAccountCredentials().get(0).getUserName());
        scrapeRequest.setPassCode(customerBillingAccount.getAccountCredentials().get(0).getPassword());
        scrapeRequest.setBaseUrl(customerBillingAccount.getBillingCompany().getBaseUrl());
        scrapeRequest.setAccountNumber(customerBillingAccount.getAccountNumber());

        ScrapeResponse scrapeResponse = scrapService.scrapWebsite(scrapeRequest);

        ScrapeObject scrapeObject = null;
        if (scrapeResponse.getScrapeResult().equals(ScrapeResult.SUCCESSFUL)) {
            GenericXmlParser genericXmlParser = new GenericXmlParser(ScrapeObject.class);
            scrapeObject = (ScrapeObject) genericXmlParser.parseScrapXml(scrapeResponse.getXmlResponse());
            mappingService = new MappingServiceImpl();
            Statement statement = mappingService.createCustomerStatement(scrapeObject, customerBillingAccount.getBillingCompany().getBillingCompanyType());
            printStatementDetails(statement);
        } else {
            scrapErrorService = new ScrapErrorServiceImpl();
            return scrapErrorService.handleScrapeError(scrapeResponse, customerBillingAccount);
        }
        return ApplicationConstants.SCRAP_SUCCESSFUL + scrapeObject.getBaseUrl();
    }

    /**
     * Prints selected example fields on the console for the user.
     *
     * @param statement
     */
    private void printStatementDetails(Statement statement) {
        System.out.println("******** Statement Details ********");
        System.out.println("Account holder name: " + statement.getAccountHolderName());
        System.out.println("Account number: " + statement.getAccountNumber());
        System.out.println("Total due: " + statement.getTotalDue());
    }
}
