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
package io.gravitee.am.gateway.handler.account.resources.account;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionTokenService;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.model.User;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.Single;
import io.vertx.reactivex.ext.web.RoutingContext;

public class AccountEndpointHandler {
    private UserService userService;
    private IntrospectionTokenService introspectionTokenService;

    public AccountEndpointHandler(UserService userService, IntrospectionTokenService introspectionTokenService) {
        this.userService = userService;
        this.introspectionTokenService = introspectionTokenService;
    }

    private Single<String> getUserIdFromToken(RoutingContext routingContext){
        return introspectionTokenService
                .introspect(routingContext.request().getHeader("Authorization"), false)
                .map(JWT::getSub);
    }

    private Single<User> getUser(RoutingContext routingContext) {
        //DJ CHECK Maybe null handling and doOnError
        return getUserIdFromToken(routingContext)
                .map(tokenSub -> userService.findById(tokenSub))
                .flatMap(userMaybe -> userMaybe.doOnError(er -> {/*HANDLE ERROR*/}).toSingle());
    }

    public void getAccount(RoutingContext routingContext) {
        getUser(routingContext).subscribe(user -> {
            //do account logic and respond
            routingContext.response()
                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                    .putHeader(HttpHeaders.PRAGMA, "no-cache")
                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .end("{ \"temp\" : true }");
        });
    }

    public void getAsset(RoutingContext routingContext) {
        getUser(routingContext).subscribe(user -> {
            //do account logic and respond
            routingContext.response()
                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                    .putHeader(HttpHeaders.PRAGMA, "no-cache")
                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .end("{ \"temp\" : true }");
        });
    }

    public void getProfile(RoutingContext routingContext) {
        getUser(routingContext).subscribe(user -> {
            //do account logic and respond
            routingContext.response()
                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                    .putHeader(HttpHeaders.PRAGMA, "no-cache")
                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .end("{ \"temp\" : true }");
        });
    }

    public void getActivity(RoutingContext routingContext) {
        getUser(routingContext).subscribe(user -> {
            //do account logic and respond
            routingContext.response()
                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                    .putHeader(HttpHeaders.PRAGMA, "no-cache")
                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .end("{ \"temp\" : true }");
        });
    }

    public void getDevices(RoutingContext routingContext) {
        getUser(routingContext).subscribe(user -> {
            //do account logic and respond
            routingContext.response()
                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                    .putHeader(HttpHeaders.PRAGMA, "no-cache")
                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .end("{ \"temp\" : true }");
        });
    }

    public void getChangePassword(RoutingContext routingContext) {
        getUser(routingContext).subscribe(user -> {
            //do account logic and respond
            routingContext.response()
                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                    .putHeader(HttpHeaders.PRAGMA, "no-cache")
                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .end("{ \"temp\" : true }");
        });
    }

    public void updateDevices(RoutingContext routingContext) {
        getUser(routingContext).subscribe(user -> {
            //do account logic and respond
            routingContext.response()
                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                    .putHeader(HttpHeaders.PRAGMA, "no-cache")
                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .end("{ \"temp\" : true }");
        });
    }

    public void updateProfile(RoutingContext routingContext) {
        getUser(routingContext).subscribe(user -> {
            //do account logic and respond
            routingContext.response()
                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                    .putHeader(HttpHeaders.PRAGMA, "no-cache")
                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .end("{ \"temp\" : true }");
        });
    }
}
