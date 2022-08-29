package io.managed.services.test.devexp;

import com.openshift.cloud.api.kas.auth.models.Record;
import com.openshift.cloud.api.kas.auth.models.Topic;
import com.openshift.cloud.api.kas.models.KafkaRequest;
import com.openshift.cloud.api.kas.models.ServiceAccountListItem;
import io.managed.services.test.Environment;
import io.managed.services.test.TestBase;
import io.managed.services.test.cli.AsyncProcess;
import io.managed.services.test.cli.CLI;
import io.managed.services.test.cli.CLIDownloader;
import io.managed.services.test.cli.CLIUtils;
import io.managed.services.test.cli.CliGenericException;
import io.managed.services.test.cli.CliNotFoundException;
import io.managed.services.test.cli.ServiceAccountSecret;
import io.managed.services.test.client.kafkainstance.KafkaInstanceApiUtils;
import io.managed.services.test.client.kafkamgmt.KafkaMgmtApiUtils;
import io.managed.services.test.client.oauth.KeycloakLoginSession;
import io.managed.services.test.client.securitymgmt.SecurityMgmtAPIUtils;
import io.vertx.core.Vertx;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;
import java.util.Objects;

import static io.managed.services.test.TestUtils.bwait;
import static io.managed.services.test.client.kafka.KafkaMessagingUtils.testTopic;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;


/**
 * Test the application services CLI[1] kafka commands.
 * <p>
 * The tests download the CLI from GitHub to the local machine where the test suite is running
 * and perform all operations using the CLI.
 * <p>
 * By default the latest version of the CLI is downloaded otherwise a specific version can be set using
 * the CLI_VERSION env. The CLI platform (linux, mac, win) and arch (amd64, arm) is automatically detected,
 * or it can be enforced using the CLI_PLATFORM and CLI_ARCH env.
 * <p>
 * 1. https://github.com/redhat-developer/app-services-cli
 * <p>
 * <b>Requires:</b>
 * <ul>
 *     <li> PRIMARY_USERNAME
 *     <li> PRIMARY_PASSWORD
 * </ul>
 */
@Test
public class KafkaCLITest extends TestBase {
    private static final Logger LOGGER = LogManager.getLogger(KafkaCLITest.class);

    private static final String KAFKA_INSTANCE_NAME = "cli-e2e-test-instance-" + Environment.LAUNCH_KEY;
    private static final String SERVICE_ACCOUNT_NAME = "cli-e2e-service-account-" + Environment.LAUNCH_KEY;
    private static final String TOPIC_NAME = "cli-e2e-test-topic";
    // used for testing quickstart for data production and consumption
    private static final String TOPIC_NAME_PRODUCE_CONSUME = "produce-consume-test-topic";
    private static final int DEFAULT_PARTITIONS = 1;
    private static final String CONSUMER_GROUP_NAME = "consumer-group-1";

    private final Vertx vertx = Vertx.vertx();

    private CLI cli;

    private KafkaRequest kafka;
    private ServiceAccountSecret serviceAccountSecret;
    private ServiceAccountListItem serviceAccount;
    private Topic topic;

    private AsyncProcess topicProduceProcess;

    @BeforeClass
    public void bootstrap() {
        assertNotNull(Environment.PRIMARY_USERNAME, "the PRIMARY_USERNAME env is null");
        assertNotNull(Environment.PRIMARY_PASSWORD, "the PRIMARY_PASSWORD env is null");
    }

    @AfterClass(alwaysRun = true)
    @SneakyThrows
    public void clean() {

        var auth = new KeycloakLoginSession(Environment.PRIMARY_USERNAME, Environment.PRIMARY_PASSWORD);
        var user = bwait(auth.loginToRedHatSSO());

        var kafkaMgmtApi =  KafkaMgmtApiUtils.kafkaMgmtApi(Environment.OPENSHIFT_API_URI, user);
        var securityMgmtApi = SecurityMgmtAPIUtils.securityMgmtApi(Environment.OPENSHIFT_API_URI, user);

        //try {
        //    KafkaMgmtApiUtils.deleteKafkaByNameIfExists(kafkaMgmtApi, KAFKA_INSTANCE_NAME);
        //} catch (Throwable t) {
        //    LOGGER.error("delete kafka instance error: ", t);
        //}

        try {
            SecurityMgmtAPIUtils.cleanServiceAccount(securityMgmtApi, SERVICE_ACCOUNT_NAME);
        } catch (Throwable t) {
            LOGGER.error("delete service account error: ", t);
        }

        // delete topic used in quickstart
        try {
            cli.deleteTopic(TOPIC_NAME_PRODUCE_CONSUME);
        } catch (Throwable t) {
            LOGGER.error("delete topic error: ", t);
        }



        //try {
        //    LOGGER.info("logout user from rhoas");
        //    cli.logout();
        //} catch (Throwable t) {
        //    LOGGER.error("CLI logout error: ", t);
        //}

        try {
            LOGGER.info("delete workdir: {}", cli.getWorkdir());
            FileUtils.deleteDirectory(new File(cli.getWorkdir()));
        } catch (Throwable t) {
            LOGGER.error("clean workdir error: ", t);
        }

        bwait(vertx.close());
    }

    @Test
    @SneakyThrows
    public void testDownloadCLI() {

        var downloader = CLIDownloader.defaultDownloader();

        // download the cli
        var binary = downloader.downloadCLIInTempDir();

        this.cli = new CLI(binary);

        LOGGER.info("validate cli");
        LOGGER.debug(cli.help());
    }


    @Test(dependsOnMethods = "testDownloadCLI", enabled = true)
    @SneakyThrows
    public void testLogin() {

        //LOGGER.info("verify that we aren't logged-in");
        //assertThrows(CliGenericException.class, () -> cli.listKafka());
        //
        LOGGER.info("login the CLI");
        CLIUtils.login(vertx, cli, Environment.PRIMARY_USERNAME, Environment.PRIMARY_PASSWORD).get();

        LOGGER.info("verify that we are logged-in");
        cli.listKafka();
    }

    @Test(dependsOnMethods = "testLogin", enabled = true)
    @SneakyThrows
    public void testCreateServiceAccount() {

        LOGGER.info("create a service account");
        serviceAccountSecret = CLIUtils.createServiceAccount(cli, SERVICE_ACCOUNT_NAME);

        LOGGER.info("get the service account");
        var sa = CLIUtils.getServiceAccountByName(cli, SERVICE_ACCOUNT_NAME);
        LOGGER.debug(sa);

        assertTrue(sa.isPresent());
        assertEquals(sa.get().getName(), SERVICE_ACCOUNT_NAME);
        assertEquals(sa.get().getClientId(), serviceAccountSecret.getClientID());

        serviceAccount = sa.get();
    }

    @Test(dependsOnMethods = "testCreateServiceAccount", enabled = false)
    @SneakyThrows
    public void testDescribeServiceAccount() {

        var sa = cli.describeServiceAccount(serviceAccount.getId());
        LOGGER.debug(sa);

        assertEquals(sa.getName(), SERVICE_ACCOUNT_NAME);
    }

    @Test(dependsOnMethods = "testLogin", enabled = true)
    @SneakyThrows
    public void testCreateKafkaInstance() {

        LOGGER.info("create kafka instance with name {}", KAFKA_INSTANCE_NAME);
        //var k = cli.createKafka(KAFKA_INSTANCE_NAME);
        var k = cli.describeKafka("cc4bn5aui4bfdhakpohg");
        LOGGER.debug(k);

        LOGGER.info("wait for kafka instance: {}", k.getId());
        kafka = CLIUtils.waitUntilKafkaIsReady(cli, k.getId());
        LOGGER.debug(kafka);
    }

    @Test(dependsOnMethods = {"testCreateKafkaInstance", "testCreateServiceAccount"}, enabled = true)
    @SneakyThrows
    public void testGrantProducerAndConsumerAccess() {
        LOGGER.info("grant producer and consumer access to the account: {}", serviceAccount.getClientId());
        cli.grantProducerAndConsumerAccess(serviceAccount.getClientId(), "all", "all");

        var acl = cli.listACLs();
        LOGGER.debug(acl);
    }

    @Test(dependsOnMethods = {"testCreateKafkaInstance", "testCreateServiceAccount"}, enabled = true)
    @SneakyThrows
    public void testProducePlainMessages(){
        LOGGER.info("create topic '{}' with 2 partitions and other default configuration", TOPIC_NAME_PRODUCE_CONSUME);
        CLIUtils.applyTopic (cli, TOPIC_NAME_PRODUCE_CONSUME, 2);

        var messages = List.of("First message", "Second message", "Third message");

        LOGGER.info("produce messages");
        int i = 0;
        for (String message: messages) {
            // produce single message
            LOGGER.info("Produce message '{}'", message);
            Record record =  cli.writeRecord(TOPIC_NAME_PRODUCE_CONSUME, kafka.getId(), message);
            LOGGER.debug(record);

            assertEquals(record.getValue(), message);
            assertEquals(record.getOffset(), i++);
            // if no partition is provided expected value is 0
            assertEquals(record.getPartition(), 0);
        }
    }

    @Test(dependsOnMethods = {"testCreateKafkaInstance", "testCreateServiceAccount"}, enabled = true)
    @SneakyThrows
    public void testProduceCustomMessage(){
        LOGGER.info("create topic '{}' with 2 partitions and other default configuration", TOPIC_NAME_PRODUCE_CONSUME);
        CLIUtils.applyTopic (cli, TOPIC_NAME_PRODUCE_CONSUME, 2);

        int partition  = 1;
        String keyValue = "my-key";

        Record record =  cli.writeRecord(TOPIC_NAME_PRODUCE_CONSUME, kafka.getId(), "someValue", partition, keyValue);
        LOGGER.debug(record);

        assertEquals(record.getPartition(), partition);
        assertEquals(record.getKey(), keyValue);
    }

    //@Test(dependsOnMethods = {"testCreateKafkaInstance", "testCreateServiceAccount"}, enabled = true)
    //@SneakyThrows
    //public void testProduceCustomizedMessages(){
    //    LOGGER.info("create topic '{}' with 2 partitions and other default configuration", TOPIC_NAME_PRODUCE_CONSUME);
    //    CLIUtils.applyTopic(cli, TOPIC_NAME_PRODUCE_CONSUME, 2);
    //
    //    LOGGER.info("produce message with key and selected partition");
    //    AsyncProcess topicProduceProcess = cli.startCustomMessageProduction(TOPIC_NAME_PRODUCE_CONSUME, "key1", 1);
    //
    //
    //    var stdin = topicProduceProcess.stdin();
    //    stdin.write("fourth message");
    //    stdin.close();
    //    // return code
    //    var returnedCode = topicProduceProcess.future(Duration.ofSeconds(5)).get().waitFor();
    //
    //    // cleanup process and dump logs
    //    try {
    //        topicProduceProcess.destroy();
    //        LOGGER.info("producer output:\n{}", topicProduceProcess.outputAsString());
    //    } catch (Throwable t) {
    //        LOGGER.error("close producer error:", t);
    //    }
    //
    //    Assert.assertEquals(returnedCode, 0);
    //}

    @Test(dependsOnMethods = "testCreateKafkaInstance", enabled = true)
    @SneakyThrows
    public void testDescribeKafkaInstance() {

        LOGGER.info("get kafka instance with name {}", KAFKA_INSTANCE_NAME);
        var k = cli.describeKafka(kafka.getId());
        LOGGER.debug(k);

        assertEquals("ready", k.getStatus());
    }

    @Test(dependsOnMethods = "testCreateKafkaInstance", enabled = false)
    @SneakyThrows
    public void testListKafkaInstances() {

        var list = cli.listKafka();
        LOGGER.debug(list);

        var exists = list.getItems().stream()
            .filter(k -> KAFKA_INSTANCE_NAME.equals(k.getName()))
            .findAny();
        assertTrue(exists.isPresent());
    }

    @Test(dependsOnMethods = "testCreateKafkaInstance", enabled = false)
    @SneakyThrows
    public void testSearchKafkaByName() {

        var list = cli.searchKafkaByName(KAFKA_INSTANCE_NAME);
        LOGGER.debug(list);

        var exists = list.getItems().stream().findAny();
        assertTrue(exists.isPresent());
        assertEquals(exists.get().getName(), KAFKA_INSTANCE_NAME);
    }

    @Test(dependsOnMethods = "testCreateKafkaInstance", enabled = false)
    @SneakyThrows
    public void testCreateTopic() {

        LOGGER.info("create kafka topic with name {}", KAFKA_INSTANCE_NAME);
        topic = cli.createTopic(TOPIC_NAME);
        LOGGER.debug(topic);

        assertEquals(topic.getName(), TOPIC_NAME);
        assertEquals(Objects.requireNonNull(topic.getPartitions()).size(), DEFAULT_PARTITIONS);
    }

    @Test(dependsOnMethods = "testCreateTopic", enabled = false)
    @SneakyThrows
    public void testListTopics() {

        var list = cli.listTopics();
        LOGGER.debug(list);

        var exists = Objects.requireNonNull(list.getItems()).stream()
            .filter(t -> TOPIC_NAME.equals(t.getName()))
            .findAny();
        assertTrue(exists.isPresent());
    }


    @Test(dependsOnMethods = {"testCreateTopic", "testGrantProducerAndConsumerAccess"}, enabled = false)
    @SneakyThrows
    public void testKafkaInstanceTopic() {

        var bootstrapHost = kafka.getBootstrapServerHost();
        var clientID = serviceAccountSecret.getClientID();
        var clientSecret = serviceAccountSecret.getClientSecret();

        bwait(testTopic(
            vertx,
            bootstrapHost,
            clientID,
            clientSecret,
            TOPIC_NAME,
            1000,
            10,
            100));
    }

    @Test(dependsOnMethods = "testCreateTopic", enabled = false)
    @SneakyThrows
    public void testUpdateTopic() {

        var retentionTime = "4";
        var retentionKey = "retention.ms";

        LOGGER.info("update kafka topic with name {}", TOPIC_NAME);
        cli.updateTopic(TOPIC_NAME, retentionTime);
        var t = cli.describeTopic(TOPIC_NAME);
        LOGGER.debug(t);

        var retentionValue = Objects.requireNonNull(t.getConfig())
            .stream()
            .filter(conf -> retentionKey.equals(conf.getKey()))
            .findFirst();

        assertTrue(retentionValue.isPresent(), "updated config not found");
        assertEquals(retentionValue.get().getValue(), retentionTime);

        topic = t;
    }

    @Test(dependsOnMethods = "testUpdateTopic", enabled = false)
    @SneakyThrows
    public void testDescribeUpdatedTopic() {

        var retentionTime = "4";
        var retentionKey = "retention.ms";

        LOGGER.info("describe kafka topic with name {}", TOPIC_NAME);
        var t = cli.describeTopic(TOPIC_NAME);
        LOGGER.debug(t);

        assertEquals(t.getName(), TOPIC_NAME);
        assertEquals(
            Objects.requireNonNull(t.getPartitions()).size(),
            Objects.requireNonNull(topic.getPartitions()).size());

        var retentionValue = Objects.requireNonNull(t.getConfig())
            .stream()
            .filter(conf -> retentionKey.equals(conf.getKey()))
            .findFirst();

        assertTrue(retentionValue.isPresent(), "updated config not found");
        assertEquals(retentionValue.get().getValue(), retentionTime);
    }

    @Test(dependsOnMethods = {"testUpdateTopic", "testGrantProducerAndConsumerAccess"}, enabled = false)
    @SneakyThrows
    public void testKafkaInstanceUpdatedTopic() {

        var bootstrapHost = kafka.getBootstrapServerHost();
        var clientID = serviceAccountSecret.getClientID();
        var clientSecret = serviceAccountSecret.getClientSecret();

        bwait(testTopic(
            vertx,
            bootstrapHost,
            clientID,
            clientSecret,
            TOPIC_NAME,
            1000,
            10,
            100));
    }

    @Test(dependsOnMethods = {"testGrantProducerAndConsumerAccess"}, enabled = false)
    @SneakyThrows
    public void testDescribeConsumerGroup() {

        var consumer = bwait(KafkaInstanceApiUtils.startConsumerGroup(vertx,
            CONSUMER_GROUP_NAME,
            TOPIC_NAME,
            kafka.getBootstrapServerHost(),
            serviceAccountSecret.getClientID(),
            serviceAccountSecret.getClientSecret()));
        consumer.close();

        var group = CLIUtils.waitForConsumerGroup(cli, CONSUMER_GROUP_NAME);
        LOGGER.debug(group);

        assertEquals(group.getGroupId(), CONSUMER_GROUP_NAME);
    }

    @Test(dependsOnMethods = "testDescribeConsumerGroup", enabled = false)
    @SneakyThrows
    public void testListConsumerGroups() {
        var groups = cli.listConsumerGroups();
        LOGGER.debug(groups);

        var filteredGroup = Objects.requireNonNull(groups.getItems())
            .stream()
            .filter(g -> CONSUMER_GROUP_NAME.equals(g.getGroupId()))
            .findAny();

        assertTrue(filteredGroup.isPresent());
    }

    @Test(dependsOnMethods = "testDescribeConsumerGroup", priority = 1, enabled = false)
    @SneakyThrows
    public void testDeleteConsumerGroup() {

        LOGGER.info("delete consumer group '{}'", CONSUMER_GROUP_NAME);
        cli.deleteConsumerGroup(CONSUMER_GROUP_NAME);

        assertThrows(CliNotFoundException.class,
            () -> cli.describeConsumerGroup(CONSUMER_GROUP_NAME));
    }

    @Test(dependsOnMethods = "testCreateTopic", priority = 2, enabled = false)
    @SneakyThrows
    public void testDeleteTopic() {

        LOGGER.info("delete topic '{}'", TOPIC_NAME);
        cli.deleteTopic(TOPIC_NAME);

        assertThrows(CliNotFoundException.class,
            () -> cli.describeTopic(TOPIC_NAME));
    }

    @Test(dependsOnMethods = "testCreateServiceAccount", priority = 2, enabled = false)
    @SneakyThrows
    public void testDeleteServiceAccount() {

        LOGGER.info("delete service account '{}'", serviceAccount.getId());
        cli.deleteServiceAccount(serviceAccount.getId());

        assertThrows(CliNotFoundException.class,
            () -> cli.describeServiceAccount(serviceAccount.getId()));
    }

    @Test(dependsOnMethods = "testCreateKafkaInstance", priority = 3, enabled = false)
    @SneakyThrows
    public void testDeleteKafkaInstance() {

        LOGGER.info("delete kafka instance '{}'", kafka.getId());
        cli.deleteKafka(kafka.getId());

        CLIUtils.waitUntilKafkaIsDeleted(cli, kafka.getId());
    }

    @Test(dependsOnMethods = "testLogin", priority = 3, enabled = false)
    @SneakyThrows
    public void testLogout() {

        LOGGER.info("verify that we are logged-in");
        cli.listKafka(); // successfully run cli command while logged in

        LOGGER.info("logout");
        cli.logout();

        LOGGER.info("verify that we are logged-in");
        assertThrows(CliGenericException.class, () -> cli.listKafka()); // unable to run the same command after logout
    }
}
