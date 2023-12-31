// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.sample.sync;

import com.azure.identity.DefaultAzureCredential;
import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.sample.common.AccountSettings;
import com.azure.cosmos.sample.common.Families;
import com.azure.cosmos.sample.common.Family;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SyncPasswordlessMain {

    private CosmosClient client;

    private final String databaseName = "AzureSampleFamilyDB";
    private final String containerName = "FamilyContainer";

    private CosmosDatabase database;
    private CosmosContainer container;

    protected static Logger logger = LoggerFactory.getLogger(SyncPasswordlessMain.class.getSimpleName());

    public void close() {
        client.close();
    }

    /**
     * Run a Hello CosmosDB console application.
     *
     * @param args command line args.
     */
    //  <Main>
    public static void main(String[] args) {
        SyncPasswordlessMain p = new SyncPasswordlessMain();

        try {
            logger.info("Starting SYNC main");
            p.getStartedDemo();
            logger.info("Demo complete, please hold while resources are released");
        } catch (Exception e) {
            logger.error("Cosmos getStarted failed with", e);
        } finally {
            logger.info("Closing the client");
            p.close();
        }
        System.exit(0);
    }

    //  </Main>

    private void getStartedDemo() throws Exception {
        logger.info("Using Azure Cosmos DB endpoint: {}", AccountSettings.HOST);

        //  Create sync client
        //  <CreatePasswordlessSyncClient>
        DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().build();

        client = new CosmosClientBuilder()
            .endpoint(AccountSettings.HOST)
            .credential(credential)
            //  Setting the preferred location to Cosmos DB Account region
            //  West US is just an example. User should set preferred location to the Cosmos DB region closest to the application
            .preferredRegions(Collections.singletonList("South Central US"))
            .consistencyLevel(ConsistencyLevel.EVENTUAL)
            .buildClient();

        //  </CreatePasswordlessSyncClient>

        //database and container creation can only be done via control plane
        //ensure you have created a database named AzureSampleFamilyDB
        database = client.getDatabase(databaseName);
        //ensure you have created the container named FamilyContainer, partitioned by /lastName
        container = database.getContainer(containerName);

        //  Setup family items to create
        ArrayList<Family> familiesToCreate = new ArrayList<>();
        familiesToCreate.add(Families.getAndersenFamilyItem());
        familiesToCreate.add(Families.getWakefieldFamilyItem());
        familiesToCreate.add(Families.getJohnsonFamilyItem());
        familiesToCreate.add(Families.getSmithFamilyItem());

        createFamilies(familiesToCreate);

        logger.info("Reading items.");
        readItems(familiesToCreate);

        logger.info("Querying items.");
        queryItems();
    }

    private void createFamilies(List<Family> families) throws Exception {
        double totalRequestCharge = 0;
        for (Family family : families) {

            //  <CreateItem>
            //  Create item using container that we created using sync client

            //  Use lastName as partitionKey for cosmos item
            //  Using appropriate partition key improves the performance of database operations
            CosmosItemRequestOptions cosmosItemRequestOptions = new CosmosItemRequestOptions();
            CosmosItemResponse<Family> item = container.createItem(family, new PartitionKey(family.getLastName()), cosmosItemRequestOptions);
            //  </CreateItem>

            //  Get request charge and other properties like latency, and diagnostics strings, etc.
            logger.info("Created item with request charge of {} within duration {}",
                item.getRequestCharge(), item.getDuration());
            totalRequestCharge += item.getRequestCharge();
        }
        logger.info("Created {} items with total request charge of {}",
            families.size(),
            totalRequestCharge);
    }

    private void readItems(ArrayList<Family> familiesToCreate) {
        //  Using partition key for point read scenarios.
        //  This will help fast look up of items because of partition key
        familiesToCreate.forEach(family -> {
            //  <ReadItem>
            try {
                CosmosItemResponse<Family> item = container.readItem(family.getId(), new PartitionKey(family.getLastName()), Family.class);
                double requestCharge = item.getRequestCharge();
                Duration requestLatency = item.getDuration();
                logger.info("Item successfully read with id {} with a charge of {} and within duration {}",
                    item.getItem().getId(), requestCharge, requestLatency);
            } catch (CosmosException e) {
                logger.error("Read Item failed with", e);
            }
            //  </ReadItem>
        });
    }

    private void queryItems() {
        //  <QueryItems>
        // Set some common query options
        CosmosQueryRequestOptions queryOptions = new CosmosQueryRequestOptions();
        //queryOptions.setEnableCrossPartitionQuery(true); //No longer necessary in SDK v4
        //  Set query metrics enabled to get metrics around query executions
        queryOptions.setQueryMetricsEnabled(true);

        CosmosPagedIterable<Family> familiesPagedIterable = container.queryItems(
            "SELECT * FROM Family WHERE Family.lastName IN ('Andersen', 'Wakefield', 'Johnson')", queryOptions, Family.class);

        familiesPagedIterable.iterableByPage(10).forEach(cosmosItemPropertiesFeedResponse -> {
            logger.info("Got a page of query result with {} items(s) and request charge of {}",
                    cosmosItemPropertiesFeedResponse.getResults().size(), cosmosItemPropertiesFeedResponse.getRequestCharge());

            logger.info("Item Ids {}", cosmosItemPropertiesFeedResponse
                .getResults()
                .stream()
                .map(Family::getId)
                .collect(Collectors.toList()));
        });
        //  </QueryItems>
    }
}
