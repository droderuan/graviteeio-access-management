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
package io.gravitee.am.model.jose;

/**
 * See <a href="https://tools.ietf.org/html/rfc7638#section-3.2">3.2. JWK Members Used in the Thumbprint Computation</a>
 * See <a href="https://tools.ietf.org/html/draft-jones-jose-json-private-and-symmetric-key-00#section-3.1">JWK Parameters for Elliptic Curve Private Keys</a>
 *
 *  The required members for an Elliptic Curve public key, in lexicographic order, are:
 *    - "crv"
 *    - "kty"
 *    - "x"
 *    - "y"
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ECKey extends JWK {

    public ECKey() {
        setKty(KeyType.EC.getKeyType());
    }

    private String crv;
    private String x;
    private String y;

    /**
     * Private key value for the Elliptic private key.
     */
    private String d;

    public String getCrv() {
        return crv;
    }

    public void setCrv(String crv) {
        this.crv = crv;
    }

    public String getX() {
        return x;
    }

    public void setX(String x) {
        this.x = x;
    }

    public String getY() {
        return y;
    }

    public void setY(String y) {
        this.y = y;
    }

    public String getD() {
        return d;
    }

    public void setD(String d) {
        this.d = d;
    }

    public boolean isPrivate() {
        return d != null;
    }
}
