package io.managed.services.test;


import com.openshift.cloud.v1alpha.models.CloudServiceAccountRequest;
import com.openshift.cloud.v1alpha.models.CloudServiceAccountRequestSpec;
import com.openshift.cloud.v1alpha.models.CloudServicesRequest;
import com.openshift.cloud.v1alpha.models.CloudServicesRequestSpec;
import com.openshift.cloud.v1alpha.models.Credentials;
import com.openshift.cloud.v1alpha.models.KafkaConnection;
import com.openshift.cloud.v1alpha.models.KafkaConnectionSpec;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.managed.services.test.client.oauth.KeycloakOAuth;
import io.managed.services.test.framework.LogCollector;
import io.managed.services.test.framework.TestTag;
import io.managed.services.test.operator.OperatorUtils;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.ext.auth.User;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.managed.services.test.TestUtils.waitFor;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


@Tag(TestTag.BINDING_OPERATOR)
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.MINUTES)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BindingOperatorTest extends TestBase {
    private static final Logger LOGGER = LogManager.getLogger(BindingOperatorTest.class);

    // use the kafka long living instance
    static final String KAFKA_INSTANCE_NAME = "mk-e2e-ll-" + Environment.KAFKA_POSTFIX_NAME;

    User user;
    KubernetesClient client;
    boolean accessToken;

    final static String ACCESS_TOKEN_SECRET_NAME = "mk-e2e-api-accesstoken";
    final static String CLOUD_SERVICE_ACCOUNT_REQUEST_NAME = "mk-e2e-service-account-request";
    final static String SERVICE_ACCOUNT_NAME = "mk-e2e-service-account";
    final static String SERVICE_ACCOUNT_SECRET_NAME = "mk-e2e-service-account-secret";
    final static String CLOUD_SERVICES_REQUEST_NAME = "mk-e2e-kafka-request";
    final static String KAFKA_CONNECTION_NAME = "mk-e2e-kafka-connection";

    private void assertENVs() {
        assumeTrue(Environment.SSO_USERNAME != null, "the SSO_USERNAME env is null");
        assumeTrue(Environment.SSO_PASSWORD != null, "the SSO_PASSWORD env is null");
        assumeTrue(Environment.DEV_CLUSTER_TOKEN != null, "the DEV_CLUSTER_TOKEN env is null");
    }

    private void assertClient() {
        assumeTrue(client != null, "client is null because the bootstrap method has failed");
    }

    private void assertUser() {
        assumeTrue(user != null, "user is null because the bootstrap method has failed");
    }

    private void assertAccessToken() {
        assumeTrue(accessToken, "accessToken is false because the createAccessTokenSecret method has failed");
    }

    private Future<Void> bootstrapUser(Vertx vertx) {

        KeycloakOAuth auth = new KeycloakOAuth(vertx,
                Environment.SSO_REDHAT_KEYCLOAK_URI,
                Environment.SSO_REDHAT_REDIRECT_URI,
                Environment.SSO_REDHAT_REALM,
                Environment.SSO_REDHAT_CLIENT_ID);

        LOGGER.info("authenticate user: {} against: {}", Environment.SSO_USERNAME, Environment.SSO_REDHAT_KEYCLOAK_URI);
        return auth.login(Environment.SSO_USERNAME, Environment.SSO_PASSWORD)
                .onSuccess(u -> user = u)
                .map(__ -> null);
    }

    private void bootstrapK8sClient() {

        Config config = new ConfigBuilder()
                .withMasterUrl(Environment.DEV_CLUSTER_SERVER)
                .withOauthToken(Environment.DEV_CLUSTER_TOKEN)
                .withNamespace(Environment.DEV_CLUSTER_NAMESPACE)
                .build();

        LOGGER.info("initialize kubernetes client");
        client = new DefaultKubernetesClient(config);
    }

    @BeforeAll
    void bootstrap(Vertx vertx, VertxTestContext context) {
        assertENVs();

        bootstrapUser(vertx)
                .onSuccess(__ -> bootstrapK8sClient())
                .onComplete(context.succeedingThenComplete());
    }

    private Future<Void> cleanAccessTokenSecret() {
        try {
            Secret s = client.secrets().withName(ACCESS_TOKEN_SECRET_NAME).get();
            if (s != null) {
                LOGGER.info("clean secret: {}", s.getMetadata().getName());
                client.secrets().delete(s);
            }
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
        return Future.succeededFuture();
    }

    private Future<Void> cleanCloudServiceAccountRequest() {
        try {
            var a = OperatorUtils.cloudServiceAccountRequest(client).withName(CLOUD_SERVICE_ACCOUNT_REQUEST_NAME).get();
            if (a != null) {
                LOGGER.info("clean CloudServiceAccountRequest: {}", a.getMetadata().getName());
                OperatorUtils.cloudServiceAccountRequest(client).delete(a);
            }
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
        return Future.succeededFuture();
    }

    private Future<Void> cleanCloudServicesRequest() {
        try {
            var k = OperatorUtils.cloudServicesRequest(client).withName(CLOUD_SERVICES_REQUEST_NAME).get();
            if (k != null) {
                LOGGER.info("clean CloudServicesRequest: {}", k.getMetadata().getName());
                OperatorUtils.cloudServicesRequest(client).delete(k);
            }
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
        return Future.succeededFuture();
    }

    private Future<Void> cleanManagedKafkaConnection() {
        try {
            var c = OperatorUtils.kafkaConnection(client).withName(KAFKA_CONNECTION_NAME).get();
            if (c != null) {
                LOGGER.info("clean ManagedKafkaConnection: {}", c.getMetadata().getName());
                OperatorUtils.kafkaConnection(client).delete(c);
            }
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
        return Future.succeededFuture();
    }

    private Future<Void> collectOperatorLogs(ExtensionContext context) {
        try {
            LogCollector.saveDeploymentLog(
                    TestUtils.getLogPath(Environment.LOG_DIR.resolve("test-logs").toString(), context),
                    client,
                    "openshift-operators",
                    "service-binding-operator");
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
        return Future.succeededFuture();
    }

    @AfterAll
    void teardown(VertxTestContext context, ExtensionContext testContext) {
        CompositeFuture.join(
                cleanManagedKafkaConnection(),
                cleanCloudServicesRequest(),
                cleanCloudServiceAccountRequest(),
                cleanAccessTokenSecret())

                .eventually(__ -> collectOperatorLogs(testContext))

                .onComplete(context.succeedingThenComplete());
    }

    @Test
    @Order(1)
    void createAccessTokenSecret() {
        assertClient();
        assertUser();

        // Create Secret
        Map<String, String> data = new HashMap<>();
        data.put("value", Base64.getEncoder().encodeToString(KeycloakOAuth.getRefreshToken(user).getBytes()));

        LOGGER.info("create access token secret with name: {}", ACCESS_TOKEN_SECRET_NAME);
        client.secrets().create(OperatorUtils.buildSecret(ACCESS_TOKEN_SECRET_NAME, data));

        accessToken = true;
    }

    @Test
    @Order(2)
    @Timeout(value = 5, timeUnit = TimeUnit.MINUTES)
    void createCloudServiceAccountRequest(Vertx vertx, VertxTestContext context) {
        assertAccessToken();

        var a = new CloudServiceAccountRequest();
        a.getMetadata().setName(CLOUD_SERVICE_ACCOUNT_REQUEST_NAME);
        a.setSpec(new CloudServiceAccountRequestSpec());
        a.getSpec().setServiceAccountName(SERVICE_ACCOUNT_NAME);
        a.getSpec().setServiceAccountDescription("");
        a.getSpec().setServiceAccountSecretName(SERVICE_ACCOUNT_SECRET_NAME);
        a.getSpec().setAccessTokenSecretName(ACCESS_TOKEN_SECRET_NAME);

        LOGGER.info("create CloudServiceAccountRequest with name: {}", CLOUD_SERVICE_ACCOUNT_REQUEST_NAME);
        a = OperatorUtils.cloudServiceAccountRequest(client).create(a);
        LOGGER.info("created CloudServiceAccountRequest: {}", Json.encode(a));

        IsReady<CloudServiceAccountRequest> ready = last -> Future.succeededFuture(OperatorUtils.cloudServiceAccountRequest(client).withName(CLOUD_SERVICE_ACCOUNT_REQUEST_NAME).get())
                .map(r -> {

                    LOGGER.info("CloudServiceAccountRequest status is: {}", Json.encode(r.getStatus()));

                    if (last) {
                        LOGGER.warn("last CloudServiceAccountRequest is: {}", Json.encode(r));
                    }

                    if (r.getStatus() != null && r.getStatus().getMessage().equals("Created")) {
                        return Pair.with(true, r);
                    }
                    return Pair.with(false, null);
                });
        waitFor(vertx, "CloudServiceAccountRequest to complete", ofSeconds(10), ofMinutes(2), ready)
                .onSuccess(r -> LOGGER.info("CloudServiceAccountRequest is ready: {}", Json.encode(r)))
                .onComplete(context.succeedingThenComplete());
    }

    @Test
    @Order(2)
    void createCloudServicesRequest(Vertx vertx, VertxTestContext context) {
        assertAccessToken();

        var k = new CloudServicesRequest();
        k.getMetadata().setName(CLOUD_SERVICES_REQUEST_NAME);
        k.setSpec(new CloudServicesRequestSpec());
        k.getSpec().setAccessTokenSecretName(ACCESS_TOKEN_SECRET_NAME);

        LOGGER.info("create CloudServicesRequest with name: {}", CLOUD_SERVICES_REQUEST_NAME);
        k = OperatorUtils.cloudServicesRequest(client).create(k);
        LOGGER.info("created CloudServicesRequest: {}", Json.encode(k));

        IsReady<CloudServicesRequest> ready = last -> Future.succeededFuture(OperatorUtils.cloudServicesRequest(client).withName(CLOUD_SERVICES_REQUEST_NAME).get())
                .map(r -> {

                    LOGGER.info("CloudServicesRequest status is: {}", Json.encode(r.getStatus()));

                    if (last) {
                        LOGGER.warn("last CloudServicesRequest is: {}", Json.encode(r));
                    }

                    if (r.getStatus() != null
                            && r.getStatus().getUserKafkas() != null
                            && !r.getStatus().getUserKafkas().isEmpty()) {

                        return Pair.with(true, r);
                    }
                    return Pair.with(false, null);
                });
        waitFor(vertx, "CloudServicesRequest to complete", ofSeconds(10), ofMinutes(3), ready)
                .onSuccess(r -> LOGGER.info("CloudServicesRequest is ready: {}", Json.encode(r)))
                .onComplete(context.succeedingThenComplete());
    }


    @Test
    @Order(3)
    void createManagedKafkaConnection(Vertx vertx, VertxTestContext context) {
        assertAccessToken();

        var cloudServicesRequest = OperatorUtils.cloudServicesRequest(client).withName(CLOUD_SERVICES_REQUEST_NAME).get();
        assumeTrue(cloudServicesRequest != null, "the CloudServicesRequest is null");
        assumeTrue(cloudServicesRequest.getStatus() != null, "the CloudServicesRequest status is null");

        var userKafka = cloudServicesRequest.getStatus().getUserKafkas().stream()
                .filter(k -> k.getName().equals(KAFKA_INSTANCE_NAME))
                .findFirst();

        if (userKafka.isEmpty()) {
            LOGGER.info("CloudServicesRequest: {}", Json.encode(cloudServicesRequest));
            fail(String.format("failed to find the user kafka instance %s in the CloudServicesRequest %s", KAFKA_INSTANCE_NAME, CLOUD_SERVICES_REQUEST_NAME));
        }

        var c = new KafkaConnection();
        c.getMetadata().setName(KAFKA_CONNECTION_NAME);
        c.setSpec(new KafkaConnectionSpec());
        c.getSpec().setAccessTokenSecretName(ACCESS_TOKEN_SECRET_NAME);
        c.getSpec().setKafkaId(userKafka.orElseThrow().getId());
        c.getSpec().setCredentials(new Credentials(SERVICE_ACCOUNT_SECRET_NAME));

        LOGGER.info("create ManagedKafkaConnection with name: {}", KAFKA_CONNECTION_NAME);
        c = OperatorUtils.kafkaConnection(client).create(c);
        LOGGER.info("created ManagedKafkaConnection: {}", Json.encode(c));

        IsReady<KafkaConnection> ready = last -> Future.succeededFuture(OperatorUtils.kafkaConnection(client).withName(KAFKA_CONNECTION_NAME).get())
                .map(r -> {

                    LOGGER.info("ManagedKafkaConnection status is: {}", Json.encode(r.getStatus()));

                    if (last) {
                        LOGGER.warn("last ManagedKafkaConnection is: {}", Json.encode(r));
                    }

                    if (r.getStatus() != null
                            && r.getStatus().getMessage() != null
                            && r.getStatus().getMessage().equals("Created")) {

                        return Pair.with(true, r);
                    }
                    return Pair.with(false, null);
                });
        waitFor(vertx, "ManagedKafkaConnection to complete", ofSeconds(10), ofMinutes(2), ready)
                .onSuccess(r -> LOGGER.info("ManagedKafkaConnection is ready: {}", Json.encode(r)))
                .onComplete(context.succeedingThenComplete());
    }
}
