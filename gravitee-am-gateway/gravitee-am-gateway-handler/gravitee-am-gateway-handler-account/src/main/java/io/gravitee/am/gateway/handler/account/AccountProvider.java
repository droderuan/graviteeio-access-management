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
package io.gravitee.am.gateway.handler.account;

import io.gravitee.am.gateway.handler.account.resources.account.AccountEndpointHandler;
import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionTokenService;
import io.gravitee.am.gateway.handler.common.oauth2.impl.IntrospectionTokenServiceImpl;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.common.service.AbstractService;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import org.springframework.beans.factory.annotation.Autowired;

public class AccountProvider extends AbstractService<ProtocolProvider> implements ProtocolProvider {
    @Override
    public String path() {
        return "/account";
    }

    @Autowired
    private Vertx vertx;

    @Autowired
    private Router router;

    @Autowired
    private UserService userService;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // Create the Users router
        final Router accountRouter = Router.router(vertx);
        final AccountEndpointHandler accountHandler = new AccountEndpointHandler(userService, new IntrospectionTokenServiceImpl());

        // user consent routes
        accountRouter.get().handler(accountHandler::getAccount);
        accountRouter.get("/assets/**").handler(accountHandler::getAsset);
        accountRouter.get("/api/profile").handler(accountHandler::getProfile);
        accountRouter.put("/api/profile").handler(accountHandler::updateProfile);
        accountRouter.get("/api/activity").handler(accountHandler::getActivity);
        accountRouter.get("/api/devices").handler(accountHandler::getDevices);
        accountRouter.put("/api/devices").handler(accountHandler::updateDevices);
        accountRouter.post("/api/changePassword").handler(accountHandler::getChangePassword);

        // error handler
        accountRouter.route().failureHandler(new ErrorHandler());


        router.mountSubRouter(path(), accountRouter);
    }
}
