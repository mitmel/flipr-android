package edu.mit.mobile.android.livingpostcards.auth;

import edu.mit.mobile.android.locast.accounts.AbsLocastAuthenticationService;
import edu.mit.mobile.android.locast.accounts.AbsLocastAuthenticator;

public class AuthenticationService extends AbsLocastAuthenticationService {

    @Override
    protected AbsLocastAuthenticator getAuthenticator(AbsLocastAuthenticationService service) {
        return new Authenticator(service);
    }
}
