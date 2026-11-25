package com.johnnyblabs.openspec.ai;

import com.intellij.openapi.project.Project;

import java.net.http.HttpClient;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/** For tests outside this package: a real {@link DirectApiService} whose requests go to the given client. */
public final class DirectApiTestSupport {

    private DirectApiTestSupport() {
    }

    public static DirectApiService serviceSendingTo(Project project, HttpClient client) {
        DirectApiService service = spy(new DirectApiService(project));
        doReturn(client).when(service).createHttpClient();
        return service;
    }
}
