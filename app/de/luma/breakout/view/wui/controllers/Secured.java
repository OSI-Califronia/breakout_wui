package de.luma.breakout.view.wui.controllers; 

import play.mvc.Http.Context;
import play.mvc.Result;
import play.mvc.Security;

/**
 * Methods marked with this attribute will require a user login.
 */
public class Secured extends Security.Authenticator {
	
        @Override
        public String getUsername(Context ctx) {
            return ctx.session().get("UserName");
        }

        @Override
        public Result onUnauthorized(Context ctx) {
            return redirect(routes.Application.login());
        }
    }
