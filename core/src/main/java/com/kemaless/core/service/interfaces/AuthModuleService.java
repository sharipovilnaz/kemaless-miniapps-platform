package com.kemaless.core.service.interfaces;

import com.kemaless.core.service.auth.DefaultRealmClientCredentials;
import com.kemaless.core.service.auth.GeneratedClientSecret;


public interface AuthModuleService {

    GeneratedClientSecret generateSecret();

    DefaultRealmClientCredentials createDefaultClient(String realmCode);

    void disableAllClients(String realmCode);
}
