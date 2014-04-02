package de.luma.breakout.view.wui.controllers;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import play.api.Play;
import play.data.DynamicForm;
import play.data.Form;
import play.libs.F;
import play.libs.OpenID;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;

import com.google.gson.Gson;

import de.luma.breakout.communication.GAME_STATE;
import de.luma.breakout.communication.MENU_ITEM;
import de.luma.breakout.controller.GameController;
import de.luma.breakout.controller.IGameController;
import de.luma.breakout.controller.IGameController.PLAYER_INPUT;
import de.luma.breakout.data.user.User;

/**
 * Main controller of Play application 
 */
public class Application extends Controller  {
    
	private static IGameController gameController;	  // static game instance
	private static final String USER_NAME = "luma.webtech";
	private static final String USER_PW = "900150983cd24fb0d6963f7d28e17f72";
	
	static {
		getGameController();
	}
	
	
	/**
	 * Initializes the global game instance.
	 */
	private static IGameController getGameController() {
		if (gameController == null) {
			if (Play.current().path().getAbsolutePath().startsWith("/app")) {
				gameController = new GameController("/app/");			
			} else {
				gameController = new GameController("");
			}
			
			// Open Swing GUI of game
//			MainWindow mainWindow = new MainWindow(gameController);                
//			gameController.addObserver(mainWindow.getBpaGameView2D());
//			mainWindow.setVisible(true);
            
        	gameController.initialize();	
		}
		return gameController;
	}
	
	
	// ##########################  FORMS AUTHENTICATION HANDLERS ###########################
	
	/**
	 * Returns name/email of logged in user or empty string.
	 */
    public static String getActiveUser() {
        if(session("UserName") != null && !session("UserName").equals("")) {
          return session("UserName");
        }
        return "";
    }
	
    /**
     * GET: /login 
     * Shows login page
     */
	public static Result login() {
		// redirect to index if already logged in
		if (!getActiveUser().equals("")) {
			return redirect(routes.Application.socket_index());
		}
		
		return ok(de.luma.breakout.view.wui.views.html.login.render(""));
	}
	
	/**
	 * GET:  /logout
	 * Terminate a user session.
	 */
	public static Result logout() {
		session().clear();
		return redirect(routes.Application.login());
	}
	
	/**
	 * POST: /processLogin
	 * Processes a forms login.
	 */
	public static Result processLogin() {
		// get form data from request
		Form<User> filledForm = DynamicForm.form(User.class).bindFromRequest();		
		User user = filledForm.get();

		if (user.getUsername().equals(USER_NAME) && user.getPassword().equals(USER_PW)) {  // login is correct
			session().clear();
            session("UserName", user.getUsername());            
			return redirect(routes.Application.socket_index());
		}
		
		return ok(de.luma.breakout.view.wui.views.html.login.render("Username or password are wrong."));
	}
	
	
	// ####################  HANDLERS FOR OPEN ID AUTHENTICATION ##################

	/**
	 * GET: /auth
	 * Show login page for OpenID authentication
	 */
	public static Result openid_auth() {
		String providerUrl = "https://www.google.com/accounts/o8/id";
		String returnToUrl = routes.Application.openid_verify().absoluteURL(request());
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put("Email", "http://schema.openid.net/contact/email");
		attributes.put("FirstName", "http://schema.openid.net/namePerson/first");
		attributes.put("LastName", "http://schema.openid.net/namePerson/last");
		F.Promise<String> redirectUrl = OpenID.redirectURL(providerUrl, returnToUrl, attributes);
		return redirect(redirectUrl.get());
	}
	
	/**
	 * GET: /verify
	 * Callback action for OpenID provider
	 */
	public Result openid_verify() {
		F.Promise<OpenID.UserInfo> userInfoPromise = OpenID.verifiedId();
		OpenID.UserInfo userInfo = userInfoPromise.get();
		session().clear();
		session("UserName", userInfo.attributes.get("Email"));
		return redirect(routes.Application.socket_index());
	}


	// #################### ACTIONS FOR WEBSOCKET VERSION ##########################

	/**
	 * GET: /socket_index
	 * Returns the Websocket-based main page layout of the game
	 */
    @play.mvc.Security.Authenticated(Secured.class)
    public static Result socket_index() {
    	return ok(de.luma.breakout.view.wui.views.html.socket_index.render());
    }
    
    /**
     * GET: /socket_connect
     * Initializes a new WebSocket connection to the running game
     */
    public static WebSocket<String> socket_connect() {
    	return new GameWebSocket(getGameController());
    }
    

	// ########################## ACTIONS FOR AJAX-POLLING VERSION  ###########################
		
	/**
	 * GET: /index
	 * Returns AJAX-based main page layout of the game
	 */
	@play.mvc.Security.Authenticated(Secured.class)
    public static Result index() {		
    	return ok(de.luma.breakout.view.wui.views.html.index.render());
    }
    
	
    /** 
     * GET: /playGrid
     * Returns content for #PlayGrid div  (game, level select or menu)
     */
    public static Result playGrid() {
   	
    	if (getGameController().getState() == GAME_STATE.RUNNING) {   // render playgrid (game is running)
    		return ok(de.luma.breakout.view.wui.views.html.gamegrid.render(
    				getGameController().getGridSize().width,
    				getGameController().getGridSize().height,
    				HtmlHelper.getBricks(gameController), HtmlHelper.getBalls(gameController)));
    		
    	} else if (getGameController().getState() == GAME_STATE.MENU_LEVEL_SEL) { // render level selection dialog
    		return getLevels();
    		
    	} else {  // render menu items
    		return ok(de.luma.breakout.view.wui.views.html.menu.render(HtmlHelper.getMenu(
    				gameController.getGameMenu().getMenuItems(),
    				gameController.getGameMenu().getTitle())));
    	}
    }
   
    /**
     * Returns JSON-formatted level list.
     */
    private static Result getLevels() {       	
    	Gson gson = new Gson();
    	System.out.println("call get LevelList");
    	List<String> levels = getGameController().getLevelList();
    	System.out.println("levels count: " + levels.size());
    	
    	
    	String json = gson.toJson(levels);
    	response().setContentType("Application.levellist");
 		return ok(json); 
    }
    
    /**
     * GET: /loadLevel?file=[FileName]
     * Loads the specified level file
     */
    public static Result loadLevel(String file) {
    	getGameController().loadLevel(new File(file));
    	getGameController().processMenuInput(MENU_ITEM.MNU_CONTINUE);
    	return ok();
    }
    
    
    /**
     * GET: /selectmenu?index=[MenuItemIndex]
     * Processes a click on a menu button from AJAX-based view
     */
    public static Result selectmenu(String index) {
    	int itemIndex = Integer.valueOf(index);
//    	
    	MENU_ITEM item = MENU_ITEM.values()[itemIndex];
//    	return ok( item + " index: " + itemIndex);
//    	
    	getGameController().processMenuInput(item);
    	
    	return index();
    }
    
    /**
     * GET: /gameInput?key=[KeyCode]
     * Processes a key event on the play grid
     */
    public static Result gameInput(String key) {
    	if (key == null) {
    		return ok();
    	}
    	
    	if (key.equals("escape")) {
			getGameController().processGameInput(PLAYER_INPUT.PAUSE);
    	} else if (key.equals("right")) {
			getGameController().processGameInput(PLAYER_INPUT.RIGHT);
			getGameController().processGameInput(PLAYER_INPUT.RIGHT);
		} else if (key.equals("left")) {
			getGameController().processGameInput(PLAYER_INPUT.LEFT);
			getGameController().processGameInput(PLAYER_INPUT.LEFT);
		}		
    	return ok();
    }
  
    public static Result test() {
    	return ok(Play.current().path().getAbsolutePath());
    }
}
