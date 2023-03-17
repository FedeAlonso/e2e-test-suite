package io.managed.services.test.client.registry;

import com.openshift.cloud.api.registry.instance.models.RoleMapping;
import com.openshift.cloud.api.registry.instance.models.ArtifactMetaData;
import com.openshift.cloud.api.registry.instance.models.ContentCreateRequest;

import io.managed.services.test.client.BaseApi;
import io.managed.services.test.client.exception.ApiGenericException;
import io.managed.services.test.client.exception.ApiUnknownException;

import com.openshift.cloud.api.registry.instance.ApiClient;
import java.util.concurrent.TimeUnit;

public class RegistryClient2 extends BaseApi {

    private final ApiClient apiClient;

    public RegistryClient2(ApiClient apiClient, String offlineToken) {
        super(offlineToken);
        this.apiClient = apiClient;
    }

    @Override
    protected ApiUnknownException toApiException(Exception e) {
        if (e instanceof com.microsoft.kiota.ApiException) {
            var err = (com.microsoft.kiota.ApiException) e.getCause();
            return new ApiUnknownException(err.getMessage(), "", err.responseStatusCode, "", "", err);
        }

        return null;
    }

    public ArtifactMetaData createArtifact(ContentCreateRequest data) throws ApiGenericException {
        String groupIdd = "default";

        return retry(() -> apiClient.groups(groupIdd).artifacts().post(data, config -> config.headers.add("X-Registry-ArtifactType", "JSON")).get(5, TimeUnit.SECONDS));
    }



    public void createRoleMapping(RoleMapping data) throws ApiGenericException {
        retry(() -> apiClient.admin().roleMappings().post(data));
    }
}
