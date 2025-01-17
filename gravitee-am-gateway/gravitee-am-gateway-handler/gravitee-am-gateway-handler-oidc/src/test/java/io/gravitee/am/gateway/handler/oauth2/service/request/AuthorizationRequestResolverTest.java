/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.gateway.handler.oauth2.service.request;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.reactivex.observers.TestObserver;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequestResolverTest {

    private final AuthorizationRequestResolver authorizationRequestResolver = new AuthorizationRequestResolver();

    @Test
    public void shouldNotResolveAuthorizationRequest_unknownScope() {
        final String scope = "read";
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setScopes(Collections.singleton(scope));
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, null).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidScopeException.class);
    }

    @Test
    public void shouldResolveAuthorizationRequest_emptyScope() {
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldResolveAuthorizationRequest_noRequestedScope() {
        final String scope = "read";
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();
        client.setScopes(Collections.singletonList(scope));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(request -> request.getScopes().iterator().next().equals(scope));
    }

    @Test
    public void shouldResolveAuthorizationRequest_invalidScope() {
        final String scope = "read";
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setScopes(Collections.singleton(scope));
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();
        client.setScopes(Collections.singletonList("write"));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, null).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidScopeException.class);
    }

    @Test
    public void shouldResolveAuthorizationRequest_invalidScope_withUser() {
        final String scope = "read";
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setScopes(Collections.singleton(scope));
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();
        client.setScopes(Collections.singletonList("write"));

        User user = new User();
        Role role = new Role();
        role.setOauthScopes(Collections.singletonList("user"));
        user.setRolesPermissions(Collections.singleton(role));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, user).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidScopeException.class);
    }

    @Test
    public void shouldResolveAuthorizationRequest_userPermissionsRequestedAll() {
        final String scope = "read";
        final List<String> userScopes = Arrays.asList("user1", "user2", "user3");
        final String redirectUri = "http://localhost:8080/callback";
        
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        List<String> authScopes = new ArrayList<>();
        authScopes.add(scope);
        authScopes.addAll(userScopes);
        authorizationRequest.setScopes(new HashSet<>(authScopes));

        Client client = new Client();
        client.setScopes(Collections.singletonList(scope));
        client.setEnhanceScopesWithUserPermissions(true);

        User user = new User();
        Role role = new Role();
        role.setOauthScopes(userScopes);
        user.setRolesPermissions(Collections.singleton(role));
        
        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        
        // Request should have been enhanced with all of user's permissions, even though none of them has been requested
        List<String> expectedScopes = new ArrayList<>();
        expectedScopes.add(scope);
        expectedScopes.addAll(userScopes);
        testObserver.assertValue(request -> request.getScopes().containsAll(expectedScopes) && request.getScopes().contains(scope) && request.getScopes().size() == 4);
    }
    
    @Test
    public void shouldResolveAuthorizationRequest_userPermissionsRequestedAny() {
    	final String scope = "read";
        final List<String> userScopes = Arrays.asList("user1", "user2", "user3");
        final String redirectUri = "http://localhost:8080/callback";
        
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        List<String> authScopes = new ArrayList<>();
        authScopes.add(scope);
        authScopes.add(userScopes.get(1)); // Request only the second of the three user scopes
        authorizationRequest.setScopes(new HashSet<>(authScopes));

        Client client = new Client();
        client.setScopes(Collections.singletonList(scope));
        client.setEnhanceScopesWithUserPermissions(true);

        User user = new User();
        Role role = new Role();
        role.setOauthScopes(userScopes);
        user.setRolesPermissions(Collections.singleton(role));
        
        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        
        // Request should have been enhanced with all of user's permissions, even though only one has been requested
        List<String> expectedScopes = new ArrayList<>(authScopes);
        testObserver.assertValue(request -> request.getScopes().containsAll(expectedScopes) && request.getScopes().contains(scope) && request.getScopes().size() == 2);
    }
    
    @Test
    public void shouldResolveAuthorizationRequest_userPermissionsRequestedNone() {
    	final String scope = "read";
        final List<String> userScopes = Arrays.asList("user1", "user2", "user3");
        final String redirectUri = "http://localhost:8080/callback";
        
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        // Request none of the three user scopes
        authorizationRequest.setScopes(new HashSet<>(Arrays.asList(scope)));
        
        Client client = new Client();
        client.setScopes(Collections.singletonList(scope));
        client.setEnhanceScopesWithUserPermissions(true);
        
        User user = new User();
        Role role = new Role();
        role.setOauthScopes(userScopes);
        user.setRolesPermissions(Collections.singleton(role));
        
        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        
        List<String> expectedScopes = new ArrayList<>();
        expectedScopes.add(scope);

        // Request should have been enhanced with all of user's permissions, even though none of them has been requested
        testObserver.assertValue(request -> request.getScopes().containsAll(expectedScopes) && request.getScopes().contains(scope) && request.getScopes().size() == 1);
    }
}
